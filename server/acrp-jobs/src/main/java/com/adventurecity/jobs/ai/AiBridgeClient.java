package com.adventurecity.jobs.ai;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.util.Json;
import org.bukkit.Bukkit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Talks to the local AI bridge over HTTP.
 *
 * <p>Two rules make this safe for a game server:</p>
 * <ol>
 *   <li>The request never touches the server thread - a blocking HTTP call there would freeze
 *       the whole server for the length of the round trip.</li>
 *   <li>It uses its own small thread pool rather than the storage IO thread, so a slow model
 *       response can never delay a player's balance being written.</li>
 * </ol>
 *
 * <p>Every failure is answered with {@code ok=false}; the caller then uses the NPC's written
 * line. There is no path where an AI problem becomes a gameplay problem.</p>
 */
public final class AiBridgeClient {

    private final ACRPJobsPlugin plugin;
    private final ExecutorService executor;
    private long lastErrorLog;

    public AiBridgeClient(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
        final AtomicInteger counter = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(3, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "ACRPJobs-AI-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public boolean enabled() {
        return plugin.settings().aiEnabled && !plugin.settings().aiUrl.isEmpty();
    }

    /**
     * Sends one line of dialogue. The callback always runs on the server thread.
     *
     * @param context        short facts the NPC may mention (never invented ones)
     * @param allowedTargets legal target ids per intent - the guard rail
     * @param history        recent turns as {role, text} pairs, oldest first
     */
    public void ask(final NpcPersona persona, final String playerId, final String playerName,
                    final String message, final Map<String, String> context,
                    final Map<String, List<String>> allowedTargets, final List<String[]> history,
                    final Consumer<BridgeReply> callback) {

        final String body = buildBody(persona, playerId, playerName, message, context, allowedTargets, history);
        final String url = plugin.settings().aiUrl;
        final String token = plugin.settings().aiToken;
        final int timeout = plugin.settings().aiTimeoutMs;

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final BridgeReply reply = post(url, token, timeout, body);
                if (!plugin.isEnabled()) {
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        callback.accept(reply);
                    }
                });
            }
        });
    }

    private String buildBody(NpcPersona persona, String playerId, String playerName, String message,
                             Map<String, String> context, Map<String, List<String>> allowedTargets,
                             List<String[]> history) {
        Json.Writer targets = new Json.Writer();
        for (Map.Entry<String, List<String>> entry : allowedTargets.entrySet()) {
            targets.array(entry.getKey(), entry.getValue());
        }

        StringBuilder turns = new StringBuilder("[");
        boolean first = true;
        for (String[] turn : history) {
            if (!first) {
                turns.append(',');
            }
            turns.append(new Json.Writer().value("role", turn[0]).value("text", turn[1]).build());
            first = false;
        }
        turns.append(']');

        return new Json.Writer()
                .value("npcId", persona.id())
                .value("npcName", persona.name())
                .value("persona", persona.persona())
                .value("playerId", playerId)
                .value("playerName", playerName)
                .value("message", message)
                .array("allowedIntents", persona.intents())
                .raw("allowedTargets", targets.build())
                .object("context", context)
                .raw("history", turns.toString())
                .build();
    }

    /** Runs off the server thread. */
    private BridgeReply post(String url, String token, int timeout, String body) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(Math.min(3000, timeout));
            connection.setReadTimeout(timeout);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }

            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            OutputStream out = connection.getOutputStream();
            try {
                out.write(payload);
            } finally {
                out.close();
            }

            int status = connection.getResponseCode();
            String response = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status != 200) {
                logThrottled("AI bridge returned HTTP " + status);
                return BridgeReply.failed("http_" + status);
            }
            return BridgeReply.parse(response);
        } catch (IOException ex) {
            logThrottled("AI bridge unreachable: " + ex.getMessage());
            return BridgeReply.failed("unreachable");
        } catch (RuntimeException ex) {
            logThrottled("AI bridge error: " + ex.getMessage());
            return BridgeReply.failed("error");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
        } finally {
            stream.close();
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    /** A dead bridge should not fill the console - one line a minute is enough to notice. */
    private synchronized void logThrottled(String message) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLog < 60000L) {
            return;
        }
        lastErrorLog = now;
        plugin.getLogger().warning("[ACRPJobs] " + message + " (NPCs are using their written lines)");
    }

    public void shutdown() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Parsed bridge answer. */
    public static final class BridgeReply {

        public final boolean ok;
        public final String reply;
        public final DialogueIntent intent;
        public final String target;
        public final String reason;

        private BridgeReply(boolean ok, String reply, DialogueIntent intent, String target, String reason) {
            this.ok = ok;
            this.reply = reply;
            this.intent = intent;
            this.target = target;
            this.reason = reason;
        }

        static BridgeReply failed(String reason) {
            return new BridgeReply(false, "", DialogueIntent.CHAT, "", reason);
        }

        static BridgeReply parse(String json) {
            Map<String, Object> object = Json.parseObject(json);
            if (!Json.bool(object, "ok", false)) {
                return failed(Json.string(object, "reason", "not_ok"));
            }
            String reply = Json.string(object, "reply", "").trim();
            if (reply.isEmpty()) {
                return failed("empty_reply");
            }
            return new BridgeReply(
                    true,
                    reply,
                    DialogueIntent.fromString(Json.string(object, "intent", "CHAT")),
                    Json.string(object, "target", ""),
                    "");
        }
    }
}

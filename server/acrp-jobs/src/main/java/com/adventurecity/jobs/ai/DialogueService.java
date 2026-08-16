package com.adventurecity.jobs.ai;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.config.ContractDefinition;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.config.Zone;
import com.adventurecity.jobs.storage.PlayerData;
import com.adventurecity.jobs.ui.ContractMenu;
import com.adventurecity.jobs.ui.JobCenterMenu;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs NPC conversations.
 *
 * <p>This is where the "the AI speaks, the engine decides" rule is enforced. The bridge already
 * filters the model's answer once; this class filters it again against live server state, so an
 * NPC can never offer a job that was deleted from the config, a contract the player's job does
 * not have, or a zone that was never placed.</p>
 */
public final class DialogueService {

    private final ACRPJobsPlugin plugin;
    private final Map<UUID, Conversation> active = new HashMap<UUID, Conversation>();

    public DialogueService(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    public Conversation get(Player player) {
        return active.get(player.getUniqueId());
    }

    public boolean isTalking(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    // ---------------------------------------------------------------- lifecycle

    public boolean start(Player player, NpcPersona npc, Location npcLocation) {
        if (active.containsKey(player.getUniqueId())) {
            plugin.msg().send(player, "npc.busy");
            return false;
        }
        active.put(player.getUniqueId(), new Conversation(player.getUniqueId(), npc, npcLocation));

        String greeting = npc.greeting();
        if (greeting != null && !greeting.isEmpty()) {
            player.sendMessage(Msg.color(greeting));
        } else {
            speak(player, npc, "هلا فيك.");
        }
        plugin.msg().send(player, "npc.started");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8F, 1.2F);
        return true;
    }

    public void end(Player player, boolean announce) {
        if (active.remove(player.getUniqueId()) == null) {
            if (announce) {
                plugin.msg().send(player, "npc.not-talking");
            }
            return;
        }
        if (announce) {
            plugin.msg().send(player, "npc.ended");
        }
    }

    public void forget(UUID playerId) {
        active.remove(playerId);
    }

    /** Ends conversations the player walked away from or stopped answering. */
    public void tick() {
        if (active.isEmpty()) {
            return;
        }
        int timeout = plugin.settings().aiConversationSeconds;
        double range = plugin.settings().aiConversationRange;

        for (UUID id : new ArrayList<UUID>(active.keySet())) {
            Conversation conversation = active.get(id);
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                active.remove(id);
                continue;
            }
            if (conversation.idleSeconds() >= timeout) {
                active.remove(id);
                plugin.msg().send(player, "npc.timeout");
                continue;
            }
            Location npcLocation = conversation.npcLocation();
            if (npcLocation != null && npcLocation.getWorld() != null
                    && (!npcLocation.getWorld().equals(player.getWorld())
                        || npcLocation.distanceSquared(player.getLocation()) > range * range)) {
                active.remove(id);
                plugin.msg().send(player, "npc.too-far");
            }
        }
    }

    // ---------------------------------------------------------------- talking

    /** Called on the server thread with one line of player speech. */
    public void handleMessage(final Player player, final String message) {
        final Conversation conversation = active.get(player.getUniqueId());
        if (conversation == null) {
            return;
        }
        conversation.touch();

        final NpcPersona npc = conversation.npc();
        if (!plugin.aiBridge().enabled()) {
            speakFallback(player, npc);
            return;
        }
        if (conversation.waitingForReply()) {
            plugin.msg().send(player, "npc.thinking", "npc", npc.name());
            return;
        }

        conversation.waitingForReply(true);
        conversation.remember("user", message);
        plugin.msg().send(player, "npc.thinking", "npc", npc.name());

        plugin.aiBridge().ask(npc, player.getUniqueId().toString(), player.getName(), message,
                buildContext(player), buildTargets(player, npc), conversation.history(),
                reply -> {
                    conversation.waitingForReply(false);
                    Player target = Bukkit.getPlayer(conversation.playerId());
                    if (target == null || !target.isOnline() || !active.containsKey(conversation.playerId())) {
                        return;
                    }
                    conversation.touch();
                    if (!reply.ok) {
                        speakFallback(target, npc);
                        return;
                    }
                    conversation.remember("npc", reply.reply);
                    speak(target, npc, reply.reply);
                    applyIntent(target, npc, reply.intent, reply.target);
                });
    }

    private void speak(Player player, NpcPersona npc, String text) {
        player.sendMessage(plugin.msg().get("npc.line", "npc", npc.name(), "text", text));
    }

    private void speakFallback(Player player, NpcPersona npc) {
        String line = npc.fallbackLine();
        if (line.isEmpty()) {
            plugin.msg().send(player, "npc.no-answer", "npc", npc.name());
            return;
        }
        player.sendMessage(Msg.color(line));
    }

    // ---------------------------------------------------------------- intents

    /**
     * Executes a validated intent. Anything that does not check out against live server state is
     * silently treated as plain chat - the player still gets the reply, just no phantom action.
     */
    private void applyIntent(final Player player, NpcPersona npc, DialogueIntent intent, String target) {
        if (intent == DialogueIntent.CHAT || intent == DialogueIntent.DECLINE) {
            return;
        }
        if (!npc.allows(intent) || (intent.needsTarget() && (target == null || target.isEmpty()))) {
            return;
        }

        switch (intent) {
            case OFFER_JOB: {
                JobDefinition job = plugin.jobs().get(target);
                if (job == null || !npc.jobs().contains(job.id())) {
                    return;
                }
                plugin.msg().send(player, "npc.job-offer", "job", Msg.color(job.name()));
                Bukkit.getScheduler().runTask(plugin, () -> new JobCenterMenu(plugin).open(player));
                break;
            }
            case OFFER_CONTRACT: {
                PlayerData data = plugin.players().get(player);
                JobDefinition job = data == null ? null : plugin.jobs().get(data.currentJob());
                if (job == null || job.contract(target) == null) {
                    return;
                }
                plugin.msg().send(player, "npc.contract-offer",
                        "contract", Msg.color(job.contract(target).name()));
                Bukkit.getScheduler().runTask(plugin, () -> new ContractMenu(plugin).open(player));
                break;
            }
            case DIRECT_TO_ZONE: {
                Zone zone = plugin.zones().get(target);
                if (zone == null || !npc.zones().contains(zone.id())) {
                    return;
                }
                double distance = zone.distanceTo(player.getLocation());
                if (distance == Double.MAX_VALUE) {
                    return;
                }
                plugin.msg().send(player, "npc.zone-hint",
                        "zone", zone.id(), "distance", (int) distance);
                plugin.hud().beam(player, zone.toLocation());
                break;
            }
            default:
                break;
        }
    }

    // ---------------------------------------------------------------- prompt inputs

    /**
     * Short facts the NPC is permitted to mention. Deliberately small: every line costs tokens on
     * every message, and anything not listed here the NPC is told not to invent.
     */
    private Map<String, String> buildContext(Player player) {
        Map<String, String> context = new LinkedHashMap<String, String>();
        PlayerData data = plugin.players().get(player);
        if (data == null) {
            return context;
        }
        JobDefinition job = plugin.jobs().get(data.currentJob());
        if (job == null) {
            context.put("الوظيفة", "لا يوجد");
        } else {
            context.put("الوظيفة", Msg.plain(job.name()));
            context.put("الرتبة", plugin.jobManager().gradeOf(data, job).name());
            context.put("الدوام", data.onDuty() ? "على الدوام" : "خارج الدوام");
        }
        context.put("الرصيد", plugin.economy().format(data.balance()));
        return context;
    }

    /** The legal target ids per intent - filtered against what actually exists right now. */
    private Map<String, List<String>> buildTargets(Player player, NpcPersona npc) {
        Map<String, List<String>> targets = new LinkedHashMap<String, List<String>>();

        if (npc.allows(DialogueIntent.OFFER_JOB)) {
            List<String> jobs = new ArrayList<String>();
            for (String id : npc.jobs()) {
                if (plugin.jobs().exists(id)) {
                    jobs.add(id);
                }
            }
            if (!jobs.isEmpty()) {
                targets.put(DialogueIntent.OFFER_JOB.name(), jobs);
            }
        }

        if (npc.allows(DialogueIntent.DIRECT_TO_ZONE)) {
            List<String> zones = new ArrayList<String>();
            for (String id : npc.zones()) {
                if (plugin.zones().exists(id)) {
                    zones.add(id);
                }
            }
            if (!zones.isEmpty()) {
                targets.put(DialogueIntent.DIRECT_TO_ZONE.name(), zones);
            }
        }

        if (npc.allows(DialogueIntent.OFFER_CONTRACT)) {
            PlayerData data = plugin.players().get(player);
            JobDefinition job = data == null ? null : plugin.jobs().get(data.currentJob());
            if (job != null) {
                List<String> contracts = new ArrayList<String>();
                for (ContractDefinition contract : job.contracts().values()) {
                    if (!contract.dispatchOnly()) {
                        contracts.add(contract.id());
                    }
                }
                if (!contracts.isEmpty()) {
                    targets.put(DialogueIntent.OFFER_CONTRACT.name(), contracts);
                }
            }
        }
        return targets;
    }
}

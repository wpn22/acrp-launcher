package com.adventurecity.jobs.storage;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the player cache and the single IO thread. Nothing else in the plugin is allowed to touch
 * {@link SqlStorage} directly - that is how the main thread stays free of database work.
 */
public final class PlayerDataManager {

    private final Plugin plugin;
    private final SqlStorage storage;
    private final Logger logger;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<UUID, PlayerData>();
    private final ExecutorService io;

    public PlayerDataManager(Plugin plugin, SqlStorage storage, Logger logger) {
        this.plugin = plugin;
        this.storage = storage;
        this.logger = logger;
        this.io = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "ACRPJobs-IO");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public SqlStorage storage() {
        return storage;
    }

    /** Loads a player off-thread, then runs the callback on the server thread. */
    public void loadAsync(final UUID uuid, final String name, final Consumer<PlayerData> callback) {
        submit(new Runnable() {
            @Override
            public void run() {
                PlayerData data;
                try {
                    data = storage.load(uuid, name);
                    data.initEarnedToday(epochDay(), storage.earnedSince(uuid, startOfDayMillis()));
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "[ACRPJobs] Failed to load player " + name, ex);
                    return;
                }
                data.name(name);
                cache.put(uuid, data);
                if (callback != null) {
                    runOnMain(new Runnable() {
                        @Override
                        public void run() {
                            PlayerData cached = cache.get(uuid);
                            if (cached != null) {
                                callback.accept(cached);
                            }
                        }
                    });
                }
            }
        });
    }

    /** Cached data, or null when the player is not loaded (yet). */
    public PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    public PlayerData get(Player player) {
        return player == null ? null : cache.get(player.getUniqueId());
    }

    public boolean isLoaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    /** Saves then drops the player from the cache. */
    public void unloadAsync(final UUID uuid) {
        final PlayerData data = cache.remove(uuid);
        if (data == null) {
            return;
        }
        submit(new Runnable() {
            @Override
            public void run() {
                saveNow(data);
            }
        });
    }

    /** Periodic flush of everything that changed since the last pass. */
    public void saveDirtyAsync() {
        final List<PlayerData> dirty = new ArrayList<PlayerData>();
        for (PlayerData data : cache.values()) {
            if (data.dirty()) {
                dirty.add(data);
            }
        }
        if (dirty.isEmpty()) {
            return;
        }
        submit(new Runnable() {
            @Override
            public void run() {
                for (PlayerData data : dirty) {
                    saveNow(data);
                }
            }
        });
    }

    public void deleteJobAsync(final UUID uuid, final String jobId) {
        submit(new Runnable() {
            @Override
            public void run() {
                try {
                    storage.deleteJob(uuid, jobId);
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "[ACRPJobs] Could not delete job row", ex);
                }
            }
        });
    }

    private void saveNow(PlayerData data) {
        try {
            storage.save(data);
            data.dirty(false);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "[ACRPJobs] Failed to save player " + data.name(), ex);
        }
    }

    /** Runs work on the IO thread and delivers the result back on the server thread. */
    public <T> void query(final Callable<T> work, final Consumer<T> callback) {
        submit(new Runnable() {
            @Override
            public void run() {
                T result = null;
                try {
                    result = work.call();
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "[ACRPJobs] Async query failed", ex);
                }
                final T value = result;
                if (callback != null) {
                    runOnMain(new Runnable() {
                        @Override
                        public void run() {
                            callback.accept(value);
                        }
                    });
                }
            }
        });
    }

    public void submit(Runnable task) {
        if (io.isShutdown()) {
            task.run();
            return;
        }
        io.execute(task);
    }

    private void runOnMain(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /** Flushes every cached player and stops the IO thread. Called from onDisable. */
    public void shutdown() {
        for (PlayerData data : cache.values()) {
            final PlayerData snapshot = data;
            io.execute(new Runnable() {
                @Override
                public void run() {
                    saveNow(snapshot);
                }
            });
        }
        cache.clear();
        io.shutdown();
        try {
            if (!io.awaitTermination(20, TimeUnit.SECONDS)) {
                logger.warning("[ACRPJobs] Storage did not finish writing within 20s - shutting down anyway.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        storage.close();
    }

    /** Local midnight - the daily earning cap resets on the server's own clock. */
    public static long startOfDayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static long epochDay() {
        return startOfDayMillis() / 86400000L;
    }
}

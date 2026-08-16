package com.adventurecity.jobs.antiabuse;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player, per-contract cooldowns - the first line of defence against contract farming. */
public final class Cooldowns {

    private final Map<String, Long> until = new ConcurrentHashMap<String, Long>();

    public void set(UUID uuid, String key, int seconds) {
        if (seconds <= 0) {
            return;
        }
        until.put(key(uuid, key), Long.valueOf(System.currentTimeMillis() + seconds * 1000L));
    }

    /** Seconds left, or 0 when ready. */
    public long remaining(UUID uuid, String key) {
        Long expiry = until.get(key(uuid, key));
        if (expiry == null) {
            return 0L;
        }
        long left = expiry.longValue() - System.currentTimeMillis();
        if (left <= 0L) {
            until.remove(key(uuid, key));
            return 0L;
        }
        return (left + 999L) / 1000L;
    }

    public boolean ready(UUID uuid, String key) {
        return remaining(uuid, key) <= 0L;
    }

    /**
     * Cooldowns deliberately survive a logout - clearing them on quit would turn relogging into a
     * cooldown reset, which is the first exploit players try.
     */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        until.values().removeIf(expiry -> expiry.longValue() <= now);
    }

    public void clear(UUID uuid) {
        String prefix = uuid.toString() + ":";
        until.keySet().removeIf(entry -> entry.startsWith(prefix));
    }

    private static String key(UUID uuid, String key) {
        return uuid.toString() + ":" + key;
    }
}

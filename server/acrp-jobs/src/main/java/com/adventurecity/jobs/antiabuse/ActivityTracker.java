package com.adventurecity.jobs.antiabuse;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks real movement so a player cannot park on a pressure plate and collect salaries all night.
 * Only block-to-block movement counts - looking around does not.
 */
public final class ActivityTracker {

    private final Map<UUID, Long> lastMove = new ConcurrentHashMap<UUID, Long>();

    public void markActive(Player player) {
        lastMove.put(player.getUniqueId(), Long.valueOf(System.currentTimeMillis()));
    }

    public void forget(UUID uuid) {
        lastMove.remove(uuid);
    }

    public boolean isAfk(Player player, int afkSeconds) {
        Long last = lastMove.get(player.getUniqueId());
        if (last == null) {
            // Never seen moving since join - treat as active so the first payroll tick is not eaten.
            markActive(player);
            return false;
        }
        return System.currentTimeMillis() - last.longValue() > afkSeconds * 1000L;
    }

    public long idleSeconds(Player player) {
        Long last = lastMove.get(player.getUniqueId());
        if (last == null) {
            return 0L;
        }
        return (System.currentTimeMillis() - last.longValue()) / 1000L;
    }

    /** True when the two locations are in different blocks (ignores head movement). */
    public static boolean changedBlock(Location from, Location to) {
        if (from == null || to == null) {
            return true;
        }
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}

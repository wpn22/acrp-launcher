package com.adventurecity.jobs.listener;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.antiabuse.ActivityTracker;
import com.adventurecity.jobs.storage.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/** Loads and flushes player state, and feeds the AFK tracker. */
public final class PlayerListener implements Listener {

    private final ACRPJobsPlugin plugin;

    public PlayerListener(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        plugin.activity().markActive(player);
        plugin.players().loadAsync(player.getUniqueId(), player.getName(), data -> {
            // Duty never survives a session: the player has to clock in again.
            if (data.onDuty()) {
                data.onDuty(false);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        plugin.contracts().abandon(uuid);
        plugin.dispatch().releaseDriver(uuid);
        plugin.hud().clear(player);
        plugin.activity().forget(uuid);
        plugin.payrollTask().forget(uuid);

        PlayerData data = plugin.players().get(uuid);
        if (data != null && plugin.settings().endDutyOnQuit) {
            data.onDuty(false);
        }
        plugin.players().unloadAsync(uuid);
    }

    /**
     * Fires constantly, so it does the cheapest possible check first and only records a timestamp
     * when the player actually left the block they were standing in.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!ActivityTracker.changedBlock(event.getFrom(), event.getTo())) {
            return;
        }
        plugin.activity().markActive(event.getPlayer());
    }
}

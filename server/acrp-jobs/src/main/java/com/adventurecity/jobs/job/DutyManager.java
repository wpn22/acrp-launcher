package com.adventurecity.jobs.job;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.config.Zone;
import com.adventurecity.jobs.spot.PumpTool;
import com.adventurecity.jobs.storage.PlayerData;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Clock-in / clock-out. Off duty means no salary, no contracts and no job powers - which is what
 * stops an "officer" from acting like one while they are meant to be a civilian.
 */
public final class DutyManager {

    private final ACRPJobsPlugin plugin;

    public DutyManager(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    public void toggle(Player player) {
        PlayerData data = plugin.players().get(player);
        if (data == null) {
            return;
        }
        if (data.onDuty()) {
            stop(player, true);
        } else {
            start(player);
        }
    }

    public boolean start(Player player) {
        PlayerData data = plugin.players().get(player);
        if (data == null) {
            return false;
        }
        JobDefinition job = plugin.jobs().get(data.currentJob());
        if (job == null) {
            plugin.msg().send(player, "jobs.no-job");
            return false;
        }
        if (plugin.settings().requireZoneForDuty && !inDutyZone(player, job)) {
            plugin.msg().send(player, "duty.need-zone");
            return false;
        }

        data.onDuty(true);
        data.dutyMinutes(0);
        plugin.msg().send(player, "duty.on", "job", Msg.color(job.name()));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_PLING, 1.0F, 1.6F);
        spawnVehicle(player, job);
        giveTool(player, job);
        return true;
    }

    public void stop(Player player, boolean announce) {
        PlayerData data = plugin.players().get(player);
        if (data == null || !data.onDuty()) {
            return;
        }
        data.onDuty(false);
        plugin.contracts().cancel(player, false);
        plugin.hud().clear(player);
        plugin.spots().pump().take(player);
        if (announce) {
            plugin.msg().send(player, "duty.off");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BASS, 1.0F, 0.8F);
        }
    }

    public boolean isOnDuty(Player player) {
        PlayerData data = plugin.players().get(player);
        return data != null && data.onDuty();
    }

    /**
     * True when the player stands in one of the job's duty zones. If the admin has not placed any of
     * them yet the check passes - an unfinished map should not lock everyone out of working.
     */
    private boolean inDutyZone(Player player, JobDefinition job) {
        boolean anyDefined = false;
        for (String zoneId : job.dutyZones()) {
            Zone zone = plugin.zones().get(zoneId);
            if (zone == null) {
                continue;
            }
            anyDefined = true;
            if (zone.contains(player.getLocation())) {
                return true;
            }
        }
        return !anyDefined;
    }

    /** Hands over the job's tool, if it declares one. Today that is the cleaner's water pump. */
    private void giveTool(Player player, JobDefinition job) {
        if (PumpTool.TOOL_ID.equals(job.tool())) {
            plugin.spots().pump().give(player);
        }
    }

    private void spawnVehicle(Player player, JobDefinition job) {
        if (!plugin.settings().vehiclesEnabled) {
            return;
        }
        String command = plugin.settings().vehicleSpawnCommand;
        if (command == null || command.isEmpty() || job.vehicle() == null || job.vehicle().isEmpty()) {
            return;
        }
        String resolved = command
                .replace("%player%", player.getName())
                .replace("%vehicle%", job.vehicle());
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[ACRPJobs] Vehicle command failed: " + resolved + " (" + ex.getMessage() + ")");
        }
    }
}

package com.adventurecity.jobs.hud;

import com.adventurecity.jobs.ACRPJobsPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The "you are never lost" layer: a boss bar with the current objective, an action bar hint and a
 * column of particles on the target that only the player working the contract can see.
 */
public final class ObjectiveHud {

    private final ACRPJobsPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<UUID, BossBar>();

    public ObjectiveHud(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    /** @param progress 0.0 - 1.0; values outside that range make Bukkit throw, so they are clamped */
    public void update(Player player, String title, double progress) {
        if (!plugin.settings().hudBossBar) {
            return;
        }
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar(title, BarColor.YELLOW, BarStyle.SOLID);
            bar.addPlayer(player);
            bars.put(player.getUniqueId(), bar);
        } else if (!bar.getPlayers().contains(player)) {
            // Player rejoined - the old bar lost its viewer.
            bar.addPlayer(player);
        }
        bar.setTitle(title);
        bar.setProgress(clamp(progress));
        bar.setVisible(true);
    }

    public void actionBar(Player player, String text) {
        if (!plugin.settings().hudActionBar || text == null) {
            return;
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
    }

    /** A short beam of particles above the objective, sent to this player only. */
    public void beam(Player player, Location target) {
        if (!plugin.settings().hudParticles || target == null || target.getWorld() == null) {
            return;
        }
        if (!target.getWorld().equals(player.getWorld())) {
            return;
        }
        if (target.distanceSquared(player.getLocation()) > (double) plugin.settings().particleRange * plugin.settings().particleRange) {
            return;
        }
        for (int i = 0; i < 8; i++) {
            Location point = target.clone().add(0.0D, 0.4D * i, 0.0D);
            player.spawnParticle(Particle.FLAME, point, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
    }

    public void celebrate(Player player, Location where) {
        if (!plugin.settings().hudParticles || where == null || where.getWorld() == null
                || !where.getWorld().equals(player.getWorld())) {
            return;
        }
        player.spawnParticle(Particle.VILLAGER_HAPPY, where.clone().add(0.0D, 1.0D, 0.0D), 20,
                0.6D, 0.6D, 0.6D, 0.0D);
    }

    public void clear(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void clearAll() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }

    private static double clamp(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        return value > 1.0D ? 1.0D : value;
    }
}

package com.adventurecity.jobs.listener;

import com.adventurecity.jobs.ACRPJobsPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Everything the route system needs from events: the drawing wand, and keeping the walkers out of
 * trouble - no villager trading, no damage, no mob aggro, and no bodies left behind.
 */
public final class RouteListener implements Listener {

    private final ACRPJobsPlugin plugin;

    public RouteListener(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- the wand

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        // 1.9+ fires this for both hands; only the main hand should count.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.routes().editor().hasSession(player)
                || !plugin.routes().editor().isWand(event.getItem())) {
            return;
        }
        switch (event.getAction()) {
            case RIGHT_CLICK_BLOCK:
                event.setCancelled(true);
                plugin.routes().editor().addCorner(player, event.getClickedBlock());
                break;
            case LEFT_CLICK_BLOCK:
                event.setCancelled(true);
                plugin.routes().editor().undo(player);
                break;
            default:
                break;
        }
    }

    /**
     * In creative mode a left click breaks the block outright, so cancelling the interact event is
     * not enough - marking a corner must never take a bite out of the build.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.routes().editor().hasSession(player)
                && plugin.routes().editor().isWand(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.routes().editor().forget(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- the walkers

    /**
     * Runs after {@link NpcListener}, which gets first refusal on starting a conversation. Whatever
     * it decides, a right click must never open a villager trade window.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (plugin.routes().isRouteEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.routes().isRouteEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Stops zombies and the like from chasing a walker that cannot fight back or run. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (plugin.routes().isRouteEntity(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    /**
     * A walker whose chunk unloads would be saved into the map and come back as an orphan the next
     * time somebody walks past. Deleting it here means the only walkers that ever exist are the
     * ones this plugin is currently driving.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (plugin.routes().isRouteEntity(entity)) {
                plugin.routes().onEntityUnloading(entity);
            }
        }
    }
}

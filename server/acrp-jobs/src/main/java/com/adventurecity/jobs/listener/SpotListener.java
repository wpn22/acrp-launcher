package com.adventurecity.jobs.listener;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.spot.SpotType;
import com.adventurecity.jobs.spot.WorkSpot;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Everything the work-spot system needs from events: the marking wand, the water pump, collecting
 * a rubbish bag, and keeping the bags from being picked up, destroyed or left behind.
 */
public final class SpotListener implements Listener {

    /** How close a worker must be to grab a bag they clicked at. */
    private static final double GRAB_REACH = 4.5D;

    private final ACRPJobsPlugin plugin;

    public SpotListener(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- clicking

    /**
     * The wand, the pump and picking up a bag all arrive here. They are handled in one method
     * rather than three listeners so the order between them is explicit and cannot drift.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        // 1.9+ fires this for both hands; only the main hand should count.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_BLOCK
                || event.getAction() == Action.RIGHT_CLICK_AIR;

        // 1. Marking wand - an admin with a session open is not doing anything else.
        if (plugin.spots().editor().hasSession(player)
                && plugin.spots().editor().isWand(event.getItem())) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                plugin.spots().editor().addPoint(player, event.getClickedBlock());
            } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                plugin.spots().editor().undo(player);
            }
            return;
        }

        if (!rightClick) {
            return;
        }

        // 2. Water pump. Always cancelled: an empty bucket must never scoop up a job site's water.
        if (plugin.spots().pump().isPump(event.getItem())) {
            event.setCancelled(true);
            plugin.spots().pump().use(player);
            return;
        }

        // 3. A rubbish bag is a dropped item, so grabbing it arrives as a plain right-click rather
        // than an entity click - the nearest tagged bag in reach wins.
        WorkSpot target = plugin.spots().targetedEntitySpot(player, GRAB_REACH);
        if (target != null && target.pool().type() == SpotType.TRASH) {
            event.setCancelled(true);
            plugin.spots().clear(player, target);
        }
    }

    /**
     * In creative a left click breaks the block outright, so cancelling the interact event is not
     * enough - marking a point must never take a bite out of the build.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.spots().editor().hasSession(player)
                && plugin.spots().editor().isWand(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.spots().editor().forget(event.getPlayer().getUniqueId());
        plugin.spots().pump().cancel(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- protecting the bags

    /** Belt and braces on top of the huge pickup delay - a bag is scenery, not loot. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (plugin.spots().isSpotEntity(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** The pump is a job tool, not an item - dropping it would leave the worker unable to work. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.spots().pump().isPump(event.getItemDrop().getItemStack())
                || plugin.spots().editor().isWand(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            plugin.msg().send(event.getPlayer(), "spot.tool-kept");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.spots().isSpotEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * A bag whose chunk unloads would be saved into the map and come back as an orphan. Deleting it
     * here means the only bags that exist are the ones this plugin is currently showing.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (plugin.spots().isSpotEntity(entity)) {
                plugin.spots().onEntityUnloading(entity);
            }
        }
    }
}

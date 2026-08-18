package com.adventurecity.jobs.listener;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.ai.NpcPersona;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Turns a right-click on an NPC into a conversation, and routes the player's chat to that NPC
 * instead of public chat while it lasts.
 *
 * <p>NPCs are recognised by display name, so this works with Citizens and CustomNPCs without
 * compiling against either.</p>
 */
public final class NpcListener implements Listener {

    private final ACRPJobsPlugin plugin;

    public NpcListener(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        // 1.9+ fires this for both hands; only the main hand should count.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (plugin.npcs().isEmpty()) {
            return;
        }
        Entity entity = event.getRightClicked();

        // A route walker carries its persona through the route it belongs to, so those NPCs can be
        // given any display name at all and still talk.
        NpcPersona persona = plugin.routes().personaOf(entity);
        if (persona == null) {
            String name = entity.getCustomName();
            if (name == null || name.isEmpty()) {
                return;
            }
            persona = plugin.npcs().byEntityName(name);
        }
        if (persona == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (plugin.dialogue().isTalking(player)) {
            plugin.dialogue().end(player, true);
            return;
        }
        // A supervisor teaches before they chat: the lesson is the point of walking up to them.
        if (plugin.training().meet(player, persona)) {
            // Stands still long enough to finish the sentence, then carries on with the round.
            plugin.routes().pause(entity, 6000L);
            return;
        }
        if (plugin.dialogue().start(player, persona, entity.getLocation())) {
            // The walker stops and turns to face the player until the conversation ends.
            plugin.routes().startTalking(entity, player);
        }
    }

    /**
     * Fires off the server thread, so the message is handed back to the scheduler before any
     * plugin state is touched.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        if (!plugin.dialogue().isTalking(player)) {
            return;
        }
        final String message = event.getMessage();
        event.setCancelled(true);

        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                plugin.dialogue().handleMessage(player, message);
            }
        });
    }
}

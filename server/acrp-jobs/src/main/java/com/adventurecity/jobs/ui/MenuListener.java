package com.adventurecity.jobs.ui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Every click in a plugin menu is cancelled - including clicks in the player's own inventory while a
 * menu is open, which is what stops shift-click from dragging items into the GUI.
 */
public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ChestMenu menu = menuOf(event.getView().getTopInventory());
        if (menu == null) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(event.getView().getTopInventory())) {
            return;
        }
        menu.click((Player) event.getWhoClicked(), event.getRawSlot());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (menuOf(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    private ChestMenu menuOf(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        return inventory.getHolder() instanceof ChestMenu ? (ChestMenu) inventory.getHolder() : null;
    }
}

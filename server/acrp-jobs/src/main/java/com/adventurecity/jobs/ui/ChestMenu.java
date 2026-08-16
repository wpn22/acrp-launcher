package com.adventurecity.jobs.ui;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal chest-GUI framework: the inventory holder is the menu itself, so the click listener can
 * find the handler for a slot without any slot-to-action bookkeeping elsewhere.
 */
public abstract class ChestMenu implements InventoryHolder {

    /** 1.12.2 rejects inventory titles longer than this. */
    private static final int MAX_TITLE = 32;

    protected final ACRPJobsPlugin plugin;
    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<Integer, Consumer<Player>>();

    protected ChestMenu(ACRPJobsPlugin plugin, String title, int rows) {
        this.plugin = plugin;
        int size = Math.max(1, Math.min(6, rows)) * 9;
        this.inventory = Bukkit.createInventory(this, size, truncate(Msg.color(title)));
    }

    /** Fills the inventory for this viewer. Called on every open, so the contents are always fresh. */
    protected abstract void build(Player player);

    public void open(Player player) {
        actions.clear();
        inventory.clear();
        build(player);
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void click(Player player, int slot) {
        Consumer<Player> action = actions.get(Integer.valueOf(slot));
        if (action != null) {
            action.accept(player);
        }
    }

    protected void set(int slot, ItemStack item, Consumer<Player> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(Integer.valueOf(slot), action);
        }
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    /** Opens another menu on the next tick - reopening inside a click event is unreliable. */
    protected void openLater(final ChestMenu menu, final Player player) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                menu.open(player);
            }
        });
    }

    protected void fillEmpty() {
        ItemStack filler = item(Material.STAINED_GLASS_PANE, (short) 15, " ", null);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    protected ItemStack item(Material material, String name, List<String> lore) {
        return item(material, (short) 0, name, lore);
    }

    protected ItemStack item(Material material, short data, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material == null ? Material.PAPER : material, 1, data);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> coloured = new ArrayList<String>();
                for (String line : lore) {
                    coloured.add(Msg.color(line));
                }
                meta.setLore(coloured);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String truncate(String title) {
        if (title == null) {
            return "";
        }
        return title.length() <= MAX_TITLE ? title : title.substring(0, MAX_TITLE);
    }
}

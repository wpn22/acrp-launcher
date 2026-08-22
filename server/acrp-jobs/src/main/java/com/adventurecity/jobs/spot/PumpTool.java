package com.adventurecity.jobs.spot;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The water pump: one tool that washes stains off the street and waters wilting plants.
 *
 * <p>Right-click while looking at a spot and the spray runs on its own for a few seconds - Bukkit
 * gives no "button is still held" event on 1.12.2, so asking the player to hold the button would
 * be a lie. Walking off, switching item or the spot being cleared by somebody else all cancel it.</p>
 */
public final class PumpTool {

    /** Jobs declare {@code tool: PUMP} in their YAML to hand this out on duty. */
    public static final String TOOL_ID = "PUMP";

    private static final String PUMP_NAME = "&b&lمضخة الماء";
    private static final Material PUMP_MATERIAL = Material.BUCKET;
    private static final double REACH = 6.0D;
    private static final double WANDER_LIMIT = 2.5D;

    private final ACRPJobsPlugin plugin;
    private final Map<UUID, Spray> sprays = new HashMap<UUID, Spray>();

    public PumpTool(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    /** One player's spray in progress. */
    private static final class Spray {
        private final WorkSpot target;
        private final Location startedAt;
        private final long endsAt;

        private Spray(WorkSpot target, Location startedAt, long endsAt) {
            this.target = target;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
        }
    }

    // ---------------------------------------------------------------- the item

    public ItemStack item() {
        ItemStack item = new ItemStack(PUMP_MATERIAL, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Msg.color(PUMP_NAME));
        meta.setLore(Msg.color(Arrays.asList(
                "&7وجّهها للبقعة واضغط &fيمين",
                "&7تنظّف الأوساخ &8· &7تسقي الزرع",
                "&8أداة عمل - تُسحب عند إنهاء الدوام")));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPump(ItemStack item) {
        if (item == null || item.getType() != PUMP_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && Msg.color(PUMP_NAME).equals(meta.getDisplayName());
    }

    public void give(Player player) {
        for (ItemStack held : player.getInventory().getContents()) {
            if (isPump(held)) {
                return;
            }
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item());
        if (!leftover.isEmpty()) {
            plugin.msg().send(player, "spot.pump-no-room");
            return;
        }
        plugin.msg().send(player, "spot.pump-given");
    }

    public void take(Player player) {
        cancel(player.getUniqueId());
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isPump(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    // ---------------------------------------------------------------- spraying

    /** Right-click with the pump in hand. */
    public void use(Player player) {
        if (sprays.containsKey(player.getUniqueId())) {
            return;
        }
        // Checked here as well as in clear(), so nobody sprays for three seconds to be told no.
        if (!plugin.spots().canWork(player)) {
            plugin.msg().send(player, "spot.need-duty");
            return;
        }
        WorkSpot target = plugin.spots().targeted(player, REACH, null);
        if (target == null) {
            plugin.msg().send(player, "spot.pump-nothing");
            return;
        }

        int seconds = Math.max(1, plugin.settings().pumpSeconds);
        sprays.put(player.getUniqueId(), new Spray(target, player.getLocation().clone(),
                System.currentTimeMillis() + seconds * 1000L));
        plugin.msg().send(player, "spot.pump-start");
        player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL, 0.7F, 1.4F);
    }

    /** Called on the spot tick. Advances every spray and finishes or cancels it. */
    public void tick() {
        if (sprays.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<UUID> finished = new ArrayList<UUID>();

        // Iterate a snapshot: finishing a spray clears a work spot, which reaches into the contract
        // engine and back out again. Nothing on that path touches this map today, but one refactor
        // away it would, and the failure mode would be a ConcurrentModificationException mid-tick.
        for (Map.Entry<UUID, Spray> entry : new ArrayList<Map.Entry<UUID, Spray>>(sprays.entrySet())) {
            UUID id = entry.getKey();
            Spray spray = entry.getValue();
            Player player = Bukkit.getPlayer(id);

            if (player == null || !player.isOnline() || !spray.target.active()
                    || !isPump(player.getInventory().getItemInMainHand())
                    || wandered(player, spray)) {
                finished.add(id);
                if (player != null && player.isOnline() && spray.target.active()) {
                    plugin.msg().send(player, "spot.pump-cancelled");
                }
                continue;
            }

            if (now >= spray.endsAt) {
                finished.add(id);
                plugin.spots().clear(player, spray.target);
                continue;
            }
            spray(player, spray.target);
        }

        for (UUID id : finished) {
            sprays.remove(id);
        }
    }

    private boolean wandered(Player player, Spray spray) {
        Location at = player.getLocation();
        if (at.getWorld() == null || !at.getWorld().equals(spray.startedAt.getWorld())) {
            return true;
        }
        return at.distanceSquared(spray.startedAt) > WANDER_LIMIT * WANDER_LIMIT;
    }

    /** A jet of water from the player's hand to the spot, seen by everyone nearby. */
    private void spray(Player player, WorkSpot target) {
        Location from = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.6D));
        double dx = target.x() - from.getX();
        double dy = target.y() + 0.2D - from.getY();
        double dz = target.z() - from.getZ();

        for (int i = 1; i <= 8; i++) {
            double t = i / 8.0D;
            from.getWorld().spawnParticle(Particle.WATER_SPLASH,
                    from.getX() + dx * t, from.getY() + dy * t, from.getZ() + dz * t,
                    2, 0.05D, 0.05D, 0.05D, 0.0D);
        }
        from.getWorld().spawnParticle(Particle.WATER_SPLASH,
                target.x(), target.y() + 0.2D, target.z(), 8, 0.3D, 0.1D, 0.3D, 0.05D);
        player.playSound(player.getLocation(), Sound.BLOCK_WATER_AMBIENT, 0.5F, 1.6F);
    }

    public void cancel(UUID playerId) {
        sprays.remove(playerId);
    }

    public boolean spraying(Player player) {
        return sprays.containsKey(player.getUniqueId());
    }

    public void clear() {
        sprays.clear();
    }
}

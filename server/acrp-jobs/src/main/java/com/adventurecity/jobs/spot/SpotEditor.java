package com.adventurecity.jobs.spot;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.util.Msg;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Marking work spots in game: clicking the points, seeing them, and answering yes or no.
 *
 * <p>Same shape as {@link com.adventurecity.jobs.route.RouteEditor} - a wand, a private particle
 * preview and a clickable confirmation - because the admin should not have to learn two tools.
 * Nothing reaches spots.yml until the yes button is pressed, and no keeps every point.</p>
 */
public final class SpotEditor {

    private static final String WAND_NAME = "&e&lعصا تحديد النقاط";
    private static final Material WAND_MATERIAL = Material.STICK;

    private static final int MARKER_HEIGHT = 5;
    private static final int PREVIEW_SECONDS = 30;

    private final ACRPJobsPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<UUID, Session>();
    private final Map<UUID, Preview> previews = new HashMap<UUID, Preview>();

    public SpotEditor(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    /** One admin's unsaved marking run. */
    public static final class Session {
        private final String poolId;
        private final SpotType type;
        private final List<double[]> points = new ArrayList<double[]>();
        private String worldName = "";
        private boolean awaitingConfirm;

        private Session(String poolId, SpotType type) {
            this.poolId = poolId;
            this.type = type;
        }

        public String poolId() {
            return poolId;
        }

        public SpotType type() {
            return type;
        }

        public int pointCount() {
            return points.size();
        }
    }

    private static final class Preview {
        private final String poolId;
        private final long expiresAt;

        private Preview(String poolId, long expiresAt) {
            this.poolId = poolId;
            this.expiresAt = expiresAt;
        }
    }

    // ---------------------------------------------------------------- sessions

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public Session session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /** Starts marking. An existing pool is opened for editing with its points loaded. */
    public void begin(Player player, String poolId, SpotType type) {
        Session current = sessions.get(player.getUniqueId());
        if (current != null) {
            plugin.msg().send(player, "spot.busy", "pool", current.poolId);
            return;
        }

        String id = poolId.toLowerCase();
        SpotPool existing = plugin.spots().registry().get(id);
        SpotType finalType = existing != null && type == null ? existing.type() : type;
        if (finalType == null) {
            finalType = SpotType.TRASH;
        }

        Session session = new Session(id, finalType);
        if (existing != null) {
            session.worldName = existing.worldName();
            for (WorkSpot spot : existing.points()) {
                session.points.add(new double[] { spot.x(), spot.y(), spot.z() });
            }
        }
        sessions.put(player.getUniqueId(), session);
        giveWand(player);

        if (existing == null) {
            plugin.msg().send(player, "spot.created", "pool", id, "type", finalType.name());
        } else {
            plugin.msg().send(player, "spot.editing", "pool", id, "points", session.points.size());
        }
        plugin.msg().send(player, "spot.wand-hint");
    }

    public void cancel(Player player) {
        if (sessions.remove(player.getUniqueId()) == null) {
            plugin.msg().send(player, "spot.no-session");
            return;
        }
        plugin.msg().send(player, "spot.cancelled");
    }

    public void forget(UUID playerId) {
        sessions.remove(playerId);
        previews.remove(playerId);
    }

    public void clear() {
        sessions.clear();
        previews.clear();
    }

    // ---------------------------------------------------------------- points

    /** From a wand click: the work sits on top of the clicked block, centred. */
    public void addPoint(Player player, Block block) {
        add(player, block.getWorld(), block.getX() + 0.5D, block.getY() + 1.0D, block.getZ() + 0.5D);
    }

    /** From /jobsadmin spot add: wherever the admin is standing. */
    public void addPoint(Player player, Location location) {
        add(player, location.getWorld(), location.getX(), location.getY(), location.getZ());
    }

    private void add(Player player, World world, double x, double y, double z) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            plugin.msg().send(player, "spot.no-session");
            return;
        }
        if (session.points.size() >= SpotPool.MAX_POINTS) {
            plugin.msg().send(player, "spot.too-many", "max", SpotPool.MAX_POINTS);
            return;
        }
        if (session.worldName.isEmpty()) {
            session.worldName = world.getName();
        } else if (!session.worldName.equals(world.getName())) {
            plugin.msg().send(player, "spot.wrong-world", "world", session.worldName);
            return;
        }

        session.points.add(new double[] { x, y, z });
        session.awaitingConfirm = false;
        plugin.msg().send(player, "spot.point-added", "index", session.points.size());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_HAT, 0.7F, 1.6F);
    }

    public void undo(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            plugin.msg().send(player, "spot.no-session");
            return;
        }
        if (session.points.isEmpty()) {
            plugin.msg().send(player, "spot.point-none");
            return;
        }
        session.points.remove(session.points.size() - 1);
        session.awaitingConfirm = false;
        plugin.msg().send(player, "spot.point-removed", "index", session.points.size() + 1);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BASS, 0.7F, 0.8F);
    }

    // ---------------------------------------------------------------- confirmation

    public void done(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            plugin.msg().send(player, "spot.no-session");
            return;
        }
        if (session.points.isEmpty()) {
            plugin.msg().send(player, "spot.need-points");
            return;
        }

        session.awaitingConfirm = true;
        SpotPool existing = plugin.spots().registry().get(session.poolId);
        int activeCount = existing == null ? plugin.settings().spotActiveCount : existing.activeCount();
        int respawn = existing == null ? plugin.settings().spotRespawnMinutes : existing.respawnMinutes();

        player.sendMessage(plugin.msg().get("spot.summary-header"));
        player.sendMessage(plugin.msg().get("spot.summary-title",
                "pool", session.poolId, "type", session.type.name()));
        player.sendMessage(plugin.msg().get("spot.summary-line",
                "points", session.points.size(),
                "active", Math.min(activeCount, session.points.size()),
                "minutes", respawn));
        player.sendMessage(plugin.msg().get("spot.summary-question"));

        TextComponent buttons = new TextComponent("");
        buttons.addExtra(button("spot.yes-button", "spot.yes-hover", "/jobsadmin spot confirm yes"));
        buttons.addExtra(new TextComponent("    "));
        buttons.addExtra(button("spot.no-button", "spot.no-hover", "/jobsadmin spot confirm no"));
        player.spigot().sendMessage(buttons);

        player.sendMessage(plugin.msg().get("spot.confirm-text"));
        player.sendMessage(plugin.msg().get("spot.summary-header"));
    }

    private TextComponent button(String labelKey, String hoverKey, String command) {
        TextComponent component = new TextComponent(
                TextComponent.fromLegacyText(plugin.msg().get(labelKey)));
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        BaseComponent[] hover = new BaseComponent[] {
                new TextComponent(TextComponent.fromLegacyText(plugin.msg().get(hoverKey))) };
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        return component;
    }

    /** Yes writes the pool and starts it; no goes back to marking with every point still there. */
    public void confirm(Player player, boolean accepted) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.awaitingConfirm) {
            plugin.msg().send(player, "spot.not-awaiting");
            return;
        }
        if (!accepted) {
            session.awaitingConfirm = false;
            plugin.msg().send(player, "spot.back-to-edit");
            return;
        }

        SpotPool pool = plugin.spots().registry().get(session.poolId);
        if (pool == null) {
            pool = new SpotPool(session.poolId);
            pool.activeCount(plugin.settings().spotActiveCount);
            pool.respawnMinutes(plugin.settings().spotRespawnMinutes);
            pool.activationRange(plugin.settings().spotActivationRange);
        }
        pool.type(session.type);
        pool.worldName(session.worldName);
        pool.points(session.points);

        plugin.spots().registry().put(pool);
        plugin.spots().load();
        sessions.remove(player.getUniqueId());
        takeWand(player);

        plugin.msg().send(player, "spot.saved",
                "pool", pool.id(),
                "points", pool.pointCount(),
                "active", Math.min(pool.activeCount(), pool.pointCount()));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
    }

    // ---------------------------------------------------------------- preview

    public void preview(Player player, SpotPool pool) {
        previews.put(player.getUniqueId(),
                new Preview(pool.id(), System.currentTimeMillis() + PREVIEW_SECONDS * 1000L));
        plugin.msg().send(player, "spot.showing", "pool", pool.id(), "seconds", PREVIEW_SECONDS);
    }

    // ---------------------------------------------------------------- visuals

    /** Redraws every open session and every running preview, for that admin only. */
    public void tickVisuals() {
        if (sessions.isEmpty() && previews.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || !player.isOnline() || session.points.isEmpty()) {
                continue;
            }
            draw(player, session.points, session.worldName, Particle.FLAME);
        }

        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Preview>> it = previews.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Preview> entry = it.next();
            Preview preview = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            SpotPool pool = plugin.spots().registry().get(preview.poolId);
            if (player == null || !player.isOnline() || pool == null || now > preview.expiresAt) {
                it.remove();
                continue;
            }
            // Active points burn orange, resting ones glow green - the rotation made visible.
            for (WorkSpot spot : pool.points()) {
                column(player, spot.x(), spot.y(), spot.z(),
                        spot.active() ? Particle.FLAME : Particle.VILLAGER_HAPPY);
            }
        }
    }

    private void draw(Player player, List<double[]> points, String worldName, Particle particle) {
        World world = Bukkit.getWorld(worldName);
        if (world == null || !world.equals(player.getWorld())) {
            return;
        }
        for (double[] point : points) {
            column(player, point[0], point[1], point[2], particle);
        }
    }

    private void column(Player player, double x, double y, double z, Particle particle) {
        double range = plugin.settings().particleRange;
        Location at = player.getLocation();
        double dx = x - at.getX();
        double dy = y - at.getY();
        double dz = z - at.getZ();
        if (dx * dx + dy * dy + dz * dz > range * range) {
            return;
        }
        for (int i = 0; i < MARKER_HEIGHT; i++) {
            player.spawnParticle(particle, x, y + 0.35D * i, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- wand

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != WAND_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && Msg.color(WAND_NAME).equals(meta.getDisplayName());
    }

    public void giveWand(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isWand(item)) {
                return;
            }
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(wand());
        if (!leftover.isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), wand());
        }
        plugin.msg().send(player, "spot.wand-given");
    }

    private void takeWand(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isWand(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private ItemStack wand() {
        ItemStack item = new ItemStack(WAND_MATERIAL, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Msg.color(WAND_NAME));
        meta.setLore(Msg.color(Arrays.asList(
                "&7نقرة يمين على بلوك &8- &fنقطة جديدة",
                "&7نقرة يسار على بلوك &8- &fتراجع",
                "&8/jobsadmin spot done")));
        item.setItemMeta(meta);
        return item;
    }
}

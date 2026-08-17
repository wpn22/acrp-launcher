package com.adventurecity.jobs.route;

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
 * Drawing a route in game: clicking the corners, seeing the line, and answering yes or no.
 *
 * <p>Everything the admin marks is drawn back to them - and only to them - as particles, so the
 * route can be judged on the map before a single walker exists. Nothing is written to routes.yml
 * until the yes button is pressed, and pressing no returns to drawing with the corners intact.</p>
 */
public final class RouteEditor {

    private static final String WAND_NAME = "&b&lعصا رسم المسار";
    private static final Material WAND_MATERIAL = Material.BLAZE_ROD;

    private static final double LINE_STEP = 0.5D;
    private static final int LINE_BUDGET = 600;
    private static final int CORNER_HEIGHT = 6;
    private static final int PREVIEW_SECONDS = 20;

    private final ACRPJobsPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<UUID, Session>();
    private final Map<UUID, Preview> previews = new HashMap<UUID, Preview>();

    public RouteEditor(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    /** One admin's unsaved drawing. */
    public static final class Session {
        private final String routeId;
        private final List<double[]> corners = new ArrayList<double[]>();
        private String worldName = "";
        private boolean awaitingConfirm;

        private Session(String routeId) {
            this.routeId = routeId;
        }

        public String routeId() {
            return routeId;
        }

        public int cornerCount() {
            return corners.size();
        }

        public boolean awaitingConfirm() {
            return awaitingConfirm;
        }
    }

    private static final class Preview {
        private final String routeId;
        private final long expiresAt;

        private Preview(String routeId, long expiresAt) {
            this.routeId = routeId;
            this.expiresAt = expiresAt;
        }
    }

    // ---------------------------------------------------------------- sessions

    public Session session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Starts a drawing session. An existing route is opened for editing with its corners loaded. */
    public void begin(Player player, String routeId) {
        Session current = sessions.get(player.getUniqueId());
        if (current != null) {
            plugin.msg().send(player, "route.busy", "route", current.routeId);
            return;
        }

        String id = routeId.toLowerCase();
        Session session = new Session(id);
        Route existing = plugin.routes().registry().get(id);
        if (existing != null) {
            session.worldName = existing.worldName();
            for (double[] corner : existing.corners()) {
                session.corners.add(new double[] { corner[0], corner[1], corner[2] });
            }
        }
        sessions.put(player.getUniqueId(), session);
        giveWand(player);

        if (existing == null) {
            plugin.msg().send(player, "route.created", "route", id);
        } else {
            plugin.msg().send(player, "route.editing", "route", id, "corners", session.corners.size());
        }
        plugin.msg().send(player, "route.wand-hint");
    }

    public void cancel(Player player) {
        if (sessions.remove(player.getUniqueId()) == null) {
            plugin.msg().send(player, "route.no-session");
            return;
        }
        plugin.msg().send(player, "route.cancelled");
    }

    public void forget(UUID playerId) {
        sessions.remove(playerId);
        previews.remove(playerId);
    }

    public void clear() {
        sessions.clear();
        previews.clear();
    }

    // ---------------------------------------------------------------- corners

    /** Corner from a wand click: the walkers stand on top of the clicked block, centred. */
    public void addCorner(Player player, Block block) {
        add(player, block.getWorld(), block.getX() + 0.5D, block.getY() + 1.0D, block.getZ() + 0.5D);
    }

    /** Corner from /jobsadmin route add: wherever the admin is standing, handy while flying. */
    public void addCorner(Player player, Location location) {
        add(player, location.getWorld(), location.getX(), location.getY(), location.getZ());
    }

    private void add(Player player, World world, double x, double y, double z) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            plugin.msg().send(player, "route.no-session");
            return;
        }
        if (session.worldName.isEmpty()) {
            session.worldName = world.getName();
        } else if (!session.worldName.equals(world.getName())) {
            plugin.msg().send(player, "route.wrong-world", "world", session.worldName);
            return;
        }

        session.corners.add(new double[] { x, y, z });
        session.awaitingConfirm = false;
        plugin.msg().send(player, "route.corner-added",
                "index", session.corners.size(),
                "x", round(x), "y", round(y), "z", round(z));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_HAT, 0.7F, 1.6F);
    }

    public void undo(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            plugin.msg().send(player, "route.no-session");
            return;
        }
        if (session.corners.isEmpty()) {
            plugin.msg().send(player, "route.corner-none");
            return;
        }
        session.corners.remove(session.corners.size() - 1);
        session.awaitingConfirm = false;
        plugin.msg().send(player, "route.corner-removed", "index", session.corners.size() + 1);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BASS, 0.7F, 0.8F);
    }

    // ---------------------------------------------------------------- confirmation

    /** Shows the finished line and asks the question, with the answer one click away. */
    public void done(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            plugin.msg().send(player, "route.no-session");
            return;
        }
        Route existing = plugin.routes().registry().get(session.routeId);
        boolean loop = existing == null || existing.loop();
        RoutePath path = new RoutePath(session.corners, loop);
        if (!path.valid()) {
            plugin.msg().send(player, "route.need-corners");
            return;
        }

        session.awaitingConfirm = true;
        double speed = existing == null ? plugin.settings().routeSpeed : existing.speed();

        player.sendMessage(plugin.msg().get("route.summary-header"));
        player.sendMessage(plugin.msg().get("route.summary-title", "route", session.routeId));
        player.sendMessage(plugin.msg().get("route.summary-line",
                "corners", path.cornerCount(),
                "length", (long) path.length(),
                "seconds", (long) (path.cycleLength() / Math.max(0.1D, speed))));
        player.sendMessage(plugin.msg().get("route.summary-question"));

        TextComponent buttons = new TextComponent("");
        buttons.addExtra(button("route.yes-button", "route.yes-hover", "/jobsadmin route confirm yes"));
        buttons.addExtra(new TextComponent("    "));
        buttons.addExtra(button("route.no-button", "route.no-hover", "/jobsadmin route confirm no"));
        player.spigot().sendMessage(buttons);

        player.sendMessage(plugin.msg().get("route.confirm-text"));
        player.sendMessage(plugin.msg().get("route.summary-header"));
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

    /** Yes writes the route and starts it; no goes back to drawing with every corner still there. */
    public void confirm(Player player, boolean accepted) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.awaitingConfirm) {
            plugin.msg().send(player, "route.not-awaiting");
            return;
        }
        if (!accepted) {
            session.awaitingConfirm = false;
            plugin.msg().send(player, "route.back-to-edit");
            return;
        }

        Route route = plugin.routes().registry().get(session.routeId);
        if (route == null) {
            route = new Route(session.routeId);
            route.mode(RoutePath.MODE_LOOP);
            route.speed(plugin.settings().routeSpeed);
            route.population(plugin.settings().routePopulation);
            route.activationRange(plugin.settings().routeActivationRange);
            route.names(Arrays.asList("مواطن", "ساكن المدينة"));
        }
        route.worldName(session.worldName);
        route.corners(session.corners);

        plugin.routes().registry().put(route);
        plugin.routes().rebuild();
        sessions.remove(player.getUniqueId());
        takeWand(player);

        plugin.msg().send(player, "route.saved", "route", route.id(), "walkers", route.population());
        plugin.msg().send(player, "route.cap-note", "max", plugin.settings().maxActiveWalkers);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
    }

    // ---------------------------------------------------------------- preview

    public void preview(Player player, Route route) {
        previews.put(player.getUniqueId(),
                new Preview(route.id(), System.currentTimeMillis() + PREVIEW_SECONDS * 1000L));
        plugin.msg().send(player, "route.showing", "route", route.id(), "seconds", PREVIEW_SECONDS);
    }

    // ---------------------------------------------------------------- visuals

    /** Called twice a second. Redraws every open session and every running preview. */
    public void tickVisuals() {
        if (sessions.isEmpty() && previews.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || !player.isOnline() || session.corners.isEmpty()) {
                continue;
            }
            Route existing = plugin.routes().registry().get(session.routeId);
            draw(player, session.corners, session.worldName, existing == null || existing.loop());
        }

        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Preview>> it = previews.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Preview> entry = it.next();
            Preview preview = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            Route route = plugin.routes().registry().get(preview.routeId);
            if (player == null || !player.isOnline() || route == null || now > preview.expiresAt) {
                it.remove();
                continue;
            }
            draw(player, route.corners(), route.worldName(), route.loop());
        }
    }

    /**
     * Two clearly different marks: a column of flame on every corner so the turns read from a
     * distance, and small white dots every half block along the exact line the walkers follow.
     */
    private void draw(Player player, List<double[]> corners, String worldName, boolean loop) {
        World world = Bukkit.getWorld(worldName);
        if (world == null || !world.equals(player.getWorld())) {
            return;
        }
        double range = plugin.settings().particleRange;
        double rangeSquared = range * range;
        Location eye = player.getLocation();

        for (double[] corner : corners) {
            if (farther(eye, corner, rangeSquared)) {
                continue;
            }
            for (int i = 0; i < CORNER_HEIGHT; i++) {
                player.spawnParticle(Particle.FLAME, corner[0], corner[1] + 0.35D * i, corner[2],
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        RoutePath path = new RoutePath(corners, loop);
        if (!path.valid()) {
            return;
        }
        double[] point = new double[3];
        int budget = LINE_BUDGET;
        for (double walked = 0.0D; walked < path.length() && budget > 0; walked += LINE_STEP) {
            path.pointAt(walked, point);
            if (farther(eye, point, rangeSquared)) {
                continue;
            }
            budget--;
            player.spawnParticle(Particle.END_ROD, point[0], point[1] + 0.15D, point[2],
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static boolean farther(Location from, double[] point, double rangeSquared) {
        double dx = point[0] - from.getX();
        double dy = point[1] - from.getY();
        double dz = point[2] - from.getZ();
        return dx * dx + dy * dy + dz * dz > rangeSquared;
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
        plugin.msg().send(player, "route.wand-given");
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
                "&7نقرة يمين على بلوك &8- &fزاوية جديدة",
                "&7نقرة يسار على بلوك &8- &fتراجع",
                "&8/jobsadmin route done")));
        item.setItemMeta(meta);
        return item;
    }

    private static String round(double value) {
        return String.valueOf(Math.round(value * 10.0D) / 10.0D);
    }
}

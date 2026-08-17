package com.adventurecity.jobs.route;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.ai.NpcPersona;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Runs the city dwellers: who exists as an entity right now, and where each of them is.
 *
 * <p>Three ideas carry the whole design:</p>
 * <ul>
 *   <li><strong>Walkers are numbers first.</strong> Every walker advances every tick whether or not
 *       it has a body, so the streets stay coherent and a walker reappears where it should be.</li>
 *   <li><strong>A hard server-wide cap.</strong> No matter how many routes are drawn, only
 *       {@code routes.maxActiveWalkers} entities exist at once, and they are always the ones
 *       nearest to a player.</li>
 *   <li><strong>No pathfinding.</strong> The admin drew the line, so the line is walkable. Walkers
 *       are spawned with {@code setAI(false)} and moved by teleport, which costs close to nothing
 *       and is the reason this system does not drag the server down.</li>
 * </ul>
 */
public final class RouteService {

    /** Written into the entity's NBT, so leftovers from a crash can be found on the next start. */
    public static final String TAG = "acrp_route_npc";

    private static final String DEFAULT_NAME = "مواطن";
    private static final long TELEPORT_PERIOD_TICKS = 2L;
    private static final int MANAGE_EVERY_MOVES = 10;

    private final ACRPJobsPlugin plugin;
    private final RouteRegistry registry;
    private final RouteEditor editor;
    private final Random random = new Random();

    private final Map<String, List<RouteWalker>> walkers = new LinkedHashMap<String, List<RouteWalker>>();
    private final Map<UUID, RouteWalker> byEntity = new HashMap<UUID, RouteWalker>();
    private final Set<String> warnedTypes = new HashSet<String>();

    private final double[] point = new double[3];
    private int movesSinceManage;
    private long lastMoveMillis;

    public RouteService(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
        this.registry = new RouteRegistry(new File(plugin.getDataFolder(), "routes.yml"), plugin.getLogger());
        this.editor = new RouteEditor(plugin);
    }

    public RouteRegistry registry() {
        return registry;
    }

    public RouteEditor editor() {
        return editor;
    }

    // ---------------------------------------------------------------- lifecycle

    /** Reads routes.yml and rebuilds the walker list. Safe to call again on /jobsadmin reload. */
    public void load() {
        registry.load();
        rebuild();
    }

    /**
     * Deletes every entity carrying our tag. Called on enable so a crash - which leaves villagers
     * standing in the map forever - cleans itself up on the next boot.
     */
    public int sweep() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    public void rebuild() {
        despawnAll();
        walkers.clear();
        for (Route route : registry.all()) {
            List<RouteWalker> list = new ArrayList<RouteWalker>();
            if (route.path().valid()) {
                for (int i = 0; i < route.population(); i++) {
                    list.add(create(route));
                }
            }
            walkers.put(route.id(), list);
        }
    }

    private RouteWalker create(Route route) {
        RouteWalker walker = new RouteWalker(route);
        // Random start along the lap, so a group does not file out of one spot.
        walker.progress(random.nextDouble() * route.path().cycleLength());
        walker.speedFactor(0.9D + random.nextDouble() * 0.2D);
        walker.segment(route.path().segmentAt(walker.progress()));
        walker.name(pickName(route));
        return walker;
    }

    private String pickName(Route route) {
        List<String> names = route.names();
        if (!names.isEmpty()) {
            return Msg.color(names.get(random.nextInt(names.size())));
        }
        if (route.hasPersona()) {
            NpcPersona persona = plugin.npcs().get(route.persona());
            if (persona != null) {
                return Msg.color(persona.name());
            }
        }
        return DEFAULT_NAME;
    }

    public void shutdown() {
        editor.clear();
        despawnAll();
    }

    public void despawnAll() {
        for (List<RouteWalker> list : walkers.values()) {
            for (RouteWalker walker : list) {
                despawn(walker);
            }
        }
        byEntity.clear();
    }

    // ---------------------------------------------------------------- ticking

    /**
     * Called every {@value #TELEPORT_PERIOD_TICKS} ticks. Advances every walker and moves the ones
     * that have a body; the spawn/despawn decision runs once a second inside the same task.
     */
    public void tick() {
        if (!plugin.settings().routesEnabled) {
            if (!byEntity.isEmpty()) {
                despawnAll();
            }
            return;
        }
        if (walkers.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        double delta = (now - lastMoveMillis) / 1000.0D;
        lastMoveMillis = now;
        // First run, a lag spike or a clock jump must not teleport everybody halfway across town.
        if (delta <= 0.0D || delta > 1.0D) {
            delta = TELEPORT_PERIOD_TICKS / 20.0D;
        }

        for (List<RouteWalker> list : walkers.values()) {
            for (RouteWalker walker : list) {
                advance(walker, delta, now);
            }
        }

        if (++movesSinceManage >= MANAGE_EVERY_MOVES) {
            movesSinceManage = 0;
            manage();
        }
    }

    private void advance(RouteWalker walker, double delta, long now) {
        Route route = walker.route();
        RoutePath path = route.path();
        if (!path.valid()) {
            return;
        }

        if (!walker.frozen(now)) {
            walker.progress(walker.progress() + route.speed() * walker.speedFactor() * delta);
            int segment = path.segmentAt(walker.progress());
            if (segment != walker.segment()) {
                walker.segment(segment);
                // A short breather at the corner - the cheapest thing that stops them looking robotic.
                if (route.pauseChance() > 0.0D && random.nextDouble() < route.pauseChance()) {
                    walker.pauseUntil(now + 1500L + (long) (random.nextDouble() * 2500.0D));
                }
            }
        }

        LivingEntity entity = walker.entity();
        if (entity == null) {
            return;
        }
        if (!entity.isValid()) {
            forget(walker);
            return;
        }
        Location target = locationOf(walker);
        if (target == null) {
            return;
        }
        faceTalker(walker, target);
        entity.teleport(target);
    }

    /** While a player is talking to a walker it stops and turns to face them. */
    private void faceTalker(RouteWalker walker, Location target) {
        UUID talking = walker.talkingWith();
        if (talking == null) {
            return;
        }
        Player player = Bukkit.getPlayer(talking);
        if (player == null || !player.getWorld().equals(target.getWorld())) {
            return;
        }
        double dx = player.getLocation().getX() - target.getX();
        double dz = player.getLocation().getZ() - target.getZ();
        if (dx == 0.0D && dz == 0.0D) {
            return;
        }
        target.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
    }

    /**
     * Decides who gets a body. Everything within its route's activation range competes, the list is
     * sorted by distance to the nearest player, and only the first {@code maxActiveWalkers} win.
     */
    private void manage() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        List<Candidate> candidates = new ArrayList<Candidate>();

        for (List<RouteWalker> list : walkers.values()) {
            for (RouteWalker walker : list) {
                releaseFinishedTalk(walker);

                Location location = locationOf(walker);
                if (location == null) {
                    despawn(walker);
                    continue;
                }
                double nearest = nearestPlayerDistanceSquared(location, online);
                double range = walker.route().activationRange();
                // A walker in mid-conversation always keeps its body, wherever the cap falls.
                boolean talking = walker.talkingWith() != null;
                if (!talking && nearest > range * range) {
                    despawn(walker);
                    continue;
                }
                candidates.add(new Candidate(walker, talking ? -1.0D : nearest));
            }
        }

        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Double.compare(a.distanceSquared, b.distanceSquared);
            }
        });

        int cap = plugin.settings().maxActiveWalkers;
        for (int i = 0; i < candidates.size(); i++) {
            RouteWalker walker = candidates.get(i).walker;
            if (i < cap) {
                spawn(walker);
            } else {
                despawn(walker);
            }
        }
    }

    private static final class Candidate {
        private final RouteWalker walker;
        private final double distanceSquared;

        private Candidate(RouteWalker walker, double distanceSquared) {
            this.walker = walker;
            this.distanceSquared = distanceSquared;
        }
    }

    private double nearestPlayerDistanceSquared(Location location, Collection<? extends Player> online) {
        double best = Double.MAX_VALUE;
        for (Player player : online) {
            if (!player.getWorld().equals(location.getWorld())) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(location);
            if (distance < best) {
                best = distance;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- entities

    private void spawn(RouteWalker walker) {
        if (walker.spawned()) {
            return;
        }
        forget(walker);

        Location location = locationOf(walker);
        if (location == null) {
            return;
        }
        World world = location.getWorld();
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        EntityType type = resolveType(walker.route());
        if (type == null) {
            return;
        }

        Entity spawned;
        try {
            spawned = world.spawnEntity(location, type);
        } catch (RuntimeException ex) {
            warnOnce(walker.route().entityType(), "[ACRPJobs] Route '" + walker.route().id()
                    + "' could not spawn " + type + ": " + ex.getMessage());
            return;
        }
        if (!(spawned instanceof LivingEntity)) {
            spawned.remove();
            warnOnce(walker.route().entityType(), "[ACRPJobs] Route '" + walker.route().id()
                    + "' uses entity " + type + ", which is not a creature - use VILLAGER.");
            return;
        }

        LivingEntity entity = (LivingEntity) spawned;
        entity.setCustomName(walker.name());
        entity.setCustomNameVisible(true);
        // The single most important line here: no vanilla AI means no pathfinding cost at all, and
        // the walker can never wander off the drawn line.
        entity.setAI(false);
        entity.setCollidable(false);
        entity.setSilent(true);
        entity.setInvulnerable(true);
        entity.setRemoveWhenFarAway(false);
        entity.setCanPickupItems(false);
        entity.addScoreboardTag(TAG);
        if (entity instanceof Villager) {
            try {
                // A nitwit has no trades, so the villager screen stays shut even if another plugin
                // un-cancels our interact handler.
                ((Villager) entity).setProfession(Villager.Profession.NITWIT);
            } catch (RuntimeException ignored) {
                // Older or modded profession enums - the cancelled interact event still covers us.
            }
        }

        walker.entity(entity);
        byEntity.put(entity.getUniqueId(), walker);
    }

    private void despawn(RouteWalker walker) {
        LivingEntity entity = walker.entity();
        if (entity == null) {
            return;
        }
        forget(walker);
        if (entity.isValid()) {
            entity.remove();
        }
    }

    private void forget(RouteWalker walker) {
        LivingEntity entity = walker.entity();
        if (entity != null) {
            byEntity.remove(entity.getUniqueId());
        }
        walker.entity(null);
        walker.talkingWith(null);
    }

    private EntityType resolveType(Route route) {
        try {
            return EntityType.valueOf(route.entityType());
        } catch (IllegalArgumentException ex) {
            warnOnce(route.entityType(), "[ACRPJobs] Route '" + route.id() + "' has an unknown entity type '"
                    + route.entityType() + "' - falling back to VILLAGER.");
            return EntityType.VILLAGER;
        }
    }

    private void warnOnce(String key, String message) {
        if (warnedTypes.add(key)) {
            plugin.getLogger().warning(message);
        }
    }

    /** Current position of a walker, or null when its world is not loaded. */
    public Location locationOf(RouteWalker walker) {
        Route route = walker.route();
        World world = Bukkit.getWorld(route.worldName());
        if (world == null || !route.path().valid()) {
            return null;
        }
        route.path().pointAt(walker.progress(), point);
        return new Location(world, point[0], point[1], point[2],
                route.path().yawAt(walker.progress()), 0.0F);
    }

    // ---------------------------------------------------------------- dialogue

    public boolean isRouteEntity(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(TAG);
    }

    public RouteWalker walkerOf(Entity entity) {
        return entity == null ? null : byEntity.get(entity.getUniqueId());
    }

    /**
     * The persona behind a walker, looked up through the route rather than the display name. That
     * lets a route give its walkers any name at all and still have them talk.
     */
    public NpcPersona personaOf(Entity entity) {
        RouteWalker walker = walkerOf(entity);
        if (walker == null || !walker.route().hasPersona()) {
            return null;
        }
        return plugin.npcs().get(walker.route().persona());
    }

    public void startTalking(Entity entity, Player player) {
        RouteWalker walker = walkerOf(entity);
        if (walker != null) {
            walker.talkingWith(player.getUniqueId());
        }
    }

    /**
     * Polls instead of hooking the dialogue service: once the conversation is over by any route -
     * ended, timed out, walked away, logged out - the walker starts moving again on the next check.
     */
    private void releaseFinishedTalk(RouteWalker walker) {
        UUID talking = walker.talkingWith();
        if (talking == null) {
            return;
        }
        Player player = Bukkit.getPlayer(talking);
        if (player == null || !player.isOnline() || !plugin.dialogue().isTalking(player)) {
            walker.talkingWith(null);
        }
    }

    /** Drops a walker's body when its chunk unloads, so it can never be left behind in the map. */
    public void onEntityUnloading(Entity entity) {
        RouteWalker walker = walkerOf(entity);
        if (walker != null) {
            forget(walker);
        }
        entity.remove();
    }

    // ---------------------------------------------------------------- reporting

    public int routeCount() {
        return registry.size();
    }

    public int walkerCount() {
        int total = 0;
        for (List<RouteWalker> list : walkers.values()) {
            total += list.size();
        }
        return total;
    }

    public int activeCount() {
        return byEntity.size();
    }

    public int activeCount(String routeId) {
        List<RouteWalker> list = walkers.get(routeId == null ? "" : routeId.toLowerCase());
        if (list == null) {
            return 0;
        }
        int active = 0;
        for (RouteWalker walker : list) {
            if (walker.spawned()) {
                active++;
            }
        }
        return active;
    }
}

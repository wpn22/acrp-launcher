package com.adventurecity.jobs.spot;

import com.adventurecity.jobs.ACRPJobsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.MaterialData;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the work spots: what is dirty right now, what the players can see of it, and what happens
 * when somebody cleans it.
 *
 * <p>The performance discipline is the one the route walkers already proved:</p>
 * <ul>
 *   <li><strong>Only TRASH costs an entity.</strong> Dirt, plants and lamps are particles drawn to
 *       nearby players, which costs the server nothing at all.</li>
 *   <li><strong>Entities exist only near players</strong>, under a server-wide cap, so a city
 *       covered in work is not a city full of entities.</li>
 *   <li><strong>Everything is tagged</strong> and swept on boot, so a crash cannot leave rubbish
 *       bags scattered through the map forever.</li>
 * </ul>
 */
public final class SpotService {

    /** Written into the entity's NBT so leftovers from a crash can be found on the next start. */
    public static final String TAG = "acrp_work_spot";

    private static final int REFILL_EVERY_TICKS = 4;

    /** FALLING_DUST takes the block whose colour it borrows - dry brown specks off a plant. */
    private static final MaterialData THIRSTY = new MaterialData(Material.DIRT);

    private final ACRPJobsPlugin plugin;
    private final SpotRegistry registry;
    private final SpotRotation rotation = new SpotRotation();
    private final SpotEditor editor;
    private final PumpTool pump;

    /** Live entity per TRASH spot. Identity-keyed - two points can share coordinates. */
    private final Map<WorkSpot, Item> bodies = new IdentityHashMap<WorkSpot, Item>();
    private final Map<UUID, WorkSpot> byEntity = new HashMap<UUID, WorkSpot>();

    private int ticks;

    public SpotService(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
        this.registry = new SpotRegistry(new File(plugin.getDataFolder(), "spots.yml"), plugin.getLogger());
        this.editor = new SpotEditor(plugin);
        this.pump = new PumpTool(plugin);
    }

    public SpotRegistry registry() {
        return registry;
    }

    public SpotEditor editor() {
        return editor;
    }

    public PumpTool pump() {
        return pump;
    }

    public SpotRotation rotation() {
        return rotation;
    }

    // ---------------------------------------------------------------- lifecycle

    /** Reads spots.yml and seeds every pool. Safe to call again on /jobsadmin reload. */
    public void load() {
        despawnAll();
        registry.load();
        long now = System.currentTimeMillis();
        for (SpotPool pool : registry.all()) {
            rotation.seed(pool, now);
        }
    }

    /** Deletes every entity carrying our tag - cleans up after a crash on the next boot. */
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

    public void shutdown() {
        editor.clear();
        pump.clear();
        despawnAll();
    }

    public void despawnAll() {
        for (Item item : bodies.values()) {
            if (item != null && item.isValid()) {
                item.remove();
            }
        }
        bodies.clear();
        byEntity.clear();
    }

    // ---------------------------------------------------------------- ticking

    /** Called twice a second. Rotation runs every other second; visuals run every tick. */
    public void tick() {
        pump.tick();
        editor.tickVisuals();
        if (!plugin.settings().spotsEnabled) {
            if (!bodies.isEmpty()) {
                despawnAll();
            }
            return;
        }
        if (registry.size() == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        if (++ticks >= REFILL_EVERY_TICKS) {
            ticks = 0;
            for (SpotPool pool : registry.all()) {
                rotation.refill(pool, now);
            }
            manageBodies();
        }
        drawParticles();
    }

    /**
     * Decides which TRASH spots get a real entity: nearest to a player first, capped server-wide.
     * Everything else is despawned, and comes back the moment somebody walks past.
     */
    private void manageBodies() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        List<Candidate> candidates = new ArrayList<Candidate>();

        for (SpotPool pool : registry.all()) {
            if (pool.type() != SpotType.TRASH) {
                continue;
            }
            World world = Bukkit.getWorld(pool.worldName());
            if (world == null) {
                continue;
            }
            for (WorkSpot spot : pool.points()) {
                if (!spot.active()) {
                    despawn(spot);
                    continue;
                }
                double nearest = nearestPlayerDistanceSquared(world, spot, online);
                double range = pool.activationRange();
                if (nearest > range * range) {
                    despawn(spot);
                    continue;
                }
                candidates.add(new Candidate(spot, world, nearest));
            }
        }

        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Double.compare(a.distanceSquared, b.distanceSquared);
            }
        });

        int cap = plugin.settings().maxActiveSpotEntities;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (i < cap) {
                spawn(candidate.spot, candidate.world);
            } else {
                despawn(candidate.spot);
            }
        }
    }

    private static final class Candidate {
        private final WorkSpot spot;
        private final World world;
        private final double distanceSquared;

        private Candidate(WorkSpot spot, World world, double distanceSquared) {
            this.spot = spot;
            this.world = world;
            this.distanceSquared = distanceSquared;
        }
    }

    private double nearestPlayerDistanceSquared(World world, WorkSpot spot,
                                                Collection<? extends Player> online) {
        double best = Double.MAX_VALUE;
        for (Player player : online) {
            if (!player.getWorld().equals(world)) {
                continue;
            }
            Location at = player.getLocation();
            double distance = spot.distanceSquared(at.getX(), at.getY(), at.getZ());
            if (distance < best) {
                best = distance;
            }
        }
        return best;
    }

    /** Particle types cost nothing to place, so they are simply drawn for whoever is close. */
    private void drawParticles() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) {
            return;
        }
        double range = plugin.settings().particleRange;
        double rangeSquared = range * range;

        for (SpotPool pool : registry.all()) {
            if (pool.type() == SpotType.TRASH) {
                continue;
            }
            World world = Bukkit.getWorld(pool.worldName());
            if (world == null) {
                continue;
            }
            for (WorkSpot spot : pool.points()) {
                if (!spot.active()) {
                    continue;
                }
                for (Player player : online) {
                    if (!player.getWorld().equals(world)) {
                        continue;
                    }
                    Location at = player.getLocation();
                    if (spot.distanceSquared(at.getX(), at.getY(), at.getZ()) > rangeSquared) {
                        continue;
                    }
                    drawSpot(player, pool.type(), spot);
                }
            }
        }
    }

    private void drawSpot(Player player, SpotType type, WorkSpot spot) {
        switch (type) {
            case DIRT:
                // A low grey smudge on the ground.
                player.spawnParticle(Particle.SMOKE_NORMAL, spot.x(), spot.y() + 0.1D, spot.z(),
                        4, 0.35D, 0.02D, 0.35D, 0.0D);
                break;
            case PLANT:
                // Drooping, thirsty - dust falling off the leaves.
                player.spawnParticle(Particle.FALLING_DUST, spot.x(), spot.y() + 0.6D, spot.z(),
                        3, 0.25D, 0.25D, 0.25D, 0.0D, THIRSTY);
                break;
            case LAMP:
                // Dead lamp: a wisp of smoke and the odd spark.
                player.spawnParticle(Particle.SMOKE_NORMAL, spot.x(), spot.y() + 0.5D, spot.z(),
                        2, 0.1D, 0.2D, 0.1D, 0.01D);
                player.spawnParticle(Particle.CRIT_MAGIC, spot.x(), spot.y() + 0.5D, spot.z(),
                        2, 0.2D, 0.2D, 0.2D, 0.0D);
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------- entities

    private void spawn(WorkSpot spot, World world) {
        Item existing = bodies.get(spot);
        if (existing != null && existing.isValid()) {
            // Dropped items despawn on their own after five minutes; keeping the age down stops
            // the rubbish quietly vanishing while a player is standing right next to it.
            existing.setTicksLived(1);
            return;
        }
        Location location = new Location(world, spot.x(), spot.y() + 0.2D, spot.z());
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }

        Material material = Material.matchMaterial(plugin.settings().trashMaterial);
        ItemStack stack = new ItemStack(material == null ? Material.PAPER : material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.msg().get("spot.trash-name"));
            stack.setItemMeta(meta);
        }

        Item item;
        try {
            item = world.dropItem(location, stack);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[ACRPJobs] Could not place a work spot item: " + ex.getMessage());
            return;
        }
        // Never picked up by walking over it - collecting is a deliberate right-click.
        item.setPickupDelay(Short.MAX_VALUE);
        item.setCustomName(plugin.msg().get("spot.trash-name"));
        item.setCustomNameVisible(true);
        item.setInvulnerable(true);
        item.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        item.addScoreboardTag(TAG);

        bodies.put(spot, item);
        byEntity.put(item.getUniqueId(), spot);
    }

    private void despawn(WorkSpot spot) {
        Item item = bodies.remove(spot);
        if (item == null) {
            return;
        }
        byEntity.remove(item.getUniqueId());
        if (item.isValid()) {
            item.remove();
        }
    }

    /**
     * Only a city employee on duty may clear the city's work. Without this any passer-by could
     * strip every bag off the map and leave the cleaners with nothing to do.
     */
    public boolean canWork(Player player) {
        return plugin.duty().isOnDuty(player);
    }

    public boolean isSpotEntity(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(TAG);
    }

    public WorkSpot spotOf(Entity entity) {
        return entity == null ? null : byEntity.get(entity.getUniqueId());
    }

    /** Drops a bag's body when its chunk unloads, so it is never saved into the map. */
    public void onEntityUnloading(Entity entity) {
        WorkSpot spot = spotOf(entity);
        if (spot != null) {
            despawn(spot);
            return;
        }
        entity.remove();
    }

    // ---------------------------------------------------------------- clearing

    /**
     * The one way a spot gets cleared, whichever job did it. Handles the reward feedback, the
     * rotation bookkeeping and the contract counter in one place.
     *
     * @return false when the spot was already gone - two players clicking the same bag
     */
    public boolean clear(Player player, WorkSpot spot) {
        if (spot == null || !spot.active()) {
            return false;
        }
        if (!canWork(player)) {
            plugin.msg().send(player, "spot.need-duty");
            return false;
        }
        SpotPool pool = spot.pool();
        Location where = new Location(Bukkit.getWorld(pool.worldName()), spot.x(), spot.y(), spot.z());

        rotation.clear(pool, spot, System.currentTimeMillis());
        despawn(spot);

        if (where.getWorld() != null) {
            player.spawnParticle(Particle.VILLAGER_HAPPY, where.clone().add(0.0D, 0.6D, 0.0D),
                    12, 0.4D, 0.4D, 0.4D, 0.0D);
        }
        player.playSound(player.getLocation(), clearSound(pool.type()), 0.8F, 1.3F);

        if (pool.type() == SpotType.LAMP && pool.swapBlock()) {
            relight(where);
        }

        plugin.contracts().onSpotCleared(player, pool.id());
        return true;
    }

    private static Sound clearSound(SpotType type) {
        switch (type) {
            case DIRT:
            case PLANT:
                return Sound.ENTITY_PLAYER_SPLASH;
            case LAMP:
                return Sound.BLOCK_LEVER_CLICK;
            default:
                return Sound.ENTITY_ITEM_PICKUP;
        }
    }

    /**
     * Optional and off by default: a redstone lamp forced on without power reverts as soon as
     * anything updates a block nearby, so the particles remain the signal that always works.
     */
    private void relight(Location where) {
        if (!plugin.settings().allowBlockChanges || where.getWorld() == null) {
            return;
        }
        Block block = where.getBlock();
        if (block.getType() == Material.REDSTONE_LAMP_OFF) {
            block.setType(Material.REDSTONE_LAMP_ON, false);
        }
    }

    // ---------------------------------------------------------------- lookups

    /** Nearest active spot of a pool to this player, or null. Drives the objective marker. */
    public WorkSpot nearestActive(Player player, String poolId) {
        SpotPool pool = registry.get(poolId);
        if (pool == null) {
            return null;
        }
        World world = Bukkit.getWorld(pool.worldName());
        if (world == null || !world.equals(player.getWorld())) {
            return null;
        }
        Location at = player.getLocation();
        WorkSpot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (WorkSpot spot : pool.points()) {
            if (!spot.active()) {
                continue;
            }
            double distance = spot.distanceSquared(at.getX(), at.getY(), at.getZ());
            if (distance < bestDistance) {
                best = spot;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Nearest active spot the player is looking at, within range - used by the water pump. */
    public WorkSpot targeted(Player player, double range, SpotType type) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        WorkSpot best = null;
        double bestDistance = Double.MAX_VALUE;

        for (SpotPool pool : registry.all()) {
            if (type != null && pool.type() != type) {
                continue;
            }
            if (pool.type() == SpotType.TRASH) {
                continue;
            }
            World world = Bukkit.getWorld(pool.worldName());
            if (world == null || !world.equals(player.getWorld())) {
                continue;
            }
            for (WorkSpot spot : pool.points()) {
                if (!spot.active()) {
                    continue;
                }
                double distanceSquared = spot.distanceSquared(eye.getX(), eye.getY(), eye.getZ());
                if (distanceSquared > range * range || distanceSquared > bestDistance) {
                    continue;
                }
                // Bukkit 1.12.2 has no ray-trace helper, so compare direction vectors instead -
                // the same approach /jobsadmin npc link already uses.
                Vector toSpot = new Vector(
                        spot.x() - eye.getX(), spot.y() + 0.3D - eye.getY(), spot.z() - eye.getZ());
                if (toSpot.lengthSquared() < 0.01D) {
                    return spot;
                }
                if (toSpot.normalize().dot(direction) < 0.9D) {
                    continue;
                }
                best = spot;
                bestDistance = distanceSquared;
            }
        }
        return best;
    }

    /**
     * The rubbish bag a player is reaching for: nearest tagged body within {@code reach} that is
     * roughly in front of them. Only bags that currently exist as entities can be grabbed, which is
     * exactly right - if you cannot see it, you cannot pick it up.
     */
    public WorkSpot targetedEntitySpot(Player player, double reach) {
        if (bodies.isEmpty()) {
            return null;
        }
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        WorkSpot best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Map.Entry<WorkSpot, Item> entry : bodies.entrySet()) {
            Item item = entry.getValue();
            if (item == null || !item.isValid() || !item.getWorld().equals(player.getWorld())) {
                continue;
            }
            Location at = item.getLocation();
            double distanceSquared = at.distanceSquared(eye);
            if (distanceSquared > reach * reach || distanceSquared >= bestDistance) {
                continue;
            }
            Vector toItem = at.toVector().subtract(eye.toVector());
            // Standing on top of a bag counts; otherwise it has to be in front of you. Bukkit
            // 1.12.2 has no ray-trace helper, so this compares direction vectors instead.
            if (toItem.lengthSquared() > 0.25D && toItem.normalize().dot(direction) < 0.75D) {
                continue;
            }
            best = entry.getKey();
            bestDistance = distanceSquared;
        }
        return best;
    }

    public Location locationOf(WorkSpot spot) {
        World world = Bukkit.getWorld(spot.pool().worldName());
        if (world == null) {
            return null;
        }
        return new Location(world, spot.x(), spot.y(), spot.z());
    }

    public int activeCount(String poolId) {
        SpotPool pool = registry.get(poolId);
        return pool == null ? 0 : SpotRotation.countActive(pool);
    }

    public int bodyCount() {
        return bodies.size();
    }
}

package com.adventurecity.jobs.config;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Zones are defined in game by admins (/jobsadmin zone set ...) and stored in zones.yml, so job
 * files stay portable across maps - a job references a zone id or a zone group, never coordinates.
 */
public final class ZoneRegistry {

    private final File file;
    private final Logger logger;
    private final Random random = new Random();
    private final Map<String, Zone> zones = new LinkedHashMap<String, Zone>();

    public ZoneRegistry(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() {
        zones.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("zones");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String world = section.getString("world");
            if (world == null || world.isEmpty()) {
                logger.warning("[ACRPJobs] Zone '" + id + "' has no world - skipped.");
                continue;
            }
            zones.put(id.toLowerCase(), new Zone(
                    id.toLowerCase(),
                    section.getString("group", ""),
                    world,
                    section.getDouble("x"),
                    section.getDouble("y"),
                    section.getDouble("z"),
                    Math.max(1.0D, section.getDouble("radius", 5.0D))));
        }
        logger.info("[ACRPJobs] Loaded " + zones.size() + " zone(s).");
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Zone zone : zones.values()) {
            String base = "zones." + zone.id() + ".";
            config.set(base + "group", zone.group());
            config.set(base + "world", zone.worldName());
            config.set(base + "x", zone.x());
            config.set(base + "y", zone.y());
            config.set(base + "z", zone.z());
            config.set(base + "radius", zone.radius());
        }
        try {
            config.save(file);
        } catch (IOException ex) {
            logger.severe("[ACRPJobs] Could not save zones.yml: " + ex.getMessage());
        }
    }

    public Zone get(String id) {
        return id == null ? null : zones.get(id.toLowerCase());
    }

    public List<Zone> group(String group) {
        List<Zone> out = new ArrayList<Zone>();
        if (group == null || group.isEmpty()) {
            return out;
        }
        for (Zone zone : zones.values()) {
            if (group.equalsIgnoreCase(zone.group())) {
                out.add(zone);
            }
        }
        return out;
    }

    /** Random member of a group, or null when the group is empty. */
    public Zone randomFromGroup(String group) {
        List<Zone> candidates = group(group);
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * Random member of a group, avoiding {@code exclude} when the group has more than one member -
     * keeps multi-stop routes from sending the player to the same house twice in a row.
     */
    public Zone randomFromGroup(String group, Zone exclude) {
        List<Zone> candidates = group(group);
        if (candidates.isEmpty()) {
            return null;
        }
        if (exclude != null && candidates.size() > 1) {
            candidates.remove(exclude);
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    public Zone set(String id, Location location, double radius, String group) {
        String key = id.toLowerCase();
        Zone existing = zones.get(key);
        String finalGroup = group;
        if (finalGroup == null) {
            finalGroup = existing == null ? "" : existing.group();
        }
        Zone zone = new Zone(key, finalGroup, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), Math.max(1.0D, radius));
        zones.put(key, zone);
        save();
        return zone;
    }

    public boolean remove(String id) {
        if (zones.remove(id.toLowerCase()) == null) {
            return false;
        }
        save();
        return true;
    }

    public Collection<Zone> all() {
        return zones.values();
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public boolean groupExists(String group) {
        return !group(group).isEmpty();
    }
}

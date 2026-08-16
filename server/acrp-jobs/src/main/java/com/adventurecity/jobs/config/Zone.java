package com.adventurecity.jobs.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** A named spherical area in the world - the anchor every contract step points at. */
public final class Zone {

    private final String id;
    private String group;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final double radius;

    public Zone(String id, String group, String world, double x, double y, double z, double radius) {
        this.id = id;
        this.group = group == null ? "" : group;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
    }

    public String id() {
        return id;
    }

    public String group() {
        return group;
    }

    public void group(String group) {
        this.group = group == null ? "" : group;
    }

    public String worldName() {
        return world;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public double radius() {
        return radius;
    }

    /** May be null when the world is not loaded (for example a world that was renamed or removed). */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z);
    }

    public boolean contains(Location location) {
        return distanceTo(location) <= radius;
    }

    /**
     * Distance from the location to the zone centre, or {@link Double#MAX_VALUE} when the location is
     * in another world. Never throws - {@link Location#distance(Location)} does for cross-world pairs.
     */
    public double distanceTo(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return Double.MAX_VALUE;
        }
        double dx = location.getX() - x;
        double dy = location.getY() - y;
        double dz = location.getZ() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

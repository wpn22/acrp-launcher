package com.adventurecity.jobs.spot;

/**
 * One marked point in the city and whether it currently holds work.
 *
 * <p>Deliberately free of Bukkit: the spawned entity for a {@link SpotType#TRASH} point lives in a
 * map inside {@link SpotService}, so the point itself stays plain data that can be tested.</p>
 */
public final class WorkSpot {

    private final SpotPool pool;
    private final int index;
    private final double x;
    private final double y;
    private final double z;

    private boolean active;
    private long clearedAt;

    public WorkSpot(SpotPool pool, int index, double x, double y, double z) {
        this.pool = pool;
        this.index = index;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public SpotPool pool() {
        return pool;
    }

    /** Position in the pool's point list - stable, and what the admin sees when listing. */
    public int index() {
        return index;
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

    /** True while this point holds work a player can see and clear. */
    public boolean active() {
        return active;
    }

    /** When this point was last cleared - the clock its respawn delay runs against. */
    public long clearedAt() {
        return clearedAt;
    }

    public void activate() {
        this.active = true;
    }

    /** A worker cleared it: it goes quiet and starts resting. */
    public void clear(long now) {
        this.active = false;
        this.clearedAt = now;
    }

    /** Back to "never touched", so the next refill may pick it immediately. */
    public void reset() {
        this.active = false;
        this.clearedAt = 0L;
    }

    public double distanceSquared(double ox, double oy, double oz) {
        double dx = x - ox;
        double dy = y - oy;
        double dz = z - oz;
        return dx * dx + dy * dy + dz * dz;
    }
}

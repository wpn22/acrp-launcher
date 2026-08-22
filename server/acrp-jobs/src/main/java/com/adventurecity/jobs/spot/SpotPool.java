package com.adventurecity.jobs.spot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A named set of marked points that share a type, an active count and a respawn delay.
 *
 * <p>One pool is one kind of work in one part of the city: mark twenty spots, keep nine of them
 * dirty, and give a cleared one ten minutes before it comes back somewhere else.</p>
 *
 * <p>Bukkit-free like {@link WorkSpot} - the world is a name here, resolved in {@link SpotService}.</p>
 */
public final class SpotPool {

    /** More points than this in one pool is a map problem, not a config problem. */
    public static final int MAX_POINTS = 400;

    private final String id;
    private SpotType type = SpotType.TRASH;
    private String worldName = "";
    private int activeCount = 9;
    private int respawnMinutes = 10;
    private double activationRange = 48.0D;
    private boolean swapBlock;

    private final List<WorkSpot> points = new ArrayList<WorkSpot>();

    /** When each owed slot is due back. One entry per point cleared and not yet replaced. */
    private final List<Long> pendingReturns = new ArrayList<Long>();
    private WorkSpot lastCleared;

    public SpotPool(String id) {
        this.id = id == null ? "" : id.toLowerCase();
    }

    public String id() {
        return id;
    }

    public List<WorkSpot> points() {
        return Collections.unmodifiableList(points);
    }

    public int pointCount() {
        return points.size();
    }

    // ---------------------------------------------------------------- rotation state

    /** Records a slot that owes the pool a point at {@code at} millis. */
    public void queueReturn(long at) {
        pendingReturns.add(Long.valueOf(at));
    }

    public int pendingCount() {
        return pendingReturns.size();
    }

    /** Removes and counts every slot whose timer has run out. */
    public int takeDueReturns(long now) {
        int due = 0;
        for (int i = pendingReturns.size() - 1; i >= 0; i--) {
            if (pendingReturns.get(i).longValue() <= now) {
                pendingReturns.remove(i);
                due++;
            }
        }
        return due;
    }

    public long soonestReturn() {
        long soonest = Long.MAX_VALUE;
        for (Long at : pendingReturns) {
            if (at.longValue() < soonest) {
                soonest = at.longValue();
            }
        }
        return soonest;
    }

    /** Testing aid: make every owed slot due now so the rotation can be watched immediately. */
    public int forceReturnsDue() {
        int count = pendingReturns.size();
        for (int i = 0; i < count; i++) {
            pendingReturns.set(i, Long.valueOf(0L));
        }
        return count;
    }

    public void clearPending() {
        pendingReturns.clear();
        lastCleared = null;
    }

    /** The point cleared most recently, so a replacement can avoid landing on the same spot. */
    public WorkSpot lastCleared() {
        return lastCleared;
    }

    public void lastCleared(WorkSpot lastCleared) {
        this.lastCleared = lastCleared;
    }

    // ---------------------------------------------------------------- points

    /** Replaces every point. Rotation state is not carried over - the map changed, so it restarts. */
    public void points(List<double[]> replacement) {
        points.clear();
        clearPending();
        if (replacement == null) {
            return;
        }
        for (double[] point : replacement) {
            if (point == null || point.length < 3) {
                continue;
            }
            if (points.size() >= MAX_POINTS) {
                break;
            }
            points.add(new WorkSpot(this, points.size() + 1, point[0], point[1], point[2]));
        }
    }

    public SpotType type() {
        return type;
    }

    public void type(SpotType type) {
        this.type = type == null ? SpotType.TRASH : type;
    }

    public String worldName() {
        return worldName;
    }

    public void worldName(String worldName) {
        this.worldName = worldName == null ? "" : worldName;
    }

    /** How many points hold work at once. Capped by the number of points that exist. */
    public int activeCount() {
        return activeCount;
    }

    public void activeCount(int activeCount) {
        this.activeCount = clamp(activeCount, 0, MAX_POINTS);
    }

    public int respawnMinutes() {
        return respawnMinutes;
    }

    public void respawnMinutes(int respawnMinutes) {
        this.respawnMinutes = clamp(respawnMinutes, 0, 24 * 60);
    }

    public long respawnMillis() {
        return respawnMinutes * 60_000L;
    }

    /** Entities and particles only exist while a player is this close. */
    public double activationRange() {
        return activationRange;
    }

    public void activationRange(double activationRange) {
        if (Double.isNaN(activationRange)) {
            return;
        }
        this.activationRange = Math.max(8.0D, Math.min(200.0D, activationRange));
    }

    /**
     * LAMP pools only: also swap the block between its lit and unlit form. Off by default because
     * a redstone lamp forced on without power reverts on the next block update nearby - the
     * particles are the signal that always works.
     */
    public boolean swapBlock() {
        return swapBlock;
    }

    public void swapBlock(boolean swapBlock) {
        this.swapBlock = swapBlock;
    }

    public boolean usable() {
        return !worldName.isEmpty() && !points.isEmpty();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }
}

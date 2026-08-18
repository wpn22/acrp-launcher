package com.adventurecity.jobs.spot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Decides which marked points currently hold work, and when a cleared one comes back somewhere else.
 *
 * <p>The rule the city runs on: the admin marks a pool of points, a fixed number of them hold work
 * at any moment, and <strong>only a point somebody actually cleared</strong> moves. A point nobody
 * has touched stays where it is forever, so a neglected district visibly piles up for free.</p>
 *
 * <p>The delay belongs to the <em>slot</em>, not to the point. Clearing a bag takes the count down
 * by one and starts a timer; when the timer runs out the count goes back up at a different point.
 * Attaching the timer to the point instead would let one of the spare points fill in instantly and
 * the street would never look clean - which is the whole reward for doing the work.</p>
 *
 * <p>No Bukkit import on purpose: the counting and the timing here are the part most likely to hide
 * an off-by-one, so they are kept testable on their own.</p>
 */
public final class SpotRotation {

    private final Random random;

    public SpotRotation() {
        this(new Random());
    }

    /** Seeded constructor - tests need a rotation that repeats. */
    public SpotRotation(Random random) {
        this.random = random;
    }

    /** A worker cleared this point: it goes quiet, and its slot returns after the pool's delay. */
    public void clear(SpotPool pool, WorkSpot spot, long now) {
        if (pool == null || spot == null || !spot.active()) {
            return;
        }
        spot.clear(now);
        pool.lastCleared(spot);
        pool.queueReturn(now + pool.respawnMillis());
    }

    /**
     * Wakes points for every slot that is owed one.
     *
     * @return the points that just became active, in the order they were chosen
     */
    public List<WorkSpot> refill(SpotPool pool, long now) {
        List<WorkSpot> woken = new ArrayList<WorkSpot>();
        if (pool == null || pool.pointCount() == 0) {
            return woken;
        }

        int target = Math.min(pool.activeCount(), pool.pointCount());
        int active = countActive(pool);

        // Slots come from two places: timers that have run out, and a plain shortfall - the first
        // fill after a restart, or an admin raising activeCount.
        int slots = pool.takeDueReturns(now);
        int shortfall = target - active - pool.pendingCount() - slots;
        if (shortfall > 0) {
            slots += shortfall;
        }

        while (slots > 0 && active < target) {
            WorkSpot chosen = pick(pool);
            if (chosen == null) {
                break;
            }
            chosen.activate();
            woken.add(chosen);
            active++;
            slots--;
        }
        return woken;
    }

    /**
     * First fill after a restart. Nobody was online to serve the delay, so the pool comes straight
     * up to strength instead of leaving the city clean until the timers expire.
     */
    public List<WorkSpot> seed(SpotPool pool, long now) {
        if (pool == null) {
            return new ArrayList<WorkSpot>();
        }
        for (WorkSpot spot : pool.points()) {
            spot.reset();
        }
        pool.clearPending();
        return refill(pool, now);
    }

    /**
     * A random sleeping point, avoiding the one that was cleared most recently so the rubbish
     * reappears down the street rather than exactly where it was just picked up. The avoidance is
     * dropped when it is the only point left, which is the case in a one-point pool.
     */
    private WorkSpot pick(SpotPool pool) {
        List<WorkSpot> candidates = new ArrayList<WorkSpot>();
        for (WorkSpot spot : pool.points()) {
            if (!spot.active()) {
                candidates.add(spot);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        WorkSpot chosen = candidates.get(random.nextInt(candidates.size()));
        if (candidates.size() > 1 && chosen == pool.lastCleared()) {
            candidates.remove(chosen);
            chosen = candidates.get(random.nextInt(candidates.size()));
        }
        return chosen;
    }

    public static int countActive(SpotPool pool) {
        int active = 0;
        for (WorkSpot spot : pool.points()) {
            if (spot.active()) {
                active++;
            }
        }
        return active;
    }

    /**
     * Seconds until the next point wakes, or -1 when nothing is on the way. Admin readout only.
     */
    public static long secondsUntilNext(SpotPool pool, long now) {
        long soonest = pool.soonestReturn();
        if (soonest == Long.MAX_VALUE) {
            return -1L;
        }
        return Math.max(0L, (soonest - now + 999L) / 1000L);
    }
}

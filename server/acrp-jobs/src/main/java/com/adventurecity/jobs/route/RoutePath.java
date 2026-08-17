package com.adventurecity.jobs.route;

import java.util.ArrayList;
import java.util.List;

/**
 * The maths of a drawn route: a broken line through the corners the admin clicked.
 *
 * <p>This class has <strong>no Bukkit import on purpose</strong>. Turning a travelled distance back
 * into a position - with wrapping in LOOP mode and bouncing in PINGPONG mode - is by far the part
 * of the route system most likely to hide an off-by-one, and keeping it free of the server API
 * means it can be compiled and tested on its own.</p>
 *
 * <p>"Progress" is simply the distance walked in blocks since the walker was created. It grows
 * forever; this class folds it back into the path.</p>
 */
public final class RoutePath {

    public static final String MODE_LOOP = "LOOP";
    public static final String MODE_PINGPONG = "PINGPONG";

    private static final double EPSILON = 1.0E-9D;

    /** Corner list, with the first corner repeated at the end in LOOP mode to close the ring. */
    private final double[][] nodes;
    /** Distance from the first node to node i. */
    private final double[] cumulative;
    private final double length;
    private final boolean loop;
    private final int cornerCount;

    public RoutePath(List<double[]> corners, boolean loop) {
        this(corners == null ? null : corners.toArray(new double[corners.size()][]), loop);
    }

    public RoutePath(double[][] corners, boolean loop) {
        this.loop = loop;

        List<double[]> clean = new ArrayList<double[]>();
        if (corners != null) {
            for (double[] corner : corners) {
                if (corner == null || corner.length < 3) {
                    continue;
                }
                if (!finite(corner[0]) || !finite(corner[1]) || !finite(corner[2])) {
                    continue;
                }
                clean.add(new double[] { corner[0], corner[1], corner[2] });
            }
        }
        this.cornerCount = clean.size();

        // In LOOP mode the walker returns to the first corner, so the ring is closed by repeating
        // it. Everything below then treats both modes as one open polyline.
        if (loop && clean.size() >= 2) {
            double[] first = clean.get(0);
            clean.add(new double[] { first[0], first[1], first[2] });
        }

        this.nodes = clean.toArray(new double[clean.size()][]);
        this.cumulative = new double[nodes.length];
        double total = 0.0D;
        for (int i = 1; i < nodes.length; i++) {
            total += distance(nodes[i - 1], nodes[i]);
            cumulative[i] = total;
        }
        this.length = total;
    }

    // ---------------------------------------------------------------- shape

    /** False for a path nobody can walk: fewer than two corners, or every corner on one spot. */
    public boolean valid() {
        return nodes.length >= 2 && length > 1.0E-6D;
    }

    public boolean loop() {
        return loop;
    }

    /** Corners the admin clicked - the repeated closing corner is not counted. */
    public int cornerCount() {
        return cornerCount;
    }

    /** Walking distance from the first corner to the last one (through the closing leg in LOOP). */
    public double length() {
        return length;
    }

    /** Distance of one full round trip: the same as {@link #length()} in LOOP, doubled in PINGPONG. */
    public double cycleLength() {
        return loop ? length : length * 2.0D;
    }

    /** One of the corners the admin clicked, or null when the index is out of range. */
    public double[] corner(int index) {
        if (index < 0 || index >= cornerCount) {
            return null;
        }
        return new double[] { nodes[index][0], nodes[index][1], nodes[index][2] };
    }

    // ---------------------------------------------------------------- position

    public double[] pointAt(double progress) {
        double[] out = new double[3];
        pointAt(progress, out);
        return out;
    }

    /** Writes the position for this progress into {@code out}, which must hold at least 3 doubles. */
    public void pointAt(double progress, double[] out) {
        if (nodes.length == 0) {
            out[0] = 0.0D;
            out[1] = 0.0D;
            out[2] = 0.0D;
            return;
        }
        if (!valid()) {
            out[0] = nodes[0][0];
            out[1] = nodes[0][1];
            out[2] = nodes[0][2];
            return;
        }

        double forward = forwardDistance(progress);
        int index = segmentIndex(forward);
        double from = cumulative[index];
        double span = cumulative[index + 1] - from;
        double t = span <= EPSILON ? 0.0D : (forward - from) / span;

        double[] a = nodes[index];
        double[] b = nodes[index + 1];
        out[0] = a[0] + (b[0] - a[0]) * t;
        out[1] = a[1] + (b[1] - a[1]) * t;
        out[2] = a[2] + (b[2] - a[2]) * t;
    }

    /**
     * Minecraft yaw for the direction of travel at this progress. Yaw 0 faces +Z, and the sign of
     * X is flipped - that is the game's convention, not a typo.
     */
    public float yawAt(double progress) {
        if (!valid()) {
            return 0.0F;
        }
        double forward = forwardDistance(progress);
        int index = segmentIndex(forward);
        double dx = nodes[index + 1][0] - nodes[index][0];
        double dz = nodes[index + 1][2] - nodes[index][2];
        if (returning(progress)) {
            dx = -dx;
            dz = -dz;
        }
        if (Math.abs(dx) < EPSILON && Math.abs(dz) < EPSILON) {
            return 0.0F;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Index of the leg the walker is on - a change means a corner was just rounded. */
    public int segmentAt(double progress) {
        if (!valid()) {
            return 0;
        }
        return segmentIndex(forwardDistance(progress));
    }

    /** True while a PINGPONG walker is on its way back. Always false in LOOP mode. */
    public boolean returning(double progress) {
        if (loop || !valid()) {
            return false;
        }
        return mod(progress, length * 2.0D) > length;
    }

    /**
     * Folds an ever-growing progress into a distance from the first corner: modulo in LOOP mode,
     * mirrored around the far end in PINGPONG mode.
     */
    public double forwardDistance(double progress) {
        if (!valid()) {
            return 0.0D;
        }
        if (loop) {
            return mod(progress, length);
        }
        double cycle = length * 2.0D;
        double folded = mod(progress, cycle);
        return folded <= length ? folded : cycle - folded;
    }

    // ---------------------------------------------------------------- internals

    /** Last node whose cumulative distance is still behind {@code forward}, never the final node. */
    private int segmentIndex(double forward) {
        int index = 0;
        for (int i = 1; i < nodes.length - 1; i++) {
            if (cumulative[i] <= forward) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    private static double mod(double value, double modulus) {
        if (modulus <= EPSILON) {
            return 0.0D;
        }
        double remainder = value % modulus;
        return remainder < 0.0D ? remainder + modulus : remainder;
    }

    private static double distance(double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double dz = b[2] - a[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}

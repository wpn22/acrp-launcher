package com.acrp.phone.client.ui;

/**
 * The rounded-square outline iOS uses for app icons and the device frame.
 *
 * <p>It is a <em>superellipse</em>, |x|<sup>n</sup> + |y|<sup>n</sup> = 1, not a
 * rectangle with circular corners. The distinction is not pedantry: a circular
 * corner splices an arc onto a straight edge, so curvature jumps from zero to a
 * fixed value at the join. The eye reads that discontinuity as a crease, and it is
 * why a plain rounded rectangle never quite looks like an iOS icon. A superellipse
 * is a single curve — the flat-looking sides and the corners both fall out of one
 * equation, so there is no seam to see.
 *
 * <p>Apple's mask sits around n = 5; its often-quoted 22.37% corner radius is the
 * circular radius that comes closest, not a parameter of the real shape.
 *
 * <p>Sampled parametrically, which avoids the Bezier approximations usually used to
 * fake this shape:
 * <pre>
 *   x = sgn(cos t) · |cos t|^(2/n)
 *   y = sgn(sin t) · |sin t|^(2/n)
 * </pre>
 */
public final class Squircle {

    /** Matches the iOS icon mask closely. */
    public static final float ICON_EXPONENT = 5f;

    /** Enough segments that the curve reads as smooth at icon and frame sizes. */
    public static final int DEFAULT_SAMPLES = 96;

    private Squircle() {
    }

    /**
     * Builds the outline of a squircle filling the given box.
     *
     * @param samples number of points around the perimeter; must be at least 8
     * @return interleaved {@code x0, y0, x1, y1, …} in the same space as the inputs,
     *         wound counter-clockwise and not repeating the first point
     */
    public static float[] outline(float x, float y, float w, float h,
                                  float exponent, int samples) {
        if (samples < 8) {
            throw new IllegalArgumentException("samples must be >= 8, got " + samples);
        }
        if (exponent <= 0f) {
            throw new IllegalArgumentException("exponent must be > 0, got " + exponent);
        }
        float halfW = w / 2f;
        float halfH = h / 2f;
        float cx = x + halfW;
        float cy = y + halfH;
        float power = 2f / exponent;

        float[] out = new float[samples * 2];
        for (int i = 0; i < samples; i++) {
            double t = (i / (double) samples) * Math.PI * 2d;
            double cos = Math.cos(t);
            double sin = Math.sin(t);
            double ux = Math.signum(cos) * Math.pow(Math.abs(cos), power);
            double uy = Math.signum(sin) * Math.pow(Math.abs(sin), power);
            out[i * 2] = (float) (cx + ux * halfW);
            out[i * 2 + 1] = (float) (cy + uy * halfH);
        }
        return out;
    }

    /** Outline with the iOS icon exponent and the default sample count. */
    public static float[] icon(float x, float y, float w, float h) {
        return outline(x, y, w, h, ICON_EXPONENT, DEFAULT_SAMPLES);
    }
}

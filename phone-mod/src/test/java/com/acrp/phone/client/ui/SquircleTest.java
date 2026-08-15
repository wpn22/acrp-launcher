package com.acrp.phone.client.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The icon shape is the detail that decides whether the phone reads as iOS, so its
 * geometry is pinned down rather than eyeballed.
 */
public class SquircleTest {

    private static final float EPS = 0.01f;

    @Test
    public void outlineHasTheRequestedNumberOfPoints() {
        float[] pts = Squircle.outline(0, 0, 60, 60, 5f, 96);
        assertEquals(96 * 2, pts.length);
    }

    @Test
    public void everyPointStaysInsideTheBox() {
        float[] pts = Squircle.outline(10, 20, 60, 80, 5f, 128);
        for (int i = 0; i < pts.length; i += 2) {
            assertTrue("x below box: " + pts[i], pts[i] >= 10 - EPS);
            assertTrue("x above box: " + pts[i], pts[i] <= 70 + EPS);
            assertTrue("y below box: " + pts[i + 1], pts[i + 1] >= 20 - EPS);
            assertTrue("y above box: " + pts[i + 1], pts[i + 1] <= 100 + EPS);
        }
    }

    /** The curve must actually touch each edge midpoint, or it is not inscribed. */
    @Test
    public void curveTouchesAllFourEdges() {
        float[] pts = Squircle.outline(0, 0, 100, 100, 5f, 4 * 32);
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < pts.length; i += 2) {
            minX = Math.min(minX, pts[i]);
            maxX = Math.max(maxX, pts[i]);
            minY = Math.min(minY, pts[i + 1]);
            maxY = Math.max(maxY, pts[i + 1]);
        }
        assertEquals(0f, minX, EPS);
        assertEquals(100f, maxX, EPS);
        assertEquals(0f, minY, EPS);
        assertEquals(100f, maxY, EPS);
    }

    /**
     * The whole point of the shape: at 45 degrees a squircle bulges further out than
     * a circle would, which is what fills the corners and makes the sides read flat.
     */
    @Test
    public void cornersAreFullerThanACircle() {
        float[] pts = Squircle.outline(-1, -1, 2, 2, 5f, 8); // unit square, centred
        // Sample index 1 of 8 is t = 45 degrees.
        float x = pts[2];
        float y = pts[3];
        float distance = (float) Math.sqrt(x * x + y * y);
        assertTrue("squircle must bulge past the unit circle at 45deg, got " + distance,
                distance > 1.05f);
        assertTrue("but must stay inside the square", distance < Math.sqrt(2) + EPS);
    }

    /** A higher exponent means squarer; a lower one means rounder. */
    @Test
    public void exponentControlsSquareness() {
        float round = cornerReach(2f);   // n = 2 is an exact circle
        float apple = cornerReach(5f);
        float squarer = cornerReach(12f);
        assertEquals("n=2 must be a circle", 1f, round, 0.02f);
        assertTrue("n=5 must be fuller than a circle", apple > round);
        assertTrue("n=12 must be fuller still", squarer > apple);
    }

    private float cornerReach(float exponent) {
        float[] pts = Squircle.outline(-1, -1, 2, 2, exponent, 8);
        return (float) Math.sqrt(pts[2] * pts[2] + pts[3] * pts[3]);
    }

    @Test
    public void shapeIsSymmetricAboutBothAxes() {
        float[] pts = Squircle.outline(-1, -1, 2, 2, 5f, 100);
        for (int i = 0; i < pts.length; i += 2) {
            float x = pts[i];
            float y = pts[i + 1];
            assertTrue("point outside unit square", Math.abs(x) <= 1 + EPS && Math.abs(y) <= 1 + EPS);
            // The mirrored point must also satisfy the superellipse equation.
            double lhs = Math.pow(Math.abs(x), 5) + Math.pow(Math.abs(y), 5);
            assertEquals("point off the superellipse", 1d, lhs, 0.02d);
        }
    }

    @Test
    public void iconHelperUsesTheAppleExponent() {
        float[] viaHelper = Squircle.icon(0, 0, 60, 60);
        float[] explicit = Squircle.outline(0, 0, 60, 60,
                Squircle.ICON_EXPONENT, Squircle.DEFAULT_SAMPLES);
        assertEquals(explicit.length, viaHelper.length);
        for (int i = 0; i < explicit.length; i++) {
            assertEquals(explicit[i], viaHelper[i], 0.0001f);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTooFewSamples() {
        Squircle.outline(0, 0, 10, 10, 5f, 4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveExponent() {
        Squircle.outline(0, 0, 10, 10, 0f, 32);
    }
}

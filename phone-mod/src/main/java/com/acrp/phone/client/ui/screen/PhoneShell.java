package com.acrp.phone.client.ui.screen;

import com.acrp.phone.client.ui.PhoneCanvas;
import com.acrp.phone.client.ui.Squircle;
import com.acrp.phone.client.ui.Theme;

/**
 * The parts of the device that sit above whatever app is open: wallpaper, status
 * bar, Dynamic Island and home indicator.
 */
public final class PhoneShell {

    private PhoneShell() {
    }

    public static void drawWallpaper(PhoneCanvas c, Theme theme) {
        c.fillGradient(0, 0, PhoneCanvas.WIDTH, PhoneCanvas.HEIGHT,
                theme.wallpaperTop(), theme.wallpaperBottom());
        // Two soft pools of colour, the way iOS wallpapers usually read.
        if (theme.isDark()) {
            glow(c, 70f, 40f, 210f, 0x4B2E86);
            glow(c, 340f, 230f, 240f, 0x0C4F92);
            glow(c, 190f, 800f, 220f, 0x6A1F52);
        } else {
            glow(c, 70f, 40f, 210f, 0xD8E4FF);
            glow(c, 340f, 230f, 240f, 0xFFE3EE);
        }
    }

    /**
     * Soft radial pool of colour.
     *
     * <p>Built from concentric rings of low alpha rather than one translucent
     * circle — a single circle leaves a visible hard rim exactly where a wallpaper
     * should fade out.
     */
    private static void glow(PhoneCanvas c, float cx, float cy, float radius, int rgb) {
        int rings = 26;
        for (int i = rings; i >= 1; i--) {
            float r = radius * i / rings;
            // Fade towards the rim so the outermost ring is almost invisible.
            float t = 1f - (i / (float) rings);
            int alpha = (int) (26f * t * t);
            if (alpha <= 0) {
                continue;
            }
            c.fillCircle(cx, cy, r, (alpha << 24) | rgb);
        }
    }

    /**
     * Status bar. When there is no SIM the carrier slot says so, which is the phone's
     * most visible signal that messaging and calls are unavailable.
     */
    public static void drawStatusBar(PhoneCanvas c, Theme theme, String time, boolean hasService) {
        float y = Theme.STATUS_PAD_TOP;
        int ink = theme.ink();

        // Clock sits on the right in an RTL layout, mirroring the system.
        c.drawText(time, PhoneCanvas.WIDTH - Theme.STATUS_PAD_X, y,
                Theme.STATUS_FONT, ink, PhoneCanvas.TextAlign.START);

        // Indicators are anchored to the left edge and never move, so the carrier
        // label grows inward from them. Letting the label start at the edge instead
        // pushed it under the corner curve, where the bezel mask clipped it.
        float x = Theme.STATUS_PAD_X;

        for (int i = 0; i < 4; i++) {
            float barHeight = 3.5f + i * 2.8f;
            int colour = hasService ? ink : withAlpha(ink, 0x4D);
            c.fillRoundRect(x + i * 4.9f, y + 12f - barHeight, 3.2f, barHeight, 1.1f, colour);
        }
        x += 4 * 4.9f + 5f;

        c.fillRoundRect(x, y + 1f, 22f, 11.8f, 3.6f, withAlpha(ink, 0x61));
        c.fillRoundRect(x + 1.6f, y + 2.6f, 16f, 8.6f, 2.2f, ink);
        c.fillRoundRect(x + 23.8f, y + 4.6f, 1.6f, 4f, 0.8f, withAlpha(ink, 0x61));
        x += 30f;

        if (!hasService) {
            // Only the gap between the indicators and the Island is usable — the
            // cutout is opaque and swallows anything drawn under it.
            float islandLeft = (PhoneCanvas.WIDTH
                    - PhoneCanvas.WIDTH * Theme.ISLAND_WIDTH_RATIO) / 2f;
            float available = islandLeft - (x + 6f) - 6f;
            String label = "لا توجد شريحة";
            if (c.textWidth(label, 12f) > available) {
                label = "لا شريحة";
            }
            if (c.textWidth(label, 12f) <= available) {
                c.drawText(label, x + 6f, y + 1.5f, 12f, ink, PhoneCanvas.TextAlign.END);
            }
        }
    }

    /** The Island is opaque black — it stands in for the sensor cutout. */
    public static void drawIsland(PhoneCanvas c) {
        float w = PhoneCanvas.WIDTH * Theme.ISLAND_WIDTH_RATIO;
        float x = (PhoneCanvas.WIDTH - w) / 2f;
        c.fillRoundRect(x, Theme.ISLAND_TOP, w, Theme.ISLAND_HEIGHT,
                Theme.ISLAND_HEIGHT / 2f, 0xFF000000);
    }

    public static void drawHomeBar(PhoneCanvas c, Theme theme) {
        float x = (PhoneCanvas.WIDTH - Theme.HOME_BAR_WIDTH) / 2f;
        float y = PhoneCanvas.HEIGHT - Theme.HOME_BAR_BOTTOM - Theme.HOME_BAR_HEIGHT;
        c.fillRoundRect(x, y, Theme.HOME_BAR_WIDTH, Theme.HOME_BAR_HEIGHT,
                Theme.HOME_BAR_HEIGHT / 2f, withAlpha(theme.ink(), 0x80));
    }

    /**
     * Masks the corners so the screen sits inside the device's rounded shell.
     *
     * <p>The frame is a squircle for the same reason the icons are — a circular
     * corner here reads as a generic rounded rectangle rather than a phone.
     */
    public static void drawBezelMask(PhoneCanvas c, int outside) {
        float[] inner = Squircle.outline(0, 0, PhoneCanvas.WIDTH, PhoneCanvas.HEIGHT, 6.2f, 128);
        // Paint the region between the canvas edge and the squircle by walking the
        // outline and filling each gap back to the nearest corner.
        for (int i = 0; i < inner.length; i += 2) {
            int next = (i + 2) % inner.length;
            float x1 = inner[i];
            float y1 = inner[i + 1];
            float x2 = inner[next];
            float y2 = inner[next + 1];
            float cornerX = x1 < PhoneCanvas.WIDTH / 2f ? 0f : PhoneCanvas.WIDTH;
            float cornerY = y1 < PhoneCanvas.HEIGHT / 2f ? 0f : PhoneCanvas.HEIGHT;
            c.fillPath(new float[]{x1, y1, x2, y2, cornerX, cornerY}, outside);
        }
    }

    private static int withAlpha(int colour, int alpha) {
        return (alpha << 24) | (colour & 0x00FFFFFF);
    }
}

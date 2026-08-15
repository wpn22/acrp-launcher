package com.acrp.phone.client.ui;

/**
 * Colours and frame metrics for the phone.
 *
 * <p>Metrics are taken from LB Phone's own frame stylesheet so the device reads at
 * the proportions players already recognise, on a 390 x 844 canvas — the iPhone 14
 * Pro logical size its numbers imply.
 */
public final class Theme {

    // --- frame metrics ------------------------------------------------------

    public static final float FRAME_RADIUS = 60f;

    /** Dynamic Island: 30% of the width, 36 tall, 12 down from the top. */
    public static final float ISLAND_WIDTH_RATIO = 0.30f;
    public static final float ISLAND_HEIGHT = 36f;
    public static final float ISLAND_TOP = 12f;

    /** Home indicator: 144 x 5, 8 up from the bottom. */
    public static final float HOME_BAR_WIDTH = 144f;
    public static final float HOME_BAR_HEIGHT = 5f;
    public static final float HOME_BAR_BOTTOM = 8f;

    public static final float STATUS_HEIGHT = 54f;
    public static final float STATUS_PAD_X = 30f;
    public static final float STATUS_PAD_TOP = 17f;
    public static final float STATUS_FONT = 16f;

    // --- home screen --------------------------------------------------------

    public static final int GRID_COLUMNS = 4;
    public static final int GRID_ROWS = 6;
    public static final float ICON_SIZE = 60f;
    public static final float ICON_LABEL_FONT = 11.5f;
    public static final float ICON_LABEL_GAP = 6f;
    public static final float GRID_TOP = 72f;
    public static final float GRID_SIDE = 16f;
    public static final float GRID_ROW_GAP = 18f;

    public static final float DOCK_HEIGHT = 92f;
    public static final float DOCK_RADIUS = 34f;
    public static final float DOCK_SIDE = 10f;
    public static final float DOCK_BOTTOM = 24f;

    // --- palette ------------------------------------------------------------

    private final boolean dark;

    private Theme(boolean dark) {
        this.dark = dark;
    }

    public static Theme dark() {
        return new Theme(true);
    }

    public static Theme light() {
        return new Theme(false);
    }

    public boolean isDark() {
        return dark;
    }

    /** Primary label colour. */
    public int ink() {
        return dark ? 0xFFFFFFFF : 0xFF000000;
    }

    /** Secondary label colour. */
    public int dim() {
        return dark ? 0x99EBEBF5 : 0x993C3C43;
    }

    /** Translucent fill behind cards, dock and controls. */
    public int fill() {
        return dark ? 0x5C787880 : 0x33787880;
    }

    /** Grouped-content background — sheets, list rows. */
    public int group() {
        return dark ? 0xFF1C1C1E : 0xFFFFFFFF;
    }

    /** Full-screen background behind an app. */
    public int sheet() {
        return dark ? 0xFF000000 : 0xFFF2F2F7;
    }

    public int separator() {
        return dark ? 0x54545488 : 0x3C3C434A;
    }

    public int accent() {
        return dark ? 0xFF0A84FF : 0xFF007AFF;
    }

    public int green() {
        return dark ? 0xFF30D158 : 0xFF34C759;
    }

    public int red() {
        return dark ? 0xFFFF453A : 0xFFFF3B30;
    }

    /** Wallpaper gradient, top to bottom. */
    public int wallpaperTop() {
        return dark ? 0xFF1B2444 : 0xFFEBF1FF;
    }

    public int wallpaperBottom() {
        return dark ? 0xFF07080E : 0xFFF5EAF2;
    }
}

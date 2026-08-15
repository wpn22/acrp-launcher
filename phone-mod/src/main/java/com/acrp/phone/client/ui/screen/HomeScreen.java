package com.acrp.phone.client.ui.screen;

import com.acrp.phone.client.ui.AppIcon;
import com.acrp.phone.client.ui.PhoneCanvas;
import com.acrp.phone.client.ui.Theme;

/**
 * The app grid.
 *
 * <p>Four columns by six rows with a four-slot dock, matching the iPhone layout the
 * design targets. Apps that need a network hide behind a dimmed tile when no SIM is
 * present, so the phone still boots and behaves — it just cannot reach anyone.
 */
public final class HomeScreen {

    /** One tile: an icon, its label, and whether it needs service. */
    public static final class Entry {
        public final AppIcon icon;
        public final String label;
        public final boolean needsService;

        public Entry(AppIcon icon, String label, boolean needsService) {
            this.icon = icon;
            this.label = label;
            this.needsService = needsService;
        }
    }

    private final Entry[] apps;
    private final Entry[] dock;

    public HomeScreen(Entry[] apps, Entry[] dock) {
        this.apps = apps;
        this.dock = dock;
    }

    /** The stock ACRP layout. */
    public static HomeScreen defaultLayout() {
        Entry[] apps = {
                new Entry(AppIcon.PHONE, "الهاتف", true),
                new Entry(AppIcon.MESSAGES, "الرسائل", true),
                new Entry(AppIcon.CONTACTS, "جهات", true),
                new Entry(AppIcon.CAMERA, "الكاميرا", false),
                new Entry(AppIcon.CLOCK, "الساعة", false),
                new Entry(AppIcon.WEATHER, "الطقس", true),
                new Entry(AppIcon.CALCULATOR, "الحاسبة", false),
                new Entry(AppIcon.NOTES, "الملاحظات", false),
                new Entry(AppIcon.STORE, "المتجر", true),
                new Entry(AppIcon.DARKCHAT, "DarkChat", true),
                new Entry(AppIcon.WALLET, "المحفظة", true),
                new Entry(AppIcon.SETTINGS, "الإعدادات", false)
        };
        Entry[] dock = {
                new Entry(AppIcon.PHONE, "الهاتف", true),
                new Entry(AppIcon.MESSAGES, "الرسائل", true),
                new Entry(AppIcon.CAMERA, "الكاميرا", false),
                new Entry(AppIcon.SETTINGS, "الإعدادات", false)
        };
        return new HomeScreen(apps, dock);
    }

    public void draw(PhoneCanvas c, Theme theme, boolean hasService) {
        drawGrid(c, theme, hasService);
        drawPageDots(c, theme);
        drawSearchPill(c, theme);
        drawDock(c, theme, hasService);
    }

    private void drawGrid(PhoneCanvas c, Theme theme, boolean hasService) {
        float usable = PhoneCanvas.WIDTH - Theme.GRID_SIDE * 2f;
        float cell = usable / Theme.GRID_COLUMNS;
        float rowHeight = Theme.ICON_SIZE + Theme.ICON_LABEL_GAP
                + c.lineHeight(Theme.ICON_LABEL_FONT) + Theme.GRID_ROW_GAP;

        for (int i = 0; i < apps.length && i < Theme.GRID_COLUMNS * Theme.GRID_ROWS; i++) {
            int column = i % Theme.GRID_COLUMNS;
            int row = i / Theme.GRID_COLUMNS;

            // Arabic reads right to left, and so does the home screen: the first app
            // belongs in the top-right cell.
            float cellLeft = Theme.GRID_SIDE + (Theme.GRID_COLUMNS - 1 - column) * cell;
            float centreX = cellLeft + cell / 2f;
            float iconX = centreX - Theme.ICON_SIZE / 2f;
            float iconY = Theme.GRID_TOP + row * rowHeight;

            drawTile(c, theme, apps[i], iconX, iconY, centreX, hasService);
        }
    }

    private void drawTile(PhoneCanvas c, Theme theme, Entry entry,
                          float iconX, float iconY, float centreX, boolean hasService) {
        boolean blocked = entry.needsService && !hasService;

        entry.icon.draw(c, iconX, iconY, Theme.ICON_SIZE);
        if (blocked) {
            // Dim by veiling the tile in the wallpaper's own darkness rather than
            // redrawing the icon, so the shape still shows through.
            c.fillSquircle(iconX, iconY, Theme.ICON_SIZE, Theme.ICON_SIZE,
                    0x8C0B0D14, 0x8C0B0D14);
        }

        int labelColour = blocked ? theme.dim() : theme.ink();
        c.drawText(entry.label, centreX,
                iconY + Theme.ICON_SIZE + Theme.ICON_LABEL_GAP,
                Theme.ICON_LABEL_FONT, labelColour, PhoneCanvas.TextAlign.CENTER);
    }

    /** Dock top, which everything above it has to clear. */
    private static float dockTop() {
        return PhoneCanvas.HEIGHT - Theme.DOCK_BOTTOM - Theme.DOCK_HEIGHT;
    }

    private void drawPageDots(PhoneCanvas c, Theme theme) {
        float y = dockTop() - 60f;
        float centre = PhoneCanvas.WIDTH / 2f;
        c.fillCircle(centre - 7f, y, 3.5f, theme.ink());
        c.fillCircle(centre + 7f, y, 3.5f, theme.dim());
    }

    private void drawSearchPill(PhoneCanvas c, Theme theme) {
        float h = 28f;
        float pad = 14f;
        float textWidth = c.textWidth("بحث", 13f);
        float w = textWidth + pad * 2f;
        float x = (PhoneCanvas.WIDTH - w) / 2f;
        // Sit clear of the dock; overlapping it hid the label entirely.
        float y = dockTop() - h - 16f;

        c.blurPanel(x, y, w, h, h / 2f, theme.fill());
        c.drawText("بحث", PhoneCanvas.WIDTH / 2f,
                y + (h - c.lineHeight(13f)) / 2f, 13f,
                theme.ink(), PhoneCanvas.TextAlign.CENTER);
    }

    private void drawDock(PhoneCanvas c, Theme theme, boolean hasService) {
        float x = Theme.DOCK_SIDE;
        float y = PhoneCanvas.HEIGHT - Theme.DOCK_BOTTOM - Theme.DOCK_HEIGHT;
        float w = PhoneCanvas.WIDTH - Theme.DOCK_SIDE * 2f;

        c.blurPanel(x, y, w, Theme.DOCK_HEIGHT, Theme.DOCK_RADIUS, theme.fill());

        float cell = w / dock.length;
        float iconY = y + (Theme.DOCK_HEIGHT - Theme.ICON_SIZE) / 2f;
        for (int i = 0; i < dock.length; i++) {
            float centreX = x + (dock.length - 1 - i) * cell + cell / 2f;
            float iconX = centreX - Theme.ICON_SIZE / 2f;
            dock[i].icon.draw(c, iconX, iconY, Theme.ICON_SIZE);
            if (dock[i].needsService && !hasService) {
                c.fillSquircle(iconX, iconY, Theme.ICON_SIZE, Theme.ICON_SIZE,
                        0x8C0B0D14, 0x8C0B0D14);
            }
        }
    }
}

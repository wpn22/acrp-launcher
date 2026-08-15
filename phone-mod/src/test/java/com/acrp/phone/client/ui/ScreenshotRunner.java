package com.acrp.phone.client.ui;

import com.acrp.phone.client.ui.screen.HomeScreen;
import com.acrp.phone.client.ui.screen.PhoneShell;

import java.io.File;

/**
 * Renders every phone screen to a PNG.
 *
 * <p>The whole point: this runs the same screen code that will run in Minecraft, in
 * about a second, with no game and no Forge. It is the only way anyone gets to look
 * at this interface at the moment.
 *
 * <pre>java -cp … com.acrp.phone.client.ui.ScreenshotRunner out/</pre>
 */
public final class ScreenshotRunner {

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "screenshots");

        shoot(new File(dir, "home-dark.png"), Theme.dark(), true);
        shoot(new File(dir, "home-light.png"), Theme.light(), true);
        shoot(new File(dir, "home-no-sim.png"), Theme.dark(), false);

        System.out.println("screenshots written to " + dir.getAbsolutePath());
    }

    private static void shoot(File file, Theme theme, boolean hasService) throws Exception {
        AwtCanvas canvas = new AwtCanvas();

        PhoneShell.drawWallpaper(canvas, theme);
        HomeScreen.defaultLayout().draw(canvas, theme, hasService);
        PhoneShell.drawStatusBar(canvas, theme, "9:41", hasService);
        PhoneShell.drawIsland(canvas);
        PhoneShell.drawHomeBar(canvas, theme);
        PhoneShell.drawBezelMask(canvas, 0x00000000);

        canvas.save(file);
        System.out.println("  " + file.getName());
    }

    private ScreenshotRunner() {
    }
}

package com.acrp.phone.client.text;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * Renders sample strings to a PNG so the Arabic pipeline can be checked by eye
 * without launching Minecraft.
 *
 * <p>It draws each glyph individually from its glyph code — the same path the
 * in-game atlas renderer takes — so if this image looks right, the atlas will too.
 *
 * <pre>java -cp ... com.acrp.phone.client.text.TextRenderProof out.png</pre>
 */
public final class TextRenderProof {

    private static final String[] SAMPLES = {
            "مرحبا بك في مدينة المغامرة",
            "رقمي هو 0501234567 اتصل بي",
            "من 100 إلى 250 ريال",
            "مرحبا ACRP اهلا وسهلا",
            "Adventure City Roleplay"
    };

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "text-proof.png");

        Font font = TextShaperTest.TestFonts.load().deriveFont(30f);
        FontRenderContext frc = new FontRenderContext(null, true, true);
        TextShaper shaper = TextShaper.arabic(font);

        int width = 900;
        int rowHeight = 52;
        int height = 60 + SAMPLES.length * rowHeight * 2;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        Font label = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        int y = 44;

        for (String sample : SAMPLES) {
            // Reference: hand the whole string to the shaper in one RTL pass. This
            // is the tempting one-liner, and it reverses embedded digit runs.
            g.setColor(new Color(0xB0, 0x00, 0x20));
            g.setFont(label);
            g.drawString("naive single-pass RTL:", 12, y - 20);
            char[] c = sample.toCharArray();
            GlyphVector naive =
                    font.layoutGlyphVector(frc, c, 0, c.length, Font.LAYOUT_RIGHT_TO_LEFT);
            g.setColor(new Color(0x88, 0x00, 0x18));
            g.drawGlyphVector(naive, 200, y);
            y += rowHeight;

            // Ours: bidi split, per-run shaping, glyphs blitted one at a time by
            // glyph code exactly like the in-game atlas will do.
            g.setColor(new Color(0x00, 0x60, 0x30));
            g.setFont(label);
            g.drawString("TextShaper (two-pass):", 12, y - 20);
            g.setColor(Color.BLACK);
            drawShaped(g, font, frc, shaper.shape(sample), 200, y);
            y += rowHeight;
        }

        g.dispose();
        ImageIO.write(img, "PNG", out);
        System.out.println("wrote " + out.getAbsolutePath());
    }

    /** Blits a {@link ShapedText} glyph by glyph, positioned by the shaper. */
    private static void drawShaped(Graphics2D g, Font font, FontRenderContext frc,
                                   ShapedText text, float originX, float baselineY) {
        int[] one = new int[1];
        for (ShapedGlyph glyph : text.glyphs()) {
            one[0] = glyph.glyphCode;
            GlyphVector gv = font.createGlyphVector(frc, one);
            g.drawGlyphVector(gv, originX + glyph.x, baselineY + glyph.y);
        }
    }

    private TextRenderProof() {
    }
}

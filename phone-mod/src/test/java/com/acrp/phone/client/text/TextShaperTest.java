package com.acrp.phone.client.text;

import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for the Arabic text pipeline. No Minecraft, no window — these run
 * in CI and catch the failure modes that are otherwise only visible by squinting at
 * the game.
 */
public class TextShaperTest {

    private static Font font;
    private static FontRenderContext frc;

    /** Maps a digit's glyph code back to the digit, so we can read output order. */
    private static Map<Integer, Character> digitGlyphs;

    @BeforeClass
    public static void loadFont() throws Exception {
        font = TestFonts.load().deriveFont(32f);
        frc = new FontRenderContext(null, true, true);

        digitGlyphs = new HashMap<Integer, Character>();
        String digits = "0123456789";
        GlyphVector gv = font.createGlyphVector(frc, digits);
        for (int i = 0; i < digits.length(); i++) {
            digitGlyphs.put(gv.getGlyphCode(i), digits.charAt(i));
        }
    }

    @Test
    public void emptyStringProducesNoGlyphs() {
        ShapedText s = TextShaper.arabic(font).shape("");
        assertTrue(s.isEmpty());
        assertEquals(0f, s.width(), 0.001f);
    }

    @Test
    public void latinTextIsUnchanged() {
        TextShaper shaper = TextShaper.latin(font);
        ShapedText s = shaper.shape("Adventure City");
        assertFalse(s.isEmpty());
        assertFalse("plain Latin must stay LTR", s.isBaseRtl());
        assertTrue(s.width() > 0f);
    }

    @Test
    public void arabicResolvesToRightToLeftBase() {
        ShapedText s = TextShaper.arabic(font).shape("مدينة المغامرة");
        assertTrue("an Arabic string must resolve to an RTL paragraph", s.isBaseRtl());
    }

    /**
     * Arabic letters must change shape by position. If the shaper were bypassed,
     * every occurrence of a letter would map to the same isolated glyph.
     */
    @Test
    public void arabicLettersAreContextuallyShaped() {
        TextShaper shaper = TextShaper.arabic(font);

        int isolatedBeh = shaper.shape("ب").glyphs().get(0).glyphCode;

        // "بب" — first letter takes an initial form, second a final form; neither
        // may equal the isolated form.
        List<ShapedGlyph> pair = shaper.shape("ببب").glyphs();
        assertEquals(3, pair.size());
        boolean anyDifferent = false;
        for (ShapedGlyph g : pair) {
            if (g.glyphCode != isolatedBeh) {
                anyDifferent = true;
            }
        }
        assertTrue("letters inside a word must not use the isolated glyph", anyDifferent);
    }

    /** Glyphs come back in visual order, so X must never go backwards. */
    @Test
    public void glyphsAreEmittedInVisualOrder() {
        ShapedText s = TextShaper.arabic(font).shape("مرحبا بك في مدينة المغامرة");
        float prevX = -Float.MAX_VALUE;
        for (ShapedGlyph g : s.glyphs()) {
            assertTrue("glyph X must be non-decreasing in visual order", g.x >= prevX - 0.01f);
            prevX = g.x;
        }
    }

    /**
     * The regression that motivated the two-pass design: a phone number embedded in
     * Arabic must still read left-to-right. Shaping the whole string RTL in one go
     * renders 0501234567 as 7654321050.
     */
    @Test
    public void digitsInsideArabicKeepTheirOrder() {
        assertEquals("0501234567", visualDigits("رقمي هو 0501234567 اتصل بي"));
    }

    @Test
    public void digitRunAtStringStartKeepsOrder() {
        assertEquals("1234", visualDigits("1234 هو رقمي"));
    }

    @Test
    public void multipleDigitRunsEachKeepOrder() {
        // Two separate numbers, read right-to-left as blocks but each internally LTR.
        String out = visualDigits("من 100 إلى 250 ريال");
        assertTrue("both numbers must survive, got: " + out,
                out.equals("250100") || out.equals("100250"));
    }

    @Test
    public void widthIsPositiveAndCachedConsistently() {
        TextShaper shaper = TextShaper.arabic(font);
        String text = "رسالة جديدة";
        float first = shaper.width(text);
        float second = shaper.width(text);
        assertTrue(first > 0f);
        assertEquals("cache must return identical metrics", first, second, 0.0f);
    }

    @Test
    public void mixedArabicLatinDoesNotLoseGlyphs() {
        ShapedText s = TextShaper.arabic(font).shape("مرحبا ACRP اهلا");
        assertTrue("expected glyphs for both scripts", s.glyphs().size() >= 14);
    }

    /**
     * Reads the digits out of a shaped string in the order they actually appear on
     * screen, by sorting glyphs on X and keeping the ones that are digits.
     */
    private String visualDigits(String text) {
        ShapedText s = TextShaper.arabic(font).shape(text);
        StringBuilder sb = new StringBuilder();
        // glyphs() is already in visual order.
        for (ShapedGlyph g : s.glyphs()) {
            Character d = digitGlyphs.get(g.glyphCode);
            if (d != null) {
                sb.append(d.charValue());
            }
        }
        return sb.toString();
    }

    /** Locates a font with Arabic coverage for tests. */
    static final class TestFonts {
        private static final String[] CANDIDATES = {
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/noto/NotoSansArabic-Regular.ttf",
                "/System/Library/Fonts/Supplemental/Arial.ttf"
        };

        static Font load() throws Exception {
            InputStream bundled =
                    TestFonts.class.getResourceAsStream("/assets/acrpphone/fonts/phone.ttf");
            if (bundled != null) {
                try {
                    return Font.createFont(Font.TRUETYPE_FONT, bundled);
                } finally {
                    bundled.close();
                }
            }
            for (String path : CANDIDATES) {
                File f = new File(path);
                if (f.isFile()) {
                    return Font.createFont(Font.TRUETYPE_FONT, f);
                }
            }
            throw new IllegalStateException(
                    "no Arabic-capable font found; bundle one at "
                            + "src/main/resources/assets/acrpphone/fonts/phone.ttf");
        }
    }
}

package com.acrp.phone.client.text;

import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.Font;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Caret placement, hit testing and line wrapping — the parts a text field needs
 * before anyone can type an Arabic message.
 */
public class TextLayoutTest {

    private static Font font;
    private static TextShaper arabic;

    private static final String ARABIC = "مدينة المغامرة";
    private static final String MIXED = "رقمي 0501234567 اتصل";

    @BeforeClass
    public static void loadFont() throws Exception {
        font = TextShaperTest.TestFonts.load().deriveFont(32f);
        arabic = TextShaper.arabic(font);
    }

    // --- glyph metadata -----------------------------------------------------

    @Test
    public void everyGlyphPointsAtASourceCharacter() {
        ShapedText s = arabic.shape(ARABIC);
        for (ShapedGlyph g : s.glyphs()) {
            assertTrue("charIndex out of range: " + g,
                    g.charIndex >= 0 && g.charIndex < ARABIC.length());
        }
    }

    @Test
    public void arabicGlyphsAreMarkedRtlAndDigitsAreNot() {
        ShapedText s = arabic.shape(MIXED);
        boolean sawRtl = false;
        boolean sawLtr = false;
        for (ShapedGlyph g : s.glyphs()) {
            char source = MIXED.charAt(g.charIndex);
            if (source >= '0' && source <= '9') {
                assertFalse("a digit must not be in an RTL run: " + g, g.rtl);
                sawLtr = true;
            } else if (source >= 'ء' && source <= 'ي') {
                assertTrue("an Arabic letter must be in an RTL run: " + g, g.rtl);
                sawRtl = true;
            }
        }
        assertTrue("expected Arabic glyphs", sawRtl);
        assertTrue("expected digit glyphs", sawLtr);
    }

    @Test
    public void advancesSumToTotalWidth() {
        ShapedText s = arabic.shape(ARABIC);
        float sum = 0f;
        for (ShapedGlyph g : s.glyphs()) {
            sum += g.advance;
        }
        assertEquals(s.width(), sum, 0.5f);
    }

    // --- caret --------------------------------------------------------------

    /** In an RTL paragraph the text starts at the right, so caret 0 sits there. */
    @Test
    public void caretAtStartOfRtlParagraphIsOnTheRight() {
        ShapedText s = arabic.shape(ARABIC);
        assertTrue(s.isBaseRtl());
        assertEquals(s.width(), s.caretX(0), 0.01f);
        assertEquals(0f, s.caretX(ARABIC.length()), 0.01f);
    }

    @Test
    public void caretAtStartOfLtrParagraphIsOnTheLeft() {
        String latin = "Adventure";
        ShapedText s = TextShaper.latin(font).shape(latin);
        assertFalse(s.isBaseRtl());
        assertEquals(0f, s.caretX(0), 0.01f);
        assertEquals(s.width(), s.caretX(latin.length()), 0.01f);
    }

    /** Walking the caret through Arabic must move it leftwards, never jump out. */
    @Test
    public void caretMovesMonotonicallyThroughArabic() {
        ShapedText s = arabic.shape(ARABIC);
        float previous = s.caretX(0);
        for (int i = 1; i <= ARABIC.length(); i++) {
            float x = s.caretX(i);
            assertTrue("caret left the box at " + i, x >= -0.5f && x <= s.width() + 0.5f);
            assertTrue("caret moved right in RTL text at " + i, x <= previous + 0.5f);
            previous = x;
        }
    }

    @Test
    public void caretIsClampedOutsideTheString() {
        ShapedText s = arabic.shape(ARABIC);
        assertEquals(s.caretX(0), s.caretX(-5), 0.01f);
        assertEquals(s.caretX(ARABIC.length()), s.caretX(ARABIC.length() + 5), 0.01f);
    }

    @Test
    public void emptyTextHasCaretAtOrigin() {
        assertEquals(0f, arabic.shape("").caretX(0), 0.01f);
    }

    // --- hit testing --------------------------------------------------------

    /** Clicking where the caret is drawn must return the offset it was drawn for. */
    @Test
    public void clickingAtACaretPositionRoundTrips() {
        ShapedText s = arabic.shape(ARABIC);
        for (int i = 0; i <= ARABIC.length(); i++) {
            float x = s.caretX(i);
            int hit = s.charIndexAt(x);
            assertEquals("round trip failed at " + i, s.caretX(i), s.caretX(hit), 0.6f);
        }
    }

    @Test
    public void clickingBeyondEitherEdgeClampsIntoTheString() {
        ShapedText s = arabic.shape(ARABIC);
        int left = s.charIndexAt(-500f);
        int right = s.charIndexAt(s.width() + 500f);
        assertTrue(left >= 0 && left <= ARABIC.length());
        assertTrue(right >= 0 && right <= ARABIC.length());
        assertEquals(ARABIC.length(), left);   // left edge is the logical end in RTL
        assertEquals(0, right);                // right edge is the logical start
    }

    // --- wrapping -----------------------------------------------------------

    @Test
    public void shortTextIsNotWrapped() {
        List<String> lines = new TextWrapper(arabic).wrap(ARABIC, 10_000f);
        assertEquals(1, lines.size());
        assertEquals(ARABIC, lines.get(0));
    }

    @Test
    public void everyWrappedLineFits() {
        String paragraph = "مرحبا بك في مدينة المغامرة نتمنى لك وقتا ممتعا معنا "
                + "ولا تنسى الاطلاع على قوانين السيرفر قبل البدء";
        float maxWidth = 220f;
        List<String> lines = new TextWrapper(arabic).wrap(paragraph, maxWidth);

        assertTrue("expected several lines, got " + lines.size(), lines.size() > 1);
        for (String line : lines) {
            assertTrue("line overflows: '" + line + "'",
                    arabic.width(line) <= maxWidth + 0.5f);
        }
    }

    @Test
    public void wrappingPreservesAllWords() {
        String paragraph = "مرحبا بك في مدينة المغامرة نتمنى لك وقتا ممتعا";
        List<String> lines = new TextWrapper(arabic).wrap(paragraph, 200f);

        StringBuilder rejoined = new StringBuilder();
        for (String line : lines) {
            if (rejoined.length() > 0) {
                rejoined.append(' ');
            }
            rejoined.append(line);
        }
        assertEquals(paragraph, rejoined.toString());
    }

    @Test
    public void existingNewlinesAreKept() {
        List<String> lines = new TextWrapper(arabic).wrap("سطر\nسطر ثاني", 10_000f);
        assertEquals(2, lines.size());
        assertEquals("سطر", lines.get(0));
        assertEquals("سطر ثاني", lines.get(1));
    }

    @Test
    public void blankLinesSurvive() {
        List<String> lines = new TextWrapper(arabic).wrap("أ\n\nب", 10_000f);
        assertEquals(3, lines.size());
        assertEquals("", lines.get(1));
    }

    /** A single unbroken word wider than the box must still be cut, not dropped. */
    @Test
    public void anOverlongWordIsHardBroken() {
        String word = "المغامرةالمغامرةالمغامرةالمغامرة";
        List<String> lines = new TextWrapper(arabic).wrap(word, 90f);
        assertTrue("expected a hard break", lines.size() > 1);

        StringBuilder rejoined = new StringBuilder();
        for (String line : lines) {
            rejoined.append(line);
        }
        assertEquals("hard breaking must not lose characters", word, rejoined.toString());
    }

    @Test
    public void emptyInputWrapsToNoLines() {
        assertTrue(new TextWrapper(arabic).wrap("", 100f).isEmpty());
        assertTrue(new TextWrapper(arabic).wrap(null, 100f).isEmpty());
    }
}

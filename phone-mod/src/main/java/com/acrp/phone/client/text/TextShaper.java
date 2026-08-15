package com.acrp.phone.client.text;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a logical-order string into positioned glyphs in visual order, with full
 * Arabic support: contextual letter joining plus bidirectional reordering.
 *
 * <p>This class deliberately touches nothing from Minecraft — everything here is
 * plain JDK, so the hardest part of the phone can be unit tested (and eyeballed as
 * a PNG) without launching the game.
 *
 * <h3>Why two passes</h3>
 * Handing a whole mixed string to {@link Font#layoutGlyphVector} with
 * {@link Font#LAYOUT_RIGHT_TO_LEFT} looks like it works, but it reverses embedded
 * Latin and digit sequences: the phone number {@code 0501234567} comes out as
 * {@code 7654321050}. So we split the string into directional runs with
 * {@link Bidi} first, reorder those runs visually, and only then shape each run in
 * its own direction.
 *
 * <p>Each run is laid out against the <em>full</em> character array, passing only
 * its start/limit. That lets the shaper see the neighbouring characters and keep
 * letters joined across a run boundary.
 */
public final class TextShaper {

    /** Shaping is expensive; strings on screen repeat every frame. */
    private static final int CACHE_CAPACITY = 512;

    private static final ShapedText EMPTY =
            new ShapedText(Collections.<ShapedGlyph>emptyList(), 0f, false, 0);

    private final Font font;
    private final FontRenderContext frc;
    private final int baseDirection;
    private final Map<String, ShapedText> cache;

    /**
     * @param font          the font to shape with, already at the desired size
     * @param baseDirection one of {@link Bidi#DIRECTION_DEFAULT_LEFT_TO_RIGHT} or
     *                      {@link Bidi#DIRECTION_DEFAULT_RIGHT_TO_LEFT}; the
     *                      "DEFAULT_" variants resolve direction from the first
     *                      strong character and only fall back to the named side
     *                      when the string has none (e.g. a bare phone number)
     */
    public TextShaper(Font font, int baseDirection) {
        this.font = font;
        this.baseDirection = baseDirection;
        // Antialiasing on, fractional metrics on: we rasterize to an atlas at a
        // fixed size, so sub-pixel advances keep spacing even.
        this.frc = new FontRenderContext(null, true, true);
        this.cache = new LinkedHashMap<String, ShapedText>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ShapedText> eldest) {
                return size() > CACHE_CAPACITY;
            }
        };
    }

    /** Shapes with an Arabic-first base direction. */
    public static TextShaper arabic(Font font) {
        return new TextShaper(font, Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT);
    }

    /** Shapes with a Latin-first base direction. */
    public static TextShaper latin(Font font) {
        return new TextShaper(font, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
    }

    public Font font() {
        return font;
    }

    /** Shapes {@code text}, reusing a cached result when the string repeats. */
    public ShapedText shape(String text) {
        if (text == null || text.isEmpty()) {
            return EMPTY;
        }
        ShapedText cached = cache.get(text);
        if (cached != null) {
            return cached;
        }
        ShapedText shaped = shapeUncached(text);
        cache.put(text, shaped);
        return shaped;
    }

    /** Convenience: advance width of {@code text} in font units. */
    public float width(String text) {
        return shape(text).width();
    }

    private ShapedText shapeUncached(String text) {
        char[] chars = text.toCharArray();

        // Fast path: no RTL characters, so a single left-to-right layout places the
        // glyphs correctly.
        if (!Bidi.requiresBidi(chars, 0, chars.length)) {
            GlyphVector gv = font.layoutGlyphVector(
                    frc, chars, 0, chars.length, Font.LAYOUT_LEFT_TO_RIGHT);
            List<ShapedGlyph> out = new ArrayList<ShapedGlyph>(gv.getNumGlyphs());
            float width = append(out, gv, 0f, 0, false);
            // The glyph order is settled, but the paragraph direction is not: a
            // string of only digits or punctuation has no strong character, so it
            // inherits the configured default. That is what decides which edge it
            // aligns to — a clock reading "9:41" belongs on the right in an Arabic
            // UI, and reporting LTR here pushes it off the screen.
            return new ShapedText(out, width, defaultsToRtl() && !hasStrongLtr(chars),
                    chars.length);
        }

        Bidi bidi = new Bidi(text, baseDirection);
        boolean baseRtl = !bidi.baseIsLeftToRight();

        // Single-run shortcut (e.g. an all-Arabic label).
        if (bidi.getRunCount() == 1) {
            boolean rtl = (bidi.getRunLevel(0) & 1) != 0;
            GlyphVector gv = font.layoutGlyphVector(frc, chars, 0, chars.length,
                    rtl ? Font.LAYOUT_RIGHT_TO_LEFT : Font.LAYOUT_LEFT_TO_RIGHT);
            List<ShapedGlyph> out = new ArrayList<ShapedGlyph>(gv.getNumGlyphs());
            float width = append(out, gv, 0f, 0, rtl);
            return new ShapedText(out, width, baseRtl, chars.length);
        }

        int runCount = bidi.getRunCount();
        byte[] levels = new byte[runCount];
        Integer[] visualOrder = new Integer[runCount];
        for (int i = 0; i < runCount; i++) {
            levels[i] = (byte) bidi.getRunLevel(i);
            visualOrder[i] = i;
        }
        // Rewrites visualOrder so that visualOrder[v] is the logical run that
        // belongs at visual position v.
        Bidi.reorderVisually(levels, 0, visualOrder, 0, runCount);

        List<ShapedGlyph> out = new ArrayList<ShapedGlyph>(chars.length + 8);
        float penX = 0f;
        for (int v = 0; v < runCount; v++) {
            int logical = visualOrder[v];
            int start = bidi.getRunStart(logical);
            int limit = bidi.getRunLimit(logical);
            boolean rtl = (bidi.getRunLevel(logical) & 1) != 0;

            // Full array + run bounds => the shaper keeps surrounding context and
            // letters stay joined across the boundary.
            GlyphVector gv = font.layoutGlyphVector(frc, chars, start, limit,
                    rtl ? Font.LAYOUT_RIGHT_TO_LEFT : Font.LAYOUT_LEFT_TO_RIGHT);
            penX = append(out, gv, penX, start, rtl);
        }
        return new ShapedText(out, penX, baseRtl, chars.length);
    }

    /** Whether this shaper falls back to RTL when a string has no strong character. */
    private boolean defaultsToRtl() {
        return baseDirection == Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT
                || baseDirection == Bidi.DIRECTION_RIGHT_TO_LEFT;
    }

    /** True if any character is strongly left-to-right, which pins the direction. */
    private static boolean hasStrongLtr(char[] chars) {
        for (char c : chars) {
            if (Character.getDirectionality(c) == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                return true;
            }
        }
        return false;
    }

    /**
     * Appends {@code gv}'s glyphs shifted by {@code originX}.
     *
     * @param runStart index in the original string where this run begins; glyph
     *                 char indices come back relative to it
     * @return the pen X after this vector, i.e. {@code originX + advance}
     */
    private float append(List<ShapedGlyph> out, GlyphVector gv, float originX,
                         int runStart, boolean rtl) {
        int n = gv.getNumGlyphs();
        // Ask for n+1 entries: the extra trailing pair is the position *after* the
        // last glyph, i.e. the run's total advance. Requesting only n loses it.
        float[] pos = gv.getGlyphPositions(0, n + 1, null);
        for (int i = 0; i < n; i++) {
            float x = originX + pos[i * 2];
            float advance = pos[(i + 1) * 2] - pos[i * 2];
            out.add(new ShapedGlyph(gv.getGlyphCode(i), x, pos[i * 2 + 1],
                    advance, runStart + gv.getGlyphCharIndex(i), rtl));
        }
        return originX + pos[n * 2];
    }
}

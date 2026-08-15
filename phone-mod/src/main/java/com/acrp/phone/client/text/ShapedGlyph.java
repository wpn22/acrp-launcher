package com.acrp.phone.client.text;

/**
 * A single positioned glyph, ready to be drawn.
 *
 * <p>{@code glyphCode} is a font-specific glyph index (NOT a unicode codepoint).
 * Arabic letters resolve to different glyph codes depending on their position in
 * the word, which is exactly what makes the script join up — so the atlas must be
 * keyed on this value, never on the source character.
 */
public final class ShapedGlyph {

    /** Font-specific glyph index, as produced by the shaper. */
    public final int glyphCode;

    /** Pen X offset from the start of the laid-out string, in font units. */
    public final float x;

    /** Pen Y offset from the baseline, in font units. Usually 0. */
    public final float y;

    public ShapedGlyph(int glyphCode, float x, float y) {
        this.glyphCode = glyphCode;
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return "ShapedGlyph{g=" + glyphCode + ", x=" + x + ", y=" + y + '}';
    }
}

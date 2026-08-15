package com.acrp.phone.client.text;

/**
 * A single positioned glyph, ready to be drawn.
 *
 * <p>{@code glyphCode} is a font-specific glyph index (NOT a unicode codepoint).
 * Arabic letters resolve to different glyph codes depending on their position in
 * the word, which is exactly what makes the script join up — so the atlas must be
 * keyed on this value, never on the source character.
 *
 * <p>{@link #charIndex} points back into the original logical string. Text fields
 * need it: the caret moves through the string in logical order but has to be drawn
 * at a visual position, and a click has to resolve back to a logical offset.
 */
public final class ShapedGlyph {

    /** Font-specific glyph index, as produced by the shaper. */
    public final int glyphCode;

    /** Pen X offset from the start of the laid-out string, in font units. */
    public final float x;

    /** Pen Y offset from the baseline, in font units. Usually 0. */
    public final float y;

    /** Advance width of this glyph, in font units. */
    public final float advance;

    /** Index of the character this glyph came from, in the original string. */
    public final int charIndex;

    /** Whether this glyph belongs to a right-to-left run. */
    public final boolean rtl;

    public ShapedGlyph(int glyphCode, float x, float y, float advance,
                       int charIndex, boolean rtl) {
        this.glyphCode = glyphCode;
        this.x = x;
        this.y = y;
        this.advance = advance;
        this.charIndex = charIndex;
        this.rtl = rtl;
    }

    /** X of this glyph's right edge. */
    public float right() {
        return x + advance;
    }

    /**
     * X of the edge a caret sits on when it is placed <em>before</em> this glyph's
     * character in logical order. In an RTL run that is the glyph's right edge.
     */
    public float leadingEdge() {
        return rtl ? right() : x;
    }

    /** The opposite edge from {@link #leadingEdge()}. */
    public float trailingEdge() {
        return rtl ? x : right();
    }

    @Override
    public String toString() {
        return "ShapedGlyph{g=" + glyphCode + ", x=" + x + ", adv=" + advance
                + ", ch=" + charIndex + (rtl ? ", rtl" : "") + '}';
    }
}

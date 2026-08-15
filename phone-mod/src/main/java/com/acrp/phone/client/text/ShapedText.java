package com.acrp.phone.client.text;

import java.util.Collections;
import java.util.List;

/**
 * The result of shaping one string: glyphs already placed in <em>visual</em> order,
 * left to right, so a renderer can simply walk the list and blit.
 *
 * <p>Also answers the two questions a text field asks — where does the caret go for
 * this logical offset, and which offset did the player just click on. Both have to
 * cross between logical and visual order, which is why they live here rather than
 * in the widget.
 *
 * <p>Instances are immutable and safe to cache — see {@link TextShaper}.
 */
public final class ShapedText {

    private final List<ShapedGlyph> glyphs;
    private final float width;
    private final boolean baseRtl;
    private final int length;

    ShapedText(List<ShapedGlyph> glyphs, float width, boolean baseRtl, int length) {
        this.glyphs = Collections.unmodifiableList(glyphs);
        this.width = width;
        this.baseRtl = baseRtl;
        this.length = length;
    }

    /** Positioned glyphs in visual (left-to-right) order. */
    public List<ShapedGlyph> glyphs() {
        return glyphs;
    }

    /** Total advance width of the string, in font units. */
    public float width() {
        return width;
    }

    /**
     * Whether the paragraph direction resolved to right-to-left. UI code uses this
     * to decide alignment: an RTL string in a left-aligned box still wants to hug
     * the right edge.
     */
    public boolean isBaseRtl() {
        return baseRtl;
    }

    /** Length of the source string. */
    public int length() {
        return length;
    }

    public boolean isEmpty() {
        return glyphs.isEmpty();
    }

    /**
     * Visual X where a caret sitting before {@code charIndex} should be drawn.
     *
     * <p>A caret at the very end of the string belongs at the trailing edge of the
     * paragraph, which is the left side when the paragraph is RTL.
     */
    public float caretX(int charIndex) {
        if (glyphs.isEmpty()) {
            return 0f;
        }
        if (charIndex <= 0) {
            return baseRtl ? width : 0f;
        }
        if (charIndex >= length) {
            return baseRtl ? 0f : width;
        }
        for (ShapedGlyph g : glyphs) {
            if (g.charIndex == charIndex) {
                return g.leadingEdge();
            }
        }
        // The index landed inside a cluster (e.g. a combining mark that produced no
        // glyph of its own). Fall back to the nearest preceding glyph's trailing
        // edge so the caret still lands somewhere sensible.
        ShapedGlyph best = null;
        for (ShapedGlyph g : glyphs) {
            if (g.charIndex < charIndex && (best == null || g.charIndex > best.charIndex)) {
                best = g;
            }
        }
        return best == null ? (baseRtl ? width : 0f) : best.trailingEdge();
    }

    /**
     * Logical offset nearest to visual position {@code x} — what a click resolves to.
     *
     * <p>Clicking past a glyph's midpoint puts the caret on its far side, which is
     * what makes selection feel right in both directions.
     */
    public int charIndexAt(float x) {
        if (glyphs.isEmpty()) {
            return 0;
        }
        // Seed with both ends of the paragraph. Which visual edge each one sits on
        // depends on direction, so ask caretX rather than assuming: in RTL, offset
        // 0 is at the right edge and offset length is at the left.
        int best = 0;
        float bestDistance = Math.abs(x - caretX(0));

        float endDistance = Math.abs(x - caretX(length));
        if (endDistance < bestDistance) {
            bestDistance = endDistance;
            best = length;
        }

        for (ShapedGlyph g : glyphs) {
            float leading = Math.abs(x - g.leadingEdge());
            if (leading < bestDistance) {
                bestDistance = leading;
                best = g.charIndex;
            }
            float trailing = Math.abs(x - g.trailingEdge());
            if (trailing < bestDistance) {
                bestDistance = trailing;
                best = g.charIndex + 1;
            }
        }
        return Math.max(0, Math.min(length, best));
    }
}

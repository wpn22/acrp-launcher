package com.acrp.phone.client.text;

import java.util.Collections;
import java.util.List;

/**
 * The result of shaping one string: glyphs already placed in <em>visual</em> order,
 * left to right, so a renderer can simply walk the list and blit.
 *
 * <p>Instances are immutable and safe to cache — see {@link TextShaper}.
 */
public final class ShapedText {

    private final List<ShapedGlyph> glyphs;
    private final float width;
    private final boolean baseRtl;

    ShapedText(List<ShapedGlyph> glyphs, float width, boolean baseRtl) {
        this.glyphs = Collections.unmodifiableList(glyphs);
        this.width = width;
        this.baseRtl = baseRtl;
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

    public boolean isEmpty() {
        return glyphs.isEmpty();
    }
}

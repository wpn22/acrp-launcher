package com.acrp.phone.client.font;

/**
 * Shelf packer for a single glyph atlas page.
 *
 * <p>Split out from {@link GlyphAtlas} and kept free of Minecraft types so the
 * arithmetic can be tested directly. An off-by-one here does not throw — it writes
 * one glyph's pixels on top of another's, which shows up as garbled letters and is
 * miserable to trace back from a screenshot.
 *
 * <p>Glyphs fill left to right along a shelf. When the next glyph does not fit the
 * current shelf the pen drops by the tallest glyph placed on it.
 */
public final class AtlasPacker {

    public static final int PAGE_SIZE = 256;

    /** Breathing room so neighbouring glyphs never bleed into each other. */
    public static final int PADDING = 1;

    private int penX = PADDING;
    private int penY = PADDING;
    private int shelfHeight;

    /** Whether {@code width}x{@code height} still fits, allowing for a shelf drop. */
    public boolean canFit(int width, int height) {
        if (width + PADDING * 2 > PAGE_SIZE || height + PADDING * 2 > PAGE_SIZE) {
            return false; // larger than a page in some dimension
        }
        int y = penY;
        if (penX + width + PADDING > PAGE_SIZE) {
            y += shelfHeight + PADDING;
        }
        return y + height + PADDING <= PAGE_SIZE;
    }

    /**
     * Claims a slot, dropping to the next shelf first if the current one is full.
     *
     * <p>Only call when {@link #canFit} said yes.
     *
     * @return the slot packed as {@code x << 16 | y}; read it with {@link #slotX}
     *         and {@link #slotY}
     */
    public int reserve(int width, int height) {
        if (penX + width + PADDING > PAGE_SIZE) {
            penX = PADDING;
            penY += shelfHeight + PADDING;
            shelfHeight = 0;
        }
        int x = penX;
        int y = penY;
        penX += width + PADDING;
        shelfHeight = Math.max(shelfHeight, height);
        return (x << 16) | (y & 0xFFFF);
    }

    public static int slotX(int slot) {
        return slot >>> 16;
    }

    public static int slotY(int slot) {
        return slot & 0xFFFF;
    }
}

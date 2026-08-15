package com.acrp.phone.client.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rasterizes glyphs on demand into texture pages Minecraft can bind.
 *
 * <p>Entries are keyed on the <strong>glyph code</strong>, not the character. One
 * Arabic letter produces different glyphs depending on where it sits in a word, and
 * that difference is precisely what makes the script join — keying on the character
 * would collapse all four forms onto one image and undo the shaper's work.
 *
 * <p>Pages fill left to right in rows. When a glyph does not fit the current row the
 * pen drops by the tallest glyph in that row; when it does not fit the page, a new
 * page is allocated. Pages are never rewritten, so uploaded texture data stays valid.
 */
public final class GlyphAtlas {

    /** Page geometry lives in {@link AtlasPacker} so UVs and packing cannot diverge. */
    private static final int PAGE_SIZE = AtlasPacker.PAGE_SIZE;

    private final Font font;
    private final FontRenderContext frc;
    private final Map<Integer, Glyph> glyphs = new HashMap<Integer, Glyph>();
    private final List<Page> pages = new ArrayList<Page>();

    private final int[] singleGlyph = new int[1];

    public GlyphAtlas(Font font) {
        this.font = font;
        this.frc = new FontRenderContext(null, true, true);
    }

    /** A rasterized glyph and where it landed in its page. */
    public static final class Glyph {

        public final ResourceLocation texture;
        /** Texture coordinates, already normalised to 0..1. */
        public final float u0;
        public final float v0;
        public final float u1;
        public final float v1;
        /** Size of the rasterized image, in pixels. */
        public final int width;
        public final int height;
        /** Offset from the pen position to the top-left of the image. */
        public final int bearingX;
        public final int bearingY;

        Glyph(ResourceLocation texture, float u0, float v0, float u1, float v1,
              int width, int height, int bearingX, int bearingY) {
            this.texture = texture;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.width = width;
            this.height = height;
            this.bearingX = bearingX;
            this.bearingY = bearingY;
        }

        /** True for glyphs with no ink — spaces. Nothing to draw. */
        public boolean isBlank() {
            return width <= 0 || height <= 0;
        }
    }

    /**
     * Returns the glyph for {@code glyphCode}, rasterizing and uploading it the
     * first time it is asked for.
     */
    public Glyph get(int glyphCode) {
        Glyph cached = glyphs.get(glyphCode);
        if (cached != null) {
            return cached;
        }
        Glyph created = rasterize(glyphCode);
        glyphs.put(glyphCode, created);
        return created;
    }

    private Glyph rasterize(int glyphCode) {
        singleGlyph[0] = glyphCode;
        GlyphVector vector = font.createGlyphVector(frc, singleGlyph);
        Rectangle2D bounds = vector.getPixelBounds(frc, 0f, 0f);

        int width = (int) Math.ceil(bounds.getWidth());
        int height = (int) Math.ceil(bounds.getHeight());
        if (width <= 0 || height <= 0) {
            // A space, or something else with no ink. Record it so we do not try
            // to rasterize it again on every frame.
            return new Glyph(null, 0f, 0f, 0f, 0f, 0, 0, 0, 0);
        }

        Page page = pageWithRoom(width, height);
        // reserve() wraps to a new row first when needed, so the returned slot is
        // always inside the page.
        int slot = page.packer.reserve(width, height);
        int x = AtlasPacker.slotX(slot);
        int y = AtlasPacker.slotY(slot);

        Graphics2D g = page.image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        // Draw so the glyph's ink lands at (x, y); getPixelBounds is relative to
        // the pen, so subtract its origin.
        g.drawGlyphVector(vector, x - (float) bounds.getX(), y - (float) bounds.getY());
        g.dispose();

        page.markDirty();

        return new Glyph(page.location,
                x / (float) PAGE_SIZE,
                y / (float) PAGE_SIZE,
                (x + width) / (float) PAGE_SIZE,
                (y + height) / (float) PAGE_SIZE,
                width, height,
                (int) Math.floor(bounds.getX()),
                (int) Math.floor(bounds.getY()));
    }

    private Page pageWithRoom(int width, int height) {
        if (!pages.isEmpty()) {
            Page current = pages.get(pages.size() - 1);
            if (current.packer.canFit(width, height)) {
                return current;
            }
        }
        Page page = new Page(pages.size());
        pages.add(page);
        return page;
    }

    /** Frees every uploaded page. Call when the font or size changes. */
    public void dispose() {
        for (Page page : pages) {
            page.dispose();
        }
        pages.clear();
        glyphs.clear();
    }

    /** One texture page: a CPU-side image plus its uploaded counterpart. */
    private static final class Page {

        final AtlasPacker packer = new AtlasPacker();
        final BufferedImage image;
        final DynamicTexture texture;
        final ResourceLocation location;

        Page(int index) {
            this.image = new BufferedImage(PAGE_SIZE, PAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
            this.texture = new DynamicTexture(PAGE_SIZE, PAGE_SIZE);
            this.location = Minecraft.getMinecraft().getTextureManager()
                    .getDynamicTextureLocation("acrpphone_glyphs_" + index, texture);
        }

        /** Copies the CPU image into the uploaded texture. */
        void markDirty() {
            int[] target = texture.getTextureData();
            image.getRGB(0, 0, PAGE_SIZE, PAGE_SIZE, target, 0, PAGE_SIZE);
            texture.updateDynamicTexture();
        }

        void dispose() {
            Minecraft.getMinecraft().getTextureManager().deleteTexture(location);
        }
    }
}

package com.acrp.phone.client.font;

import com.acrp.phone.client.text.ShapedGlyph;
import com.acrp.phone.client.text.ShapedText;
import com.acrp.phone.client.text.TextShaper;
import com.acrp.phone.client.text.TextWrapper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Draws Arabic-capable text inside the phone.
 *
 * <p>Deliberately mirrors the shape of vanilla's {@code FontRenderer} so widgets can
 * be written against a familiar surface, but everything underneath is ours: vanilla
 * cannot join Arabic letters or order a bidirectional line, which is the whole
 * reason this class exists.
 *
 * <p>Text goes through three stages — {@link TextShaper} turns a string into
 * positioned glyphs, {@link GlyphAtlas} turns each glyph code into a texture region,
 * and this class batches those regions into one draw call per string.
 */
public final class PhoneFontRenderer {

    /** Path of the bundled font inside the mod jar. */
    private static final String BUNDLED_FONT =
            "/assets/acrpphone/fonts/phone.ttf";

    /** Used only if the bundled font is missing, so development never hard-stops. */
    private static final String FALLBACK_FAMILY = Font.SANS_SERIF;

    private final Font font;
    private final TextShaper shaper;
    private final TextWrapper wrapper;
    private final GlyphAtlas atlas;
    private final float ascent;
    private final float lineHeight;

    private PhoneFontRenderer(Font font) {
        this.font = font;
        this.shaper = TextShaper.arabic(font);
        this.wrapper = new TextWrapper(shaper);
        this.atlas = new GlyphAtlas(font);

        // AWT only exposes metrics through a graphics context, so borrow a 1x1 one.
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scratch.createGraphics();
        try {
            FontMetrics metrics = graphics.getFontMetrics(font);
            this.ascent = metrics.getAscent();
            this.lineHeight = metrics.getHeight();
        } finally {
            graphics.dispose();
        }
    }

    /**
     * Loads the bundled font at {@code sizePx}, falling back to a system sans-serif
     * if the resource is absent.
     */
    public static PhoneFontRenderer create(float sizePx) {
        Font loaded = null;
        InputStream in = PhoneFontRenderer.class.getResourceAsStream(BUNDLED_FONT);
        if (in != null) {
            try {
                loaded = Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Exception e) {
                loaded = null;
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // nothing useful to do here
                }
            }
        }
        if (loaded == null) {
            loaded = new Font(FALLBACK_FAMILY, Font.PLAIN, 1);
        }
        return new PhoneFontRenderer(loaded.deriveFont(sizePx));
    }

    public float lineHeight() {
        return lineHeight;
    }

    /** Advance width of {@code text}, in the phone's logical pixels. */
    public float getStringWidth(String text) {
        return shaper.width(text);
    }

    /** Breaks {@code text} into lines that each fit {@code maxWidth}. */
    public List<String> wrapText(String text, float maxWidth) {
        return wrapper.wrap(text, maxWidth);
    }

    /** Shapes {@code text}; exposed so widgets can do caret maths. */
    public ShapedText shape(String text) {
        return shaper.shape(text);
    }

    /**
     * Truncates {@code text} to fit {@code maxWidth}, appending an ellipsis.
     *
     * <p>Trimming happens on the logical string and the result is re-measured, since
     * cutting shaped glyphs would break letter joining.
     */
    public String trimToWidth(String text, float maxWidth) {
        if (getStringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        float budget = maxWidth - getStringWidth(ellipsis);
        if (budget <= 0f) {
            return ellipsis;
        }
        int fits = 0;
        for (int n = 1; n <= text.length(); n++) {
            if (getStringWidth(text.substring(0, n)) > budget) {
                break;
            }
            fits = n;
        }
        return text.substring(0, fits) + ellipsis;
    }

    /**
     * Draws {@code text} with its left edge at {@code x} and its baseline derived
     * from {@code y} (the top of the line box).
     *
     * @param colour packed ARGB
     */
    public void drawString(String text, float x, float y, int colour) {
        drawShaped(shaper.shape(text), x, y, colour);
    }

    /**
     * Draws pre-shaped text. Widgets that already hold a {@link ShapedText} — a text
     * field tracking a caret, say — should use this and skip re-shaping.
     */
    public void drawShaped(ShapedText text, float x, float y, int colour) {
        if (text.isEmpty()) {
            return;
        }
        float baseline = y + ascent;

        float alpha = (colour >> 24 & 0xFF) / 255f;
        float red = (colour >> 16 & 0xFF) / 255f;
        float green = (colour >> 8 & 0xFF) / 255f;
        float blue = (colour & 0xFF) / 255f;
        if (alpha == 0f) {
            alpha = 1f; // treat a missing alpha channel as opaque
        }

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.color(red, green, blue, alpha);

        // Glyphs of one string usually share a page, so bind lazily and only
        // re-batch when the page actually changes.
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        ResourceLocation bound = null;
        boolean drawing = false;

        for (ShapedGlyph glyph : text.glyphs()) {
            GlyphAtlas.Glyph rendered = atlas.get(glyph.glyphCode);
            if (rendered.isBlank()) {
                continue;
            }
            if (!rendered.texture.equals(bound)) {
                if (drawing) {
                    tessellator.draw();
                }
                Minecraft.getMinecraft().getTextureManager().bindTexture(rendered.texture);
                bound = rendered.texture;
                buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
                drawing = true;
            }
            quad(buffer,
                    x + glyph.x + rendered.bearingX,
                    baseline + rendered.bearingY,
                    rendered);
        }
        if (drawing) {
            tessellator.draw();
        }

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
    }

    private void quad(BufferBuilder buffer, float left, float top,
                      GlyphAtlas.Glyph glyph) {
        float right = left + glyph.width;
        float bottom = top + glyph.height;
        buffer.pos(left, bottom, 0d).tex(glyph.u0, glyph.v1).endVertex();
        buffer.pos(right, bottom, 0d).tex(glyph.u1, glyph.v1).endVertex();
        buffer.pos(right, top, 0d).tex(glyph.u1, glyph.v0).endVertex();
        buffer.pos(left, top, 0d).tex(glyph.u0, glyph.v0).endVertex();
    }

    /** Releases the atlas pages. */
    public void dispose() {
        atlas.dispose();
    }

    public Font font() {
        return font;
    }
}

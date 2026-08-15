package com.acrp.phone.client.ui;

import com.acrp.phone.client.text.ShapedGlyph;
import com.acrp.phone.client.text.ShapedText;
import com.acrp.phone.client.text.TextShaper;
import com.acrp.phone.client.text.TextWrapper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Paints the phone UI into an image instead of onto the screen.
 *
 * <p>This is how the interface gets looked at at all right now: Forge's maven is
 * unreachable from the build environment, so the in-game renderer cannot even be
 * compiled here, let alone run. Screens are written against {@link PhoneCanvas}, so
 * the exact same screen code that will run in Minecraft can be rendered to a PNG in
 * a second and inspected.
 *
 * <p>Lives in test sources deliberately — it must never ship inside the mod jar.
 */
public final class AwtCanvas implements PhoneCanvas {

    private static final String BUNDLED_FONT = "/assets/acrpphone/fonts/phone.ttf";
    private static final String[] FALLBACK_FONTS = {
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansArabic-Regular.ttf"
    };

    private final BufferedImage image;
    private final Graphics2D g;
    private final Font baseFont;
    private final FontRenderContext frc;
    private final Deque<Shape> clips = new ArrayDeque<Shape>();

    /** One shaper per size; shaping is cached inside each. */
    private final Map<Float, TextShaper> shapers = new HashMap<Float, TextShaper>();
    private final Map<Float, Font> fonts = new HashMap<Float, Font>();

    private final int[] oneGlyph = new int[1];

    public AwtCanvas() {
        this(PhoneCanvas.WIDTH, PhoneCanvas.HEIGHT);
    }

    public AwtCanvas(int width, int height) {
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.g = image.createGraphics();
        this.g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        this.g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        this.g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        this.baseFont = loadFont();
        this.frc = new FontRenderContext(null, true, true);
    }

    private static Font loadFont() {
        InputStream in = AwtCanvas.class.getResourceAsStream(BUNDLED_FONT);
        if (in != null) {
            try {
                return Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Exception ignored) {
                // fall through to a system face
            } finally {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // nothing useful to do
                }
            }
        }
        for (String path : FALLBACK_FONTS) {
            File f = new File(path);
            if (f.isFile()) {
                try {
                    return Font.createFont(Font.TRUETYPE_FONT, f);
                } catch (Exception ignored) {
                    // try the next one
                }
            }
        }
        throw new IllegalStateException("no Arabic-capable font available for rendering");
    }

    private Font fontAt(float size) {
        Font cached = fonts.get(size);
        if (cached == null) {
            cached = baseFont.deriveFont(size);
            fonts.put(size, cached);
        }
        return cached;
    }

    private TextShaper shaperAt(float size) {
        TextShaper cached = shapers.get(size);
        if (cached == null) {
            cached = TextShaper.arabic(fontAt(size));
            shapers.put(size, cached);
        }
        return cached;
    }

    private static Color argb(int colour) {
        return new Color(colour, true);
    }

    // --- surfaces -----------------------------------------------------------

    @Override
    public void clear(int colour) {
        Shape old = g.getClip();
        g.setClip(null);
        g.setColor(argb(colour));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setClip(old);
    }

    @Override
    public void fillRect(float x, float y, float w, float h, int colour) {
        g.setColor(argb(colour));
        g.fill(new java.awt.geom.Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void fillGradient(float x, float y, float w, float h, int top, int bottom) {
        g.setPaint(new GradientPaint(x, y, argb(top), x, y + h, argb(bottom)));
        g.fill(new java.awt.geom.Rectangle2D.Float(x, y, w, h));
        g.setPaint(null);
        g.setColor(Color.BLACK);
    }

    @Override
    public void fillRoundRect(float x, float y, float w, float h, float radius, int colour) {
        g.setColor(argb(colour));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, radius * 2f, radius * 2f));
    }

    @Override
    public void fillSquircle(float x, float y, float w, float h, int top, int bottom) {
        g.setPaint(new GradientPaint(x, y, argb(top), x, y + h, argb(bottom)));
        g.fill(squirclePath(x, y, w, h));
        g.setPaint(null);
        g.setColor(Color.BLACK);
    }

    /** Builds the superellipse outline as an AWT path. */
    static Path2D.Float squirclePath(float x, float y, float w, float h) {
        float[] pts = Squircle.icon(x, y, w, h);
        Path2D.Float path = new Path2D.Float();
        path.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) {
            path.lineTo(pts[i], pts[i + 1]);
        }
        path.closePath();
        return path;
    }

    @Override
    public void fillCircle(float cx, float cy, float radius, int colour) {
        g.setColor(argb(colour));
        g.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f));
    }

    @Override
    public void fillPath(float[] points, int colour) {
        if (points == null || points.length < 6) {
            return; // fewer than three vertices encloses no area
        }
        Path2D.Float path = new Path2D.Float();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) {
            path.lineTo(points[i], points[i + 1]);
        }
        path.closePath();
        g.setColor(argb(colour));
        g.fill(path);
    }

    @Override
    public void drawLine(float x1, float y1, float x2, float y2, float width, int colour) {
        g.setColor(argb(colour));
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    // --- text ---------------------------------------------------------------

    @Override
    public void drawText(String text, float x, float y, float size, int colour, TextAlign align) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ShapedText shaped = shaperAt(size).shape(text);
        float left = leftEdge(shaped, x, align);
        float baseline = y + ascent(size);

        Font font = fontAt(size);
        g.setColor(argb(colour));
        for (ShapedGlyph glyph : shaped.glyphs()) {
            oneGlyph[0] = glyph.glyphCode;
            GlyphVector gv = font.createGlyphVector(frc, oneGlyph);
            g.drawGlyphVector(gv, left + glyph.x, baseline + glyph.y);
        }
    }

    /**
     * Resolves an anchor into the string's left edge.
     *
     * <p>START is the leading edge, which for Arabic is the <em>right</em> — get
     * this wrong and every Arabic label drifts off its container.
     */
    private float leftEdge(ShapedText shaped, float x, TextAlign align) {
        float width = shaped.width();
        switch (align) {
            case CENTER:
                return x - width / 2f;
            case END:
                return shaped.isBaseRtl() ? x : x - width;
            case START:
            default:
                return shaped.isBaseRtl() ? x - width : x;
        }
    }

    @Override
    public float textWidth(String text, float size) {
        return shaperAt(size).width(text);
    }

    @Override
    public float lineHeight(float size) {
        return g.getFontMetrics(fontAt(size)).getHeight();
    }

    private float ascent(float size) {
        return g.getFontMetrics(fontAt(size)).getAscent();
    }

    @Override
    public List<String> wrapText(String text, float size, float maxWidth) {
        return new TextWrapper(shaperAt(size)).wrap(text, maxWidth);
    }

    // --- clipping -----------------------------------------------------------

    @Override
    public void pushClip(float x, float y, float w, float h) {
        clips.push(g.getClip());
        g.clip(new java.awt.geom.Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void popClip() {
        if (clips.isEmpty()) {
            throw new IllegalStateException("popClip without a matching pushClip");
        }
        g.setClip(clips.pop());
    }

    // --- frosted panels -----------------------------------------------------

    @Override
    public void blurPanel(float x, float y, float w, float h, float radius, int tint) {
        int bx = Math.max(0, (int) x);
        int by = Math.max(0, (int) y);
        int bw = Math.min(image.getWidth() - bx, (int) Math.ceil(w));
        int bh = Math.min(image.getHeight() - by, (int) Math.ceil(h));
        if (bw <= 0 || bh <= 0) {
            return;
        }

        BufferedImage region = boxBlur(image.getSubimage(bx, by, bw, bh), 8);

        Shape old = g.getClip();
        g.clip(new RoundRectangle2D.Float(x, y, w, h, radius * 2f, radius * 2f));
        g.drawImage(region, bx, by, null);
        g.setColor(argb(tint));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, radius * 2f, radius * 2f));
        g.setClip(old);
    }

    /** Separable box blur, run twice — close enough to a gaussian for a preview. */
    private static BufferedImage boxBlur(BufferedImage src, int radius) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] pixels = src.getRGB(0, 0, w, h, null, 0, w);
        int[] tmp = new int[pixels.length];
        for (int pass = 0; pass < 2; pass++) {
            blurPass(pixels, tmp, w, h, radius);
            blurPass(tmp, pixels, h, w, radius); // transposed back
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        return out;
    }

    /** Blurs rows of {@code in} and writes the transpose into {@code out}. */
    private static void blurPass(int[] in, int[] out, int w, int h, int radius) {
        int window = radius * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int a = 0, r = 0, gg = 0, b = 0, n = 0;
                for (int d = -radius; d <= radius; d++) {
                    int sx = x + d;
                    if (sx < 0 || sx >= w) {
                        continue;
                    }
                    int p = in[row + sx];
                    a += (p >>> 24); r += (p >> 16) & 0xFF; gg += (p >> 8) & 0xFF; b += p & 0xFF;
                    n++;
                }
                if (n == 0) {
                    n = window;
                }
                out[x * h + y] = ((a / n) << 24) | ((r / n) << 16) | ((gg / n) << 8) | (b / n);
            }
        }
    }

    // --- output -------------------------------------------------------------

    public BufferedImage image() {
        return image;
    }

    /** Writes the canvas to {@code file}, creating parent directories. */
    public void save(File file) throws Exception {
        g.dispose();
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        ImageIO.write(image, "PNG", file);
    }
}

package com.acrp.phone.client.ui;

/**
 * Everything the phone UI is allowed to draw with.
 *
 * <p>Screens are written against this interface and nothing else, so none of them
 * import Minecraft. Two implementations back it: one that issues OpenGL calls in
 * game, and one that paints into an image so the real UI code can be rendered to a
 * PNG and inspected without launching anything.
 *
 * <p>That second implementation is the only way this project gets looked at right
 * now — Forge's maven is unreachable from the build environment, so the in-game
 * path cannot even be compiled locally. Keeping the drawing surface this narrow is
 * what makes the UI verifiable anyway.
 *
 * <p>Coordinates are in the phone's logical space: 390 x 844, origin top-left,
 * matching the frame the design is specified in. Colours are packed ARGB.
 */
public interface PhoneCanvas {

    /** Logical width of the phone screen. */
    int WIDTH = 390;

    /** Logical height of the phone screen. */
    int HEIGHT = 844;

    /** Fills the whole canvas. */
    void clear(int colour);

    /** Axis-aligned rectangle. */
    void fillRect(float x, float y, float w, float h, int colour);

    /** Vertical two-stop gradient. */
    void fillGradient(float x, float y, float w, float h, int top, int bottom);

    /** Rectangle with circular corners — for cards, sheets and bubbles. */
    void fillRoundRect(float x, float y, float w, float h, float radius, int colour);

    /**
     * Rectangle with a <em>superellipse</em> outline — the shape iOS uses for app
     * icons and the device frame.
     *
     * <p>Not interchangeable with {@link #fillRoundRect}: a circular corner welds a
     * straight edge onto an arc, and the curvature jumps at the seam. A superellipse
     * is one continuous curve, and the difference is visible at icon size.
     */
    void fillSquircle(float x, float y, float w, float h, int top, int bottom);

    /** Filled circle, centred on {@code cx},{@code cy}. */
    void fillCircle(float cx, float cy, float radius, int colour);

    /**
     * Filled polygon from interleaved {@code x0, y0, x1, y1, …} points.
     *
     * <p>Icon glyphs that are not just circles and rounded rectangles are built from
     * this, and so is any squircle outline the caller wants to fill itself.
     */
    void fillPath(float[] points, int colour);

    /** Stroked line. */
    void drawLine(float x1, float y1, float x2, float y2, float width, int colour);

    /**
     * Draws text with its <em>start</em> edge at {@code x} and the top of its line
     * box at {@code y}.
     *
     * <p>"Start" depends on direction: an Arabic string is laid out from the right,
     * so it grows leftwards from {@code x}. Use {@link #textWidth} to position.
     */
    void drawText(String text, float x, float y, float size, int colour, TextAlign align);

    /** Advance width of {@code text} at {@code size}, in logical pixels. */
    float textWidth(String text, float size);

    /** Height of one line at {@code size}. */
    float lineHeight(float size);

    /** Breaks {@code text} into lines fitting {@code maxWidth}. */
    java.util.List<String> wrapText(String text, float size, float maxWidth);

    /**
     * Restricts drawing to a rectangle until the matching {@link #popClip}.
     * Nesting intersects with whatever is already clipped.
     */
    void pushClip(float x, float y, float w, float h);

    void popClip();

    /**
     * Approximates the frosted panels iOS uses for the dock, notifications and the
     * passcode sheet.
     *
     * <p>A real gaussian blur of the framebuffer is not worth its cost here, so
     * implementations may fake it — what matters is that the call site expresses
     * intent and both backends agree on the result.
     */
    void blurPanel(float x, float y, float w, float h, float radius, int tint);

    /** Where {@link #drawText} anchors relative to {@code x}. */
    enum TextAlign {
        /** {@code x} is the leading edge — right in RTL, left in LTR. */
        START,
        /** {@code x} is the centre of the string. */
        CENTER,
        /** {@code x} is the trailing edge. */
        END
    }
}

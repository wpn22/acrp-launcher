package com.acrp.phone.client.ui;

/**
 * App icons, drawn rather than shipped.
 *
 * <p>An iOS icon is a gradient plate with a solid white glyph on it, and both halves
 * are cheap to draw: the plate is a {@link Squircle} fill, the glyph a handful of
 * circles, rounded rectangles and polygons. Generating them keeps the jar small — no
 * PNG per app per resolution — and they stay sharp at any size.
 *
 * <p>The plate must be a squircle. A rounded rectangle at this size reads as
 * subtly wrong next to anything genuinely iOS.
 */
public enum AppIcon {

    PHONE(0xFF6EE787, 0xFF22C043),
    MESSAGES(0xFF6EE787, 0xFF22C043),
    CONTACTS(0xFFD9BE8A, 0xFF93724A),
    CAMERA(0xFFA9A9AE, 0xFF4A4A4F),
    CLOCK(0xFF2E2E33, 0xFF0A0A0C),
    WEATHER(0xFF63B8FF, 0xFF0C63D6),
    CALCULATOR(0xFF3A3A3F, 0xFF141416),
    NOTES(0xFFFFE9A3, 0xFFF5C21B),
    STORE(0xFF4FB4FF, 0xFF0A62E0),
    DARKCHAT(0xFF5A5A66, 0xFF17171C),
    WALLET(0xFF3ED47E, 0xFF12924D),
    SETTINGS(0xFFC6C9D1, 0xFF7A7D86);

    private static final int WHITE = 0xFFFFFFFF;

    private final int top;
    private final int bottom;

    AppIcon(int top, int bottom) {
        this.top = top;
        this.bottom = bottom;
    }

    /** Draws the icon at {@code size} with its top-left corner at {@code x},{@code y}. */
    public void draw(PhoneCanvas c, float x, float y, float size) {
        c.fillSquircle(x, y, size, size, top, bottom);
        // Glyphs are authored on a 24-unit grid, like the source SVGs.
        float u = size / 24f;
        int ink = this == NOTES ? 0xFF4A3B00 : WHITE;
        glyph(c, x, y, u, ink);
    }

    private void glyph(PhoneCanvas c, float x, float y, float u, int ink) {
        switch (this) {
            case PHONE:
                // Handset: a thick bar swept diagonally, with the earpiece and
                // mouthpiece as pads at each end.
                c.fillPath(new float[]{
                        x + 6.5f * u, y + 4.4f * u, x + 9.6f * u, y + 3.6f * u,
                        x + 11.6f * u, y + 8.2f * u, x + 8.9f * u, y + 10.2f * u,
                        x + 13.8f * u, y + 15.1f * u, x + 15.8f * u, y + 12.4f * u,
                        x + 20.4f * u, y + 14.4f * u, x + 19.6f * u, y + 17.5f * u,
                        x + 17.4f * u, y + 19.8f * u, x + 12.2f * u, y + 18.4f * u,
                        x + 5.6f * u, y + 11.8f * u, x + 4.2f * u, y + 6.6f * u
                }, ink);
                break;

            case MESSAGES: {
                c.fillRoundRect(x + 3.6f * u, y + 4.6f * u, 16.8f * u, 12.4f * u, 6f * u, ink);
                c.fillPath(new float[]{
                        x + 6.4f * u, y + 15.4f * u,
                        x + 10.2f * u, y + 15.4f * u,
                        x + 5.2f * u, y + 20.2f * u
                }, ink);
                break;
            }

            case CONTACTS:
                c.fillCircle(x + 12 * u, y + 9 * u, 3.4f * u, ink);
                c.fillRoundRect(x + 5.4f * u, y + 13.4f * u, 13.2f * u, 7f * u, 3.5f * u, ink);
                break;

            case CAMERA:
                c.fillRoundRect(x + 4f * u, y + 6.5f * u, 16f * u, 12f * u, 3f * u, ink);
                c.fillRoundRect(x + 9f * u, y + 4.6f * u, 6f * u, 2.6f * u, 1.2f * u, ink);
                c.fillCircle(x + 12 * u, y + 12.5f * u, 4.3f * u, blend(top, bottom));
                c.fillCircle(x + 12 * u, y + 12.5f * u, 2.5f * u, ink);
                break;

            case CLOCK:
                c.fillCircle(x + 12 * u, y + 12 * u, 8.6f * u, ink);
                c.fillCircle(x + 12 * u, y + 12 * u, 7.4f * u, 0xFF101014);
                c.drawLine(x + 12 * u, y + 12 * u, x + 12 * u, y + 6.6f * u, 1.1f * u, ink);
                c.drawLine(x + 12 * u, y + 12 * u, x + 15.6f * u, y + 13.6f * u, 1.1f * u, ink);
                break;

            case WEATHER:
                c.fillCircle(x + 9.4f * u, y + 8.8f * u, 3.5f * u, 0xFFFFD75E);
                c.fillCircle(x + 9.2f * u, y + 14.4f * u, 3.6f * u, ink);
                c.fillCircle(x + 13.4f * u, y + 12.6f * u, 4.4f * u, ink);
                c.fillRoundRect(x + 6f * u, y + 14f * u, 12f * u, 4.6f * u, 2.3f * u, ink);
                break;

            case CALCULATOR:
                c.fillRoundRect(x + 5f * u, y + 3.6f * u, 14f * u, 16.8f * u, 2.6f * u, ink);
                c.fillRoundRect(x + 6.8f * u, y + 5.6f * u, 10.4f * u, 3.4f * u, 1f * u, 0xFF2A2A2E);
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 3; col++) {
                        c.fillCircle(x + (8f + col * 4f) * u, y + (13f + row * 4f) * u,
                                1.3f * u, col == 2 ? 0xFFFF9F0A : 0xFF2A2A2E);
                    }
                }
                break;

            case NOTES:
                c.fillRect(x + 5.6f * u, y + 4f * u, 12.8f * u, 16f * u, 0xFFFFFFFF);
                c.fillRect(x + 5.6f * u, y + 4f * u, 12.8f * u, 3f * u, 0xFFF0C64A);
                for (int i = 0; i < 3; i++) {
                    c.fillRect(x + 7.6f * u, y + (9.4f + i * 2.6f) * u,
                            (i == 2 ? 5f : 8.8f) * u, 1f * u, ink);
                }
                break;

            case STORE:
                c.fillPath(new float[]{
                        x + 12 * u, y + 4.6f * u,
                        x + 18.4f * u, y + 15.6f * u,
                        x + 5.6f * u, y + 15.6f * u
                }, ink);
                c.fillRoundRect(x + 6.6f * u, y + 17f * u, 10.8f * u, 2.2f * u, 1.1f * u, ink);
                break;

            case DARKCHAT:
                c.fillRoundRect(x + 6.4f * u, y + 10.4f * u, 11.2f * u, 9.2f * u, 2.2f * u, ink);
                c.fillCircle(x + 12 * u, y + 9.2f * u, 3.7f * u, ink);
                c.fillCircle(x + 12 * u, y + 9.2f * u, 2.1f * u, blend(top, bottom));
                c.fillRect(x + 9.9f * u, y + 9.2f * u, 4.2f * u, 2.4f * u, blend(top, bottom));
                break;

            case WALLET:
                c.fillRoundRect(x + 4f * u, y + 6.6f * u, 16f * u, 11.6f * u, 2.6f * u, ink);
                c.fillRect(x + 4f * u, y + 10.6f * u, 16f * u, 2.4f * u, blend(top, bottom));
                c.fillRoundRect(x + 14.4f * u, y + 13.8f * u, 4.6f * u, 3f * u, 1.5f * u,
                        blend(top, bottom));
                break;

            case SETTINGS:
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    c.fillCircle(x + 12 * u + (float) Math.cos(a) * 6.4f * u,
                            y + 12 * u + (float) Math.sin(a) * 6.4f * u, 1.9f * u, ink);
                }
                c.fillCircle(x + 12 * u, y + 12 * u, 5.6f * u, ink);
                c.fillCircle(x + 12 * u, y + 12 * u, 2.4f * u, blend(top, bottom));
                break;

            default:
                break;
        }
    }

    /** Midpoint of the plate gradient — used to punch holes back to the plate. */
    private static int blend(int a, int b) {
        int alpha = (((a >>> 24) + (b >>> 24)) / 2) << 24;
        int red = ((((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2) << 16;
        int green = ((((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2) << 8;
        int blue = (((a & 0xFF) + (b & 0xFF)) / 2);
        return alpha | red | green | blue;
    }
}

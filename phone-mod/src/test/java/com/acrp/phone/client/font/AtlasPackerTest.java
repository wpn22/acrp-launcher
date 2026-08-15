package com.acrp.phone.client.font;

import org.junit.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The packer decides where each glyph's pixels land. Overlaps do not throw — they
 * silently corrupt letters — so the invariants are asserted rather than eyeballed.
 */
public class AtlasPackerTest {

    @Test
    public void firstSlotSitsAtThePadding() {
        AtlasPacker packer = new AtlasPacker();
        int slot = packer.reserve(10, 12);
        assertEquals(AtlasPacker.PADDING, AtlasPacker.slotX(slot));
        assertEquals(AtlasPacker.PADDING, AtlasPacker.slotY(slot));
    }

    @Test
    public void slotsAdvanceAlongTheShelf() {
        AtlasPacker packer = new AtlasPacker();
        int first = packer.reserve(10, 12);
        int second = packer.reserve(10, 12);
        assertEquals(AtlasPacker.slotY(first), AtlasPacker.slotY(second));
        assertTrue(AtlasPacker.slotX(second) > AtlasPacker.slotX(first));
    }

    /** The regression: a glyph that does not fit the shelf must drop, not overflow. */
    @Test
    public void aGlyphTooWideForTheShelfDropsToTheNextOne() {
        AtlasPacker packer = new AtlasPacker();
        // Fill most of the first shelf.
        packer.reserve(200, 20);
        int slot = packer.reserve(100, 20);

        assertTrue("glyph must drop to a new shelf",
                AtlasPacker.slotY(slot) > AtlasPacker.PADDING);
        assertEquals("a dropped glyph restarts at the left",
                AtlasPacker.PADDING, AtlasPacker.slotX(slot));
        assertTrue("glyph must stay inside the page",
                AtlasPacker.slotX(slot) + 100 <= AtlasPacker.PAGE_SIZE);
    }

    @Test
    public void everyReservedSlotStaysInsideThePage() {
        AtlasPacker packer = new AtlasPacker();
        Random random = new Random(7);
        for (int i = 0; i < 400; i++) {
            int w = 4 + random.nextInt(40);
            int h = 4 + random.nextInt(40);
            if (!packer.canFit(w, h)) {
                continue;
            }
            int slot = packer.reserve(w, h);
            int x = AtlasPacker.slotX(slot);
            int y = AtlasPacker.slotY(slot);
            assertTrue("x underflow", x >= 0);
            assertTrue("y underflow", y >= 0);
            assertTrue("x overflow: " + (x + w), x + w <= AtlasPacker.PAGE_SIZE);
            assertTrue("y overflow: " + (y + h), y + h <= AtlasPacker.PAGE_SIZE);
        }
    }

    @Test
    public void reservedSlotsNeverOverlap() {
        AtlasPacker packer = new AtlasPacker();
        Random random = new Random(99);
        List<Rectangle> placed = new ArrayList<Rectangle>();

        for (int i = 0; i < 300; i++) {
            int w = 3 + random.nextInt(30);
            int h = 3 + random.nextInt(30);
            if (!packer.canFit(w, h)) {
                continue;
            }
            int slot = packer.reserve(w, h);
            Rectangle rect = new Rectangle(
                    AtlasPacker.slotX(slot), AtlasPacker.slotY(slot), w, h);
            for (Rectangle other : placed) {
                assertFalse("glyphs overlap: " + rect + " vs " + other,
                        rect.intersects(other));
            }
            placed.add(rect);
        }
        assertTrue("expected a decent number of placements", placed.size() > 50);
    }

    @Test
    public void canFitEventuallyReportsFull() {
        AtlasPacker packer = new AtlasPacker();
        int placements = 0;
        while (packer.canFit(60, 60)) {
            packer.reserve(60, 60);
            placements++;
            if (placements > 1000) {
                break; // canFit never went false
            }
        }
        assertTrue("packer should fill up", placements > 0);
        assertTrue("packer never reported full", placements <= 1000);
        assertFalse(packer.canFit(60, 60));
    }

    @Test
    public void oversizedGlyphsAreRejected() {
        AtlasPacker packer = new AtlasPacker();
        assertFalse(packer.canFit(AtlasPacker.PAGE_SIZE, 10));
        assertFalse(packer.canFit(10, AtlasPacker.PAGE_SIZE));
        assertFalse(packer.canFit(AtlasPacker.PAGE_SIZE * 2, AtlasPacker.PAGE_SIZE * 2));
    }
}

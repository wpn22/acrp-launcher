package com.acrp.phone.core;

import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PhoneNumberTest {

    @Test
    public void acceptsAWellFormedNumber() {
        assertEquals("0512345678", PhoneNumber.of("0512345678").digits());
    }

    @Test
    public void toleratesSpacesAndDashes() {
        assertEquals("0512345678", PhoneNumber.of("051 234-5678").digits());
    }

    /** Players on an Arabic keypad type ٠-٩; those must match stored ASCII digits. */
    @Test
    public void foldsArabicIndicDigits() {
        assertEquals("0512345678", PhoneNumber.of("٠٥١٢٣٤٥٦٧٨").digits());
    }

    @Test
    public void foldsExtendedArabicIndicDigits() {
        assertEquals("0512345678", PhoneNumber.of("۰۵۱۲۳۴۵۶۷۸").digits());
    }

    @Test
    public void rejectsWrongPrefix() {
        assertNull(PhoneNumber.parseOrNull("0612345678"));
    }

    @Test
    public void rejectsWrongLength() {
        assertNull(PhoneNumber.parseOrNull("051234567"));
        assertNull(PhoneNumber.parseOrNull("05123456789"));
    }

    @Test
    public void rejectsNonDigits() {
        assertNull(PhoneNumber.parseOrNull("05123456ab"));
    }

    @Test
    public void rejectsNull() {
        assertNull(PhoneNumber.parseOrNull(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void ofThrowsOnMalformed() {
        PhoneNumber.of("nope");
    }

    @Test
    public void formatsInGroups() {
        assertEquals("051 234 5678", PhoneNumber.of("0512345678").formatted());
    }

    @Test
    public void generatedNumbersAreAlwaysValid() {
        Random random = new Random(1234);
        for (int i = 0; i < 500; i++) {
            PhoneNumber n = PhoneNumber.random(random);
            assertNotNull(PhoneNumber.parseOrNull(n.digits()));
            assertTrue(n.digits().startsWith(PhoneNumber.PREFIX));
            assertEquals(PhoneNumber.LENGTH, n.digits().length());
        }
    }

    @Test
    public void generatedNumbersSpreadOut() {
        Random random = new Random(99);
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < 200; i++) {
            seen.add(PhoneNumber.random(random).digits());
        }
        // Collisions are possible and the registry handles them, but 200 draws
        // from 10^8 should essentially never repeat.
        assertTrue("generator looks degenerate: " + seen.size(), seen.size() > 190);
    }

    @Test
    public void equalityIsByDigits() {
        assertEquals(PhoneNumber.of("051 234 5678"), PhoneNumber.of("0512345678"));
        assertEquals(PhoneNumber.of("0512345678").hashCode(),
                PhoneNumber.of("051-234-5678").hashCode());
        assertFalse(PhoneNumber.of("0512345678").equals(PhoneNumber.of("0512345679")));
    }
}

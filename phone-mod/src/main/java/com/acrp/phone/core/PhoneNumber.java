package com.acrp.phone.core;

import java.util.Random;

/**
 * A subscriber number, stored as plain digits and formatted for display.
 *
 * <p>Deliberately free of Minecraft types: numbers are validated, generated and
 * formatted the same way on both sides, and the rules are unit tested.
 *
 * <p>Display is grouped {@code 05X XXX XXXX}. The groups always read left to
 * right even inside Arabic text — the text engine's bidi pass is what guarantees
 * that, and it is covered by its own regression tests.
 */
public final class PhoneNumber {

    /** Every ACRP number starts with this. */
    public static final String PREFIX = "05";

    /** Total digit count including the prefix. */
    public static final int LENGTH = 10;

    private final String digits;

    private PhoneNumber(String digits) {
        this.digits = digits;
    }

    /**
     * Parses a number, tolerating spaces and dashes.
     *
     * @throws IllegalArgumentException if it is not a well-formed ACRP number
     */
    public static PhoneNumber of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("number is null");
        }
        String cleaned = strip(raw);
        if (!isValid(cleaned)) {
            throw new IllegalArgumentException("malformed phone number: " + raw);
        }
        return new PhoneNumber(cleaned);
    }

    /** Parses a number, returning {@code null} instead of throwing. */
    public static PhoneNumber parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = strip(raw);
        return isValid(cleaned) ? new PhoneNumber(cleaned) : null;
    }

    /** Allocates a fresh random number. Callers must check it is not already taken. */
    public static PhoneNumber random(Random random) {
        StringBuilder sb = new StringBuilder(LENGTH).append(PREFIX);
        while (sb.length() < LENGTH) {
            sb.append(random.nextInt(10));
        }
        return new PhoneNumber(sb.toString());
    }

    /** True if {@code cleaned} is exactly {@link #LENGTH} digits behind {@link #PREFIX}. */
    public static boolean isValid(String cleaned) {
        if (cleaned == null || cleaned.length() != LENGTH || !cleaned.startsWith(PREFIX)) {
            return false;
        }
        for (int i = 0; i < cleaned.length(); i++) {
            if (cleaned.charAt(i) < '0' || cleaned.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * Removes spaces and dashes, and folds Arabic-Indic digits (٠-٩ and the
     * Extended set ۰-۹) to ASCII so a number typed on an Arabic keypad still
     * matches one stored from a Latin one.
     */
    private static String strip(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '-' || c == ' ') {
                continue;
            }
            if (c >= '٠' && c <= '٩') {          // Arabic-Indic
                sb.append((char) ('0' + (c - '٠')));
            } else if (c >= '۰' && c <= '۹') {   // Extended Arabic-Indic
                sb.append((char) ('0' + (c - '۰')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** The raw digits, e.g. {@code "0512345678"}. This is the storage key. */
    public String digits() {
        return digits;
    }

    /** Grouped for display, e.g. {@code "051 234 5678"}. */
    public String formatted() {
        return digits.substring(0, 3) + ' ' + digits.substring(3, 6) + ' ' + digits.substring(6);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PhoneNumber)) {
            return false;
        }
        return digits.equals(((PhoneNumber) o).digits);
    }

    @Override
    public int hashCode() {
        return digits.hashCode();
    }

    @Override
    public String toString() {
        return digits;
    }
}

package com.acrp.phone.client.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Breaks text into lines that fit a width.
 *
 * <p>Wrapping happens on the <em>logical</em> string, before shaping — each
 * resulting line is shaped on its own afterwards. Doing it the other way round
 * (shaping first, then cutting the glyph list) would slice through the middle of a
 * bidi run and split joined Arabic letters apart.
 */
public final class TextWrapper {

    private final TextShaper shaper;

    public TextWrapper(TextShaper shaper) {
        this.shaper = shaper;
    }

    /**
     * Wraps {@code text} to {@code maxWidth}, honouring existing newlines.
     *
     * @return one entry per visual line; never {@code null}, may be empty
     */
    public List<String> wrap(String text, float maxWidth) {
        List<String> lines = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        for (String paragraph : text.split("\n", -1)) {
            wrapParagraph(paragraph, maxWidth, lines);
        }
        return lines;
    }

    private void wrapParagraph(String paragraph, float maxWidth, List<String> out) {
        if (paragraph.isEmpty()) {
            out.add("");
            return;
        }
        if (shaper.width(paragraph) <= maxWidth) {
            out.add(paragraph);
            return;
        }

        StringBuilder line = new StringBuilder();
        int i = 0;
        int length = paragraph.length();

        while (i < length) {
            int wordEnd = nextBreak(paragraph, i);
            String word = paragraph.substring(i, wordEnd);

            String candidate = line.length() == 0 ? word : line + word;
            if (shaper.width(candidate) <= maxWidth) {
                line.append(word);
                i = wordEnd;
                continue;
            }

            if (line.length() > 0) {
                // Flush and retry this word on a fresh line.
                out.add(trimTrailingSpace(line.toString()));
                line.setLength(0);
                // A wrapped line never starts with the space that caused the break.
                while (i < length && paragraph.charAt(i) == ' ') {
                    i++;
                }
                continue;
            }

            // A single word wider than the box: break it mid-word by characters.
            int fits = charsThatFit(word, maxWidth);
            out.add(word.substring(0, fits));
            i += fits;
        }

        if (line.length() > 0) {
            out.add(trimTrailingSpace(line.toString()));
        }
    }

    /** End index of the token starting at {@code from}, including trailing spaces. */
    private int nextBreak(String text, int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) != ' ') {
            i++;
        }
        while (i < text.length() && text.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    /** Largest prefix of {@code word} that fits, always at least one character. */
    private int charsThatFit(String word, float maxWidth) {
        int fits = 0;
        for (int n = 1; n <= word.length(); n++) {
            if (shaper.width(word.substring(0, n)) > maxWidth) {
                break;
            }
            fits = n;
        }
        return Math.max(1, fits);
    }

    private String trimTrailingSpace(String line) {
        int end = line.length();
        while (end > 0 && line.charAt(end - 1) == ' ') {
            end--;
        }
        return line.substring(0, end);
    }
}

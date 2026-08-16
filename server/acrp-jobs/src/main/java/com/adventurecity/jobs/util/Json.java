package com.adventurecity.jobs.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON writer and reader.
 *
 * <p>Written by hand rather than pulled in as a dependency: the plugin targets Java 8 on a
 * 1.12.2 server, and shading a JSON library for a handful of flat objects is not worth the
 * jar size or the classloader risk on a hybrid server.</p>
 */
public final class Json {

    private Json() {
    }

    // ---------------------------------------------------------------- writing

    /** Fluent builder for a flat JSON object. */
    public static final class Writer {

        private final StringBuilder builder = new StringBuilder("{");
        private boolean first = true;

        public Writer value(String key, String value) {
            return raw(key, value == null ? "null" : quote(value));
        }

        public Writer value(String key, boolean value) {
            return raw(key, Boolean.toString(value));
        }

        public Writer value(String key, long value) {
            return raw(key, Long.toString(value));
        }

        /** Writes an array of strings. */
        public Writer array(String key, Iterable<String> values) {
            StringBuilder array = new StringBuilder("[");
            boolean firstItem = true;
            for (String value : values) {
                if (!firstItem) {
                    array.append(',');
                }
                array.append(quote(value));
                firstItem = false;
            }
            return raw(key, array.append(']').toString());
        }

        /** Writes a nested object whose values are all strings. */
        public Writer object(String key, Map<String, String> values) {
            Writer nested = new Writer();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                nested.value(entry.getKey(), entry.getValue());
            }
            return raw(key, nested.build());
        }

        /** Writes a pre-rendered JSON fragment. */
        public Writer raw(String key, String json) {
            if (!first) {
                builder.append(',');
            }
            builder.append(quote(key)).append(':').append(json);
            first = false;
            return this;
        }

        public String build() {
            return builder.toString() + "}";
        }
    }

    public static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }

    // ---------------------------------------------------------------- reading

    /**
     * Parses a JSON object.
     *
     * @return the parsed map, never null - a malformed document yields an empty map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String input) {
        if (input == null || input.isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            Object parsed = new Parser(input).parseValue();
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
        } catch (RuntimeException ex) {
            // Fall through - callers treat an empty map as "no usable answer".
        }
        return new LinkedHashMap<String, Object>();
    }

    /** Reads a string field, or {@code fallback} when absent or of another type. */
    public static String string(Map<String, Object> object, String key, String fallback) {
        Object value = object.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    public static boolean bool(Map<String, Object> object, String key, boolean fallback) {
        Object value = object.get(key);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    private static final class Parser {

        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return parseNumber();
            }
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            pos++; // {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (peek() != ':') {
                    throw new IllegalStateException("expected ':' at " + pos);
                }
                pos++;
                map.put(key, parseValue());
                skipWhitespace();
                char c = peek();
                pos++;
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new IllegalStateException("expected ',' or '}' at " + pos);
                }
            }
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<Object>();
            pos++; // [
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char c = peek();
                pos++;
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IllegalStateException("expected ',' or ']' at " + pos);
                }
            }
        }

        private String parseString() {
            if (peek() != '"') {
                throw new IllegalStateException("expected '\"' at " + pos);
            }
            pos++;
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = next();
                switch (escape) {
                    case '"':
                        out.append('"');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    case '/':
                        out.append('/');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'u':
                        if (pos + 4 > src.length()) {
                            throw new IllegalStateException("truncated \\u escape");
                        }
                        out.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw new IllegalStateException("bad escape \\" + escape);
                }
            }
        }

        private Double parseNumber() {
            int start = pos;
            while (pos < src.length() && "+-.eE0123456789".indexOf(src.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalStateException("expected a value at " + pos);
            }
            return Double.valueOf(src.substring(start, pos));
        }

        private void expect(String literal) {
            if (!src.startsWith(literal, pos)) {
                throw new IllegalStateException("expected " + literal + " at " + pos);
            }
            pos += literal.length();
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= src.length()) {
                throw new IllegalStateException("unexpected end of input");
            }
            return src.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }
    }
}

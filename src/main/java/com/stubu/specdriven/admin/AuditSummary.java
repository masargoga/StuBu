package com.stubu.specdriven.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the JSON documents kept as old and new values into something to read: a one-line summary of what changed
 * and a line-per-field rendering. The documents are flat objects of strings, numbers, booleans and null; anything
 * else is shown as it is.
 */
public final class AuditSummary {

    private static final int MAX_SUMMARY = 140;

    private AuditSummary() {
    }

    /** What changed, in one line: the changed fields of an update, or the stored fields otherwise. */
    public static String of(String oldValues, String newValues) {
        Optional<Map<String, String>> before = parse(oldValues);
        Optional<Map<String, String>> after = parse(newValues);
        String summary;
        if (before.isPresent() && after.isPresent()) {
            List<String> changes = new ArrayList<>();
            after.get().forEach((key, value) -> {
                String old = before.get().get(key);
                if (!value.equals(old)) {
                    changes.add(key + ": " + old + " → " + value);
                }
            });
            summary = String.join(", ", changes);
        } else if (after.isPresent()) {
            summary = String.join(", ", after.get().entrySet().stream().map(e -> e.getKey() + ": " + e.getValue())
                    .toList());
        } else if (before.isPresent()) {
            summary = String.join(", ", before.get().entrySet().stream().map(e -> e.getKey() + ": " + e.getValue())
                    .toList());
        } else {
            summary = newValues != null ? newValues : oldValues == null ? "" : oldValues;
        }
        return summary.length() > MAX_SUMMARY ? summary.substring(0, MAX_SUMMARY - 1) + "…" : summary;
    }

    /** One line per field ("name: value"); unreadable documents are returned unchanged, {@code null} as empty. */
    public static String lines(String json) {
        if (json == null) {
            return "";
        }
        return parse(json).map(values -> String.join("\n", values.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue()).toList())).orElse(json);
    }

    /** Parses a flat JSON object; empty if the text is not one. Values are given without quotes. */
    static Optional<Map<String, String>> parse(String json) {
        if (json == null) {
            return Optional.empty();
        }
        String text = json.strip();
        if (!text.startsWith("{") || !text.endsWith("}")) {
            return Optional.empty();
        }
        Map<String, String> values = new LinkedHashMap<>();
        int[] position = { 1 };
        try {
            skipSpaces(text, position);
            if (text.charAt(position[0]) == '}') {
                return Optional.of(values);
            }
            while (true) {
                skipSpaces(text, position);
                String key = readString(text, position);
                skipSpaces(text, position);
                expect(text, position, ':');
                skipSpaces(text, position);
                values.put(key, readValue(text, position));
                skipSpaces(text, position);
                char next = text.charAt(position[0]++);
                if (next == '}') {
                    return position[0] == text.length() ? Optional.of(values) : Optional.empty();
                }
                if (next != ',') {
                    return Optional.empty();
                }
            }
        } catch (RuntimeException notFlat) {
            return Optional.empty();
        }
    }

    private static String readValue(String text, int[] position) {
        char c = text.charAt(position[0]);
        if (c == '"') {
            return readString(text, position);
        }
        if (c == '{' || c == '[') {
            throw new IllegalArgumentException("not flat");
        }
        int start = position[0];
        while (position[0] < text.length() && ",}".indexOf(text.charAt(position[0])) < 0) {
            position[0]++;
        }
        return text.substring(start, position[0]).strip();
    }

    private static String readString(String text, int[] position) {
        expect(text, position, '"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = text.charAt(position[0]++);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                char escaped = text.charAt(position[0]++);
                switch (escaped) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(text.substring(position[0], position[0] + 4), 16));
                        position[0] += 4;
                    }
                    default -> out.append(escaped);
                }
            } else {
                out.append(c);
            }
        }
    }

    private static void expect(String text, int[] position, char expected) {
        if (text.charAt(position[0]++) != expected) {
            throw new IllegalArgumentException("expected " + expected);
        }
    }

    private static void skipSpaces(String text, int[] position) {
        while (position[0] < text.length() && Character.isWhitespace(text.charAt(position[0]))) {
            position[0]++;
        }
    }
}

package com.stubu.specdriven.audit;

import java.util.Map;
import java.util.stream.Collectors;

/** Builds the small JSON documents kept as old and new values in the audit log, with correct escaping. */
public final class AuditJson {

    private AuditJson() {
    }

    /** A JSON object with the entries in their iteration order; values are strings, numbers, booleans or null. */
    public static String object(Map<String, ?> values) {
        return values.entrySet().stream().map(entry -> quote(entry.getKey()) + ":" + value(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return quote(value.toString());
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}

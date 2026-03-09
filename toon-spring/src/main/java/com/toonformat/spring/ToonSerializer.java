package com.toonformat.spring;

import com.toonformat.spring.annotation.ToonField;
import com.toonformat.spring.annotation.ToonIgnore;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Serializes Java objects into TOON (Token-Oriented Object Notation) format.
 * <p>
 * Supports objects, maps, collections, arrays, and all primitive/wrapper types.
 * Handles nested structures with proper indentation and selects the optimal
 * array encoding (inline, tabular, or list) automatically.
 */
public class ToonSerializer {

    private static final Pattern SAFE_KEY_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");
    private static final Pattern LOOKS_LIKE_NUMBER = Pattern.compile("^-?(\\d+\\.?\\d*|\\d*\\.\\d+)([eE][+-]?\\d+)?$");
    private static final Pattern LOOKS_LIKE_ARRAY_HEADER = Pattern.compile("^\\[\\d+.*");
    private static final Pattern LOOKS_LIKE_FIELD_HEADER = Pattern.compile("^\\{.*\\}$");

    private final ToonOptions options;

    public ToonSerializer() {
        this(new ToonOptions());
    }

    public ToonSerializer(ToonOptions options) {
        this.options = options;
    }

    /**
     * Serializes any Java value to a TOON string.
     */
    public String serialize(Object value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        serializeValue(value, sb, 0, null);
        return sb.toString();
    }

    // ── Internal dispatch ───────────────────────────────────────────────

    private void serializeValue(Object value, StringBuilder sb, int depth, String parentKey) {
        if (value == null) {
            sb.append("null");
            return;
        }

        if (value instanceof Map<?, ?> map) {
            serializeMap(map, sb, depth);
        } else if (value instanceof Collection<?> col) {
            serializeArray(col.stream().toList(), sb, depth, parentKey);
        } else if (value.getClass().isArray()) {
            serializeArray(arrayToList(value), sb, depth, parentKey);
        } else if (isPrimitive(value)) {
            sb.append(formatPrimitive(value, options.getDelimiter()));
        } else {
            // Treat as a bean / POJO
            serializeObject(value, sb, depth);
        }
    }

    // ── Object / Bean ───────────────────────────────────────────────────

    private void serializeObject(Object obj, StringBuilder sb, int depth) {
        Map<String, Object> fields = extractFields(obj);
        serializeMap(fields, sb, depth);
    }

    @SuppressWarnings("unchecked")
    private void serializeMap(Map<?, ?> map, StringBuilder sb, int depth) {
        String indent = indent(depth);
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = encodeKey(String.valueOf(entry.getKey()));
            Object val = entry.getValue();

            if (!first) {
                sb.append('\n');
            }
            first = false;

            if (val == null) {
                sb.append(indent).append(key).append(": null");
            } else if (val instanceof Map<?, ?> nested) {
                if (nested.isEmpty()) {
                    sb.append(indent).append(key).append(":");
                } else {
                    sb.append(indent).append(key).append(":\n");
                    serializeMap(nested, sb, depth + 1);
                }
            } else if (val instanceof Collection<?> col) {
                serializeArrayField(key, col.stream().toList(), sb, depth);
            } else if (val.getClass().isArray()) {
                serializeArrayField(key, arrayToList(val), sb, depth);
            } else if (isPrimitive(val)) {
                sb.append(indent).append(key).append(": ").append(formatPrimitive(val, options.getDelimiter()));
            } else {
                // Nested object
                sb.append(indent).append(key).append(":\n");
                serializeObject(val, sb, depth + 1);
            }
        }
    }

    // ── Arrays ──────────────────────────────────────────────────────────

    private void serializeArrayField(String key, List<?> items, StringBuilder sb, int depth) {
        String indent = indent(depth);
        char delim = options.getDelimiter().getCharacter();
        String delimSuffix = options.getDelimiter().getBracketSuffix();
        String lengthPrefix = options.isLengthMarker() ? "#" : "";

        if (items.isEmpty()) {
            sb.append(indent).append(key).append("[").append(lengthPrefix).append("0").append(delimSuffix).append("]:");
            return;
        }

        // Check if all elements are primitives → inline format
        if (allPrimitives(items)) {
            sb.append(indent).append(key)
              .append("[").append(lengthPrefix).append(items.size()).append(delimSuffix).append("]: ");
            appendInlineValues(items, sb, delim);
            return;
        }

        // Check if all elements are uniform objects → tabular format
        List<String> tabularFields = getTabularFields(items);
        if (tabularFields != null) {
            sb.append(indent).append(key)
              .append("[").append(lengthPrefix).append(items.size()).append(delimSuffix).append("]")
              .append("{").append(String.join(String.valueOf(delim), tabularFields)).append("}:");
            String rowIndent = indent(depth + 1);
            for (Object item : items) {
                sb.append('\n').append(rowIndent);
                Map<String, Object> fields = extractFieldsGeneric(item);
                boolean firstField = true;
                for (String field : tabularFields) {
                    if (!firstField) sb.append(delim);
                    firstField = false;
                    Object fieldVal = fields.get(field);
                    sb.append(formatPrimitive(fieldVal, options.getDelimiter()));
                }
            }
            return;
        }

        // Fallback: list format
        sb.append(indent).append(key)
          .append("[").append(lengthPrefix).append(items.size()).append(delimSuffix).append("]:");
        String itemIndent = indent(depth + 1);
        for (Object item : items) {
            sb.append('\n');
            if (item == null || isPrimitive(item)) {
                sb.append(itemIndent).append("- ").append(formatPrimitive(item, options.getDelimiter()));
            } else if (item instanceof Collection<?> col) {
                // Array of arrays
                sb.append(itemIndent).append("- [").append(col.size()).append(delimSuffix).append("]: ");
                appendInlineValues(col.stream().toList(), sb, delim);
            } else if (item.getClass().isArray()) {
                List<?> sub = arrayToList(item);
                sb.append(itemIndent).append("- [").append(sub.size()).append(delimSuffix).append("]: ");
                appendInlineValues(sub, sb, delim);
            } else {
                // Object in list – first field on the marker line
                Map<String, Object> fields = extractFieldsGeneric(item);
                boolean firstEntry = true;
                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    String fieldKey = encodeKey(entry.getKey());
                    Object fieldVal = entry.getValue();
                    if (firstEntry) {
                        sb.append(itemIndent).append("- ").append(fieldKey).append(": ");
                        appendFieldValue(fieldVal, sb, depth + 2);
                        firstEntry = false;
                    } else {
                        sb.append('\n');
                        sb.append(indent(depth + 2)).append(fieldKey).append(": ");
                        appendFieldValue(fieldVal, sb, depth + 2);
                    }
                }
            }
        }
    }

    /**
     * Root-level array serialization (no parent key).
     */
    private void serializeArray(List<?> items, StringBuilder sb, int depth, String parentKey) {
        if (parentKey != null) {
            serializeArrayField(parentKey, items, sb, depth);
            return;
        }

        // Root array
        char delim = options.getDelimiter().getCharacter();
        String delimSuffix = options.getDelimiter().getBracketSuffix();
        String lengthPrefix = options.isLengthMarker() ? "#" : "";

        if (items.isEmpty()) {
            sb.append("[").append(lengthPrefix).append("0").append(delimSuffix).append("]:");
            return;
        }

        if (allPrimitives(items)) {
            sb.append("[").append(lengthPrefix).append(items.size()).append(delimSuffix).append("]: ");
            appendInlineValues(items, sb, delim);
            return;
        }

        List<String> tabularFields = getTabularFields(items);
        if (tabularFields != null) {
            sb.append("[").append(lengthPrefix).append(items.size()).append(delimSuffix).append("]")
              .append("{").append(String.join(String.valueOf(delim), tabularFields)).append("}:");
            String rowIndent = indent(1);
            for (Object item : items) {
                sb.append('\n').append(rowIndent);
                Map<String, Object> fields = extractFieldsGeneric(item);
                boolean first = true;
                for (String field : tabularFields) {
                    if (!first) sb.append(delim);
                    first = false;
                    sb.append(formatPrimitive(fields.get(field), options.getDelimiter()));
                }
            }
            return;
        }

        // List format
        sb.append("[").append(lengthPrefix).append(items.size()).append(delimSuffix).append("]:");
        String itemIndent = indent(1);
        for (Object item : items) {
            sb.append('\n');
            if (item == null || isPrimitive(item)) {
                sb.append(itemIndent).append("- ").append(formatPrimitive(item, options.getDelimiter()));
            } else {
                Map<String, Object> fields = extractFieldsGeneric(item);
                boolean firstEntry = true;
                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    String fieldKey = encodeKey(entry.getKey());
                    Object fieldVal = entry.getValue();
                    if (firstEntry) {
                        sb.append(itemIndent).append("- ").append(fieldKey).append(": ");
                        appendFieldValue(fieldVal, sb, 2);
                        firstEntry = false;
                    } else {
                        sb.append('\n');
                        sb.append(indent(2)).append(fieldKey).append(": ");
                        appendFieldValue(fieldVal, sb, 2);
                    }
                }
            }
        }
    }

    private void appendFieldValue(Object val, StringBuilder sb, int depth) {
        if (val == null || isPrimitive(val)) {
            sb.append(formatPrimitive(val, options.getDelimiter()));
        } else if (val instanceof Collection<?> col) {
            // Nested array inside a list-format object – use inline if primitives
            if (allPrimitives(col.stream().toList())) {
                char delim = options.getDelimiter().getCharacter();
                String delimSuffix = options.getDelimiter().getBracketSuffix();
                sb.setLength(sb.length()); // keep as-is
                // Overwrite the "key: " with the full array header
                // Actually we need to handle this differently
            }
            // For simplicity, serialize inline
            StringBuilder nested = new StringBuilder();
            serializeValue(val, nested, depth, null);
            sb.append(nested);
        } else {
            sb.append('\n');
            serializeObject(val, sb, depth);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void appendInlineValues(List<?> items, StringBuilder sb, char delim) {
        boolean first = true;
        for (Object item : items) {
            if (!first) sb.append(delim);
            first = false;
            sb.append(formatPrimitive(item, options.getDelimiter()));
        }
    }

    /**
     * Checks if all list items are uniform objects (same primitive-only field set).
     * Returns the shared field names, or null if not tabular-eligible.
     */
    private List<String> getTabularFields(List<?> items) {
        if (items.isEmpty()) return null;

        LinkedHashSet<String> referenceFields = null;
        for (Object item : items) {
            if (item == null || isPrimitive(item) || item instanceof Collection || item.getClass().isArray()) {
                return null;
            }
            Map<String, Object> fields = extractFieldsGeneric(item);
            if (fields.isEmpty()) return null;

            // Check all values are primitives
            for (Object val : fields.values()) {
                if (val != null && !isPrimitive(val)) {
                    return null;
                }
            }

            LinkedHashSet<String> keys = new LinkedHashSet<>(fields.keySet());
            if (referenceFields == null) {
                referenceFields = keys;
            } else if (!referenceFields.equals(keys)) {
                return null;
            }
        }
        return referenceFields != null ? new ArrayList<>(referenceFields) : null;
    }

    private boolean allPrimitives(List<?> items) {
        for (Object item : items) {
            if (item != null && !isPrimitive(item)) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFieldsGeneric(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return extractFields(obj);
    }

    /**
     * Extracts field name → value pairs from a POJO using reflection.
     * Respects {@link ToonField} and {@link ToonIgnore} annotations.
     */
    private Map<String, Object> extractFields(Object obj) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        Class<?> clazz = obj.getClass();

        // Walk up the hierarchy
        List<Field> allFields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            allFields.addAll(0, List.of(current.getDeclaredFields()));
            current = current.getSuperclass();
        }

        for (Field field : allFields) {
            if (field.isSynthetic()) continue;
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            if (field.isAnnotationPresent(ToonIgnore.class)) continue;

            field.setAccessible(true);
            String key = field.isAnnotationPresent(ToonField.class)
                    ? field.getAnnotation(ToonField.class).value()
                    : field.getName();
            try {
                result.put(key, field.get(obj));
            } catch (IllegalAccessException e) {
                throw new ToonException("Cannot access field: " + field.getName(), e);
            }
        }
        return result;
    }

    // ── Primitive formatting ────────────────────────────────────────────

    static boolean isPrimitive(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof Temporal
                || value instanceof Date
                || value instanceof UUID;
    }

    String formatPrimitive(Object value, ToonDelimiter delimiter) {
        if (value == null) return "null";

        if (value instanceof Boolean b) return b ? "true" : "false";
        if (value instanceof Character c) return quoteIfNeeded(String.valueOf(c), delimiter);
        if (value instanceof Enum<?> e) return quoteIfNeeded(e.name(), delimiter);
        if (value instanceof UUID uuid) return quoteIfNeeded(uuid.toString(), delimiter);
        if (value instanceof Temporal t) return "\"" + t.toString() + "\"";
        if (value instanceof Date d) return "\"" + d.toInstant().toString() + "\"";

        if (value instanceof Number num) {
            if (value instanceof Double d) {
                if (d.isNaN() || d.isInfinite()) return "null";
                return formatDecimal(d);
            }
            if (value instanceof Float f) {
                if (f.isNaN() || f.isInfinite()) return "null";
                return formatDecimal(f.doubleValue());
            }
            if (value instanceof BigDecimal bd) {
                return bd.stripTrailingZeros().toPlainString();
            }
            if (value instanceof BigInteger bi) {
                // Check safe integer range
                if (bi.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0
                        && bi.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0) {
                    return bi.toString();
                }
                return "\"" + bi.toString() + "\"";
            }
            return num.toString();
        }

        if (value instanceof String s) {
            return quoteIfNeeded(s, delimiter);
        }

        return quoteIfNeeded(value.toString(), delimiter);
    }

    private String formatDecimal(double d) {
        if (d == 0.0) return "0";
        // Avoid scientific notation
        BigDecimal bd = BigDecimal.valueOf(d).stripTrailingZeros();
        return bd.toPlainString();
    }

    // ── Quoting logic ───────────────────────────────────────────────────

    String quoteIfNeeded(String s, ToonDelimiter delimiter) {
        if (s.isEmpty()) return "\"\"";
        if (needsQuoting(s, delimiter)) {
            return "\"" + escapeString(s) + "\"";
        }
        return s;
    }

    private boolean needsQuoting(String s, ToonDelimiter delimiter) {
        // Leading/trailing whitespace
        if (s.charAt(0) == ' ' || s.charAt(s.length() - 1) == ' ') return true;
        if (s.charAt(0) == '\t' || s.charAt(s.length() - 1) == '\t') return true;

        // Contains active delimiter
        if (s.indexOf(delimiter.getCharacter()) >= 0) return true;

        // Always quote if contains colon
        if (s.contains(":")) return true;

        // Contains control characters or quotes or backslash
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\' || c == '\n' || c == '\r' || c == '\t') return true;
        }

        // Reserved literals
        if ("true".equals(s) || "false".equals(s) || "null".equals(s)) return true;

        // Looks like a number
        if (LOOKS_LIKE_NUMBER.matcher(s).matches()) return true;

        // Starts with list marker
        if (s.startsWith("- ") || s.equals("-")) return true;

        // Looks like array or field header
        if (LOOKS_LIKE_ARRAY_HEADER.matcher(s).matches()) return true;
        if (LOOKS_LIKE_FIELD_HEADER.matcher(s).matches()) return true;

        return false;
    }

    private String escapeString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
    }

    // ── Key encoding ────────────────────────────────────────────────────

    String encodeKey(String key) {
        if (key.isEmpty()) return "\"\"";
        if (SAFE_KEY_PATTERN.matcher(key).matches()) return key;
        return "\"" + escapeString(key) + "\"";
    }

    // ── Utility ─────────────────────────────────────────────────────────

    String indent(int depth) {
        return " ".repeat(depth * options.getIndent());
    }

    private List<?> arrayToList(Object array) {
        int len = Array.getLength(array);
        List<Object> list = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            list.add(Array.get(array, i));
        }
        return list;
    }
}

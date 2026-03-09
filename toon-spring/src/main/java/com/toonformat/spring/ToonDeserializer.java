package com.toonformat.spring;

import com.toonformat.spring.annotation.ToonField;
import com.toonformat.spring.annotation.ToonIgnore;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deserializes TOON (Token-Oriented Object Notation) strings into Java objects.
 * <p>
 * Supports deserialization into:
 * <ul>
 *   <li>POJOs / JavaBeans</li>
 *   <li>{@code Map<String, Object>} (generic tree)</li>
 *   <li>{@code List<T>} for root arrays</li>
 *   <li>Primitive types</li>
 * </ul>
 */
public class ToonDeserializer {

    // key[N]{fields}:  or  key[N]:  or  [N]{fields}:  or  [N]:
    private static final Pattern ARRAY_HEADER = Pattern.compile(
            "^(\\\"[^\\\"]*\\\"|[a-zA-Z_][a-zA-Z0-9_.]*)?\\[#?(\\d+)([ \\t|]?)\\](\\{(.+)\\})?:\\s*(.*)$"
    );
    // key: value
    private static final Pattern KEY_VALUE = Pattern.compile(
            "^(\\\"[^\\\"]*\\\"|[a-zA-Z_][a-zA-Z0-9_.]*):(.*)$"
    );
    // - value  or  - key: value
    private static final Pattern LIST_ITEM = Pattern.compile("^-\\s(.*)$");

    private final ToonOptions options;

    public ToonDeserializer() {
        this(new ToonOptions());
    }

    public ToonDeserializer(ToonOptions options) {
        this.options = options;
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Deserializes a TOON string into a generic tree of Maps, Lists, and primitives.
     */
    public Object deserialize(String toon) {
        if (toon == null || toon.isBlank()) return null;
        List<Line> lines = parseLines(toon);
        if (lines.isEmpty()) return new LinkedHashMap<>();
        return parseBlock(lines, 0, lines.size());
    }

    /**
     * Deserializes a TOON string into the specified Java type.
     */
    @SuppressWarnings("unchecked")
    public <T> T deserialize(String toon, Class<T> type) {
        Object raw = deserialize(toon);
        if (raw == null) return null;
        return convertTo(raw, type, null);
    }

    /**
     * Deserializes a TOON string into a List of the specified element type.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> deserializeList(String toon, Class<T> elementType) {
        Object raw = deserialize(toon);
        if (raw instanceof List<?> list) {
            List<T> result = new ArrayList<>();
            for (Object item : list) {
                result.add(convertTo(item, elementType, null));
            }
            return result;
        }
        throw new ToonException("TOON root is not an array, cannot deserialize to List");
    }

    // ── Line parsing ────────────────────────────────────────────────────

    private record Line(int indent, String content, int lineNumber) {}

    private List<Line> parseLines(String toon) {
        String[] rawLines = toon.split("\n", -1);
        List<Line> result = new ArrayList<>();
        for (int i = 0; i < rawLines.length; i++) {
            String line = rawLines[i];
            if (line.isBlank()) continue;
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') {
                indent++;
            }
            result.add(new Line(indent, line.substring(indent), i + 1));
        }
        return result;
    }

    // ── Block parsing ───────────────────────────────────────────────────

    private Object parseBlock(List<Line> lines, int start, int end) {
        if (start >= end) return new LinkedHashMap<>();

        Line first = lines.get(start);
        int baseIndent = first.indent;

        // Check if root is a bare array header (no key)
        if (first.content.startsWith("[")) {
            Matcher m = ARRAY_HEADER.matcher(first.content);
            if (m.matches() && (m.group(1) == null || m.group(1).isEmpty())) {
                return parseArrayFromHeader(m, lines, start, end, baseIndent);
            }
        }

        // Check if root is a single primitive
        if (start + 1 == end && !first.content.contains(":") && !first.content.startsWith("-")) {
            return parsePrimitiveValue(first.content);
        }

        // Otherwise parse as object
        return parseObject(lines, start, end, baseIndent);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject(List<Line> lines, int start, int end, int baseIndent) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        int i = start;

        while (i < end) {
            Line line = lines.get(i);
            if (line.indent != baseIndent) {
                i++;
                continue;
            }

            // Try array header: key[N]{fields}: ...
            Matcher arrM = ARRAY_HEADER.matcher(line.content);
            if (arrM.matches() && arrM.group(1) != null && !arrM.group(1).isEmpty()) {
                String key = decodeKey(arrM.group(1));
                Object arr = parseArrayFromHeader(arrM, lines, i, end, baseIndent);
                result.put(key, arr);
                i = findBlockEnd(lines, i, end, baseIndent);
                continue;
            }

            // Try key: value
            Matcher kvM = KEY_VALUE.matcher(line.content);
            if (kvM.matches()) {
                String key = decodeKey(kvM.group(1));
                String valueStr = kvM.group(2);

                if (!valueStr.isBlank()) {
                    // Inline value
                    result.put(key, parsePrimitiveValue(valueStr.trim()));
                } else {
                    // Value on next indented lines
                    int childStart = i + 1;
                    int childEnd = findChildBlockEnd(lines, childStart, end, baseIndent);

                    if (childStart < childEnd) {
                        Line firstChild = lines.get(childStart);
                        // Check if child is an array header
                        Matcher childArr = ARRAY_HEADER.matcher(firstChild.content);
                        if (firstChild.content.startsWith("-")) {
                            // List items under this key – wrap in anonymous array parse
                            result.put(key, parseListItems(lines, childStart, childEnd, firstChild.indent));
                        } else if (childArr.matches() && (childArr.group(1) == null || childArr.group(1).isEmpty())) {
                            result.put(key, parseArrayFromHeader(childArr, lines, childStart, childEnd, firstChild.indent));
                        } else {
                            result.put(key, parseObject(lines, childStart, childEnd, firstChild.indent));
                        }
                    } else {
                        // Empty nested object
                        result.put(key, new LinkedHashMap<>());
                    }
                }
                i = findBlockEnd(lines, i, end, baseIndent);
                continue;
            }

            i++;
        }

        return result;
    }

    // ── Array parsing ───────────────────────────────────────────────────

    private Object parseArrayFromHeader(Matcher m, List<Line> lines, int headerLine, int end, int baseIndent) {
        int count = Integer.parseInt(m.group(2));
        String delimStr = m.group(3);
        String fieldsStr = m.group(5);
        String inlineRemainder = m.group(6);

        char delim = resolveDelimiter(delimStr);

        // Inline primitive array: key[N]: val1,val2,...
        if (fieldsStr == null && inlineRemainder != null && !inlineRemainder.isBlank()) {
            return splitValues(inlineRemainder.trim(), delim);
        }

        // Tabular array: key[N]{field1,field2,...}:
        if (fieldsStr != null) {
            List<String> fields = splitRaw(fieldsStr, delim);
            List<Map<String, Object>> rows = new ArrayList<>();
            int childIndent = baseIndent + options.getIndent();
            for (int i = headerLine + 1; i < end; i++) {
                Line line = lines.get(i);
                if (line.indent < childIndent) break;
                if (line.indent != childIndent) continue;
                List<Object> values = splitValues(line.content, delim);
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                for (int j = 0; j < fields.size(); j++) {
                    row.put(fields.get(j), j < values.size() ? values.get(j) : null);
                }
                rows.add(row);
            }
            return rows;
        }

        // List-format array (or empty)
        if (count == 0) return new ArrayList<>();

        int childStart = headerLine + 1;
        int childEnd = findChildBlockEnd(lines, childStart, end, baseIndent);
        return parseListItems(lines, childStart, childEnd, baseIndent + options.getIndent());
    }

    private List<Object> parseListItems(List<Line> lines, int start, int end, int itemIndent) {
        List<Object> result = new ArrayList<>();
        int i = start;

        while (i < end) {
            Line line = lines.get(i);
            if (line.indent != itemIndent) {
                i++;
                continue;
            }

            Matcher listM = LIST_ITEM.matcher(line.content);
            if (!listM.matches()) {
                i++;
                continue;
            }

            String itemContent = listM.group(1);

            // Check if it's an inline array: - [N]: ...
            if (itemContent.startsWith("[")) {
                Matcher innerArr = ARRAY_HEADER.matcher(itemContent);
                if (innerArr.matches()) {
                    result.add(parseArrayFromHeader(innerArr, lines, i, end, itemIndent));
                    i = findBlockEnd(lines, i, end, itemIndent);
                    continue;
                }
            }

            // Check if it's a key: value (object in list)
            Matcher kvM = KEY_VALUE.matcher(itemContent);
            if (kvM.matches()) {
                String key = decodeKey(kvM.group(1));
                String valStr = kvM.group(2);
                LinkedHashMap<String, Object> obj = new LinkedHashMap<>();
                if (!valStr.isBlank()) {
                    obj.put(key, parsePrimitiveValue(valStr.trim()));
                } else {
                    // Nested value on indented lines below
                    int childStart = i + 1;
                    int contIndent = itemIndent + options.getIndent();
                    int childEnd = findChildBlockEnd(lines, childStart, end, itemIndent);
                    if (childStart < childEnd) {
                        obj.put(key, parseBlock(lines, childStart, childEnd));
                    } else {
                        obj.put(key, new LinkedHashMap<>());
                    }
                }

                // Continuation fields at indent + indentSize
                int contIndent = itemIndent + options.getIndent();
                int j = i + 1;
                while (j < end && j < lines.size()) {
                    Line contLine = lines.get(j);
                    if (contLine.indent < contIndent) break;
                    if (contLine.indent == contIndent) {
                        // Additional field of the same object
                        Matcher contKv = KEY_VALUE.matcher(contLine.content);
                        Matcher contArr = ARRAY_HEADER.matcher(contLine.content);
                        if (contArr.matches() && contArr.group(1) != null) {
                            String contKey = decodeKey(contArr.group(1));
                            obj.put(contKey, parseArrayFromHeader(contArr, lines, j, end, contIndent));
                            j = findBlockEnd(lines, j, end, contIndent);
                            continue;
                        } else if (contKv.matches()) {
                            String contKey = decodeKey(contKv.group(1));
                            String contVal = contKv.group(2);
                            if (!contVal.isBlank()) {
                                obj.put(contKey, parsePrimitiveValue(contVal.trim()));
                            } else {
                                int childStart = j + 1;
                                int childEnd = findChildBlockEnd(lines, childStart, end, contIndent);
                                if (childStart < childEnd) {
                                    obj.put(contKey, parseBlock(lines, childStart, childEnd));
                                } else {
                                    obj.put(contKey, new LinkedHashMap<>());
                                }
                            }
                        }
                    }
                    j++;
                }

                result.add(obj);
                i = j;
                continue;
            }

            // Plain primitive list item
            result.add(parsePrimitiveValue(itemContent));
            i++;
        }

        return result;
    }

    // ── Value parsing ───────────────────────────────────────────────────

    private List<Object> splitValues(String s, char delim) {
        List<String> parts = splitRespectingQuotes(s, delim);
        List<Object> result = new ArrayList<>(parts.size());
        for (String part : parts) {
            result.add(parsePrimitiveValue(part.trim()));
        }
        return result;
    }

    Object parsePrimitiveValue(String s) {
        if (s == null || s.isEmpty()) return "";
        if ("null".equals(s)) return null;
        if ("true".equals(s)) return true;
        if ("false".equals(s)) return false;

        // Quoted string
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return unescapeString(s.substring(1, s.length() - 1));
        }

        // Try number
        try {
            if (s.contains(".")) {
                return Double.parseDouble(s);
            }
            long l = Long.parseLong(s);
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return (int) l;
            }
            return l;
        } catch (NumberFormatException ignored) {}

        // Plain unquoted string
        return s;
    }

    private String unescapeString(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '\\' -> { sb.append('\\'); i++; }
                    case '"'  -> { sb.append('"');  i++; }
                    case 'n'  -> { sb.append('\n'); i++; }
                    case 'r'  -> { sb.append('\r'); i++; }
                    case 't'  -> { sb.append('\t'); i++; }
                    default   -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ── Delimiter handling ──────────────────────────────────────────────

    private char resolveDelimiter(String delimStr) {
        if (delimStr == null || delimStr.isEmpty()) return ',';
        char d = delimStr.charAt(0);
        if (d == '|') return '|';
        if (d == '\t' || d == ' ') return '\t'; // space in regex capture might indicate tab
        return ',';
    }

    private List<String> splitRaw(String s, char delim) {
        return splitRespectingQuotes(s, delim);
    }

    /**
     * Splits on the delimiter but respects quoted segments.
     */
    private List<String> splitRespectingQuotes(String s, char delim) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                current.append(c);
                escaped = true;
                continue;
            }
            if (c == '"') {
                current.append(c);
                inQuotes = !inQuotes;
                continue;
            }
            if (c == delim && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        parts.add(current.toString());
        return parts;
    }

    // ── Block boundary helpers ──────────────────────────────────────────

    private int findBlockEnd(List<Line> lines, int start, int end, int baseIndent) {
        int i = start + 1;
        while (i < end) {
            if (lines.get(i).indent <= baseIndent) return i;
            i++;
        }
        return i;
    }

    private int findChildBlockEnd(List<Line> lines, int childStart, int end, int parentIndent) {
        int i = childStart;
        while (i < end) {
            if (lines.get(i).indent <= parentIndent) return i;
            i++;
        }
        return i;
    }

    // ── Key decoding ────────────────────────────────────────────────────

    private String decodeKey(String raw) {
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            return unescapeString(raw.substring(1, raw.length() - 1));
        }
        return raw;
    }

    // ── Type conversion ─────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    <T> T convertTo(Object raw, Class<T> type, Type genericType) {
        if (raw == null) return null;
        if (type == Object.class) return (T) raw;

        // Already correct type
        if (type.isInstance(raw)) return type.cast(raw);

        // String
        if (type == String.class) return (T) String.valueOf(raw);

        // Primitives and wrappers
        if (type == int.class || type == Integer.class) return (T) Integer.valueOf(toInt(raw));
        if (type == long.class || type == Long.class) return (T) Long.valueOf(toLong(raw));
        if (type == double.class || type == Double.class) return (T) Double.valueOf(toDouble(raw));
        if (type == float.class || type == Float.class) return (T) Float.valueOf((float) toDouble(raw));
        if (type == boolean.class || type == Boolean.class) return (T) Boolean.valueOf(toBoolean(raw));
        if (type == short.class || type == Short.class) return (T) Short.valueOf((short) toInt(raw));
        if (type == byte.class || type == Byte.class) return (T) Byte.valueOf((byte) toInt(raw));
        if (type == char.class || type == Character.class) {
            String s = String.valueOf(raw);
            return (T) Character.valueOf(s.isEmpty() ? '\0' : s.charAt(0));
        }
        if (type == BigDecimal.class) return (T) new BigDecimal(String.valueOf(raw));
        if (type == BigInteger.class) return (T) new BigInteger(String.valueOf(raw));

        // Enums
        if (type.isEnum()) {
            return (T) Enum.valueOf((Class<Enum>) type, String.valueOf(raw));
        }

        // UUID
        if (type == UUID.class) return (T) UUID.fromString(String.valueOf(raw));

        // Temporal types
        if (type == LocalDateTime.class) return (T) LocalDateTime.parse(String.valueOf(raw));
        if (type == LocalDate.class) return (T) LocalDate.parse(String.valueOf(raw));
        if (type == Instant.class) return (T) Instant.parse(String.valueOf(raw));
        if (type == ZonedDateTime.class) return (T) ZonedDateTime.parse(String.valueOf(raw));
        if (type == OffsetDateTime.class) return (T) OffsetDateTime.parse(String.valueOf(raw));

        // List
        if (List.class.isAssignableFrom(type) && raw instanceof List<?> list) {
            return (T) list;
        }

        // Map
        if (Map.class.isAssignableFrom(type) && raw instanceof Map<?, ?> map) {
            return (T) map;
        }

        // POJO from Map
        if (raw instanceof Map<?, ?> map) {
            return mapToPojo((Map<String, Object>) map, type);
        }

        throw new ToonException("Cannot convert " + raw.getClass().getSimpleName() + " to " + type.getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private <T> T mapToPojo(Map<String, Object> map, Class<T> type) {
        try {
            Constructor<T> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            T instance = ctor.newInstance();

            // Build a reverse lookup: toonKey → Field
            Map<String, Field> fieldMap = new LinkedHashMap<>();
            Class<?> current = type;
            while (current != null && current != Object.class) {
                for (Field f : current.getDeclaredFields()) {
                    if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) continue;
                    if (f.isAnnotationPresent(ToonIgnore.class)) continue;
                    String key = f.isAnnotationPresent(ToonField.class)
                            ? f.getAnnotation(ToonField.class).value()
                            : f.getName();
                    f.setAccessible(true);
                    fieldMap.put(key, f);
                }
                current = current.getSuperclass();
            }

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Field field = fieldMap.get(entry.getKey());
                if (field == null) continue;

                Object value = entry.getValue();
                Class<?> fieldType = field.getType();
                Type fieldGeneric = field.getGenericType();

                if (value instanceof Map<?, ?> nestedMap && !Map.class.isAssignableFrom(fieldType)) {
                    field.set(instance, mapToPojo((Map<String, Object>) nestedMap, fieldType));
                } else if (value instanceof List<?> list && List.class.isAssignableFrom(fieldType)) {
                    // Try to figure out the element type from generics
                    if (fieldGeneric instanceof ParameterizedType pt) {
                        Type elemType = pt.getActualTypeArguments()[0];
                        if (elemType instanceof Class<?> elemClass) {
                            List<Object> converted = new ArrayList<>();
                            for (Object item : list) {
                                converted.add(convertTo(item, elemClass, null));
                            }
                            field.set(instance, converted);
                        } else {
                            field.set(instance, list);
                        }
                    } else {
                        field.set(instance, list);
                    }
                } else {
                    field.set(instance, convertTo(value, fieldType, fieldGeneric));
                }
            }

            return instance;
        } catch (ToonException e) {
            throw e;
        } catch (Exception e) {
            throw new ToonException("Cannot instantiate " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    // ── Numeric conversions ─────────────────────────────────────────────

    private int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(o));
    }

    private long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(o));
    }

    private double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }

    private boolean toBoolean(Object o) {
        if (o instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(o));
    }
}

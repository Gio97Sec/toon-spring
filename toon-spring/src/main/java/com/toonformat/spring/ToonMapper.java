package com.toonformat.spring;

import java.util.List;
import java.util.Map;

/**
 * Main entry point for TOON serialization and deserialization.
 * <p>
 * Analogous to Jackson's {@code ObjectMapper} for JSON.
 *
 * <pre>{@code
 * ToonMapper mapper = new ToonMapper();
 *
 * // Serialize
 * String toon = mapper.writeValueAsString(myObject);
 *
 * // Deserialize
 * MyClass obj = mapper.readValue(toon, MyClass.class);
 * List<MyClass> list = mapper.readList(toon, MyClass.class);
 * }</pre>
 */
public class ToonMapper {

    private final ToonSerializer serializer;
    private final ToonDeserializer deserializer;
    private final ToonOptions options;

    public ToonMapper() {
        this(new ToonOptions());
    }

    public ToonMapper(ToonOptions options) {
        this.options = options;
        this.serializer = new ToonSerializer(options);
        this.deserializer = new ToonDeserializer(options);
    }

    // ── Serialization ───────────────────────────────────────────────────

    /**
     * Serializes any Java value to a TOON string.
     */
    public String writeValueAsString(Object value) {
        return serializer.serialize(value);
    }

    // ── Deserialization ─────────────────────────────────────────────────

    /**
     * Deserializes a TOON string into the specified Java type.
     */
    public <T> T readValue(String toon, Class<T> type) {
        return deserializer.deserialize(toon, type);
    }

    /**
     * Deserializes a TOON string into a generic tree of Maps/Lists/primitives.
     */
    public Object readTree(String toon) {
        return deserializer.deserialize(toon);
    }

    /**
     * Deserializes a TOON string (root array) into a List of the given element type.
     */
    public <T> List<T> readList(String toon, Class<T> elementType) {
        return deserializer.deserializeList(toon, elementType);
    }

    /**
     * Deserializes a TOON string into a Map.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readMap(String toon) {
        Object result = deserializer.deserialize(toon);
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ToonException("TOON root is not an object, cannot deserialize to Map");
    }

    // ── Configuration ───────────────────────────────────────────────────

    public ToonOptions getOptions() {
        return options;
    }
}

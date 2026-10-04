package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Immutable BLOCK_STATE payload: canonical non-blank property names and values.
 * Identity keys ({@code blockId}, {@code id}) are never stored.
 * Semantic property/block compatibility is {@link com.nodecraft.nodesystem.nodes.material.block_state.BlockStateValidationUtils}.
 */
public final class BlockStateData {

    private final Map<String, String> properties;

    public BlockStateData() {
        this.properties = Map.of();
    }

    public BlockStateData(@Nullable Map<String, String> stateMap) {
        this.properties = canonicalize(stateMap);
    }

    public BlockStateData(@Nullable BlockStateData other) {
        this.properties = other == null ? Map.of() : other.properties;
    }

    public Map<String, String> properties() {
        return properties;
    }

    public boolean isEmpty() {
        return properties.isEmpty();
    }

    public int size() {
        return properties.size();
    }

    public Set<String> keySet() {
        return properties.keySet();
    }

    public Set<Map.Entry<String, String>> entrySet() {
        return properties.entrySet();
    }

    public void forEach(BiConsumer<String, String> action) {
        properties.forEach(action);
    }

    public @Nullable String get(@Nullable String property) {
        return property == null ? null : properties.get(property);
    }

    public String getProperty(String property, String defaultValue) {
        String value = get(property);
        return value != null ? value : defaultValue;
    }

    public boolean hasProperty(String property) {
        return property != null && properties.containsKey(property);
    }

    public boolean getBooleanProperty(String property, boolean defaultValue) {
        String value = get(property);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value);
    }

    public int getIntProperty(String property, int defaultValue) {
        String value = get(property);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public BlockStateData withProperty(String property, String value) {
        String name = canonicalizeKey(property);
        String canonicalValue = canonicalizeValue(value);
        if (name == null || canonicalValue == null) {
            return this;
        }
        Map<String, String> next = new LinkedHashMap<>(properties);
        next.put(name, canonicalValue);
        return new BlockStateData(next);
    }

    public BlockStateData withBooleanProperty(String property, boolean value) {
        return withProperty(property, String.valueOf(value));
    }

    public BlockStateData withIntProperty(String property, int value) {
        return withProperty(property, String.valueOf(value));
    }

    public BlockStateData copy() {
        return this;
    }

    /**
     * Merges {@code override} into {@code base}; override keys win.
     */
    public static BlockStateData merge(@Nullable BlockStateData base, @Nullable BlockStateData override) {
        if (override == null || override.isEmpty()) {
            return base != null ? base : new BlockStateData();
        }
        if (base == null || base.isEmpty()) {
            return override;
        }
        Map<String, String> merged = new LinkedHashMap<>(base.properties);
        merged.putAll(override.properties);
        return new BlockStateData(merged);
    }

    public static void stripIdentityKeys(BlockStateData state) {
        // Identity keys are stripped at construction; kept for call-site compatibility.
    }

    private static Map<String, String> canonicalize(@Nullable Map<String, String> stateMap) {
        if (stateMap == null || stateMap.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : stateMap.entrySet()) {
            String name = canonicalizeKey(entry.getKey());
            String value = canonicalizeValue(entry.getValue());
            if (name != null && value != null) {
                copy.put(name, value);
            }
        }
        return copy.isEmpty() ? Map.of() : Map.copyOf(copy);
    }

    private static @Nullable String canonicalizeKey(@Nullable String key) {
        if (key == null) {
            return null;
        }
        String name = key.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || isIdentityKey(name)) {
            return null;
        }
        return name;
    }

    private static @Nullable String canonicalizeValue(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isIdentityKey(String key) {
        return "blockId".equals(key) || "id".equals(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlockStateData that)) {
            return false;
        }
        return properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties);
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}

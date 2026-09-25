package com.nodecraft.nodesystem.nodes.material.block_state;

import com.nodecraft.nodesystem.util.BlockStateData;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared validation and merge helpers for {@code material.block_state.*} nodes.
 */
public final class BlockStateValidationUtils {

    public record ValidationResult(boolean valid, String message) {
        public static ValidationResult ok() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }
    }

    public record PropertiesTextResult(boolean valid, String error) {
        public static PropertiesTextResult ok() {
            return new PropertiesTextResult(true, "");
        }

        public static PropertiesTextResult fail(String error) {
            return new PropertiesTextResult(false, error);
        }
    }

    private BlockStateValidationUtils() {
    }

    public static @Nullable String normalizeBlockId(@Nullable Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    public static boolean containsBlockId(@Nullable String blockType) {
        if (blockType == null || blockType.isBlank()) {
            return false;
        }
        try {
            Identifier id = Identifier.tryParse(blockType);
            return id != null && Registries.BLOCK.containsId(id);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static ValidationResult validateProperties(@Nullable String blockType, BlockStateData state) {
        if (blockType == null) {
            return ValidationResult.fail("Block type required for validation");
        }
        if (!containsBlockId(blockType)) {
            return ValidationResult.fail("Unknown block: " + blockType);
        }

        Block block;
        try {
            Identifier id = Identifier.of(blockType);
            block = Registries.BLOCK.get(id);
        } catch (Throwable e) {
            return ValidationResult.fail("Block registry unavailable");
        }

        List<String> errors = new ArrayList<>();
        for (String key : state.keySet()) {
            if (isIdentityKey(key)) {
                continue;
            }
            Property<?> property = findProperty(block, key);
            if (property == null) {
                errors.add("Unsupported property '" + key + "' for " + blockType);
                continue;
            }
            String value = state.get(key);
            if (property.parse(value).isEmpty()) {
                errors.add("Invalid value '" + value + "' for property '" + key + "'");
            }
        }
        return errors.isEmpty()
            ? ValidationResult.ok()
            : ValidationResult.fail(String.join("; ", errors));
    }

    public static BlockStateData mergeStateData(@Nullable BlockStateData base, @Nullable BlockStateData override) {
        return BlockStateData.merge(base, override);
    }

    public static PropertiesTextResult applyPropertiesText(BlockStateData state, @Nullable String text) {
        if (text == null || text.isBlank()) {
            return PropertiesTextResult.ok();
        }
        String[] pairs = text.split(",");
        for (String pair : pairs) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] kv = trimmed.split("=", 2);
            if (kv.length != 2) {
                return PropertiesTextResult.fail("Malformed property entry: " + trimmed);
            }
            String name = kv[0].trim();
            String propertyValue = kv[1].trim();
            if (name.isEmpty() || propertyValue.isEmpty()) {
                return PropertiesTextResult.fail("Malformed property entry: " + trimmed);
            }
            putProperty(state, name, propertyValue);
        }
        return PropertiesTextResult.ok();
    }

    public static void putProperty(BlockStateData state, String rawName, String rawValue) {
        if (rawName == null || rawValue == null) {
            return;
        }
        String name = rawName.trim().toLowerCase(Locale.ROOT);
        String value = rawValue.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || value.isEmpty() || isIdentityKey(name)) {
            return;
        }
        state.setProperty(name, value);
    }

    public static int propertyCount(BlockStateData state) {
        int count = 0;
        for (String key : state.keySet()) {
            if (!isIdentityKey(key)) {
                count++;
            }
        }
        return count;
    }

    public static void stripIdentityKeys(BlockStateData state) {
        state.remove("blockId");
        state.remove("id");
    }

    public static boolean isIdentityKey(@Nullable String key) {
        return "blockId".equals(key) || "id".equals(key);
    }

    /**
     * Graph-facing VECTOR ports accept canonical {@link Vector3d} only (no POINT/BLOCK_POS coercion).
     */
    public static @Nullable Vector3d resolveStrictVector3d(@Nullable Object value) {
        if (!(value instanceof Vector3d vector)) {
            return null;
        }
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            return null;
        }
        return new Vector3d(vector);
    }

    public static @Nullable Vector3d resolveStrictVectorListElement(@Nullable Object value) {
        return resolveStrictVector3d(value);
    }

    private static @Nullable Property<?> findProperty(Block block, String name) {
        for (Property<?> property : block.getDefaultState().getProperties()) {
            if (property.getName().equals(name)) {
                return property;
            }
        }
        return null;
    }
}

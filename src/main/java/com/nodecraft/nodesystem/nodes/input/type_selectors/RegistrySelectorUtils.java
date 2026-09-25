package com.nodecraft.nodesystem.nodes.input.type_selectors;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Shared helpers for {@code input.type_selectors.*} registry ID nodes.
 */
public final class RegistrySelectorUtils {

    private static final String MINECRAFT_NAMESPACE = "minecraft";

    private RegistrySelectorUtils() {
    }

    /**
     * Normalizes user/registry text to canonical {@code namespace:path}, or {@code null} when blank or unparsable.
     */
    public static @Nullable String normalizeCanonicalId(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.contains(":")) {
            normalized = MINECRAFT_NAMESPACE + ":" + normalized;
        }
        return Identifier.tryParse(normalized) == null ? null : normalized;
    }

    public static String[] splitNamespacePath(String canonicalId) {
        int separator = canonicalId.indexOf(':');
        if (separator < 0) {
            return new String[] {MINECRAFT_NAMESPACE, canonicalId};
        }
        return new String[] {canonicalId.substring(0, separator), canonicalId.substring(separator + 1)};
    }

    public static boolean isModdedNamespace(String namespace) {
        return !MINECRAFT_NAMESPACE.equals(namespace);
    }

    public static boolean computeValid(@Nullable String canonicalId, boolean registryContains, boolean allowModded) {
        if (canonicalId == null || !registryContains) {
            return false;
        }
        if (!allowModded && !canonicalId.startsWith(MINECRAFT_NAMESPACE + ":")) {
            return false;
        }
        return true;
    }
}

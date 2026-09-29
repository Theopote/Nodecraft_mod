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

    /**
     * Graph Valid requires an authoritative registry, membership, and allow-modded policy.
     * Non-authoritative UI fallback catalogs must never yield {@code true}.
     */
    public static boolean computeValid(
        @Nullable String canonicalId,
        boolean registryContains,
        boolean allowModded,
        boolean registryAuthoritative
    ) {
        if (!registryAuthoritative || canonicalId == null || !registryContains) {
            return false;
        }
        return allowModded || canonicalId.startsWith(MINECRAFT_NAMESPACE + ":");
    }

    /**
     * @deprecated Prefer {@link #computeValid(String, boolean, boolean, boolean)} with explicit
     *             {@code registryAuthoritative}. Assumes authoritative=true for legacy callers.
     */
    @Deprecated
    public static boolean computeValid(@Nullable String canonicalId, boolean registryContains, boolean allowModded) {
        return computeValid(canonicalId, registryContains, allowModded, true);
    }
}

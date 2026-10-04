package com.nodecraft.nodesystem.nodes.input.type_selectors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Shared immutable registry id catalogs for type-selector pickers.
 * Instances keep only search/filter/page state; catalog lists are reused across nodes.
 */
public final class RegistryCatalogCache {

    public enum Kind {
        BLOCK,
        ITEM,
        ENTITY,
        BIOME
    }

    private record CacheKey(Kind kind, boolean authoritative) {}

    private static final ConcurrentHashMap<CacheKey, RegistryCatalog> CACHE = new ConcurrentHashMap<>();

    private RegistryCatalogCache() {
    }

    /**
     * Returns a shared sorted catalog for {@code kind}. Prefers an authoritative cache entry.
     * Empty catalogs are not cached so later registry readiness can retry.
     */
    public static RegistryCatalog getOrLoad(Kind kind, Supplier<RegistryCatalog> loader) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(loader, "loader");

        RegistryCatalog authoritative = CACHE.get(new CacheKey(kind, true));
        if (authoritative != null) {
            return authoritative;
        }

        RegistryCatalog loaded = Objects.requireNonNull(loader.get(), "catalog");
        ArrayList<String> sorted = new ArrayList<>(loaded.ids());
        sorted.sort(Comparator.naturalOrder());
        RegistryCatalog shared = new RegistryCatalog(sorted, loaded.authoritative());

        if (shared.ids().isEmpty()) {
            RegistryCatalog fallback = CACHE.get(new CacheKey(kind, false));
            return fallback != null ? fallback : shared;
        }
        return CACHE.computeIfAbsent(new CacheKey(kind, shared.authoritative()), ignored -> shared);
    }

    /** Test/support: drop all cached catalogs. */
    public static void clearForTest() {
        CACHE.clear();
    }
}

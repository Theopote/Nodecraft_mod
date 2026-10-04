package com.nodecraft.nodesystem.nodes.geometry.solids;

import java.util.Locale;
import java.util.Set;

/**
 * Loft section compatibility mode. Historical Graph V72 residue;
 * {@code GraphFormatVersion.CURRENT} is stamp-only 1.
 */
public enum MatchSectionsMode {
    STRICT,
    RESAMPLE_MAX,
    RESAMPLE_COUNT;

    static final Set<String> KEYS = Set.of("strict", "resample_max", "resample_count");

    static MatchSectionsMode fromKey(String key) {
        return valueOf(key.toUpperCase(Locale.ROOT));
    }

    String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}

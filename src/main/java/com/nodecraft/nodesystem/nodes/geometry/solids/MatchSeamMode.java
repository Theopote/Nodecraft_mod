package com.nodecraft.nodesystem.nodes.geometry.solids;

import java.util.Locale;
import java.util.Set;

/**
 * Section seam correspondence. INDEX is default (no silent auto-shift).
 */
public enum MatchSeamMode {
    INDEX,
    AUTO_SEAM;

    static final Set<String> KEYS = Set.of("index", "auto_seam");

    static MatchSeamMode fromKey(String key) {
        return valueOf(key.toUpperCase(Locale.ROOT));
    }

    String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}

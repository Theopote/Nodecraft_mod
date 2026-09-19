package com.nodecraft.gui.components.property.core;

import java.util.Locale;
import java.util.Set;

/**
 * Categories that hold power-user / compatibility controls and should stay
 * de-emphasized in the property panel (sorted last, collapsed by default).
 */
public final class AdvancedPropertyCategories {

    public static final String ADVANCED = "Advanced";
    public static final String COMPATIBILITY = "Compatibility";

    private static final Set<String> KEYS = Set.of(
            ADVANCED.toLowerCase(Locale.ROOT),
            COMPATIBILITY.toLowerCase(Locale.ROOT),
            "compat"
    );

    private AdvancedPropertyCategories() {
    }

    public static boolean isAdvanced(String categoryKey) {
        if (categoryKey == null || categoryKey.isBlank()) {
            return false;
        }
        return KEYS.contains(categoryKey.trim().toLowerCase(Locale.ROOT));
    }

    /** Advanced sections stay collapsed until the user opens them. */
    public static boolean collapsedByDefault(String categoryKey) {
        return isAdvanced(categoryKey);
    }
}

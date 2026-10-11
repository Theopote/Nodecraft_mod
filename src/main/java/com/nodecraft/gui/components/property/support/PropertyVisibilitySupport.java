package com.nodecraft.gui.components.property.support;

import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.nodesystem.api.INode;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * Central registry for node-specific property visibility rules.
 */
public final class PropertyVisibilitySupport {

    private static final List<BiPredicate<INode, PropertyDescriptor>> VISIBILITY_RULES = List.of(
            GeometryViewerPropertySupport::shouldDisplayProperty,
            AttractorFieldPropertySupport::shouldDisplayProperty
    );

    private PropertyVisibilitySupport() {
    }

    public static boolean shouldDisplayProperty(INode node, PropertyDescriptor prop) {
        for (BiPredicate<INode, PropertyDescriptor> rule : VISIBILITY_RULES) {
            if (!rule.test(node, prop)) {
                return false;
            }
        }
        return true;
    }
}

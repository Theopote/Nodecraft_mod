package com.nodecraft.gui.components.property.support;

import com.nodecraft.gui.components.property.core.PropertyConstraints;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.nodesystem.api.INode;

/**
 * Combines {@link NodeProperty} bounds with semantic UI hints (percent sliders, integer counts, etc.).
 */
public final class PropertyConstraintResolver {

    private PropertyConstraintResolver() {
    }

    public static PropertyConstraints resolve(INode node, PropertyDescriptor prop) {
        PropertyConstraints base = prop.constraints != null ? prop.constraints : PropertyConstraints.UNBOUNDED;
        PropertySemanticHints.ScalarMode mode = PropertySemanticHints.scalarMode(node, prop, prop.type);
        return switch (mode) {
            case PERCENT -> base.merge(new PropertyConstraints(0.0d, 1.0d, null));
            case INTEGER_COUNT -> base.merge(new PropertyConstraints(0.0d, null, 1.0d));
            case ANGLE, GENERIC -> base;
        };
    }
}

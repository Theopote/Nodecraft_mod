package com.nodecraft.gui.components.property.support;

import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.nodesystem.api.INode;

import java.util.Map;
import java.util.Set;

/**
 * Attractor / vortex field property visibility: hide Exponent when Falloff is GAUSSIAN
 * (GAUSSIAN falloff does not use Exponent).
 */
public final class AttractorFieldPropertySupport {

    private static final Set<String> FALLOFF_ATTRACTOR_TYPE_IDS = Set.of(
            "math.fields.point_attractor_field",
            "math.fields.curve_attractor_field",
            "math.fields.volume_attractor_field",
            "math.fields.vortex_field"
    );

    private AttractorFieldPropertySupport() {
    }

    public static boolean shouldDisplayProperty(INode node, PropertyDescriptor prop) {
        if (node == null || prop == null || !"exponent".equals(prop.name)) {
            return true;
        }
        if (!FALLOFF_ATTRACTOR_TYPE_IDS.contains(node.getTypeId())) {
            return true;
        }
        return !isGaussianFalloff(node);
    }

    private static boolean isGaussianFalloff(INode node) {
        Object state = node.getNodeState();
        if (!(state instanceof Map<?, ?> map)) {
            return false;
        }
        Object falloff = map.get("falloff");
        if (falloff == null) {
            return false;
        }
        return "GAUSSIAN".equalsIgnoreCase(String.valueOf(falloff));
    }
}

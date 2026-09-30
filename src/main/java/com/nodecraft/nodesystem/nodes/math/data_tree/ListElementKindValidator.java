package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;

/**
 * Lightweight element-kind checks at List→Tree construction boundaries (V130).
 */
final class ListElementKindValidator {

    private ListElementKindValidator() {
    }

    static boolean matches(Object value, ListElementKind kind) {
        if (kind == null
                || kind == ListElementKind.UNCONSTRAINED
                || kind == ListElementKind.NONE
                || kind == ListElementKind.BLOCK_PLACEMENT) {
            return true;
        }
        if (value == null) {
            return false;
        }
        NodeDataType elementType = NodeDataType.elementTypeForKind(kind);
        return elementType.isCompatible(value);
    }
}

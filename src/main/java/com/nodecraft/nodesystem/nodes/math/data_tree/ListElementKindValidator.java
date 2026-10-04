package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime element-kind checks for typed {@code LIST<T>} / {@code DATA_TREE<T>}.
 * Generic {@link ListElementKind#UNCONSTRAINED} lists may contain nulls; constrained T may not.
 */
public final class ListElementKindValidator {

    private ListElementKindValidator() {
    }

    public static boolean isConstrained(@Nullable ListElementKind kind) {
        return kind != null
                && kind != ListElementKind.UNCONSTRAINED
                && kind != ListElementKind.NONE;
    }

    public static boolean matches(Object value, ListElementKind kind) {
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

    /**
     * @return error code, or {@code null} when the value is allowed
     */
    public static @Nullable String validateListElement(@Nullable Object value, ListElementKind kind) {
        if (!isConstrained(kind) || kind == ListElementKind.BLOCK_PLACEMENT) {
            return null;
        }
        if (value == null) {
            return DataTreeNodeUtils.ERROR_NULL_ITEM;
        }
        if (!matches(value, kind)) {
            return DataTreeNodeUtils.ERROR_ELEMENT_KIND_MISMATCH;
        }
        return null;
    }
}

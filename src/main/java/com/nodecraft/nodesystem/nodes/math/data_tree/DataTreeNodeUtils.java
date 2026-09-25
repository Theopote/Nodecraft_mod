package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.PortTypeResolver;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.TreePathData;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed boundary helpers for data-tree nodes.
 * <p>
 * LIST to DATA_TREE coercion is not performed here — use Graft List / Flatten Tree.
 * Path ports accept {@link TreePathData} only (fail-closed; no string fallback to {@code {0}}).
 */
final class DataTreeNodeUtils {
    private DataTreeNodeUtils() {
    }

    static DataTreeData requireTree(Object value) {
        if (value instanceof DataTreeData tree) {
            return tree;
        }
        return DataTreeData.empty();
    }

    static List<Object> requireList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    /**
     * @return path when value is a valid {@link TreePathData}; otherwise {@code null} (not found).
     */
    static TreePathData requirePath(Object value) {
        if (value instanceof TreePathData path) {
            return path;
        }
        return null;
    }

    /**
     * List index language: negatives from end; out of range → -1 (no wrap).
     */
    static int resolveIndex(int index, int size) {
        if (size <= 0) {
            return -1;
        }
        int resolved = index;
        if (resolved < 0) {
            resolved = size + resolved;
        }
        return resolved >= 0 && resolved < size ? resolved : -1;
    }

    static ListElementKind resolveElementKindFromListPort(BaseNode node, String listPortId) {
        for (IPort port : node.getInputPorts()) {
            if (port != null && listPortId.equals(port.getId())) {
                NodeDataType effective = PortTypeResolver.resolveEffectiveType(port);
                if (effective != null && effective.isListType()) {
                    return effective.getListElementKind();
                }
            }
        }
        return ListElementKind.UNCONSTRAINED;
    }

    static ListElementKind resolveElementKindFromTreePort(BaseNode node, String treePortId, Object treeValue) {
        ListElementKind fromBinding = kindFromSharedVariable(node, treePortId);
        if (isConstrained(fromBinding)) {
            return fromBinding;
        }
        if (treeValue instanceof DataTreeData tree) {
            return tree.getElementKind();
        }
        return ListElementKind.UNCONSTRAINED;
    }

    static ListElementKind mergeKinds(ListElementKind a, ListElementKind b) {
        if (!isConstrained(a)) {
            return b == null ? ListElementKind.UNCONSTRAINED : b;
        }
        if (!isConstrained(b)) {
            return a;
        }
        return a == b ? a : ListElementKind.UNCONSTRAINED;
    }

    private static ListElementKind kindFromSharedVariable(BaseNode node, String treePortId) {
        String variable = null;
        for (IPort port : node.getInputPorts()) {
            if (port != null && treePortId.equals(port.getId())) {
                variable = port.getListTypeVariable();
                break;
            }
        }
        if (variable == null || variable.isBlank()) {
            return ListElementKind.UNCONSTRAINED;
        }
        for (IPort port : node.getInputPorts()) {
            if (port == null || !variable.equals(port.getListTypeVariable())) {
                continue;
            }
            if (port.getDataType() != null && port.getDataType().isListType()) {
                NodeDataType effective = PortTypeResolver.resolveEffectiveType(port);
                if (effective != null && isConstrained(effective.getListElementKind())) {
                    return effective.getListElementKind();
                }
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (port == null || !variable.equals(port.getListTypeVariable())) {
                continue;
            }
            if (port.getDataType() != null && port.getDataType().isListType()) {
                NodeDataType effective = PortTypeResolver.resolveEffectiveType(port);
                if (effective != null && isConstrained(effective.getListElementKind())) {
                    return effective.getListElementKind();
                }
            }
        }
        // Tree-only T: effective list kind on outputs that share T after binding from connected tree
        for (IPort port : node.getOutputPorts()) {
            if (port != null && variable.equals(port.getListTypeVariable())
                    && port.getDataType() != null && port.getDataType().isListType()) {
                NodeDataType effective = PortTypeResolver.resolveEffectiveType(port);
                if (effective != null && isConstrained(effective.getListElementKind())) {
                    return effective.getListElementKind();
                }
            }
        }
        // Probe via any port sharing T through PortTypeResolver (DATA_TREE chain)
        for (IPort port : node.getInputPorts()) {
            if (port != null && variable.equals(port.getListTypeVariable())) {
                // Force resolution path used by list outputs: temporarily not available —
                // use PortTypeResolver by reading a synthetic: resolveEffectiveType on list-declared
                // ports only. For Branch/Item, output LIST shares T — handled above.
                NodeDataType effective = PortTypeResolver.resolveEffectiveType(port);
                if (effective != null && effective.isListType() && isConstrained(effective.getListElementKind())) {
                    return effective.getListElementKind();
                }
            }
        }
        return ListElementKind.UNCONSTRAINED;
    }

    private static boolean isConstrained(ListElementKind kind) {
        return kind != null
                && kind != ListElementKind.UNCONSTRAINED
                && kind != ListElementKind.NONE;
    }
}

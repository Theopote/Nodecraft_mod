package com.nodecraft.nodesystem.nodes.variable;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.PortTypeResolver;
import com.nodecraft.nodesystem.core.BaseNode;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime type helpers for typed variable slots.
 */
public final class VariableTypeOps {

    private static final NodeDataType[] INFER_PRIORITY = {
            NodeDataType.BLOCK_POS,
            NodeDataType.POINT,
            NodeDataType.VECTOR,
            NodeDataType.PLANE,
            NodeDataType.FRAME,
            NodeDataType.PATH,
            NodeDataType.LINE,
            NodeDataType.GEOMETRY,
            NodeDataType.SDF,
            NodeDataType.SCALAR_FIELD,
            NodeDataType.VECTOR_FIELD,
            NodeDataType.BLOCK_PALETTE,
            NodeDataType.COLOR,
            NodeDataType.STRING,
            NodeDataType.BOOLEAN,
            NodeDataType.INTEGER,
            NodeDataType.DOUBLE,
            NodeDataType.FLOAT,
            NodeDataType.STRING_LIST,
            NodeDataType.BLOCK_LIST,
            NodeDataType.LIST,
            NodeDataType.DATA_TREE,
            NodeDataType.NBT
    };

    private VariableTypeOps() {
    }

    public static NodeDataType resolvePortType(BaseNode node, String portId) {
        if (node == null || portId == null) {
            return NodeDataType.ANY;
        }
        for (IPort port : node.getInputPorts()) {
            if (portId.equals(port.getId())) {
                NodeDataType effective = PortTypeResolver.resolveEffectiveType(port);
                return effective == null ? NodeDataType.ANY : effective;
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (portId.equals(port.getId())) {
                NodeDataType effective = PortTypeResolver.resolveEffectiveType(port);
                return effective == null ? NodeDataType.ANY : effective;
            }
        }
        return NodeDataType.ANY;
    }

    public static NodeDataType resolveWriteType(BaseNode node, String valuePortId, @Nullable Object value) {
        NodeDataType bound = resolvePortType(node, valuePortId);
        if (bound != NodeDataType.ANY) {
            return bound;
        }
        return inferFromValue(value);
    }

    public static NodeDataType inferFromValue(@Nullable Object value) {
        if (value == null) {
            return NodeDataType.ANY;
        }
        for (NodeDataType type : INFER_PRIORITY) {
            if (type.isCompatible(value)) {
                return type;
            }
        }
        return NodeDataType.ANY;
    }

    /**
     * Whether a write/read of {@code candidate} is allowed against a slot typed {@code slotType}.
     * {@link NodeDataType#ANY} on either side is permissive.
     */
    public static boolean typesAgree(NodeDataType slotType, NodeDataType candidate) {
        NodeDataType slot = slotType == null ? NodeDataType.ANY : slotType;
        NodeDataType other = candidate == null ? NodeDataType.ANY : candidate;
        if (slot == NodeDataType.ANY || other == NodeDataType.ANY) {
            return true;
        }
        return slot == other;
    }

    public static @Nullable String writeTypeMismatchError(String name, NodeDataType slotType, NodeDataType writeType) {
        if (typesAgree(slotType, writeType)) {
            return null;
        }
        return "Variable '" + name + "' is " + slotType.getId()
                + " but write value is " + writeType.getId() + ".";
    }

    public static @Nullable String readTypeMismatchError(String name, NodeDataType slotType, NodeDataType expectedType) {
        if (typesAgree(slotType, expectedType)) {
            return null;
        }
        return "Variable '" + name + "' is " + slotType.getId()
                + " but this node expects " + expectedType.getId() + ".";
    }
}

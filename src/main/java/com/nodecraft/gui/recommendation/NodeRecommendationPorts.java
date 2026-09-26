package com.nodecraft.gui.recommendation;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;

import java.util.List;

public final class NodeRecommendationPorts {

    private NodeRecommendationPorts() {
    }

    public static NodeDataType resolvePortDataType(INode node, String portId, boolean output) {
        List<IPort> ports = output ? node.getOutputPorts() : node.getInputPorts();
        for (IPort port : ports) {
            if (port.getId().equals(portId)) {
                return port.getDataType();
            }
        }
        return null;
    }

    public static String resolveOutputPortId(INode node, String preferredPortId) {
        if (preferredPortId != null) {
            for (IPort port : node.getOutputPorts()) {
                if (port.getId().equals(preferredPortId)) {
                    return preferredPortId;
                }
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (port.getDataType() != NodeDataType.EXEC) {
                return port.getId();
            }
        }
        return node.getOutputPorts().isEmpty() ? null : node.getOutputPorts().getFirst().getId();
    }

    public static String resolveInputPortId(INode node, String preferredPortId) {
        if (preferredPortId != null) {
            for (IPort port : node.getInputPorts()) {
                if (port.getId().equals(preferredPortId)) {
                    return preferredPortId;
                }
            }
        }
        for (IPort port : node.getInputPorts()) {
            if (port.getDataType() != NodeDataType.EXEC) {
                return port.getId();
            }
        }
        return node.getInputPorts().isEmpty() ? null : node.getInputPorts().getFirst().getId();
    }
}

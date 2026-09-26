package com.nodecraft.gui.components.node;

import com.nodecraft.nodesystem.graph.NodeGraph;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared graph mutations used by node action chrome (port-branch add/remove).
 */
public final class NodeActionGraphSupport {

    private NodeActionGraphSupport() {
    }

    public static void removeConnectionsForPort(
            Supplier<NodeGraph> graphSupplier,
            UUID nodeId,
            String portId,
            boolean inputPort
    ) {
        NodeGraph graph = graphSupplier.get();
        if (graph == null || portId == null) {
            return;
        }

        for (NodeGraph.Connection connection : graph.getConnections()) {
            boolean matched;
            if (inputPort) {
                matched = connection.targetNode().getId().equals(nodeId)
                        && connection.targetPort().getId().equals(portId);
            } else {
                matched = connection.sourceNode().getId().equals(nodeId)
                        && connection.sourcePort().getId().equals(portId);
            }

            if (matched) {
                graph.removeConnection(connection);
            }
        }
    }
}

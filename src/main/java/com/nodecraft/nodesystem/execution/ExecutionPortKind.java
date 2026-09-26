package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;

/**
 * Helpers for separating data ports from execution-control ports.
 */
public final class ExecutionPortKind {

    private ExecutionPortKind() {
    }

    public static boolean isExecPort(IPort port) {
        return port != null && port.getDataType() == NodeDataType.EXEC;
    }

    public static boolean isDataPort(IPort port) {
        return port != null && port.getDataType() != NodeDataType.EXEC;
    }

    public static boolean isExecConnection(NodeGraph.Connection connection) {
        if (connection == null || connection.sourcePort() == null || connection.targetPort() == null) {
            return false;
        }
        return isExecPort(connection.sourcePort()) && isExecPort(connection.targetPort());
    }

    public static boolean isDataConnection(NodeGraph.Connection connection) {
        if (connection == null || connection.sourcePort() == null || connection.targetPort() == null) {
            return false;
        }
        return isDataPort(connection.sourcePort()) && isDataPort(connection.targetPort());
    }
}

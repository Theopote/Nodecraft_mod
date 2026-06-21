package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.ExecRoutingNode;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves which exec output ports fired after a node computes.
 */
public final class ExecRouting {

    private ExecRouting() {
    }

    public static Set<String> activeExecOutputPortIds(INode node) {
        return Set.copyOf(orderedActiveExecOutputPortIds(node));
    }

    public static List<String> orderedActiveExecOutputPortIds(INode node) {
        if (node instanceof ExecRoutingNode routingNode) {
            Set<String> ports = routingNode.getActiveExecOutputPortIds();
            if (ports == null || ports.isEmpty()) {
                return List.of();
            }
            if (ports instanceof LinkedHashSet<String>) {
                return List.copyOf(ports);
            }
            return List.copyOf(ports);
        }

        List<String> fired = new ArrayList<>();
        for (IPort port : node.getOutputPorts()) {
            if (!ExecutionPortKind.isExecPort(port)) {
                continue;
            }
            if (Boolean.TRUE.equals(node.getOutput(port.getId()))) {
                fired.add(port.getId());
            }
        }
        return List.copyOf(fired);
    }

    public static boolean drainExecPortsSequentially(INode node) {
        return node instanceof ExecRoutingNode routingNode && routingNode.drainExecPortsSequentially();
    }
}

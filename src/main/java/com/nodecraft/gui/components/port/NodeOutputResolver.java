package com.nodecraft.gui.components.port;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.utilities.organization.SubgraphCallStackBridge;
import com.nodecraft.nodesystem.nodes.variable.VariableScopeBridge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class NodeOutputResolver {
    private static final int MAX_RESOLVE_DEPTH = 20;
    private static final String PREVIEW_NODE_TYPE_PREFIX = "output.preview.";

    private NodeOutputResolver() {
    }

    static Object resolveNodeOutput(NodeGraph graph, INode node, String outputPortId) {
        return resolveNodeOutput(graph, node, outputPortId, new java.util.HashSet<>(), 0);
    }

    static boolean shouldUseCachedOutput(INode node) {
        if (node == null || node.getTypeId() == null) {
            return false;
        }
        return node.getTypeId().startsWith(PREVIEW_NODE_TYPE_PREFIX);
    }

    private static Map<String, Object> collectConnectedInputs(NodeGraph graph, INode node, Set<UUID> visiting, int depth) {
        Map<String, Object> inputs = new HashMap<>();
        if (graph == null || node == null) {
            return inputs;
        }

        Map<String, IPort> inputPortsById = new HashMap<>();
        for (IPort port : node.getInputPorts()) {
            inputPortsById.put(port.getId(), port);
        }

        for (NodeGraph.Connection connection : graph.getConnections()) {
            if (connection.targetNode.getId().equals(node.getId())) {
                Object value = resolveNodeOutput(graph, connection.sourceNode, connection.sourcePort.getId(), visiting, depth + 1);
                mergeCollectedInput(inputs, inputPortsById.get(connection.targetPort.getId()), value);
            }
        }
        return inputs;
    }

    private static void mergeCollectedInput(Map<String, Object> inputs, IPort targetPort, Object value) {
        if (targetPort == null) {
            return;
        }

        String portId = targetPort.getId();
        if (targetPort.allowsMultipleIncomingConnections()) {
            List<Object> values = null;
            Object existing = inputs.get(portId);
            if (existing instanceof List<?> existingList) {
                values = new ArrayList<>(existingList);
            }
            if (values == null) {
                values = new ArrayList<>();
                if (existing != null) {
                    values.add(existing);
                }
            }
            values.add(value);
            inputs.put(portId, values);
            return;
        }

        inputs.put(portId, value);
    }

    private static Object resolveNodeOutput(NodeGraph graph, INode node, String outputPortId, Set<UUID> visiting, int depth) {
        if (graph == null || node == null || outputPortId == null) {
            return null;
        }

        if (shouldUseCachedOutput(node)) {
            Object cached = node.getOutput(outputPortId);
            return cached != null ? cached : getPortValue(node, outputPortId, false);
        }

        if (depth > MAX_RESOLVE_DEPTH) {
            Object cached = node.getOutput(outputPortId);
            return cached != null ? cached : getPortValue(node, outputPortId, false);
        }

        if (visiting.contains(node.getId())) {
            Object cached = node.getOutput(outputPortId);
            return cached != null ? cached : getPortValue(node, outputPortId, false);
        }

        boolean hasIncomingConnections = false;
        for (NodeGraph.Connection connection : graph.getConnections()) {
            if (connection.targetNode.getId().equals(node.getId())) {
                hasIncomingConnections = true;
                break;
            }
        }

        if (hasIncomingConnections) {
            visiting.add(node.getId());
            try {
                Map<String, Object> inputs = collectConnectedInputs(graph, node, visiting, depth);
                for (IPort port : node.getInputPorts()) {
                    inputs.putIfAbsent(port.getId(), port.getValue());
                }
                try (VariableScopeBridge.ScopeBinding ignored =
                         VariableScopeBridge.bindFallbackScope(graph.getId().toString());
                     SubgraphCallStackBridge.ScopeBinding ignoredStack =
                         SubgraphCallStackBridge.bindFallbackScope(graph.getId().toString())) {
                    try {
                        node.compute(inputs);
                    } catch (Exception e) {
                        // Property panel resolves outputs in UI render path; swallow node compute failures here
                        // so one bad node does not corrupt ImGui stack state.
                        NodeCraft.LOGGER.error(
                            "NodeOutputResolver safe-compute failed: nodeType={}, nodeId={}, outputPort={}",
                            node.getClass().getSimpleName(),
                            node.getId(),
                            outputPortId,
                            e
                        );
                        Object cached = node.getOutput(outputPortId);
                        return cached != null ? cached : getPortValue(node, outputPortId, false);
                    }
                }
            } finally {
                visiting.remove(node.getId());
            }
        }

        Object value = node.getOutput(outputPortId);
        return value != null ? value : getPortValue(node, outputPortId, false);
    }

    private static Object getPortValue(INode node, String portId, boolean inputPort) {
        List<IPort> ports = inputPort ? node.getInputPorts() : node.getOutputPorts();
        for (IPort port : ports) {
            if (port.getId().equals(portId)) {
                return port.getValue();
            }
        }
        return null;
    }
}

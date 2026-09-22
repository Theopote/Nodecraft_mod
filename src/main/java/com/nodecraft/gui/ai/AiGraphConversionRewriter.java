package com.nodecraft.gui.ai;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Inserts TypeConversionRegistry bridge nodes into AI DSL graphs when a planned
 * connection is EXPLICIT_REQUIRED rather than implicitly connectable.
 */
public final class AiGraphConversionRewriter {

    private AiGraphConversionRewriter() {
    }

    public record RewriteResult(
            AiGraphDslSupport.DslGraph graph,
            List<String> warnings,
            int insertedConverters
    ) {
    }

    public static RewriteResult rewrite(AiGraphDslSupport.DslGraph graph, NodeRegistry registry) {
        if (graph == null || registry == null || graph.connections() == null || graph.connections().isEmpty()) {
            return new RewriteResult(graph, List.of(), 0);
        }

        List<String> warnings = new ArrayList<>();
        List<AiGraphDslSupport.DslNode> nodes = new ArrayList<>(graph.nodes());
        List<AiGraphDslSupport.DslConnection> connections = new ArrayList<>();
        Map<String, AiGraphDslSupport.DslNode> byId = new HashMap<>();
        for (AiGraphDslSupport.DslNode node : nodes) {
            byId.put(node.id(), node);
        }

        Set<String> usedIds = new HashSet<>(byId.keySet());
        int inserted = 0;
        int converterSerial = 1;

        for (AiGraphDslSupport.DslConnection connection : graph.connections()) {
            AiGraphDslSupport.DslNode fromNode = byId.get(connection.from().nodeId());
            AiGraphDslSupport.DslNode toNode = byId.get(connection.to().nodeId());
            if (fromNode == null || toNode == null) {
                connections.add(connection);
                continue;
            }

            INode fromInstance;
            INode toInstance;
            try {
                fromInstance = registry.createNodeInstance(fromNode.type());
                toInstance = registry.createNodeInstance(toNode.type());
            } catch (Exception e) {
                connections.add(connection);
                continue;
            }

            IPort outPort = findPort(fromInstance.getOutputPorts(), connection.from().port());
            IPort inPort = findPort(toInstance.getInputPorts(), connection.to().port());
            if (outPort == null || inPort == null) {
                connections.add(connection);
                continue;
            }

            NodeDataType outType = outPort.getDataType();
            NodeDataType inType = inPort.getDataType();
            if (NodeDataType.isConnectableTo(outType, inType)) {
                connections.add(connection);
                continue;
            }

            if (!TypeConversionRegistry.requiresExplicitConversion(outType, inType)) {
                connections.add(connection);
                continue;
            }

            TypeConversionRegistry.ConversionSuggestion suggestion =
                    TypeConversionRegistry.getSuggestedConversion(outType, inType);
            if (suggestion == null || registry.getNodeInfo(suggestion.nodeId()) == null) {
                connections.add(connection);
                continue;
            }

            INode converterInstance;
            try {
                converterInstance = registry.createNodeInstance(suggestion.nodeId());
            } catch (Exception e) {
                connections.add(connection);
                continue;
            }

            String converterInput = findCompatibleInput(converterInstance, outType);
            String converterOutput = findCompatibleOutput(converterInstance, inType);
            if (converterInput == null || converterOutput == null) {
                connections.add(connection);
                continue;
            }

            String converterId = nextConverterId(usedIds, converterSerial++);
            usedIds.add(converterId);

            float midX = midpoint(fromNode.position().x(), toNode.position().x());
            float midY = midpoint(fromNode.position().y(), toNode.position().y()) + 40.0f;
            AiGraphDslSupport.DslNode converterNode = new AiGraphDslSupport.DslNode(
                    converterId,
                    suggestion.nodeId().toLowerCase(Locale.ROOT),
                    Map.of(),
                    new AiGraphDslSupport.DslPosition(midX, midY)
            );
            nodes.add(converterNode);
            byId.put(converterId, converterNode);
            inserted++;

            connections.add(new AiGraphDslSupport.DslConnection(
                    connection.from(),
                    new AiGraphDslSupport.DslEndpoint(converterId, converterInput)
            ));
            connections.add(new AiGraphDslSupport.DslConnection(
                    new AiGraphDslSupport.DslEndpoint(converterId, converterOutput),
                    connection.to()
            ));

            warnings.add("Inserted conversion node '" + suggestion.nodeId()
                    + "' (" + suggestion.displayName() + ") for "
                    + connection.from().nodeId() + "." + connection.from().port()
                    + " -> " + connection.to().nodeId() + "." + connection.to().port());
        }

        if (inserted == 0) {
            return new RewriteResult(graph, warnings, 0);
        }

        AiGraphDslSupport.DslGraph rewritten = new AiGraphDslSupport.DslGraph(
                List.copyOf(nodes),
                List.copyOf(connections),
                graph.description()
        );
        return new RewriteResult(rewritten, warnings, inserted);
    }

    private static String nextConverterId(Set<String> usedIds, int serial) {
        String candidate = "conv_" + serial;
        while (usedIds.contains(candidate)) {
            serial++;
            candidate = "conv_" + serial;
        }
        return candidate;
    }

    private static float midpoint(float a, float b) {
        return (a + b) * 0.5f;
    }

    private static String findCompatibleInput(INode node, NodeDataType sourceType) {
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (IPort port : node.getInputPorts()) {
            if (port.getDataType() == NodeDataType.EXEC) {
                continue;
            }
            if (!NodeDataType.isConnectableTo(sourceType, port.getDataType())) {
                continue;
            }
            int score = port.getDataType() == sourceType ? 100 : 50;
            if (port.isRequired()) {
                score += 20;
            }
            if (score > bestScore) {
                bestScore = score;
                best = port.getId();
            }
        }
        return best;
    }

    private static String findCompatibleOutput(INode node, NodeDataType targetType) {
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (IPort port : node.getOutputPorts()) {
            if (port.getDataType() == NodeDataType.EXEC) {
                continue;
            }
            if (!NodeDataType.isConnectableTo(port.getDataType(), targetType)) {
                continue;
            }
            int score = port.getDataType() == targetType ? 100 : 50;
            if (score > bestScore) {
                bestScore = score;
                best = port.getId();
            }
        }
        return best;
    }

    private static IPort findPort(List<IPort> ports, String portId) {
        if (ports == null || portId == null) {
            return null;
        }
        for (IPort port : ports) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }
}

package com.nodecraft.nodesystem.preset;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Instantiates node graphs from preset definitions.
 *
 * <p>Handles parameter substitution, node creation, and connection establishment.</p>
 */
public class PresetInstantiator {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresetInstantiator.class);

    /**
     * Instantiates a preset with the given parameter values.
     *
     * @param preset the preset definition
     * @param parameterValues map of parameter ID to value (uses defaults if not provided)
     * @return the instantiated node graph
     * @throws PresetInstantiationException if instantiation fails
     */
    public static NodeGraph instantiate(PresetDefinition preset, Map<String, Object> parameterValues)
            throws PresetInstantiationException {

        LOGGER.debug("Instantiating preset: {}", preset.getPresetId());

        // Merge provided values with defaults
        Map<String, Object> resolvedParams = resolveParameters(preset, parameterValues);

        // Validate all parameters
        for (PresetParameter param : preset.getParameters()) {
            Object value = resolvedParams.get(param.getId());
            resolvedParams.put(param.getId(), param.validateValue(value));
        }

        NodeGraph graph = new NodeGraph();
        Map<String, UUID> nodeIdMapping = new HashMap<>(); // preset node ID -> actual node UUID

        try {
            // Step 1: Create all nodes
            for (PresetGraph.PresetNodeDefinition nodeDef : preset.getGraph().getNodes()) {
                INode node = createNode(nodeDef, resolvedParams);
                graph.addNode(node);
                nodeIdMapping.put(nodeDef.getId(), node.getId());

                // Set position if provided
                if (nodeDef.getPosition() != null && !nodeDef.getPosition().isEmpty()) {
                    // Position will be handled by the editor
                    // Store as metadata if needed
                }
            }

            // Step 2: Create all connections
            for (PresetGraph.PresetConnectionDefinition connDef : preset.getGraph().getConnections()) {
                UUID fromNodeId = nodeIdMapping.get(connDef.getFrom().getNode());
                UUID toNodeId = nodeIdMapping.get(connDef.getTo().getNode());

                if (fromNodeId == null || toNodeId == null) {
                    throw new PresetInstantiationException(
                        "Connection references unknown node: " + connDef.getFrom().getNode() + " -> " + connDef.getTo().getNode()
                    );
                }

                INode fromNode = graph.getNode(fromNodeId);
                INode toNode = graph.getNode(toNodeId);

                if (fromNode == null || toNode == null) {
                    throw new PresetInstantiationException("Failed to find nodes for connection");
                }

                // Find ports and connect
                String fromPortId = connDef.getFrom().getPort();
                String toPortId = connDef.getTo().getPort();

                // The actual connection logic depends on your node graph API
                // This is a placeholder - adjust based on your actual API
                try {
                    graph.connect(fromNodeId, fromPortId, toNodeId, toPortId);
                } catch (Exception e) {
                    LOGGER.warn("Failed to create connection: {} -> {}", fromPortId, toPortId, e);
                    // Continue - some connections might fail but graph might still be useful
                }
            }

            LOGGER.info("Successfully instantiated preset: {} with {} nodes, {} connections",
                preset.getPresetId(), graph.getNodes().size(), preset.getGraph().getConnections().size());

            return graph;

        } catch (Exception e) {
            LOGGER.error("Failed to instantiate preset: {}", preset.getPresetId(), e);
            throw new PresetInstantiationException("Failed to instantiate preset: " + e.getMessage(), e);
        }
    }

    /**
     * Instantiates a preset with default parameter values.
     *
     * @param preset the preset definition
     * @return the instantiated node graph
     * @throws PresetInstantiationException if instantiation fails
     */
    public static NodeGraph instantiate(PresetDefinition preset) throws PresetInstantiationException {
        return instantiate(preset, Map.of());
    }

    /**
     * Creates a single node from a preset node definition.
     *
     * @param nodeDef the node definition
     * @param parameterValues resolved parameter values
     * @return the created node
     * @throws PresetInstantiationException if node creation fails
     */
    private static INode createNode(PresetGraph.PresetNodeDefinition nodeDef, Map<String, Object> parameterValues)
            throws PresetInstantiationException {

        try {
            // Create node instance
            INode node = NodeRegistry.getInstance().createNodeInstance(nodeDef.getType());

            if (node == null) {
                throw new PresetInstantiationException("Unknown node type: " + nodeDef.getType());
            }

            // Set node parameters with substitution
            for (Map.Entry<String, Object> entry : nodeDef.getParameters().entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();

                // Resolve parameter references
                Object resolvedValue = resolveParameterValue(paramValue, parameterValues);

                // Set the input value on the node
                try {
                    node.setInput(paramName, resolvedValue);
                } catch (Exception e) {
                    LOGGER.warn("Failed to set parameter {} on node {}: {}", paramName, nodeDef.getType(), e.getMessage());
                    // Continue - some parameters might be optional
                }
            }

            return node;

        } catch (Exception e) {
            throw new PresetInstantiationException("Failed to create node: " + nodeDef.getType(), e);
        }
    }

    /**
     * Resolves parameter values by merging provided values with defaults.
     *
     * @param preset the preset definition
     * @param providedValues user-provided parameter values
     * @return merged parameter values
     */
    private static Map<String, Object> resolveParameters(PresetDefinition preset, Map<String, Object> providedValues) {
        Map<String, Object> resolved = new HashMap<>();

        // Start with defaults
        for (PresetParameter param : preset.getParameters()) {
            resolved.put(param.getId(), param.getDefaultValue());
        }

        // Override with provided values
        if (providedValues != null) {
            resolved.putAll(providedValues);
        }

        return resolved;
    }

    /**
     * Resolves a parameter value, handling parameter references.
     *
     * <p>Parameter references are objects with a "param" key, e.g., {"param": "width"}.</p>
     *
     * @param value the value to resolve
     * @param parameterValues the parameter value map
     * @return the resolved value
     */
    @SuppressWarnings("unchecked")
    private static Object resolveParameterValue(Object value, Map<String, Object> parameterValues) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;

            // Check if this is a parameter reference
            if (map.containsKey("param")) {
                String paramId = map.get("param").toString();
                Object paramValue = parameterValues.get(paramId);
                if (paramValue == null) {
                    LOGGER.warn("Parameter reference not found: {}", paramId);
                    return value;
                }
                return paramValue;
            }

            // Otherwise, recursively resolve nested maps
            Map<String, Object> resolvedMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                resolvedMap.put(entry.getKey(), resolveParameterValue(entry.getValue(), parameterValues));
            }
            return resolvedMap;

        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            return list.stream()
                .map(item -> resolveParameterValue(item, parameterValues))
                .collect(java.util.stream.Collectors.toList());
        }

        return value;
    }

    /**
     * Exception thrown when preset instantiation fails.
     */
    public static class PresetInstantiationException extends Exception {
        public PresetInstantiationException(String message) {
            super(message);
        }

        public PresetInstantiationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

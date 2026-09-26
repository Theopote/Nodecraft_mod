package com.nodecraft.nodesystem.preset;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
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

    public record LayoutPoint(float x, float y) {
    }

    public record InstantiateResult(NodeGraph graph, Map<UUID, LayoutPoint> nodePositions) {
    }

    /**
     * Instantiates a preset with the given parameter values.
     *
     * @param preset the preset definition
     * @param parameterValues map of parameter ID to value (uses defaults if not provided)
     * @return the instantiated node graph
     * @throws PresetInstantiationException if instantiation fails
     */
    public static InstantiateResult instantiateWithLayout(PresetDefinition preset, Map<String, Object> parameterValues)
            throws PresetInstantiationException {

        LOGGER.debug("Instantiating preset: {}", preset.presetId());

        // Merge provided values with defaults
        Map<String, Object> resolvedParams = resolveParameters(preset, parameterValues);

        // Validate all parameters
        for (PresetParameter param : preset.parameters()) {
            resolvedParams.compute(param.id(), (k, value) -> param.validateValue(value));
        }

        NodeGraph graph = new NodeGraph();
        Map<String, UUID> nodeIdMapping = new HashMap<>();
        Map<UUID, LayoutPoint> nodePositions = new LinkedHashMap<>();

        try {
            for (PresetGraph.PresetNodeDefinition nodeDef : preset.graph().nodes()) {
                INode node = createNode(nodeDef, resolvedParams);
                graph.addNode(node);
                nodeIdMapping.put(nodeDef.id(), node.getId());

                Map<String, Double> position = nodeDef.position();
                if (position != null) {
                    float x = position.getOrDefault("x", 0.0).floatValue();
                    float y = position.getOrDefault("y", 0.0).floatValue();
                    nodePositions.put(node.getId(), new LayoutPoint(x, y));
                }
            }

            for (PresetGraph.PresetConnectionDefinition connDef : preset.graph().connections()) {
                UUID fromNodeId = nodeIdMapping.get(connDef.from().node());
                UUID toNodeId = nodeIdMapping.get(connDef.to().node());

                if (fromNodeId == null || toNodeId == null) {
                    throw new PresetInstantiationException(
                            "Connection references unknown node: "
                                    + connDef.from().node() + " -> " + connDef.to().node()
                    );
                }

                String fromPortId = connDef.from().port();
                String toPortId = connDef.to().port();

                try {
                    graph.connect(fromNodeId, fromPortId, toNodeId, toPortId);
                } catch (Exception e) {
                    LOGGER.warn(
                            "Failed to create connection {}.{} -> {}.{} in preset {}: {}",
                            connDef.from().node(),
                            fromPortId,
                            connDef.to().node(),
                            toPortId,
                            preset.presetId(),
                            e.getMessage()
                    );
                }
            }

            if (graph.getNodes().isEmpty()) {
                throw new PresetInstantiationException("Preset produced no nodes: " + preset.presetId());
            }

            LOGGER.info(
                    "Successfully instantiated preset: {} with {} nodes, {} connections",
                    preset.presetId(),
                    graph.getNodes().size(),
                    preset.graph().connections().size()
            );

            return new InstantiateResult(graph, Map.copyOf(nodePositions));

        } catch (PresetInstantiationException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to instantiate preset: {}", preset.presetId(), e);
            throw new PresetInstantiationException("Failed to instantiate preset: " + e.getMessage(), e);
        }
    }

    /**
     * Instantiates a preset with default parameter values and editor layout positions.
     */
    public static InstantiateResult instantiateWithLayout(PresetDefinition preset) throws PresetInstantiationException {
        return instantiateWithLayout(preset, Map.of());
    }

    /**
     * Instantiates a preset with the given parameter values.
     */
    public static NodeGraph instantiate(PresetDefinition preset, Map<String, Object> parameterValues)
            throws PresetInstantiationException {
        return instantiateWithLayout(preset, parameterValues).graph();
    }

    /**
     * Instantiates a preset with default parameter values.
     */
    public static NodeGraph instantiate(PresetDefinition preset) throws PresetInstantiationException {
        return instantiateWithLayout(preset, Map.of()).graph();
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
            INode node = NodeRegistry.getInstance().createNodeInstance(nodeDef.type());

            if (node == null) {
                throw new PresetInstantiationException("Unknown node type: " + nodeDef.type());
            }

            // Set node parameters with substitution
            for (Map.Entry<String, Object> entry : nodeDef.parameters().entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();

                // Resolve parameter references
                Object resolvedValue = resolveParameterValue(paramValue, parameterValues);

                // Set the input value on the node
                try {
                    node.setInput(paramName, resolvedValue);
                } catch (Exception e) {
                    LOGGER.warn("Failed to set parameter {} on node {}: {}", paramName, nodeDef.type(), e.getMessage());
                    // Continue - some parameters might be optional
                }
            }

            return node;

        } catch (Exception e) {
            throw new PresetInstantiationException("Failed to create node: " + nodeDef.type(), e);
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
        for (PresetParameter param : preset.parameters()) {
            resolved.put(param.id(), param.defaultValue());
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

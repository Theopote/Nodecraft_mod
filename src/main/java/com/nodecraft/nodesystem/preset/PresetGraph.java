package com.nodecraft.nodesystem.preset;

import java.util.List;
import java.util.Map;

/**
 * Represents the node graph structure in a preset.
 *
 * <p>Contains node definitions, connections, and parameter bindings.</p>
 */
public record PresetGraph(List<PresetNodeDefinition> nodes, List<PresetConnectionDefinition> connections) {
    public PresetGraph(List<PresetNodeDefinition> nodes, List<PresetConnectionDefinition> connections) {
        this.nodes = nodes != null ? nodes : List.of();
        this.connections = connections != null ? connections : List.of();
    }

    /**
     * Gets a node definition by its ID.
     *
     * @param nodeId the node identifier within this preset
     * @return the node definition, or null if not found
     */
    public PresetNodeDefinition getNode(String nodeId) {
        return nodes.stream()
                .filter(n -> n.id().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    /**
         * Represents a node definition in the preset graph.
         */
        public record PresetNodeDefinition(String id, String type, Map<String, Double> position,
                                           Map<String, Object> parameters) {
            public PresetNodeDefinition(String id, String type, Map<String, Double> position, Map<String, Object> parameters) {
                this.id = id;
                this.type = type;
                this.position = position;
                this.parameters = parameters != null ? parameters : Map.of();
            }
        }

    /**
         * Represents a connection between nodes in the preset graph.
         */
        public record PresetConnectionDefinition(ConnectionEndpoint from, ConnectionEndpoint to) {

        public record ConnectionEndpoint(String node, String port) {
        }
        }
}

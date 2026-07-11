package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.io.GraphFormat;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies incremental migrations to {@link SavedGraph} payloads loaded from disk or embedded JSON.
 */
public final class GraphMigrationRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphMigrationRegistry.class);

    /**
     * Old node type IDs to their replacement IDs for graphs below {@link GraphFormat#CURRENT}.
     * Add entries here when renaming node IDs; do not rely on {@link NodeRegistry#resolveCanonicalNodeId(String)}.
     */
    private static final Map<String, String> NODE_TYPE_ALIASES = Map.of(
        // Example: "math.operators.add_legacy", "math.operators.add"
    );

    /**
     * Old port IDs to replacement IDs keyed by node type ID after alias resolution.
     */
    private static final Map<String, Map<String, String>> PORT_ALIASES_BY_NODE_TYPE = Map.of(
        // Example: "math.operators.add", Map.of("input_a", "input_left")
    );

    private GraphMigrationRegistry() {
    }

    public static SavedGraph migrateToCurrent(SavedGraph input) {
        if (input == null) {
            return null;
        }

        int version = normalizeVersion(input.formatVersion);
        if (version > GraphFormat.CURRENT) {
            LOGGER.warn(
                "Saved graph format version {} is newer than supported version {}. Loading best-effort without migration.",
                version,
                GraphFormat.CURRENT
            );
            return input;
        }

        SavedGraph current = input;
        while (version < GraphFormat.CURRENT) {
            current = migrateStep(current, version);
            version++;
            current.formatVersion = version;
        }
        return current;
    }

    static int normalizeVersion(int formatVersion) {
        return formatVersion <= GraphFormat.LEGACY_UNSPECIFIED
            ? GraphFormat.LEGACY_UNSPECIFIED
            : formatVersion;
    }

    private static SavedGraph migrateStep(SavedGraph graph, int fromVersion) {
        return switch (fromVersion) {
            case GraphFormat.LEGACY_UNSPECIFIED -> migrateLegacyToV1(graph);
            default -> graph;
        };
    }

    private static SavedGraph migrateLegacyToV1(SavedGraph graph) {
        if (graph.nodes == null) {
            graph.nodes = new ArrayList<>();
        }
        if (graph.connections == null) {
            graph.connections = new ArrayList<>();
        }
        if (graph.nodePositions == null) {
            graph.nodePositions = new HashMap<>();
        }

        applyNodeTypeAliases(graph);
        applyPortAliases(graph);
        return graph;
    }

    private static void applyNodeTypeAliases(SavedGraph graph) {
        if (graph.nodes == null) {
            return;
        }

        NodeRegistry registry = NodeRegistry.getInstance();
        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            String normalized = registry.resolveCanonicalNodeId(node.typeId);
            String migrated = NODE_TYPE_ALIASES.getOrDefault(normalized, normalized);
            if (!normalized.equals(migrated)) {
                LOGGER.info("Migrated saved node type alias: {} -> {}", normalized, migrated);
            }
            node.typeId = migrated;
        }
    }

    private static void applyPortAliases(SavedGraph graph) {
        if (graph.connections == null || graph.nodes == null || PORT_ALIASES_BY_NODE_TYPE.isEmpty()) {
            return;
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        NodeRegistry registry = NodeRegistry.getInstance();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, registry.resolveCanonicalNodeId(node.typeId));
            }
        }

        for (SavedConnection connection : graph.connections) {
            if (connection == null) {
                continue;
            }
            connection.sourcePortId = remapPortId(nodeTypeBySavedId.get(connection.sourceNodeId), connection.sourcePortId);
            connection.targetPortId = remapPortId(nodeTypeBySavedId.get(connection.targetNodeId), connection.targetPortId);
        }
    }

    private static String remapPortId(String nodeTypeId, String portId) {
        if (nodeTypeId == null || portId == null) {
            return portId;
        }
        Map<String, String> aliases = PORT_ALIASES_BY_NODE_TYPE.get(nodeTypeId);
        if (aliases == null || aliases.isEmpty()) {
            return portId;
        }
        return aliases.getOrDefault(portId, portId);
    }

    /**
     * Returns a copy of the node-type alias table for diagnostics and tests.
     */
    static Map<String, String> nodeTypeAliases() {
        return Map.copyOf(NODE_TYPE_ALIASES);
    }
}

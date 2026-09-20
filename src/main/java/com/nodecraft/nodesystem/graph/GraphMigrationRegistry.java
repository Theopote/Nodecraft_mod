package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Applies incremental migrations to {@link SavedGraph} payloads loaded from disk or embedded JSON.
 * <p>
 * V0→V1 migration data lives in {@code nodecraft/migration/v0-to-v1.json}; runtime registries stay
 * canonical-only. Later steps apply Batch A/B remaps inline.
 */
public final class GraphMigrationRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphMigrationRegistry.class);

    private static final String INTEGER_SLIDER_TYPE = "input.numeric.integer_slider";
    private static final String LEGACY_INTEGER_SLIDER_VALUE_PORT = "value";
    private static final String INTEGER_SLIDER_OUTPUT_VALUE_PORT = "output_value";

    private static final String LEGACY_COORDINATE_INPUT_TYPE = "reference.points.point_from_coordinates";
    private static final String BLOCK_POSITION_INPUT_TYPE = "reference.points.block_position";

    private GraphMigrationRegistry() {
    }

    public static SavedGraph migrateToCurrent(SavedGraph input) {
        if (input == null) {
            return null;
        }

        SavedGraph graph = SavedGraphNormalizer.normalizeStructure(input);
        int version = GraphFormatVersion.normalize(graph.formatVersion);
        if (GraphFormatVersion.isNewerThanCurrent(version)) {
            LOGGER.warn(
                    "Saved graph format version {} is newer than supported version {}. Loading best-effort without migration.",
                    version,
                    GraphFormatVersion.CURRENT
            );
            return graph;
        }

        while (GraphFormatVersion.needsMigration(version)) {
            graph = migrateStep(graph, version);
            version++;
            graph.formatVersion = version;
        }
        return graph;
    }

    private static SavedGraph migrateStep(SavedGraph graph, int fromVersion) {
        return switch (fromVersion) {
            case GraphFormatVersion.V0 -> migrateV0ToV1(graph);
            case GraphFormatVersion.V1 -> migrateV1ToV2(graph);
            case GraphFormatVersion.V2 -> migrateV2ToV3(graph);
            default -> graph;
        };
    }

    private static SavedGraph migrateV0ToV1(SavedGraph graph) {
        GraphMigrationManifest manifest = GraphMigrationManifest.get();
        applyNodeTypeMigration(graph, manifest);
        applyNodeStateMigration(graph, manifest);
        applyPortMigration(graph, manifest);
        return graph;
    }

    /**
     * Batch A: rename Integer Slider output port {@code value} → {@code output_value}.
     */
    private static SavedGraph migrateV1ToV2(SavedGraph graph) {
        if (graph.connections == null || graph.nodes == null) {
            return graph;
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase());
            }
        }

        for (SavedConnection connection : graph.connections) {
            if (connection == null || connection.sourcePortId == null) {
                continue;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            if (INTEGER_SLIDER_TYPE.equals(sourceType)
                    && LEGACY_INTEGER_SLIDER_VALUE_PORT.equalsIgnoreCase(connection.sourcePortId)) {
                LOGGER.debug(
                        "Migrated integer slider port: {} -> {}",
                        connection.sourcePortId,
                        INTEGER_SLIDER_OUTPUT_VALUE_PORT
                );
                connection.sourcePortId = INTEGER_SLIDER_OUTPUT_VALUE_PORT;
            }
        }
        return graph;
    }

    /**
     * Batch B: rename Coordinate Input type id to Block Position Input.
     */
    private static SavedGraph migrateV2ToV3(SavedGraph graph) {
        if (graph.nodes == null) {
            return graph;
        }
        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            if (LEGACY_COORDINATE_INPUT_TYPE.equalsIgnoreCase(node.typeId)) {
                LOGGER.debug("Migrated node type: {} -> {}", node.typeId, BLOCK_POSITION_INPUT_TYPE);
                node.typeId = BLOCK_POSITION_INPUT_TYPE;
            }
        }
        return graph;
    }

    private static void applyNodeTypeMigration(SavedGraph graph, GraphMigrationManifest manifest) {
        if (graph.nodes == null) {
            return;
        }
        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            String migrated = manifest.migrateNodeTypeId(node.typeId);
            if (!migrated.equalsIgnoreCase(node.typeId)) {
                LOGGER.debug("Migrated saved node type: {} -> {}", node.typeId, migrated);
            }
            node.typeId = migrated;
        }
    }

    private static void applyNodeStateMigration(SavedGraph graph, GraphMigrationManifest manifest) {
        if (graph.nodes == null) {
            return;
        }
        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            node.state = manifest.migrateNodeState(node.typeId, node.state);
        }
    }

    private static void applyPortMigration(SavedGraph graph, GraphMigrationManifest manifest) {
        if (graph.connections == null || graph.nodes == null) {
            return;
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase());
            }
        }

        for (SavedConnection connection : graph.connections) {
            if (connection == null) {
                continue;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            connection.sourcePortId = manifest.migratePortId(sourceType, connection.sourcePortId, true);
            connection.targetPortId = manifest.migratePortId(targetType, connection.targetPortId, false);
        }
    }

    static Map<String, String> nodeTypeAliases() {
        return GraphMigrationManifest.get().nodeTypeAliases();
    }
}

package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Applies incremental migrations to {@link SavedGraph} payloads loaded from disk or embedded JSON.
 * <p>
 * V0→V1 migration data lives in {@code nodecraft/migration/v0-to-v1.json}; runtime registries stay
 * canonical-only. Later steps apply Batch A/B/3 remaps inline.
 * <p>
 * Pre-release policy: migrate in-repo format bumps; do not preserve abandoned semantic variants
 * (e.g. Angle Slider {@code unit=RADIANS} is ignored — angles are degrees-only).
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
            case GraphFormatVersion.V3 -> migrateV3ToV4(graph);
            case GraphFormatVersion.V4 -> migrateV4ToV5(graph);
            case GraphFormatVersion.V5 -> migrateV5ToV6(graph);
            case GraphFormatVersion.V6 -> migrateV6ToV7(graph);
            case GraphFormatVersion.V7 -> migrateV7ToV8(graph);
            case GraphFormatVersion.V8 -> migrateV8ToV9(graph);
            case GraphFormatVersion.V9 -> migrateV9ToV10(graph);
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
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
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

    private static final String LEGACY_POINTS_TO_PATH_TYPE = "geometry.curves.curve_from_points";
    private static final String POINTS_TO_PATH_TYPE = "geometry.curves.points_to_path";
    private static final String LEGACY_PATH_TO_POINTS_TYPE = "geometry.curves.divide_curve_to_points";
    private static final String PATH_TO_POINTS_TYPE = "geometry.curves.path_to_points";

    /**
     * Only these Batch 3 PATH consumers remapped legacy triple path ports → {@code input_path}.
     * Architectural Railing / Staircase were migrated later in V9→V10.
     */
    private static final Set<String> PATH_INPUT_MIGRATION_NODE_TYPES = Set.of(
            "geometry.curves.evaluate_curve",
            "geometry.curves.rebuild_curve_length",
            "geometry.curves.frame_along_path",
            "geometry.curves.offset_curve_plane",
            "geometry.curves.path_to_points",
            "geometry.curves.voxelize_curve",
            "geometry.curves.rainbow_curve_offset",
            "geometry.curves.tween_curves",
            "geometry.curves.blend_curves",
            "geometry.curves.resample_polyline_length",
            "geometry.curves.polyline_length",
            "geometry.curves.offset_polyline_plane",
            "geometry.curves.fillet_polyline_corners",
            "geometry.solids.sweep",
            "geometry.solids.sweep_from_points",
            "pattern.linear.along_path",
            "pattern.linear.path_instances",
            "pattern.linear.curve_array_geometry",
            "geometry.architectural_primitives.array_along_curve",
            "transform.orientation.project_curve_to_plane",
            "reference.points.project_to_polyline",
            "reference.points.closest_point_to_object",
            "output.preview.preview_curves",
            "math.fields.curve_attractor_field",
            "transform.deformations.curve_attract"
    );

    /**
     * Batch 3: canonical Points To Path / Path To Points ids, and allowlisted legacy path ports
     * → {@code input_path}.
     */
    private static SavedGraph migrateV3ToV4(SavedGraph graph) {
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                if (LEGACY_POINTS_TO_PATH_TYPE.equalsIgnoreCase(node.typeId)) {
                    LOGGER.debug("Migrated node type: {} -> {}", node.typeId, POINTS_TO_PATH_TYPE);
                    node.typeId = POINTS_TO_PATH_TYPE;
                } else if (LEGACY_PATH_TO_POINTS_TYPE.equalsIgnoreCase(node.typeId)) {
                    LOGGER.debug("Migrated node type: {} -> {}", node.typeId, PATH_TO_POINTS_TYPE);
                    node.typeId = PATH_TO_POINTS_TYPE;
                }
            }
        }

        if (graph.connections == null || graph.nodes == null) {
            return graph;
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
            }
        }

        for (SavedConnection connection : graph.connections) {
            if (connection == null || connection.targetPortId == null) {
                continue;
            }
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            if (targetType == null || !PATH_INPUT_MIGRATION_NODE_TYPES.contains(targetType)) {
                continue;
            }
            String port = connection.targetPortId.toLowerCase(Locale.ROOT);
            String migrated = migrateLegacyPathInputPort(port);
            if (migrated != null && !migrated.equals(connection.targetPortId)) {
                LOGGER.debug("Migrated path port: {} ({}) -> {}", connection.targetPortId, targetType, migrated);
                connection.targetPortId = migrated;
            }
        }
        return graph;
    }

    private static @Nullable String migrateLegacyPathInputPort(String portId) {
        return switch (portId) {
            case "input_curve", "input_polyline", "input_line" -> "input_path";
            case "input_curve_a", "input_polyline_a", "input_line_a" -> "input_path_a";
            case "input_curve_b", "input_polyline_b", "input_line_b" -> "input_path_b";
            default -> null;
        };
    }

    private static final String LEGACY_PRISM_EXTRUDE_TYPE = "geometry.solids.extrude_profile";
    private static final String EXTRUDE_TYPE = "geometry.solids.extrude";
    private static final String LEGACY_SURFACE_STRIP_TO_GEOMETRY_TYPE = "geometry.solids.surface_strip_to_geometry";
    private static final String SURFACE_STRIP_TO_LATTICE_TYPE = "geometry.solids.surface_strip_to_lattice";

    /**
     * Batch 4: Extrude canonicalization + Surface Strip To Lattice rename.
     * Prefers player Extrude over legacy Prism By Profile Vector for saved graphs.
     */
    private static SavedGraph migrateV4ToV5(SavedGraph graph) {
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                if (LEGACY_PRISM_EXTRUDE_TYPE.equalsIgnoreCase(node.typeId)) {
                    LOGGER.debug("Migrated node type: {} -> {}", node.typeId, EXTRUDE_TYPE);
                    node.typeId = EXTRUDE_TYPE;
                } else if (LEGACY_SURFACE_STRIP_TO_GEOMETRY_TYPE.equalsIgnoreCase(node.typeId)) {
                    LOGGER.debug("Migrated node type: {} -> {}", node.typeId, SURFACE_STRIP_TO_LATTICE_TYPE);
                    node.typeId = SURFACE_STRIP_TO_LATTICE_TYPE;
                }
            }
        }

        if (graph.connections == null || graph.nodes == null) {
            return graph;
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
            }
        }

        for (SavedConnection connection : graph.connections) {
            if (connection == null) {
                continue;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);

            if (connection.targetPortId != null
                    && EXTRUDE_TYPE.equals(targetType)
                    && "input_extrusion_vector".equalsIgnoreCase(connection.targetPortId)) {
                connection.targetPortId = "input_direction";
            }
            if (connection.sourcePortId != null
                    && EXTRUDE_TYPE.equals(sourceType)
                    && "output_surface_strip".equalsIgnoreCase(connection.sourcePortId)) {
                connection.sourcePortId = "output_side_surface";
            }
        }
        return graph;
    }

    private static final String LEGACY_COMBINE_GEOMETRY_TYPE = "geometry.boolean.union";
    private static final String COMBINE_GEOMETRY_TYPE = "geometry.combine.geometry";

    private static final String ROTATE_VECTOR_TYPE = "transform.orientation.rotate_vector";
    private static final String LEGACY_ROTATE_VECTOR_ANGLE_PORT = "input_angle_rad";

    private static final String LEGACY_BAKE_GEOMETRY_TO_BLOCKS_TYPE = "output.execute.bake_geometry_to_blocks";
    private static final String VOXELIZE_GEOMETRY_TYPE = "geometry.voxel.voxelize_geometry";

    private static final Set<String> TRIG_DEGREES_INPUT_TYPES = Set.of(
            "math.trigonometry.sin",
            "math.trigonometry.cos",
            "math.trigonometry.tan"
    );
    private static final Set<String> TRIG_DEGREES_OUTPUT_TYPES = Set.of(
            "math.trigonometry.asin",
            "math.trigonometry.acos",
            "math.trigonometry.atan",
            "math.trigonometry.atan2"
    );
    private static final String LEGACY_TRIG_ANGLE_INPUT_PORT = "input_angle_rad";
    private static final String LEGACY_TRIG_ANGLE_OUTPUT_PORT = "output_angle_rad";

    /**
     * Batch 5: Combine Geometry is structural compose, not analytic boolean union.
     * Remap legacy {@code geometry.boolean.union} → {@code geometry.combine.geometry}.
     */
    private static SavedGraph migrateV5ToV6(SavedGraph graph) {
        if (graph.nodes == null) {
            return graph;
        }
        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            if (LEGACY_COMBINE_GEOMETRY_TYPE.equalsIgnoreCase(node.typeId)) {
                LOGGER.debug("Migrated node type: {} -> {}", node.typeId, COMBINE_GEOMETRY_TYPE);
                node.typeId = COMBINE_GEOMETRY_TYPE;
            }
        }
        return graph;
    }

    /**
     * Batch 6: Rotate Vector angle language freezes to degrees.
     * <p>
     * Pre-release policy: do <em>not</em> remap {@code input_angle_rad} → {@code input_angle}.
     * That would keep the upstream numeric payload but reinterpret radians as degrees
     * (e.g. {@code π/2} become ~1.57° instead of 90°). Drop the abandoned radians
     * connection instead — graphs must reconnect with degree values.
     */
    private static SavedGraph migrateV6ToV7(SavedGraph graph) {
        if (graph.connections == null || graph.nodes == null) {
            return graph;
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
            }
        }

        List<SavedConnection> kept = new ArrayList<>(graph.connections.size());
        for (SavedConnection connection : graph.connections) {
            if (connection == null) {
                continue;
            }
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            if (ROTATE_VECTOR_TYPE.equals(targetType)
                    && LEGACY_ROTATE_VECTOR_ANGLE_PORT.equalsIgnoreCase(connection.targetPortId)) {
                LOGGER.warn(
                        "Dropped Rotate Vector radians angle connection {} -> {} (pre-release: "
                                + "input_angle_rad is abandoned; reconnect with degrees to input_angle)",
                        connection.sourceNodeId,
                        connection.targetNodeId
                );
                continue;
            }
            kept.add(connection);
        }
        graph.connections = kept;
        return graph;
    }

    /**
     * Batch 9: Bake Geometry To Blocks was never a world write — remap to PURE Voxelize Geometry.
     * Ports ({@code input_geometry}, {@code output_blocks}, …) are unchanged.
     */
    private static SavedGraph migrateV7ToV8(SavedGraph graph) {
        if (graph.nodes == null) {
            return graph;
        }
        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            if (LEGACY_BAKE_GEOMETRY_TO_BLOCKS_TYPE.equalsIgnoreCase(node.typeId)) {
                LOGGER.debug("Migrated node type: {} -> {}", node.typeId, VOXELIZE_GEOMETRY_TYPE);
                node.typeId = VOXELIZE_GEOMETRY_TYPE;
            }
        }
        return graph;
    }

    /**
     * Batch 10: trigonometry freezes to degrees.
     * <p>
     * Pre-release policy: drop abandoned radians ports rather than remapping payloads
     * (would silently reinterpret radians as degrees). Graphs must reconnect with degree values.
     */
    private static SavedGraph migrateV8ToV9(SavedGraph graph) {
        if (graph.connections == null || graph.nodes == null) {
            return graph;
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
            }
        }

        List<SavedConnection> kept = new ArrayList<>(graph.connections.size());
        for (SavedConnection connection : graph.connections) {
            if (connection == null) {
                continue;
            }
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            if (TRIG_DEGREES_INPUT_TYPES.contains(targetType)
                    && LEGACY_TRIG_ANGLE_INPUT_PORT.equalsIgnoreCase(connection.targetPortId)) {
                LOGGER.warn(
                        "Dropped Sin/Cos/Tan radians angle connection {} -> {} (pre-release: "
                                + "input_angle_rad abandoned; reconnect degrees to input_angle)",
                        connection.sourceNodeId,
                        connection.targetNodeId
                );
                continue;
            }
            if (TRIG_DEGREES_OUTPUT_TYPES.contains(sourceType)
                    && LEGACY_TRIG_ANGLE_OUTPUT_PORT.equalsIgnoreCase(connection.sourcePortId)) {
                LOGGER.warn(
                        "Dropped inverse-trig radians angle connection {} -> {} (pre-release: "
                                + "output_angle_rad abandoned; reconnect degrees from output_angle)",
                        connection.sourceNodeId,
                        connection.targetNodeId
                );
                continue;
            }
            kept.add(connection);
        }
        graph.connections = kept;
        return graph;
    }

    private static final Set<String> ARCH_PATH_INPUT_MIGRATION_NODE_TYPES = Set.of(
            "geometry.architectural_primitives.railing",
            "geometry.architectural_primitives.staircase"
    );

    /**
     * Batch 13: Railing / Staircase join PATH language ({@code input_line} → {@code input_path}).
     */
    private static SavedGraph migrateV9ToV10(SavedGraph graph) {
        if (graph.connections == null || graph.nodes == null) {
            return graph;
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
            }
        }

        for (SavedConnection connection : graph.connections) {
            if (connection == null || connection.targetPortId == null) {
                continue;
            }
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            if (targetType == null || !ARCH_PATH_INPUT_MIGRATION_NODE_TYPES.contains(targetType)) {
                continue;
            }
            if ("input_line".equalsIgnoreCase(connection.targetPortId)) {
                LOGGER.debug("Migrated architectural path port: {} ({}) input_line -> input_path",
                        connection.targetNodeId, targetType);
                connection.targetPortId = "input_path";
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
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
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

package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
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
            case GraphFormatVersion.V10 -> migrateV10ToV11(graph);
            case GraphFormatVersion.V11 -> migrateV11ToV12(graph);
            case GraphFormatVersion.V12 -> migrateV12ToV13(graph);
            case GraphFormatVersion.V13 -> migrateV13ToV14(graph);
            case GraphFormatVersion.V14 -> migrateV14ToV15(graph);
            case GraphFormatVersion.V15 -> migrateV15ToV16(graph);
            case GraphFormatVersion.V16 -> migrateV16ToV17(graph);
            case GraphFormatVersion.V17 -> migrateV17ToV18(graph);
            case GraphFormatVersion.V18 -> migrateV18ToV19(graph);
            case GraphFormatVersion.V19 -> migrateV19ToV20(graph);
            case GraphFormatVersion.V20 -> migrateV20ToV21(graph);
            case GraphFormatVersion.V21 -> migrateV21ToV22(graph);
            case GraphFormatVersion.V22 -> migrateV22ToV23(graph);
            case GraphFormatVersion.V23 -> migrateV23ToV24(graph);
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
            "geometry.curves.path_length",
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
            "geometry.curves.closest_point_on_path",
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

    private static final String CLOSEST_POINT_TYPE = "reference.points.closest_point";
    private static final String LEGACY_DECONSTRUCT_POINT_AS_BLOCK_TYPE = "reference.points.deconstruct_point";
    private static final String DECONSTRUCT_BLOCK_POSITION_TYPE = "reference.points.deconstruct_block_position";

    /**
     * Spatial P1: Closest Point continuous output; Deconstruct Block Position rename.
     * <p>
     * Closest Point: {@code output_point_data} → {@code output_closest_point} (now POINT).
     * Legacy {@code output_closest_point} BLOCK_POS and {@code output_vector} wires are dropped
     * (pre-release: no hidden snap preservation).
     * Deconstruct: type id {@code deconstruct_point} (old BLOCK_POS node) → {@code deconstruct_block_position}.
     */
    private static SavedGraph migrateV10ToV11(SavedGraph graph) {
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                if (LEGACY_DECONSTRUCT_POINT_AS_BLOCK_TYPE.equalsIgnoreCase(node.typeId)) {
                    // Only remap when this graph version predates Deconstruct Point (POINT).
                    // At V10 the id still meant the BLOCK_POS deconstruct node.
                    LOGGER.debug("Migrated node type: {} -> {}", node.typeId, DECONSTRUCT_BLOCK_POSITION_TYPE);
                    node.typeId = DECONSTRUCT_BLOCK_POSITION_TYPE;
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

        // Drop legacy discrete / vector Closest Point outputs first, then remap continuous POINT alias.
        graph.connections.removeIf(connection -> {
            if (connection == null || connection.sourcePortId == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            if (!CLOSEST_POINT_TYPE.equals(sourceType)) {
                return false;
            }
            String port = connection.sourcePortId.toLowerCase(Locale.ROOT);
            if ("output_vector".equals(port) || "output_closest_point".equals(port)) {
                LOGGER.debug("Dropped Closest Point legacy {} connection from {}",
                        connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            return false;
        });

        for (SavedConnection connection : graph.connections) {
            if (connection == null || connection.sourcePortId == null) {
                continue;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            if (!CLOSEST_POINT_TYPE.equals(sourceType)) {
                continue;
            }
            if ("output_point_data".equalsIgnoreCase(connection.sourcePortId)) {
                LOGGER.debug("Migrated Closest Point port: output_point_data -> output_closest_point");
                connection.sourcePortId = "output_closest_point";
            }
        }
        return graph;
    }

    private static final String DOMAIN_INPUT_TYPE = "input.numeric.range";
    private static final String REMAP_TYPE = "math.scalar_math.remap";
    private static final String CLAMP_TYPE = "math.scalar_math.clamp";
    private static final String GRAPH_MAPPER_TYPE = "math.scalar_math.graph_mapper";
    private static final String RANDOM_NUMBER_TYPE = "math.random.random_number";
    private static final String RANDOM_NUMBERS_TYPE = "math.random.random_numbers";
    private static final String CIRCULAR_ANGLE_TYPE = "input.numeric.angle_picker";
    private static final String FRAME_ALONG_PATH_TYPE = "geometry.curves.frame_along_path";
    private static final String PATH_INSTANCES_TYPE = "pattern.linear.path_instances";
    private static final String ARC_TYPE = "geometry.curves.arc";
    private static final String BEZIER_TYPE = "geometry.curves.bezier";

    /**
     * Batch 14 numeric domain + path sampling language.
     */
    private static SavedGraph migrateV11ToV12(SavedGraph graph) {
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                if (FRAME_ALONG_PATH_TYPE.equalsIgnoreCase(node.typeId)) {
                    LOGGER.debug("Migrated node type: {} -> {}", node.typeId, PATH_INSTANCES_TYPE);
                    node.typeId = PATH_INSTANCES_TYPE;
                }
                migrateDomainInputNodeState(node);
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

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);
            String targetPort = connection.targetPortId == null ? "" : connection.targetPortId.toLowerCase(Locale.ROOT);

            if (CIRCULAR_ANGLE_TYPE.equals(sourceType) && "output_radians".equals(sourcePort)) {
                LOGGER.debug("Dropped Circular Angle radians output connection from {}", connection.sourceNodeId);
                return true;
            }
            if (PATH_INSTANCES_TYPE.equals(targetType) && "input_path_points".equals(targetPort)) {
                LOGGER.debug("Dropped Path Frames legacy input_path_points on {}", connection.targetNodeId);
                return true;
            }
            if (REMAP_TYPE.equals(targetType) && (targetPort.startsWith("input_in_") || targetPort.startsWith("input_out_"))) {
                LOGGER.debug("Dropped Remap legacy range port {} on {}", connection.targetPortId, connection.targetNodeId);
                return true;
            }
            if (CLAMP_TYPE.equals(targetType) && ("input_min".equals(targetPort) || "input_max".equals(targetPort))) {
                LOGGER.debug("Dropped Clamp legacy min/max port {} on {}", connection.targetPortId, connection.targetNodeId);
                return true;
            }
            if (GRAPH_MAPPER_TYPE.equals(targetType) && (targetPort.startsWith("input_in_") || targetPort.startsWith("input_out_"))) {
                LOGGER.debug("Dropped Graph Mapper legacy range port {} on {}", connection.targetPortId, connection.targetNodeId);
                return true;
            }
            if (RANDOM_NUMBER_TYPE.equals(targetType) && "input_count".equals(targetPort)) {
                LOGGER.debug("Dropped Random Number legacy count port on {} (use Random Numbers)", connection.targetNodeId);
                return true;
            }
            return false;
        });

        for (SavedConnection connection : graph.connections) {
            if (connection == null) {
                continue;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            if (DOMAIN_INPUT_TYPE.equals(sourceType)) {
                connection.sourcePortId = migrateDomainOutputPort(connection.sourcePortId);
            }
            if (REMAP_TYPE.equals(targetType)) {
                connection.targetPortId = migrateRemapInputPort(connection.targetPortId);
            }
            if (GRAPH_MAPPER_TYPE.equals(targetType)) {
                connection.targetPortId = migrateGraphMapperInputPort(connection.targetPortId);
            }
            if (CLAMP_TYPE.equals(targetType) && "input_min".equalsIgnoreCase(connection.targetPortId)) {
                connection.targetPortId = "input_domain";
            }
            if (RANDOM_NUMBER_TYPE.equals(targetType)) {
                if ("input_min".equalsIgnoreCase(connection.targetPortId) || "input_max".equalsIgnoreCase(connection.targetPortId)) {
                    connection.targetPortId = "input_domain";
                }
            }
            if (ARC_TYPE.equals(targetType) && "input_resolution".equalsIgnoreCase(connection.targetPortId)) {
                connection.targetPortId = "input_samples";
            }
            if (BEZIER_TYPE.equals(targetType) && "input_resolution".equalsIgnoreCase(connection.targetPortId)) {
                connection.targetPortId = "input_samples";
            }
            if (PATH_INSTANCES_TYPE.equals(targetType) && "output_origins".equalsIgnoreCase(connection.sourcePortId)) {
                connection.sourcePortId = "output_points";
            }
        }
        return graph;
    }

    private static final String RESAMPLE_PATH_TYPE = "geometry.curves.resample_path";
    private static final String LEGACY_RESAMPLE_POLYLINE_TYPE = "geometry.curves.resample_polyline_length";
    private static final String LEGACY_REBUILD_CURVE_TYPE = "geometry.curves.rebuild_curve_length";
    private static final String LEGACY_EVALUATE_PATH_TYPE = "geometry.curves.evaluate_path";
    private static final String EVALUATE_CURVE_TYPE = "geometry.curves.evaluate_curve";
    private static final String LEGACY_OFFSET_POLYLINE_TYPE = "geometry.curves.offset_polyline_plane";
    private static final String OFFSET_CURVE_PLANE_TYPE = "geometry.curves.offset_curve_plane";
    private static final String VOXELIZE_CURVE_TYPE = "geometry.curves.voxelize_curve";

    /**
     * Curve path language: merge resample/rebuild, consolidate evaluate, remove hidden sampling
     * from Path Frames / Voxelize / Offset, delete Offset Polyline.
     */
    private static SavedGraph migrateV12ToV13(SavedGraph graph) {
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                if (LEGACY_RESAMPLE_POLYLINE_TYPE.equalsIgnoreCase(node.typeId)
                        || LEGACY_REBUILD_CURVE_TYPE.equalsIgnoreCase(node.typeId)) {
                    LOGGER.debug("Migrated node type: {} -> {}", node.typeId, RESAMPLE_PATH_TYPE);
                    node.typeId = RESAMPLE_PATH_TYPE;
                } else if (LEGACY_EVALUATE_PATH_TYPE.equalsIgnoreCase(node.typeId)) {
                    LOGGER.debug("Migrated node type: {} -> {}", node.typeId, EVALUATE_CURVE_TYPE);
                    node.typeId = EVALUATE_CURVE_TYPE;
                } else if (LEGACY_OFFSET_POLYLINE_TYPE.equalsIgnoreCase(node.typeId)) {
                    LOGGER.debug("Migrated node type: {} -> {}", node.typeId, OFFSET_CURVE_PLANE_TYPE);
                    node.typeId = OFFSET_CURVE_PLANE_TYPE;
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

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);
            String targetPort = connection.targetPortId == null ? "" : connection.targetPortId.toLowerCase(Locale.ROOT);

            if (EVALUATE_CURVE_TYPE.equals(sourceType)
                    && ("output_normal".equals(sourcePort) || "output_binormal".equals(sourcePort))) {
                LOGGER.debug("Dropped Evaluate Path frame output connection from {}", connection.sourceNodeId);
                return true;
            }
            if (EVALUATE_CURVE_TYPE.equals(targetType) && "input_up_vector".equals(targetPort)) {
                LOGGER.debug("Dropped Evaluate Path up-vector input on {}", connection.targetNodeId);
                return true;
            }
            if (RESAMPLE_PATH_TYPE.equals(sourceType) && "output_curve".equals(sourcePort)) {
                LOGGER.debug("Dropped Resample Path legacy curve output from {}", connection.sourceNodeId);
                return true;
            }
            if (PATH_INSTANCES_TYPE.equals(targetType)
                    && ("input_mode".equals(targetPort) || "input_count".equals(targetPort) || "input_spacing".equals(targetPort))) {
                LOGGER.debug("Dropped Path Frames sampling port {} on {}", connection.targetPortId, connection.targetNodeId);
                return true;
            }
            if (VOXELIZE_CURVE_TYPE.equals(targetType)
                    && ("input_mode".equals(targetPort) || "input_count".equals(targetPort) || "input_spacing".equals(targetPort))) {
                LOGGER.debug("Dropped Voxelize Path sampling port {} on {}", connection.targetPortId, connection.targetNodeId);
                return true;
            }
            if (OFFSET_CURVE_PLANE_TYPE.equals(targetType)
                    && ("input_count".equals(targetPort) || "input_spacing".equals(targetPort))) {
                LOGGER.debug("Dropped Offset Path resample port {} on {}", connection.targetPortId, connection.targetNodeId);
                return true;
            }
            if (OFFSET_CURVE_PLANE_TYPE.equals(sourceType)
                    && ("output_polyline".equals(sourcePort) || "output_points".equals(sourcePort) || "output_length".equals(sourcePort))) {
                LOGGER.debug("Dropped Offset Path legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            return false;
        });

        for (SavedConnection connection : graph.connections) {
            if (connection == null) {
                continue;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);

            if (RESAMPLE_PATH_TYPE.equals(sourceType) && "output_polyline".equalsIgnoreCase(connection.sourcePortId)) {
                connection.sourcePortId = "output_path";
            }
            if (EVALUATE_CURVE_TYPE.equals(targetType) && "input_parameter".equalsIgnoreCase(connection.targetPortId)) {
                connection.targetPortId = "input_t";
            }
            if (OFFSET_CURVE_PLANE_TYPE.equals(sourceType) && "output_polyline".equalsIgnoreCase(connection.sourcePortId)) {
                connection.sourcePortId = "output_path";
            }
        }
        return graph;
    }

    private static final String TWEEN_CURVES_TYPE = "geometry.curves.tween_curves";
    private static final String BLEND_CURVES_TYPE = "geometry.curves.blend_curves";
    private static final String FILLET_POLYLINE_TYPE = "geometry.curves.fillet_polyline_corners";

    private static final Set<String> TWEEN_LEGACY_OUTPUT_PORTS = Set.of(
            "output_curves",
            "output_curves_tree",
            "output_polylines",
            "output_polylines_tree",
            "output_point_rows",
            "output_point_rows_tree",
            "output_first_polyline"
    );

    /**
     * Curve path P2: Tween emits PATH_LIST; Blend/Fillet emit PATH; Join Paths replaces Blend joined output.
     */
    private static SavedGraph migrateV13ToV14(SavedGraph graph) {
        if (graph.connections == null || graph.nodes == null) {
            return graph;
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
            }
        }

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);

            if (TWEEN_CURVES_TYPE.equals(sourceType) && TWEEN_LEGACY_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Tween Paths legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            if (BLEND_CURVES_TYPE.equals(sourceType)
                    && ("output_joined_polyline".equals(sourcePort) || "output_curve".equals(sourcePort))) {
                LOGGER.debug("Dropped Blend Paths legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            return false;
        });

        for (SavedConnection connection : graph.connections) {
            if (connection == null) {
                continue;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            if (BLEND_CURVES_TYPE.equals(sourceType) && "output_polyline".equalsIgnoreCase(connection.sourcePortId)) {
                connection.sourcePortId = "output_path";
            }
            if (FILLET_POLYLINE_TYPE.equals(sourceType) && "output_polyline".equalsIgnoreCase(connection.sourcePortId)) {
                connection.sourcePortId = "output_path";
            }
        }
        return graph;
    }

    private static final String PATH_LENGTH_TYPE = "geometry.curves.path_length";
    private static final String LEGACY_POLYLINE_LENGTH_TYPE = "geometry.curves.polyline_length";

    /**
     * Path Length canonical id rename.
     */
    private static SavedGraph migrateV14ToV15(SavedGraph graph) {
        if (graph.nodes == null) {
            return graph;
        }
        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            if (LEGACY_POLYLINE_LENGTH_TYPE.equalsIgnoreCase(node.typeId)) {
                LOGGER.debug("Migrated node type: {} -> {}", node.typeId, PATH_LENGTH_TYPE);
                node.typeId = PATH_LENGTH_TYPE;
            }
        }
        return graph;
    }

    private static final String CLOSEST_POINT_ON_PATH_TYPE = "geometry.curves.closest_point_on_path";
    private static final String LEGACY_PROJECT_TO_POLYLINE_TYPE = "reference.points.project_to_polyline";

    private static final Set<String> PROJECT_TO_POLYLINE_LEGACY_OUTPUT_PORTS = Set.of(
            "output_vector",
            "output_segment_index",
            "output_segment_t"
    );

    /**
     * Project Point To Polyline → Closest Point On Path; drop Fillet legacy polyline output wires.
     */
    private static SavedGraph migrateV15ToV16(SavedGraph graph) {
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                if (LEGACY_PROJECT_TO_POLYLINE_TYPE.equalsIgnoreCase(node.typeId)) {
                    LOGGER.debug("Migrated node type: {} -> {}", node.typeId, CLOSEST_POINT_ON_PATH_TYPE);
                    node.typeId = CLOSEST_POINT_ON_PATH_TYPE;
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

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);

            if (CLOSEST_POINT_ON_PATH_TYPE.equals(sourceType)
                    && PROJECT_TO_POLYLINE_LEGACY_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Closest Point On Path legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            return false;
        });

        for (SavedConnection connection : graph.connections) {
            if (connection == null) {
                continue;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            if (FILLET_POLYLINE_TYPE.equals(sourceType) && "output_polyline".equalsIgnoreCase(connection.sourcePortId)) {
                connection.sourcePortId = "output_path";
            }
        }
        return graph;
    }

    private static final String LEGACY_PATH_PARAMETER_AT_POINT_TYPE = "geometry.curves.path_parameter_at_point";

    /**
     * Path Parameter At Point → Closest Point On Path (port ids unchanged for parameter/distance/valid).
     */
    private static SavedGraph migrateV16ToV17(SavedGraph graph) {
        if (graph.nodes == null) {
            return graph;
        }
        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            if (LEGACY_PATH_PARAMETER_AT_POINT_TYPE.equalsIgnoreCase(node.typeId)) {
                LOGGER.debug("Migrated node type: {} -> {}", node.typeId, CLOSEST_POINT_ON_PATH_TYPE);
                node.typeId = CLOSEST_POINT_ON_PATH_TYPE;
            }
        }
        return graph;
    }

    private static final String TRANSFORM_FRAME_TYPE = "reference.frames.transform_frame";
    private static final String CONSTRUCT_FRAME_TYPE = "reference.frames.construct_frame";
    private static final String WORLD_FRAME_TYPE = "reference.frames.world_frame";
    private static final String TRANSFORM_BY_FRAMES_TYPE = "transform.basic_transforms.transform_by_frames";
    private static final String WORLD_PLANE_TYPE = "reference.planes.world_plane";
    private static final String OFFSET_PLANE_TYPE = "reference.planes.offset_plane";
    private static final String BOX_FACE_TO_PLANE_TYPE = "reference.planes.block_face_plane";
    private static final String FACE_CENTER_FRAME_TYPE = "reference.frames.frame_from_face";
    private static final String SPHERE_SURFACE_FRAME_TYPE = "reference.frames.frame_along_surface";
    private static final String CONSTRUCT_PLANE_TYPE = "reference.planes.construct_plane";
    private static final String PLANE_FROM_POINTS_TYPE = "reference.planes.plane_from_points";

    private static final Set<String> TRANSFORM_FRAME_LEGACY_OUTPUT_PORTS = Set.of(
            "output_origin",
            "output_x_axis",
            "output_y_axis",
            "output_z_axis",
            "output_plane"
    );

    private static final Set<String> TRANSFORM_FRAME_LEGACY_INPUT_PORTS = Set.of(
            "input_origin",
            "input_x_axis",
            "input_y_axis",
            "input_z_axis",
            "input_scale"
    );

    private static final Set<String> WORLD_FRAME_LEGACY_OUTPUT_PORTS = Set.of(
            "output_origin_pos",
            "output_origin",
            "output_x_axis",
            "output_y_axis",
            "output_z_axis",
            "output_xy_plane",
            "output_valid"
    );

    private static final Set<String> TRANSFORM_BY_FRAMES_LEGACY_INPUT_PORTS = Set.of(
            "input_origins",
            "input_x_axes",
            "input_y_axes",
            "input_z_axes"
    );

    private static final Set<String> TRANSFORM_BY_FRAMES_LEGACY_OUTPUT_PORTS = Set.of(
            "output_frame_count",
            "output_used_frame_count",
            "output_skipped_frame_count"
    );

    private static final Set<String> WORLD_PLANE_LEGACY_OUTPUT_PORTS = Set.of(
            "output_origin",
            "output_origin_vector",
            "output_normal"
    );

    private static final Set<String> OFFSET_PLANE_LEGACY_OUTPUT_PORTS = Set.of(
            "output_origin",
            "output_normal"
    );

    private static final Set<String> BOX_FACE_TO_PLANE_LEGACY_OUTPUT_PORTS = Set.of(
            "output_center",
            "output_normal",
            "output_name",
            "output_index"
    );

    private static final Set<String> FACE_CENTER_FRAME_LEGACY_OUTPUT_PORTS = Set.of(
            "output_plane",
            "output_x_axis",
            "output_y_axis",
            "output_z_axis",
            "output_normal",
            "output_corner_indices"
    );

    private static final Set<String> SPHERE_SURFACE_FRAME_LEGACY_OUTPUT_PORTS = Set.of(
            "output_center",
            "output_x_axis",
            "output_y_axis",
            "output_z_axis",
            "output_plane"
    );

    /**
     * Frame/plane v1: drop wires to removed producer ports; decomposed-frame graphs may need manual repair.
     */
    private static SavedGraph migrateV17ToV18(SavedGraph graph) {
        if (graph.connections == null || graph.nodes == null) {
            return graph;
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
            }
        }

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);
            String targetPort = connection.targetPortId == null ? "" : connection.targetPortId.toLowerCase(Locale.ROOT);

            if (TRANSFORM_FRAME_TYPE.equals(sourceType) && TRANSFORM_FRAME_LEGACY_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Transform Frame legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            if (TRANSFORM_FRAME_TYPE.equals(targetType) && TRANSFORM_FRAME_LEGACY_INPUT_PORTS.contains(targetPort)) {
                LOGGER.debug("Dropped Transform Frame legacy input {} to {}", connection.targetPortId, connection.targetNodeId);
                return true;
            }
            if (CONSTRUCT_FRAME_TYPE.equals(sourceType) && "output_plane".equals(sourcePort)) {
                LOGGER.debug("Dropped Construct Frame legacy output_plane from {}", connection.sourceNodeId);
                return true;
            }
            if (CONSTRUCT_FRAME_TYPE.equals(targetType) && "input_z_axis".equals(targetPort)) {
                LOGGER.debug("Dropped Construct Frame legacy input_z_axis to {}", connection.targetNodeId);
                return true;
            }
            if (WORLD_FRAME_TYPE.equals(sourceType) && WORLD_FRAME_LEGACY_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped World Frame legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            if (TRANSFORM_BY_FRAMES_TYPE.equals(targetType) && TRANSFORM_BY_FRAMES_LEGACY_INPUT_PORTS.contains(targetPort)) {
                LOGGER.debug("Dropped Transform Points by Frames legacy input {} to {}", connection.targetPortId, connection.targetNodeId);
                return true;
            }
            if (TRANSFORM_BY_FRAMES_TYPE.equals(sourceType) && TRANSFORM_BY_FRAMES_LEGACY_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Transform Points by Frames legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            if (WORLD_PLANE_TYPE.equals(sourceType) && WORLD_PLANE_LEGACY_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped World Plane legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            if (OFFSET_PLANE_TYPE.equals(sourceType) && OFFSET_PLANE_LEGACY_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Offset Plane legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            if (BOX_FACE_TO_PLANE_TYPE.equals(sourceType) && BOX_FACE_TO_PLANE_LEGACY_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Box Face To Plane legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            if (FACE_CENTER_FRAME_TYPE.equals(sourceType) && FACE_CENTER_FRAME_LEGACY_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Face Center Frame legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            if (SPHERE_SURFACE_FRAME_TYPE.equals(sourceType) && SPHERE_SURFACE_FRAME_LEGACY_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Sphere Surface Frame legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            if (CONSTRUCT_PLANE_TYPE.equals(sourceType) && "output_normalized_normal".equals(sourcePort)) {
                LOGGER.debug("Dropped Construct Plane legacy output_normalized_normal from {}", connection.sourceNodeId);
                return true;
            }
            if (PLANE_FROM_POINTS_TYPE.equals(sourceType) && "output_normal".equals(sourcePort)) {
                LOGGER.debug("Dropped Construct Plane From Points legacy output_normal from {}", connection.sourceNodeId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static final String BLOCK_TO_VECTOR_TYPE = "reference.points.block_to_vector";
    private static final String CLOSEST_POINT_TO_OBJECT_TYPE = "reference.points.closest_point_to_object";
    private static final String MID_POINT_TYPE = "reference.points.mid_point";
    private static final String BLOCK_TO_POINT_TYPE = "reference.points.point_from_block";
    private static final String POINT_ALONG_VECTOR_TYPE = "reference.points.point_along_vector";
    private static final String POINT_LIST_CENTER_TYPE = "reference.points.point_list_center";
    private static final String POINT_LIST_BOUNDS_TYPE = "reference.points.point_list_bounds";
    private static final String CONSTRUCT_COORDINATE_TYPE = "reference.points.construct_coordinate";
    private static final String PROJECT_TO_PLANE_TYPE = "transform.orientation.project_to_plane";
    private static final String ANGLE_BETWEEN_TYPE = "reference.vectors.angle_between";
    private static final String SLERP_VECTORS_TYPE = "reference.vectors.slerp";

    private static final Set<String> DELETED_NODE_TYPES = Set.of(
            BLOCK_TO_VECTOR_TYPE,
            CLOSEST_POINT_TO_OBJECT_TYPE
    );

    /**
     * Point/vector v1: drop legacy ports, remap angle outputs, remove deleted nodes.
     */
    private static SavedGraph migrateV18ToV19(SavedGraph graph) {
        if (graph.nodes == null) {
            return graph;
        }

        graph.nodes = new ArrayList<>(graph.nodes);
        graph.nodes.removeIf(node -> node != null && node.typeId != null
                && DELETED_NODE_TYPES.contains(node.typeId.toLowerCase(Locale.ROOT)));

        if (graph.connections == null) {
            migratePointAlongVectorState(graph);
            return graph;
        }

        graph.connections = new ArrayList<>(graph.connections);

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
            if (sourceType == null) {
                continue;
            }
            String sourcePort = connection.sourcePortId.toLowerCase(Locale.ROOT);
            if (ANGLE_BETWEEN_TYPE.equals(sourceType)) {
                if ("output_degrees".equals(sourcePort)) {
                    connection.sourcePortId = "output_angle";
                } else if ("output_signed_degrees".equals(sourcePort)) {
                    connection.sourcePortId = "output_signed_angle";
                }
            } else if (SLERP_VECTORS_TYPE.equals(sourceType) && "output_angle_radians".equals(sourcePort)) {
                connection.sourcePortId = "output_angle";
            }
        }

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            if (sourceType == null || targetType == null) {
                LOGGER.debug("Dropped connection involving removed node {} or {}",
                        connection.sourceNodeId, connection.targetNodeId);
                return true;
            }

            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);
            String targetPort = connection.targetPortId == null ? "" : connection.targetPortId.toLowerCase(Locale.ROOT);

            if (CLOSEST_POINT_TO_OBJECT_TYPE.equals(targetType) && "input_path".equals(targetPort)) {
                LOGGER.debug("Dropped Closest Point To Object legacy input_path to {}", connection.targetNodeId);
                return true;
            }

            if (shouldDropPointVectorLegacyOutput(sourceType, sourcePort)) {
                LOGGER.debug("Dropped legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }

            return false;
        });

        migratePointAlongVectorState(graph);
        return graph;
    }

    private static void migratePointAlongVectorState(SavedGraph graph) {
        if (graph.nodes == null) {
            return;
        }
        for (SavedNode node : graph.nodes) {
            if (node == null || !POINT_ALONG_VECTOR_TYPE.equalsIgnoreCase(node.typeId)) {
                continue;
            }
            if (node.state instanceof Map<?, ?> state) {
                Map<String, Object> migrated = new HashMap<>();
                for (Map.Entry<?, ?> entry : state.entrySet()) {
                    if (entry.getKey() instanceof String key && !"normalizeDirection".equals(key)) {
                        migrated.put(key, entry.getValue());
                    }
                }
                node.state = migrated.isEmpty() ? null : migrated;
            }
        }
    }

    private static boolean shouldDropPointVectorLegacyOutput(String sourceType, String sourcePort) {
        if (MID_POINT_TYPE.equals(sourceType) && "output_vector".equals(sourcePort)) {
            return true;
        }
        if (BLOCK_TO_POINT_TYPE.equals(sourceType)
                && ("output_vector".equals(sourcePort) || "output_x".equals(sourcePort)
                || "output_y".equals(sourcePort) || "output_z".equals(sourcePort))) {
            return true;
        }
        if (POINT_ALONG_VECTOR_TYPE.equals(sourceType)
                && ("output_vector".equals(sourcePort) || "output_direction".equals(sourcePort))) {
            return true;
        }
        if (POINT_LIST_CENTER_TYPE.equals(sourceType) && "output_center_vector".equals(sourcePort)) {
            return true;
        }
        if (POINT_LIST_BOUNDS_TYPE.equals(sourceType) && "output_region".equals(sourcePort)) {
            return true;
        }
        if (CONSTRUCT_COORDINATE_TYPE.equals(sourceType)
                && ("output_coordinate".equals(sourcePort) || "output_x".equals(sourcePort)
                || "output_y".equals(sourcePort) || "output_z".equals(sourcePort))) {
            return true;
        }
        if (BLOCK_POSITION_INPUT_TYPE.equals(sourceType)
                && ("output_coordinate".equals(sourcePort) || "output_x".equals(sourcePort)
                || "output_y".equals(sourcePort) || "output_z".equals(sourcePort))) {
            return true;
        }
        if (PROJECT_TO_PLANE_TYPE.equals(sourceType) && "output_vector".equals(sourcePort)) {
            return true;
        }
        if (CLOSEST_POINT_TO_OBJECT_TYPE.equals(sourceType)
                && ("output_vector".equals(sourcePort) || "output_segment_index".equals(sourcePort)
                || "output_segment_t".equals(sourcePort) || "output_arc_length".equals(sourcePort))) {
            return true;
        }
        if (ANGLE_BETWEEN_TYPE.equals(sourceType)
                && ("output_radians".equals(sourcePort) || "output_signed_radians".equals(sourcePort))) {
            return true;
        }
        if (SLERP_VECTORS_TYPE.equals(sourceType) && "output_angle_radians".equals(sourcePort)) {
            return true;
        }
        return false;
    }

    private static final String GET_FACE_EDGE_TYPE = "reference.points.get_face_edge";
    private static final String DECONSTRUCT_EDGE_TYPE = "reference.points.deconstruct_edge";
    private static final String SNAP_POINT_TO_BLOCK_TYPE = "world.selection.snap_point_to_block";

    private static final Set<String> GET_FACE_EDGE_LEGACY_OUTPUT_PORTS = Set.of(
            "output_start",
            "output_end",
            "output_start_corner_index",
            "output_end_corner_index"
    );

    private static final Set<String> DECONSTRUCT_EDGE_LEGACY_PORTS = Set.of(
            "input_start_corner_index",
            "input_end_corner_index",
            "output_start_corner_index",
            "output_end_corner_index"
    );

    private static final Set<String> SNAP_POINT_LEGACY_OUTPUT_PORTS = Set.of(
            "output_x",
            "output_y",
            "output_z"
    );

    /**
     * Box/face + snap typing v1: drop slimmed ports; corner/edge positions remain same IDs (now POINT).
     */
    private static SavedGraph migrateV19ToV20(SavedGraph graph) {
        if (graph.connections == null || graph.nodes == null) {
            return graph;
        }

        graph.connections = new ArrayList<>(graph.connections);

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
            }
        }

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);
            String targetPort = connection.targetPortId == null ? "" : connection.targetPortId.toLowerCase(Locale.ROOT);

            if (GET_FACE_EDGE_TYPE.equals(sourceType) && GET_FACE_EDGE_LEGACY_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Get Face Edge legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            if (DECONSTRUCT_EDGE_TYPE.equals(sourceType) && DECONSTRUCT_EDGE_LEGACY_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Deconstruct Face Edge legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            if (DECONSTRUCT_EDGE_TYPE.equals(targetType) && DECONSTRUCT_EDGE_LEGACY_PORTS.contains(targetPort)) {
                LOGGER.debug("Dropped Deconstruct Face Edge legacy input {} to {}", connection.targetPortId, connection.targetNodeId);
                return true;
            }
            if (SNAP_POINT_TO_BLOCK_TYPE.equals(sourceType) && SNAP_POINT_LEGACY_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Snap Point To Block legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static final String CONSTRUCT_VECTOR_TYPE = "reference.vectors.construct_vector";
    private static final String VECTOR_INPUT_TYPE = "reference.vectors.vector";

    private static final Set<String> VECTOR_PRODUCER_LEGACY_COMPONENT_PORTS = Set.of(
            "output_x",
            "output_y",
            "output_z"
    );

    /**
     * Vector producer closure: drop X/Y/Z echo wires from Construct Vector / Vector Input.
     */
    private static SavedGraph migrateV20ToV21(SavedGraph graph) {
        if (graph.connections == null || graph.nodes == null) {
            return graph;
        }

        graph.connections = new ArrayList<>(graph.connections);

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
            }
        }

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);

            if ((CONSTRUCT_VECTOR_TYPE.equals(sourceType) || VECTOR_INPUT_TYPE.equals(sourceType))
                    && VECTOR_PRODUCER_LEGACY_COMPONENT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped vector producer legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static final Set<String> LIST_V22_DELETED_NODE_TYPES = Set.of(
            "math.list.chunk",
            "math.list.combine_lists",
            "math.list.zip",
            "math.list.transpose"
    );

    private static final String MAP_LIST_LEGACY_TYPE = "math.list.map_list";
    private static final String MAP_NUMBERS_TYPE = "math.list.map_numbers";
    private static final String GROUP_LIST_TYPE = "math.list.group_list";

    /**
     * List/collection v1: delete nested-list structure nodes; remap Map List → Map Numbers;
     * drop Group List legacy {@code output_groups} wires (now DATA_TREE {@code output_tree}).
     */
    private static SavedGraph migrateV21ToV22(SavedGraph graph) {
        if (graph.nodes == null) {
            return graph;
        }

        graph.nodes = new ArrayList<>(graph.nodes);
        graph.nodes.removeIf(node -> node != null && node.typeId != null
                && LIST_V22_DELETED_NODE_TYPES.contains(node.typeId.toLowerCase(Locale.ROOT)));

        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            if (MAP_LIST_LEGACY_TYPE.equalsIgnoreCase(node.typeId)) {
                node.typeId = MAP_NUMBERS_TYPE;
            }
            if (node.state instanceof Map<?, ?> state
                    && ("math.list.create_list".equalsIgnoreCase(node.typeId)
                    || "math.sequence.series".equalsIgnoreCase(node.typeId)
                    || MAP_NUMBERS_TYPE.equalsIgnoreCase(node.typeId)
                    || "math.list.statistics".equalsIgnoreCase(node.typeId))) {
                Map<String, Object> cleaned = new HashMap<>();
                for (Map.Entry<?, ?> entry : state.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        continue;
                    }
                    if ("allowDifferentTypes".equals(key)
                            || "useIntegerType".equals(key)
                            || "ignoreNonNumeric".equals(key)
                            || "ignoreNulls".equals(key)) {
                        continue;
                    }
                    cleaned.put(key, entry.getValue());
                }
                node.state = cleaned;
            }
        }

        if (graph.connections == null) {
            return graph;
        }

        graph.connections = new ArrayList<>(graph.connections);

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
            }
        }

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);
            if (GROUP_LIST_TYPE.equals(sourceType) && "output_groups".equals(sourcePort)) {
                LOGGER.debug("Dropped Group List legacy output_groups from {}", connection.sourceNodeId);
                return true;
            }
            if (MAP_NUMBERS_TYPE.equals(sourceType) && "output_changed_count".equals(sourcePort)) {
                LOGGER.debug("Dropped Map Numbers legacy output_changed_count from {}", connection.sourceNodeId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static final Set<String> LIST_V23_DELETED_NODE_TYPES = Set.of(
            "math.list.sort_list",
            "math.list.reduce"
    );

    /** Node-specific legacy state keys stripped during V22→V23 (never global key names). */
    private static final Map<String, Set<String>> LIST_V23_STATE_STRIP_BY_TYPE = Map.ofEntries(
            Map.entry("math.list.shuffle_list", Set.of("preserveInput")),
            Map.entry("math.list.deduplicate_list", Set.of("preserveOrder")),
            Map.entry("math.list.sub_list", Set.of("allowNegativeIndex", "clampToList")),
            Map.entry("math.list.set_item", Set.of("expandList", "allowNegativeIndex")),
            Map.entry("math.list.insert_item", Set.of("allowNegativeIndex", "append")),
            Map.entry("math.list.remove_item", Set.of("allowNegativeIndex")),
            Map.entry("math.list.get_item", Set.of("allowNegativeIndex")),
            Map.entry("math.list.dispatch_list", Set.of("useDefaultValue", "defaultValue"))
    );

    /**
     * List typed boundary: drop LIST→typed wires; Filter/Dispatch mask must be BOOLEAN_LIST;
     * remove generic Sort/Reduce and orphan wires to deleted nodes.
     */
    private static SavedGraph migrateV22ToV23(SavedGraph graph) {
        if (graph.nodes == null) {
            return graph;
        }

        graph.nodes = new ArrayList<>(graph.nodes);
        graph.nodes.removeIf(node -> node != null && node.typeId != null
                && LIST_V23_DELETED_NODE_TYPES.contains(node.typeId.toLowerCase(Locale.ROOT)));

        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null || !(node.state instanceof Map<?, ?> state)) {
                continue;
            }
            Set<String> stripKeys = LIST_V23_STATE_STRIP_BY_TYPE.get(node.typeId.toLowerCase(Locale.ROOT));
            if (stripKeys == null || stripKeys.isEmpty()) {
                continue;
            }
            Map<String, Object> cleaned = new HashMap<>();
            for (Map.Entry<?, ?> entry : state.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    continue;
                }
                if (stripKeys.contains(key)) {
                    continue;
                }
                cleaned.put(key, entry.getValue());
            }
            node.state = cleaned;
        }

        if (graph.connections == null) {
            return graph;
        }

        graph.connections = new ArrayList<>(graph.connections);

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
            }
        }

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            if (sourceType == null || targetType == null) {
                LOGGER.debug("Dropped orphan connection after List V23 node removal: {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }
            String targetPort = connection.targetPortId == null ? "" : connection.targetPortId.toLowerCase(Locale.ROOT);

            NodeDataType sourceDataType = resolveDeclaredPortType(sourceType, connection.sourcePortId, true);
            NodeDataType targetDataType = resolveDeclaredPortType(targetType, connection.targetPortId, false);

            if (sourceDataType != null && targetDataType != null
                    && sourceDataType.isListType() && targetDataType.isListType()
                    && sourceDataType.getListElementKind() == ListElementKind.UNCONSTRAINED
                    && targetDataType.getListElementKind() != ListElementKind.UNCONSTRAINED) {
                LOGGER.debug("Dropped LIST→typed connection {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }

            if (("math.list.filter_list".equals(targetType) || "math.list.dispatch_list".equals(targetType))
                    && "input_condition".equals(targetPort)) {
                if (sourceDataType == null || sourceDataType != NodeDataType.BOOLEAN_LIST) {
                    LOGGER.debug("Dropped non-BOOLEAN_LIST mask wire to {}#{}",
                            connection.targetNodeId, connection.targetPortId);
                    return true;
                }
            }

            return false;
        });

        return graph;
    }

    private static final Map<String, Set<String>> DATA_TREE_V24_STATE_STRIP_BY_TYPE = Map.ofEntries(
            Map.entry("math.data_tree.item", Set.of("allowNegativeIndex", "wrapIndex")),
            Map.entry("math.data_tree.merge", Set.of("preserveSourceIndex")),
            Map.entry("math.data_tree.partition_list", Set.of("dropRemainder"))
    );

    /**
     * Data Tree v1: TREE_PATH ports, unique-path trees, Merge/Entwine/Partition/Statistics cleanup.
     */
    private static SavedGraph migrateV23ToV24(SavedGraph graph) {
        if (graph.nodes == null) {
            return graph;
        }

        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null || !(node.state instanceof Map<?, ?> state)) {
                continue;
            }
            Set<String> stripKeys = DATA_TREE_V24_STATE_STRIP_BY_TYPE.get(node.typeId.toLowerCase(Locale.ROOT));
            if (stripKeys == null || stripKeys.isEmpty()) {
                continue;
            }
            Map<String, Object> cleaned = new HashMap<>();
            for (Map.Entry<?, ?> entry : state.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    continue;
                }
                if (stripKeys.contains(key)) {
                    continue;
                }
                cleaned.put(key, entry.getValue());
            }
            node.state = cleaned;
        }

        if (graph.connections == null) {
            return graph;
        }

        graph.connections = new ArrayList<>(graph.connections);

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
            }
        }

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            if (sourceType == null || targetType == null) {
                LOGGER.debug("Dropped orphan connection after Data Tree V24: {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }

            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);
            String targetPort = connection.targetPortId == null ? "" : connection.targetPortId.toLowerCase(Locale.ROOT);

            // Legacy STRING (or non-TREE_PATH) into path ports
            if (("math.data_tree.branch".equals(targetType) || "math.data_tree.item".equals(targetType))
                    && "input_path".equals(targetPort)) {
                NodeDataType sourceDataType = resolveDeclaredPortType(sourceType, connection.sourcePortId, true);
                if (sourceDataType != NodeDataType.TREE_PATH) {
                    LOGGER.debug("Dropped non-TREE_PATH wire to {}#{}", connection.targetNodeId, targetPort);
                    return true;
                }
            }

            // Removed ports
            if ("math.data_tree.paths".equals(sourceType) && "output_path_strings".equals(sourcePort)) {
                return true;
            }
            if ("math.data_tree.paths".equals(sourceType) && "output_branch_count".equals(sourcePort)) {
                return true;
            }
            if ("math.data_tree.statistics".equals(sourceType) && "output_paths".equals(sourcePort)) {
                return true;
            }
            if ("math.data_tree.partition_list".equals(sourceType) && "output_remainder".equals(sourcePort)) {
                return true;
            }

            // Simplify removed_prefix is now TREE_PATH (was LIST of ints / generic LIST)
            if ("math.data_tree.simplify".equals(sourceType) && "output_removed_prefix".equals(sourcePort)) {
                NodeDataType targetDataType = resolveDeclaredPortType(targetType, connection.targetPortId, false);
                if (targetDataType != null && targetDataType != NodeDataType.TREE_PATH
                        && targetDataType != NodeDataType.ANY) {
                    LOGGER.debug("Dropped Simplify Removed Prefix wire incompatible with TREE_PATH");
                    return true;
                }
            }

            return false;
        });

        return graph;
    }

    private static NodeDataType resolveDeclaredPortType(@Nullable String typeId, @Nullable String portId,
                                                        boolean output) {
        if (typeId == null || portId == null) {
            return null;
        }
        try {
            var registry = com.nodecraft.nodesystem.registry.NodeRegistry.getInstance();
            if (!registry.isInitialized()) {
                return null;
            }
            com.nodecraft.nodesystem.api.INode instance = registry.createNodeInstance(typeId);
            if (instance == null) {
                return null;
            }
            var ports = output ? instance.getOutputPorts() : instance.getInputPorts();
            for (com.nodecraft.nodesystem.api.IPort port : ports) {
                if (port != null && portId.equalsIgnoreCase(port.getId())) {
                    return port.getDataType();
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static void migrateDomainInputNodeState(SavedNode node) {
        if (!DOMAIN_INPUT_TYPE.equalsIgnoreCase(node.typeId) || !(node.state instanceof Map<?, ?> state)) {
            return;
        }
        Map<String, Object> migrated = new HashMap<>();
        for (Map.Entry<?, ?> entry : state.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            if ("min".equals(key) && entry.getValue() != null) {
                migrated.put("start", entry.getValue());
            } else if ("max".equals(key) && entry.getValue() != null) {
                migrated.put("end", entry.getValue());
            } else {
                migrated.put(key, entry.getValue());
            }
        }
        node.state = migrated;
    }

    private static String migrateDomainOutputPort(@Nullable String portId) {
        if (portId == null) {
            return null;
        }
        return switch (portId.toLowerCase(Locale.ROOT)) {
            case "output_range" -> "output_domain";
            case "output_min" -> "output_start";
            case "output_max" -> "output_end";
            default -> portId;
        };
    }

    private static String migrateRemapInputPort(@Nullable String portId) {
        if (portId == null) {
            return null;
        }
        return switch (portId.toLowerCase(Locale.ROOT)) {
            case "input_in_min", "input_in_max" -> "input_source";
            case "input_out_min", "input_out_max" -> "input_target";
            default -> portId;
        };
    }

    private static String migrateGraphMapperInputPort(@Nullable String portId) {
        return migrateRemapInputPort(portId);
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

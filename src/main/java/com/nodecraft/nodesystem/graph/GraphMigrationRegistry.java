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
import java.util.UUID;

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
            case GraphFormatVersion.V24 -> migrateV24ToV25(graph);
            case GraphFormatVersion.V25 -> migrateV25ToV26(graph);
            case GraphFormatVersion.V26 -> migrateV26ToV27(graph);
            case GraphFormatVersion.V27 -> migrateV27ToV28(graph);
            case GraphFormatVersion.V28 -> migrateV28ToV29(graph);
            case GraphFormatVersion.V29 -> migrateV29ToV30(graph);
            case GraphFormatVersion.V30 -> migrateV30ToV31(graph);
            case GraphFormatVersion.V31 -> migrateV31ToV32(graph);
            case GraphFormatVersion.V32 -> migrateV32ToV33(graph);
            case GraphFormatVersion.V33 -> migrateV33ToV34(graph);
            case GraphFormatVersion.V34 -> migrateV34ToV35(graph);
            case GraphFormatVersion.V35 -> migrateV35ToV36(graph);
            case GraphFormatVersion.V36 -> migrateV36ToV37(graph);
            case GraphFormatVersion.V37 -> migrateV37ToV38(graph);
            case GraphFormatVersion.V38 -> migrateV38ToV39(graph);
            case GraphFormatVersion.V39 -> migrateV39ToV40(graph);
            case GraphFormatVersion.V49 -> migrateV49ToV50(graph);
            case GraphFormatVersion.V50 -> migrateV50ToV51(graph);
            case GraphFormatVersion.V51 -> migrateV51ToV52(graph);
            case GraphFormatVersion.V52 -> migrateV52ToV53(graph);
            case GraphFormatVersion.V53 -> migrateV53ToV54(graph);
            case GraphFormatVersion.V54 -> migrateV54ToV55(graph);
            case GraphFormatVersion.V55 -> migrateV55ToV56(graph);
            case GraphFormatVersion.V56 -> migrateV56ToV57(graph);
            case GraphFormatVersion.V57 -> migrateV57ToV58(graph);
            case GraphFormatVersion.V58 -> migrateV58ToV59(graph);
            case GraphFormatVersion.V59 -> migrateV59ToV60(graph);
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
            "pattern.linear.path_frames",
            "pattern.linear.curve_array",
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

            if (EXTRUDE_TYPE.equals(targetType)
                    && "input_extrusion_vector".equalsIgnoreCase(connection.targetPortId)) {
                connection.targetPortId = "input_direction";
            }
            if (EXTRUDE_TYPE.equals(sourceType)
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
    private static final String PATH_FRAMES_TYPE = "pattern.linear.path_frames";
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
                    LOGGER.debug("Migrated node type: {} -> {}", node.typeId, PATH_FRAMES_TYPE);
                    node.typeId = PATH_FRAMES_TYPE;
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
            if (PATH_FRAMES_TYPE.equals(targetType) && "input_path_points".equals(targetPort)) {
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
            if (PATH_FRAMES_TYPE.equals(targetType) && "output_origins".equalsIgnoreCase(connection.sourcePortId)) {
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
            if (PATH_FRAMES_TYPE.equals(targetType)
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
    private static final String BOX_FACE_TO_PLANE_TYPE = "reference.planes.box_face_plane";
    private static final String FACE_CENTER_FRAME_TYPE = "reference.frames.frame_from_face";
    private static final String SPHERE_SURFACE_FRAME_TYPE = "reference.frames.sphere_surface_frame";
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
        return SLERP_VECTORS_TYPE.equals(sourceType) && "output_angle_radians".equals(sourcePort);
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
                if (sourceDataType != NodeDataType.BOOLEAN_LIST) {
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

    private static final String FRAC_TYPE = "math.scalar_math.frac";
    private static final Set<String> GRAPH_MAPPER_REMOVED_INPUT_PORTS = Set.of(
            "input_exponent",
            "input_gaussian_center",
            "input_gaussian_width"
    );

    /**
     * Scalar Math v1 schema cleanup: drop deleted Fraction Floor output wires and Graph Mapper
     * curve-parameter input ports (now properties only). Dynamic wires are dropped without
     * guessing static property values (pre-release).
     */
    private static SavedGraph migrateV24ToV25(SavedGraph graph) {
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

            if (FRAC_TYPE.equals(sourceType) && "output_floor".equals(sourcePort)) {
                LOGGER.debug("Dropped Fraction output_floor wire from {}", connection.sourceNodeId);
                return true;
            }
            if (GRAPH_MAPPER_TYPE.equals(targetType) && GRAPH_MAPPER_REMOVED_INPUT_PORTS.contains(targetPort)) {
                LOGGER.debug("Dropped Graph Mapper {} wire into {}", targetPort, connection.targetNodeId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static final String LEGACY_TRIG_PI_TYPE = "math.trigonometry.pi";
    private static final String LEGACY_TRIG_E_TYPE = "math.trigonometry.e";
    private static final String NUMERIC_PI_TYPE = "input.numeric.pi";
    private static final String NUMERIC_E_TYPE = "input.numeric.e";

    private static final Set<String> TRIG_V26_DELETED_NODE_TYPES = Set.of(
            "math.trigonometry.deg_to_rad",
            "math.trigonometry.rad_to_deg"
    );

    /**
     * Trigonometry v1: remap Pi/E to input.numeric; delete deg↔rad nodes; drop orphan wires.
     */
    private static SavedGraph migrateV25ToV26(SavedGraph graph) {
        if (graph.nodes == null) {
            return graph;
        }

        graph.nodes = new ArrayList<>(graph.nodes);

        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            String type = node.typeId.toLowerCase(Locale.ROOT);
            if (LEGACY_TRIG_PI_TYPE.equals(type)) {
                LOGGER.debug("Migrated node type: {} -> {}", node.typeId, NUMERIC_PI_TYPE);
                node.typeId = NUMERIC_PI_TYPE;
            } else if (LEGACY_TRIG_E_TYPE.equals(type)) {
                LOGGER.debug("Migrated node type: {} -> {}", node.typeId, NUMERIC_E_TYPE);
                node.typeId = NUMERIC_E_TYPE;
            }
        }

        graph.nodes.removeIf(node -> node != null && node.typeId != null
                && TRIG_V26_DELETED_NODE_TYPES.contains(node.typeId.toLowerCase(Locale.ROOT)));

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
                LOGGER.debug("Dropped orphan connection after Trigonometry V26 node removal: {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static final Set<String> COMPARE_V27_DELETED_NODE_TYPES = Set.of(
            "math.compare.compare"
    );

    /**
     * Compare v1: delete composite Compare node; drop orphan wires (no remap).
     */
    private static SavedGraph migrateV26ToV27(SavedGraph graph) {
        if (graph.nodes == null) {
            return graph;
        }

        graph.nodes = new ArrayList<>(graph.nodes);
        graph.nodes.removeIf(node -> node != null && node.typeId != null
                && COMPARE_V27_DELETED_NODE_TYPES.contains(node.typeId.toLowerCase(Locale.ROOT)));

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
                LOGGER.debug("Dropped orphan connection after Compare V27 node removal: {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static final String SERIES_TYPE = "math.sequence.series";
    private static final String RANDOM_VECTOR_TYPE = "math.random.random_vector";
    private static final String RANDOM_LIST_ITEM_TYPE = "math.random.random_list_item";
    private static final String SCALAR_SAMPLE_POINTS_TYPE = "math.fields.scalar_sample_points";

    /**
     * Sequence v1: drop Number Series Sum output wires (reduction belongs on Sum Numbers).
     */
    private static SavedGraph migrateV27ToV28(SavedGraph graph) {
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
            if (SERIES_TYPE.equals(sourceType) && "output_sum".equals(sourcePort)) {
                LOGGER.debug("Dropped Number Series output_sum wire from {}", connection.sourceNodeId);
                return true;
            }
            return false;
        });

        return graph;
    }

    /**
     * Random v1: Random Vector schema cleanup + drop type-tightening incompatibilities
     * for Random List Item (ANY→LIST&lt;T&gt;) and Random Numbers (LIST→DOUBLE_LIST).
     */
    private static SavedGraph migrateV28ToV29(SavedGraph graph) {
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

            if (RANDOM_VECTOR_TYPE.equals(sourceType) && "output_random_vector".equals(sourcePort)) {
                LOGGER.debug("Dropped Random Vector output_random_vector wire from {}", connection.sourceNodeId);
                return true;
            }
            if (RANDOM_VECTOR_TYPE.equals(targetType) && "input_count".equals(targetPort)) {
                LOGGER.debug("Dropped Random Vector input_count wire to {}", connection.targetNodeId);
                return true;
            }

            // Random List Item / Random Numbers: drop wires incompatible with V29 declared types
            if (isRandomV29TypeTightenedEndpoint(sourceType, sourcePort, targetType, targetPort)
                    && !isDeclaredConnectionStillCompatible(sourceType, connection.sourcePortId,
                    targetType, connection.targetPortId)) {
                LOGGER.debug("Dropped Random v1 type-incompatible wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }
            return false;
        });

        return graph;
    }

    /**
     * Field v1: Scalar Field Sample Points output is DOUBLE_LIST; drop incompatible wires.
     */
    private static SavedGraph migrateV29ToV30(SavedGraph graph) {
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

            if (SCALAR_SAMPLE_POINTS_TYPE.equals(sourceType) && "output_values".equals(sourcePort)
                    && !isDeclaredConnectionStillCompatible(sourceType, connection.sourcePortId,
                    nodeTypeBySavedId.get(connection.targetNodeId), connection.targetPortId)) {
                LOGGER.debug("Dropped Field v1 type-incompatible wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static final String XY_SLIDER_TYPE = "input.numeric.xy_slider";
    private static final String NUMERIC_OUTPUT_VALUE_PORT = "output_value";

    /**
     * Input Numeric v1: drop XY Slider {@code output_vector}; tighten {@code output_uv} to DOUBLE_LIST;
     * remap Pi/E legacy ports to {@code output_value}.
     */
    private static SavedGraph migrateV30ToV31(SavedGraph graph) {
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

        for (SavedConnection connection : graph.connections) {
            if (connection == null || connection.sourcePortId == null) {
                continue;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            if (NUMERIC_PI_TYPE.equals(sourceType) && "output_pi".equalsIgnoreCase(connection.sourcePortId)) {
                connection.sourcePortId = NUMERIC_OUTPUT_VALUE_PORT;
            } else if (NUMERIC_E_TYPE.equals(sourceType) && "output_e".equalsIgnoreCase(connection.sourcePortId)) {
                connection.sourcePortId = NUMERIC_OUTPUT_VALUE_PORT;
            }
        }

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);

            if (XY_SLIDER_TYPE.equals(sourceType) && "output_vector".equals(sourcePort)) {
                LOGGER.debug("Dropped XY Slider legacy output_vector wire from {}", connection.sourceNodeId);
                return true;
            }
            if (XY_SLIDER_TYPE.equals(sourceType) && "output_uv".equals(sourcePort)
                    && !isDeclaredConnectionStillCompatible(sourceType, connection.sourcePortId,
                    nodeTypeBySavedId.get(connection.targetNodeId), connection.targetPortId)) {
                LOGGER.debug("Dropped Input Numeric v1 type-incompatible wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static final String LEGACY_PLAYER_LOOK_DIRECTION_TYPE = "input.context.player_look_direction";
    private static final String PLAYER_RAYCAST_TYPE = "input.context.player_raycast";
    private static final String CURRENT_TIME_TYPE = "input.context.current_time";

    /**
     * Input Context v1: remap Player Look At → Player Raycast; drop incompatible hit/time wires.
     */
    private static SavedGraph migrateV31ToV32(SavedGraph graph) {
        if (graph.nodes != null) {
            graph.nodes = new ArrayList<>(graph.nodes);
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                if (LEGACY_PLAYER_LOOK_DIRECTION_TYPE.equalsIgnoreCase(node.typeId)) {
                    LOGGER.debug("Migrated node type: {} -> {}", node.typeId, PLAYER_RAYCAST_TYPE);
                    node.typeId = PLAYER_RAYCAST_TYPE;
                }
            }
        }

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

            if (PLAYER_RAYCAST_TYPE.equals(sourceType) && "output_hit_position".equals(sourcePort)
                    && !isDeclaredConnectionStillCompatible(sourceType, connection.sourcePortId,
                    nodeTypeBySavedId.get(connection.targetNodeId), connection.targetPortId)) {
                LOGGER.debug("Dropped Input Context v1 incompatible wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }
            if (PLAYER_RAYCAST_TYPE.equals(sourceType) && "output_hit_distance".equals(sourcePort)
                    && !isDeclaredConnectionStillCompatible(sourceType, connection.sourcePortId,
                    nodeTypeBySavedId.get(connection.targetNodeId), connection.targetPortId)) {
                LOGGER.debug("Dropped Input Context v1 incompatible wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }
            if (CURRENT_TIME_TYPE.equals(sourceType) && "output_time_ticks".equals(sourcePort)
                    && !isDeclaredConnectionStillCompatible(sourceType, connection.sourcePortId,
                    nodeTypeBySavedId.get(connection.targetNodeId), connection.targetPortId)) {
                LOGGER.debug("Dropped Input Context v1 incompatible wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static final String BLOCK_TYPE_SELECTOR_TYPE = "input.type_selectors.block_type_selector";
    private static final String BLOCK_STATE_SELECTOR_TYPE = "input.type_selectors.block_state_selector";
    private static final String BUILD_BLOCK_STATE_TYPE = "material.block_state.build_block_state";

    private static final String LEGACY_TEXT_INPUT_TYPE = "input.basic.text_input";
    private static final String LEGACY_COLOR_PICKER_TYPE = "input.basic.color_picker";
    private static final String LEGACY_BOOLEAN_TOGGLE_TYPE = "input.basic.boolean_toggle";
    private static final String VALUES_TEXT_INPUT_TYPE = "input.values.text_input";
    private static final String VALUES_COLOR_PICKER_TYPE = "input.values.color_picker";
    private static final String VALUES_BOOLEAN_TOGGLE_TYPE = "input.values.boolean_toggle";
    private static final String VALUES_DROPDOWN_TYPE = "input.values.dropdown";
    private static final String VALUES_GRADIENT_RAMP_TYPE = "input.values.gradient_ramp";
    private static final Set<String> COLOR_CHANNEL_PORTS = Set.of(
            "output_red", "output_green", "output_blue", "output_alpha"
    );

    private static final String LEGACY_BLOCK_STATE_ASSIGN_TYPE = "material.block_state.block_state_assign";
    private static final String APPLY_BLOCK_STATE_TYPE = "material.block_state.apply_block_state";
    private static final String LEGACY_SLAB_AUTOFILL_TYPE = "material.block_state.slab_autofill";
    private static final String SLAB_STAIR_AUTOFILL_TYPE = "material.directional_mapping.slab_stair_autofill";
    private static final String STAIR_SHAPE_TYPE = "material.block_state.stair_shape";

    private static final Set<String> REMOVED_BLOCK_STATE_TYPES = Set.of(
            "material.block_state.auto_orient_blocks",
            "material.block_state.waterlogged",
            "material.block_state.facing_from_normal"
    );

    private static final Set<String> BLOCK_STATE_GEOMETRY_PORTS = Set.of(
            "input_coordinates",
            "input_geometry",
            "input_box_geometry",
            "input_cylinder_geometry",
            "input_sphere_geometry",
            "input_torus_geometry",
            "input_block_type"
    );

    private static final Set<String> BLOCK_STATE_DECONSTRUCT_OUTPUT_PORTS = Set.of(
            "output_positions",
            "output_block_ids"
    );

    private static final Set<String> DIRECTIONAL_MAPPING_TYPES = Set.of(
            "material.directional_mapping.top_side_bottom_map",
            "material.directional_mapping.slope_map",
            "material.directional_mapping.slab_stair_autofill"
    );

    private static final Set<String> DIRECTIONAL_MAPPING_DECONSTRUCT_OUTPUT_PORTS = Set.of(
            "output_positions",
            "output_block_ids"
    );

    private static final Set<String> GRADIENT_MAPPING_TYPES = Set.of(
            "material.gradient_mapping.height_gradient_map",
            "material.gradient_mapping.noise_material",
            "material.gradient_mapping.gradient_ramp_map",
            "material.gradient_mapping.distance_material",
            "material.gradient_mapping.sdf_material"
    );

    private static final Set<String> GRADIENT_MAPPING_DECONSTRUCT_OUTPUT_PORTS = Set.of(
            "output_positions",
            "output_block_ids"
    );

    private static final Set<String> GRADIENT_MAPPING_DIAGNOSTIC_OUTPUT_PORTS = Set.of(
            "output_noise_values",
            "output_distances",
            "output_weights"
    );

    private static final String GRADIENT_RAMP_MAP_TYPE = "material.gradient_mapping.gradient_ramp_map";
    private static final String DISTANCE_MATERIAL_TYPE = "material.gradient_mapping.distance_material";
    private static final String DISTANCE_REFERENCE_POINT_PORT = "input_reference_point";

    private static final Set<String> PATTERN_MAPPING_TYPES = Set.of(
            "material.pattern_mapping.checker_pattern_map",
            "material.pattern_mapping.stripe_pattern_map",
            "material.pattern_mapping.brick_pattern_map",
            "material.pattern_mapping.grid_pattern_map"
    );

    private static final Set<String> PATTERN_MAPPING_DECONSTRUCT_OUTPUT_PORTS = Set.of(
            "output_positions",
            "output_block_ids"
    );

    private static final Set<String> SURFACE_AGING_TYPES = Set.of(
            "material.surface_aging.weathering",
            "material.surface_aging.moss_growth",
            "material.surface_aging.crack_pattern"
    );

    private static final Set<String> SURFACE_AGING_DECONSTRUCT_OUTPUT_PORTS = Set.of(
            "output_positions",
            "output_block_ids"
    );

    private static final String CRACK_PATTERN_TYPE = "material.surface_aging.crack_pattern";
    private static final String CRACK_LEGACY_INTERVAL_PORT = "input_interval";

    private static final Set<String> BASIC_ASSIGNMENT_ASSIGNMENT_TYPES = Set.of(
            "material.basic_assignment.assign_block_type",
            "material.basic_assignment.block_palette",
            "material.basic_assignment.weighted_palette"
    );

    private static final Set<String> BASIC_ASSIGNMENT_DECONSTRUCT_OUTPUT_PORTS = Set.of(
            "output_positions",
            "output_block_ids",
            "output_positions_tree",
            "output_block_ids_tree"
    );

    private static final String CREATE_BLOCK_PALETTE_TYPE = "material.basic_assignment.create_block_palette";
    private static final String WEIGHTED_PALETTE_TYPE = "material.basic_assignment.weighted_palette";
    private static final String BLOCK_PALETTE_TYPE = "material.basic_assignment.block_palette";
    private static final String CREATE_BLOCK_IDS_PORT = "input_block_ids";
    private static final String CREATE_WEIGHTS_PORT = "input_weights";
    private static final String WEIGHTED_WEIGHTS_PORT = "input_weights";
    private static final String FALLBACK_BLOCK_TYPE_PORT = "input_fallback_block_type";

    /**
     * Type Selectors v1: Block Type {@code BLOCK_TYPE} port; remove Block State Selector.
     */
    private static SavedGraph migrateV32ToV33(SavedGraph graph) {
        if (graph.nodes != null) {
            graph.nodes = new ArrayList<>(graph.nodes);
        } else {
            graph.nodes = new ArrayList<>();
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        Map<String, SavedNode> nodeById = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && node.nodeId != null && node.typeId != null) {
                nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
                nodeById.put(node.nodeId, node);
            }
        }

        if (graph.connections == null) {
            graph.connections = new ArrayList<>();
        } else {
            graph.connections = new ArrayList<>(graph.connections);
        }

        List<SavedConnection> stateOutputConnections = new ArrayList<>();
        for (SavedConnection connection : graph.connections) {
            if (connection == null || connection.sourceNodeId == null) {
                continue;
            }
            if (!BLOCK_STATE_SELECTOR_TYPE.equals(nodeTypeBySavedId.get(connection.sourceNodeId))) {
                continue;
            }
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);
            if ("output_block_state".equals(sourcePort) || "output_has_properties".equals(sourcePort)) {
                stateOutputConnections.add(connection);
            }
        }

        List<SavedNode> nodesToMigrate = new ArrayList<>();
        for (SavedNode node : graph.nodes) {
            if (node != null && BLOCK_STATE_SELECTOR_TYPE.equalsIgnoreCase(node.typeId)) {
                nodesToMigrate.add(node);
            }
        }

        List<SavedNode> insertedBuildNodes = new ArrayList<>();
        for (SavedNode node : nodesToMigrate) {
            Object legacyState = node.state;
            boolean hasStateOutput = stateOutputConnections.stream()
                    .anyMatch(c -> node.nodeId.equals(c.sourceNodeId));
            boolean hasProperties = hasNonEmptyStateProperties(legacyState);

            node.typeId = BLOCK_TYPE_SELECTOR_TYPE;
            nodeTypeBySavedId.put(node.nodeId, BLOCK_TYPE_SELECTOR_TYPE);
            migrateBlockStateSelectorState(node);

            if (hasStateOutput || hasProperties) {
                String buildNodeId = java.util.UUID.randomUUID().toString();
                SavedNode buildNode = new SavedNode();
                buildNode.nodeId = buildNodeId;
                buildNode.typeId = BUILD_BLOCK_STATE_TYPE;
                buildNode.state = buildBlockStateNodeStateFromLegacy(legacyState);
                insertedBuildNodes.add(buildNode);
                nodeTypeBySavedId.put(buildNodeId, BUILD_BLOCK_STATE_TYPE);
                nodeById.put(buildNodeId, buildNode);

                SavedConnection typeWire = new SavedConnection();
                typeWire.sourceNodeId = node.nodeId;
                typeWire.sourcePortId = "output_block_id";
                typeWire.targetNodeId = buildNodeId;
                typeWire.targetPortId = "input_block_type";
                graph.connections.add(typeWire);

                for (SavedConnection connection : new ArrayList<>(stateOutputConnections)) {
                    if (!node.nodeId.equals(connection.sourceNodeId)) {
                        continue;
                    }
                    String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);
                    if ("output_has_properties".equals(sourcePort)) {
                        graph.connections.remove(connection);
                        continue;
                    }
                    if ("output_block_state".equals(sourcePort)) {
                        connection.sourceNodeId = buildNodeId;
                        connection.sourcePortId = "output_block_state";
                    }
                }
            }

            LOGGER.debug("Migrated node type: {} -> {} (nodeId={})", BLOCK_STATE_SELECTOR_TYPE, BLOCK_TYPE_SELECTOR_TYPE, node.nodeId);
        }
        graph.nodes.addAll(insertedBuildNodes);

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);

            if (BLOCK_TYPE_SELECTOR_TYPE.equals(sourceType) && "output_block_id".equals(sourcePort)
                    && !isDeclaredConnectionStillCompatible(sourceType, connection.sourcePortId,
                    nodeTypeBySavedId.get(connection.targetNodeId), connection.targetPortId)) {
                LOGGER.debug("Dropped Type Selectors v1 incompatible wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }
            return false;
        });

        return graph;
    }

    /**
     * Input Values v1: remap basic value sources; tighten Color / Value List ports;
     * drop Gradient {@code output_ramp} wires.
     */
    private static SavedGraph migrateV33ToV34(SavedGraph graph) {
        if (graph.nodes == null) {
            graph.nodes = new ArrayList<>();
        } else {
            graph.nodes = new ArrayList<>(graph.nodes);
        }
        if (graph.connections == null) {
            graph.connections = new ArrayList<>();
        } else {
            graph.connections = new ArrayList<>(graph.connections);
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node == null || node.nodeId == null || node.typeId == null) {
                continue;
            }
            String remapped = remapInputValuesTypeId(node.typeId);
            if (!remapped.equals(node.typeId)) {
                LOGGER.debug("Migrated node type: {} -> {} (nodeId={})", node.typeId, remapped, node.nodeId);
                node.typeId = remapped;
            }
            nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
        }

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);
            String targetPort = connection.targetPortId == null ? "" : connection.targetPortId.toLowerCase(Locale.ROOT);

            if (VALUES_GRADIENT_RAMP_TYPE.equals(sourceType) && "output_ramp".equals(sourcePort)) {
                LOGGER.debug("Dropped Gradient Ramp output_ramp wire from {}", connection.sourceNodeId);
                return true;
            }

            boolean colorChannelEndpoint = VALUES_COLOR_PICKER_TYPE.equals(sourceType)
                    && COLOR_CHANNEL_PORTS.contains(sourcePort);
            boolean dropdownOptionsEndpoint =
                    (VALUES_DROPDOWN_TYPE.equals(sourceType) && "output_options".equals(sourcePort))
                            || (VALUES_DROPDOWN_TYPE.equals(targetType) && "input_options".equals(targetPort));

            if (colorChannelEndpoint || dropdownOptionsEndpoint) {
                if (!isDeclaredConnectionStillCompatible(sourceType, connection.sourcePortId,
                        targetType, connection.targetPortId)) {
                    LOGGER.debug("Dropped Input Values v1 incompatible wire {}#{} → {}#{}",
                            connection.sourceNodeId, connection.sourcePortId,
                            connection.targetNodeId, connection.targetPortId);
                    return true;
                }
            }
            return false;
        });

        return graph;
    }

    /**
     * Block State v1: shrink to four state-only nodes; move slab autofill; drop geometry ports and legacy outputs.
     */
    private static SavedGraph migrateV34ToV35(SavedGraph graph) {
        if (graph.nodes == null) {
            graph.nodes = new ArrayList<>();
        } else {
            graph.nodes = new ArrayList<>(graph.nodes);
        }
        if (graph.connections == null) {
            graph.connections = new ArrayList<>();
        } else {
            graph.connections = new ArrayList<>(graph.connections);
        }

        graph.nodes.removeIf(node -> node != null && node.typeId != null
                && REMOVED_BLOCK_STATE_TYPES.contains(node.typeId.toLowerCase(Locale.ROOT)));

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        for (SavedNode node : graph.nodes) {
            if (node == null || node.nodeId == null || node.typeId == null) {
                continue;
            }
            String remapped = remapBlockStateTypeId(node.typeId);
            if (!remapped.equals(node.typeId)) {
                LOGGER.debug("Migrated node type: {} -> {} (nodeId={})", node.typeId, remapped, node.nodeId);
                node.typeId = remapped;
            }
            node.state = stripBlockStateIdentityFromNodeState(node.state);
            nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
        }

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            if (sourceType == null || targetType == null) {
                return true;
            }
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);
            String targetPort = connection.targetPortId == null ? "" : connection.targetPortId.toLowerCase(Locale.ROOT);

            if (BUILD_BLOCK_STATE_TYPE.equals(sourceType) && "output_block_info".equals(sourcePort)) {
                LOGGER.debug("Dropped Build Block State output_block_info wire from {}", connection.sourceNodeId);
                return true;
            }

            if (isBlockStateGeometryWire(sourceType, sourcePort, targetType, targetPort)) {
                LOGGER.debug("Dropped Block State v1 geometry wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }

            if (isBlockStateDeconstructOutputEndpoint(sourceType, sourcePort)) {
                LOGGER.debug("Dropped Block State v1 deconstruct output wire {}#{}",
                        connection.sourceNodeId, connection.sourcePortId);
                return true;
            }

            if (SLAB_STAIR_AUTOFILL_TYPE.equals(targetType) && "input_normals".equals(targetPort)
                    && !isDeclaredConnectionStillCompatible(sourceType, connection.sourcePortId,
                    targetType, connection.targetPortId)) {
                LOGGER.debug("Dropped Slab/Stair Auto-Fill incompatible normals wire to {}",
                        connection.targetNodeId);
                return true;
            }

            return false;
        });

        return graph;
    }

    /**
     * Directional Mapping v1: drop deconstruct outputs from the three directional_mapping nodes.
     */
    private static SavedGraph migrateV35ToV36(SavedGraph graph) {
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
            if (sourceType == null || !DIRECTIONAL_MAPPING_TYPES.contains(sourceType)) {
                return false;
            }
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);
            if (DIRECTIONAL_MAPPING_DECONSTRUCT_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Directional Mapping v1 deconstruct output wire {}#{}",
                        connection.sourceNodeId, connection.sourcePortId);
                return true;
            }
            return false;
        });

        return graph;
    }

    /**
     * Gradient Mapping v1: drop deconstruct outputs; tighten diagnostics to DOUBLE_LIST;
     * Distance reference POINT; strip legacy rampBlocks property.
     */
    private static SavedGraph migrateV36ToV37(SavedGraph graph) {
        if (graph.nodes == null) {
            graph.nodes = new ArrayList<>();
        } else {
            graph.nodes = new ArrayList<>(graph.nodes);
        }
        if (graph.connections == null) {
            graph.connections = new ArrayList<>();
        } else {
            graph.connections = new ArrayList<>(graph.connections);
        }

        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            if (GRADIENT_RAMP_MAP_TYPE.equalsIgnoreCase(node.typeId) && node.state instanceof Map<?, ?> state) {
                Map<String, Object> cleaned = new HashMap<>();
                for (Map.Entry<?, ?> entry : state.entrySet()) {
                    if (entry.getKey() instanceof String key && !"rampBlocks".equals(key)) {
                        cleaned.put(key, entry.getValue());
                    }
                }
                node.state = cleaned;
            }
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

            if (sourceType != null
                    && GRADIENT_MAPPING_TYPES.contains(sourceType)
                    && GRADIENT_MAPPING_DECONSTRUCT_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Gradient Mapping v1 deconstruct output wire {}#{}",
                        connection.sourceNodeId, connection.sourcePortId);
                return true;
            }

            if (sourceType != null
                    && GRADIENT_MAPPING_TYPES.contains(sourceType)
                    && GRADIENT_MAPPING_DIAGNOSTIC_OUTPUT_PORTS.contains(sourcePort)
                    && !isDeclaredConnectionStillCompatible(sourceType, connection.sourcePortId,
                    targetType, connection.targetPortId)) {
                LOGGER.debug("Dropped Gradient Mapping v1 incompatible diagnostic wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }

            if (DISTANCE_MATERIAL_TYPE.equals(targetType)
                    && DISTANCE_REFERENCE_POINT_PORT.equals(targetPort)
                    && !isDeclaredConnectionStillCompatible(sourceType, connection.sourcePortId,
                    targetType, connection.targetPortId)) {
                LOGGER.debug("Dropped Gradient Mapping v1 incompatible Distance reference wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }

            return false;
        });

        return graph;
    }

    /**
     * Pattern Mapping v1: drop deconstruct outputs from the four pattern_mapping nodes.
     * Pattern Origin is new and optional — missing wires default to (0,0,0) at runtime.
     */
    private static SavedGraph migrateV37ToV38(SavedGraph graph) {
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
            if (sourceType == null || !PATTERN_MAPPING_TYPES.contains(sourceType)) {
                return false;
            }
            String sourcePort = connection.sourcePortId == null ? "" : connection.sourcePortId.toLowerCase(Locale.ROOT);
            if (PATTERN_MAPPING_DECONSTRUCT_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Pattern Mapping v1 deconstruct output wire {}#{}",
                        connection.sourceNodeId, connection.sourcePortId);
                return true;
            }
            return false;
        });

        return graph;
    }

    /**
     * Surface Aging v1: drop deconstruct outputs; drop Crack legacy {@code input_interval} wires.
     */
    private static SavedGraph migrateV38ToV39(SavedGraph graph) {
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

            if (sourceType != null
                    && SURFACE_AGING_TYPES.contains(sourceType)
                    && SURFACE_AGING_DECONSTRUCT_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Surface Aging v1 deconstruct output wire {}#{}",
                        connection.sourceNodeId, connection.sourcePortId);
                return true;
            }

            if (CRACK_PATTERN_TYPE.equals(targetType)
                    && CRACK_LEGACY_INTERVAL_PORT.equals(targetPort)) {
                LOGGER.debug("Dropped Surface Aging v1 legacy Crack interval wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }

            return false;
        });

        return graph;
    }

    /**
     * Basic Assignment v1: drop deconstruct outputs; drop fallback ports; tighten LIST ports.
     */
    private static SavedGraph migrateV39ToV40(SavedGraph graph) {
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

            if (sourceType != null
                    && BASIC_ASSIGNMENT_ASSIGNMENT_TYPES.contains(sourceType)
                    && BASIC_ASSIGNMENT_DECONSTRUCT_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Basic Assignment v1 deconstruct output wire {}#{}",
                        connection.sourceNodeId, connection.sourcePortId);
                return true;
            }

            if ((BLOCK_PALETTE_TYPE.equals(targetType) || WEIGHTED_PALETTE_TYPE.equals(targetType))
                    && FALLBACK_BLOCK_TYPE_PORT.equals(targetPort)) {
                LOGGER.debug("Dropped Basic Assignment v1 fallback wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }

            if (CREATE_BLOCK_PALETTE_TYPE.equals(targetType)
                    && (CREATE_BLOCK_IDS_PORT.equals(targetPort) || CREATE_WEIGHTS_PORT.equals(targetPort))
                    && !isDeclaredConnectionStillCompatible(sourceType, connection.sourcePortId,
                    targetType, connection.targetPortId)) {
                LOGGER.debug("Dropped Basic Assignment v1 incompatible Create Palette wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }

            if (WEIGHTED_PALETTE_TYPE.equals(targetType)
                    && WEIGHTED_WEIGHTS_PORT.equals(targetPort)
                    && !isDeclaredConnectionStillCompatible(sourceType, connection.sourcePortId,
                    targetType, connection.targetPortId)) {
                LOGGER.debug("Dropped Basic Assignment v1 incompatible Weighted Palette weights wire {}#{} → {}#{}",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                return true;
            }

            return false;
        });

        return graph;
    }

    private static final String REFLECT_VECTOR_TYPE = "reference.vectors.reflect";
    private static final String VECTOR2_INPUT_TYPE = "reference.vectors.vector2_input";

    private static final Set<String> VECTOR2_INPUT_LEGACY_OUTPUT_PORTS = Set.of(
            "output_x",
            "output_y",
            "output_uv"
    );

    /**
     * Reference Vectors v1: drop Reflect normalized-normal echo, Vector2 component/UV echoes,
     * strip obsolete Slerp shortestPath state.
     */
    private static SavedGraph migrateV49ToV50(SavedGraph graph) {
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                if (SLERP_VECTORS_TYPE.equalsIgnoreCase(node.typeId) && node.state instanceof Map<?, ?> state) {
                    Map<String, Object> cleaned = new HashMap<>();
                    for (Map.Entry<?, ?> entry : state.entrySet()) {
                        if (entry.getKey() instanceof String key && !"shortestpath".equals(key.toLowerCase(Locale.ROOT))) {
                            cleaned.put(key, entry.getValue());
                        }
                    }
                    node.state = cleaned.isEmpty() ? null : cleaned;
                }
            }
        }

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
            if (sourceType == null || connection.sourcePortId == null) {
                return false;
            }
            String sourcePort = connection.sourcePortId.toLowerCase(Locale.ROOT);

            if (REFLECT_VECTOR_TYPE.equals(sourceType) && "output_normalized_normal".equals(sourcePort)) {
                LOGGER.debug("Dropped Reflect Vector legacy output_normalized_normal from {}", connection.sourceNodeId);
                return true;
            }
            if (VECTOR2_INPUT_TYPE.equals(sourceType) && VECTOR2_INPUT_LEGACY_OUTPUT_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped 2D Vector Input legacy output {} from {}", connection.sourcePortId, connection.sourceNodeId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static final String LEGACY_VECTOR_COMPONENT_MINMAX_TYPE = "reference.vectors.component_minmax";
    private static final String VECTOR_COMPONENT_MINMAX_TYPE = "math.vector.component_minmax";

    /**
     * Reference Vectors P1: move Component Min/Max to math.vector family.
     */
    private static SavedGraph migrateV50ToV51(SavedGraph graph) {
        if (graph.nodes == null) {
            return graph;
        }
        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            if (LEGACY_VECTOR_COMPONENT_MINMAX_TYPE.equalsIgnoreCase(node.typeId)) {
                node.typeId = VECTOR_COMPONENT_MINMAX_TYPE;
                LOGGER.debug("Remapped {} -> {}", LEGACY_VECTOR_COMPONENT_MINMAX_TYPE, VECTOR_COMPONENT_MINMAX_TYPE);
            }
        }
        return graph;
    }

    private static final String LEGACY_OFFSET_COORDINATE_TYPE = "transform.basic_transforms.offset_coordinate";
    private static final String LEGACY_OFFSET_COORDINATES_TYPE = "transform.basic_transforms.offset_coordinates";
    private static final String LEGACY_ROTATE_COORDINATES_TYPE = "transform.basic_transforms.rotate_coordinates";
    private static final String LEGACY_SCALE_COORDINATES_TYPE = "transform.basic_transforms.scale_coordinates";
    private static final String LEGACY_MIRROR_COORDINATES_TYPE = "transform.basic_transforms.mirror_coordinates";
    private static final String LEGACY_MIRROR_VECTOR_LIST_TYPE = "transform.basic_transforms.mirror_vector_list_plane";
    private static final String LEGACY_SHEAR_TYPE = "transform.basic_transforms.shear";

    private static final String PLACEMENT_OFFSET_COORDINATE_TYPE = "transform.placement.offset_coordinate";
    private static final String PLACEMENT_OFFSET_COORDINATES_TYPE = "transform.placement.offset_coordinates";
    private static final String PLACEMENT_ROTATE_COORDINATES_TYPE = "transform.placement.rotate_coordinates";
    private static final String PLACEMENT_SCALE_COORDINATES_TYPE = "transform.placement.scale_coordinates";
    private static final String PLACEMENT_MIRROR_COORDINATES_TYPE = "transform.placement.mirror_coordinates";
    private static final String MIRROR_POINT_LIST_TYPE = "transform.basic_transforms.mirror_point_list_plane";
    private static final String DEFORMATIONS_SHEAR_TYPE = "transform.deformations.shear_point_list";
    private static final String OFFSET_FACE_TYPE = "transform.basic_transforms.offset_face";
    private static final String INSET_FACE_TYPE = "transform.basic_transforms.inset_face";

    private static final Set<String> BASIC_TRANSFORMS_FACE_ECHO_PORTS = Set.of(
            "output_polyline",
            "output_points",
            "output_corners",
            "output_center",
            "output_normal",
            "output_plane",
            "output_edges",
            "output_effective_distance"
    );

    private static final Set<String> OFFSET_COORDINATE_ECHO_PORTS = Set.of(
            "output_x",
            "output_y",
            "output_z"
    );

    /**
     * Basic Transforms v1: move block-grid to placement, Shear to deformations,
     * rename mirror vector list → point list, drop removed echo / skipped ports.
     */
    private static SavedGraph migrateV51ToV52(SavedGraph graph) {
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                String remapped = remapBasicTransformsV52TypeId(node.typeId);
                if (!remapped.equals(node.typeId)) {
                    LOGGER.debug("Remapped {} -> {}", node.typeId, remapped);
                    node.typeId = remapped;
                }
            }
        }

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
            if (connection == null || connection.sourcePortId == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            if (sourceType == null) {
                return false;
            }
            String sourcePort = connection.sourcePortId.toLowerCase(Locale.ROOT);

            if ((OFFSET_FACE_TYPE.equals(sourceType) || INSET_FACE_TYPE.equals(sourceType))
                    && BASIC_TRANSFORMS_FACE_ECHO_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Basic Transforms face echo {}#{}", connection.sourceNodeId, connection.sourcePortId);
                return true;
            }
            if (PLACEMENT_OFFSET_COORDINATE_TYPE.equals(sourceType)
                    && OFFSET_COORDINATE_ECHO_PORTS.contains(sourcePort)) {
                LOGGER.debug("Dropped Offset Coordinate echo {}#{}", connection.sourceNodeId, connection.sourcePortId);
                return true;
            }
            if ((MIRROR_POINT_LIST_TYPE.equals(sourceType) || DEFORMATIONS_SHEAR_TYPE.equals(sourceType))
                    && "output_skipped_count".equals(sourcePort)) {
                LOGGER.debug("Dropped Skipped Count {}#{}", connection.sourceNodeId, connection.sourcePortId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static String remapBasicTransformsV52TypeId(String typeId) {
        String normalized = typeId.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case LEGACY_OFFSET_COORDINATE_TYPE -> PLACEMENT_OFFSET_COORDINATE_TYPE;
            case LEGACY_OFFSET_COORDINATES_TYPE -> PLACEMENT_OFFSET_COORDINATES_TYPE;
            case LEGACY_ROTATE_COORDINATES_TYPE -> PLACEMENT_ROTATE_COORDINATES_TYPE;
            case LEGACY_SCALE_COORDINATES_TYPE -> PLACEMENT_SCALE_COORDINATES_TYPE;
            case LEGACY_MIRROR_COORDINATES_TYPE -> PLACEMENT_MIRROR_COORDINATES_TYPE;
            case LEGACY_MIRROR_VECTOR_LIST_TYPE -> MIRROR_POINT_LIST_TYPE;
            case LEGACY_SHEAR_TYPE -> DEFORMATIONS_SHEAR_TYPE;
            default -> typeId;
        };
    }

    private static final String TAPER_TYPE = "transform.deformations.taper";
    private static final String RELAX_TYPE = "transform.deformations.relax_points";
    private static final String SPHERICAL_TYPE = "transform.deformations.spherical_displace";
    private static final String TWIST_GEOMETRY_TYPE = "transform.deformations.twist_geometry";
    private static final String BEND_GEOMETRY_TYPE = "transform.deformations.bend_geometry";

    private static final Set<String> DEFORMATIONS_REMOVED_STATE_KEYS = Set.of(
            "minscale",
            "maxpoints",
            "maxsourcevoxels",
            "affectoutsideradius"
    );

    /**
     * Deformations v1: strip removed properties (minScale, maxPoints, maxSourceVoxels,
     * affectOutsideRadius). Type ids unchanged.
     */
    private static SavedGraph migrateV52ToV53(SavedGraph graph) {
        if (graph.nodes == null) {
            return graph;
        }
        for (SavedNode node : graph.nodes) {
            if (node == null || node.typeId == null || !(node.state instanceof Map<?, ?> state)) {
                continue;
            }
            String type = node.typeId.toLowerCase(Locale.ROOT);
            boolean strip = TAPER_TYPE.equals(type)
                    || RELAX_TYPE.equals(type)
                    || SPHERICAL_TYPE.equals(type)
                    || TWIST_GEOMETRY_TYPE.equals(type)
                    || BEND_GEOMETRY_TYPE.equals(type);
            if (!strip) {
                continue;
            }
            Map<String, Object> cleaned = new HashMap<>();
            for (Map.Entry<?, ?> entry : state.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    continue;
                }
                if (DEFORMATIONS_REMOVED_STATE_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                    LOGGER.debug("Stripped Deformations v1 obsolete state {} from {}", key, node.nodeId);
                    continue;
                }
                cleaned.put(key, entry.getValue());
            }
            node.state = cleaned.isEmpty() ? null : cleaned;
        }
        return graph;
    }

    private static final String PLACE_GEOMETRY_ON_FRAMES_TYPE = "transform.placement.place_geometry_on_frames";
    private static final String LEGACY_PLACEMENT_OFFSET_COORDINATE = "transform.placement.offset_coordinate";
    private static final String LEGACY_PLACEMENT_OFFSET_COORDINATES = "transform.placement.offset_coordinates";
    private static final String LEGACY_PLACEMENT_ROTATE_COORDINATES = "transform.placement.rotate_coordinates";
    private static final String LEGACY_PLACEMENT_SCALE_COORDINATES = "transform.placement.scale_coordinates";
    private static final String LEGACY_PLACEMENT_MIRROR_COORDINATES = "transform.placement.mirror_coordinates";

    private static final String OFFSET_BLOCK_POSITION_TYPE = "transform.placement.offset_block_position";
    private static final String OFFSET_BLOCK_POSITIONS_TYPE = "transform.placement.offset_block_positions";
    private static final String ROTATE_BLOCK_POSITIONS_TYPE = "transform.placement.rotate_block_positions";
    private static final String SCALE_BLOCK_POSITIONS_TYPE = "transform.placement.scale_block_positions";
    private static final String MIRROR_BLOCK_POSITIONS_TYPE = "transform.placement.mirror_block_positions";

    /**
     * Placement v1: rename Coordinate(s) → Block Position(s), drop Geometries LIST port wires,
     * strip RoundingMode / CUSTOM axis-plane enum state.
     */
    private static SavedGraph migrateV53ToV54(SavedGraph graph) {
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                String remapped = remapPlacementV54TypeId(node.typeId);
                if (!remapped.equals(node.typeId)) {
                    LOGGER.debug("Remapped {} -> {}", node.typeId, remapped);
                    node.typeId = remapped;
                }
                sanitizePlacementV54State(node);
            }
        }

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
            if (connection == null || connection.sourcePortId == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            if (sourceType == null) {
                return false;
            }
            if (PLACE_GEOMETRY_ON_FRAMES_TYPE.equals(sourceType)
                    && "output_geometries".equals(connection.sourcePortId.toLowerCase(Locale.ROOT))) {
                LOGGER.debug("Dropped Place On Frames Geometries LIST {}#{}",
                        connection.sourceNodeId, connection.sourcePortId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static String remapPlacementV54TypeId(String typeId) {
        String normalized = typeId.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case LEGACY_PLACEMENT_OFFSET_COORDINATE -> OFFSET_BLOCK_POSITION_TYPE;
            case LEGACY_PLACEMENT_OFFSET_COORDINATES -> OFFSET_BLOCK_POSITIONS_TYPE;
            case LEGACY_PLACEMENT_ROTATE_COORDINATES -> ROTATE_BLOCK_POSITIONS_TYPE;
            case LEGACY_PLACEMENT_SCALE_COORDINATES -> SCALE_BLOCK_POSITIONS_TYPE;
            case LEGACY_PLACEMENT_MIRROR_COORDINATES -> MIRROR_BLOCK_POSITIONS_TYPE;
            default -> typeId;
        };
    }

    private static void sanitizePlacementV54State(SavedNode node) {
        if (!(node.state instanceof Map<?, ?> state)) {
            return;
        }
        String type = node.typeId == null ? "" : node.typeId.toLowerCase(Locale.ROOT);
        boolean isRotate = ROTATE_BLOCK_POSITIONS_TYPE.equals(type)
                || LEGACY_PLACEMENT_ROTATE_COORDINATES.equals(type);
        boolean isMirror = MIRROR_BLOCK_POSITIONS_TYPE.equals(type)
                || LEGACY_PLACEMENT_MIRROR_COORDINATES.equals(type);
        boolean isOffsetList = OFFSET_BLOCK_POSITIONS_TYPE.equals(type)
                || LEGACY_PLACEMENT_OFFSET_COORDINATES.equals(type);

        if (!isRotate && !isMirror && !isOffsetList) {
            return;
        }

        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<?, ?> entry : state.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            String keyLower = key.toLowerCase(Locale.ROOT);
            if ("roundingmode".equals(keyLower)) {
                LOGGER.debug("Stripped Placement RoundingMode from {}", node.nodeId);
                continue;
            }
            Object value = entry.getValue();
            if (isRotate && "rotationaxis".equals(keyLower) && value instanceof String axisName) {
                if ("CUSTOM".equalsIgnoreCase(axisName)) {
                    cleaned.put(key, "Y_AXIS");
                    LOGGER.debug("Mapped CUSTOM rotationAxis → Y_AXIS on {}", node.nodeId);
                    continue;
                }
            }
            if (isMirror && "mirrorplane".equals(keyLower) && value instanceof String planeName) {
                if ("CUSTOM".equalsIgnoreCase(planeName)) {
                    cleaned.put(key, "XZ");
                    LOGGER.debug("Mapped CUSTOM mirrorPlane → XZ on {}", node.nodeId);
                    continue;
                }
            }
            cleaned.put(key, value);
        }
        node.state = cleaned.isEmpty() ? null : cleaned;
    }

    private static final String LEGACY_REROUTE_TYPE = "utilities.assist.reroute";
    private static final String LEGACY_TAG_RELAY_TYPE = "utilities.assist.tag_relay";
    private static final String LEGACY_ASSERT_TYPE = "utilities.assist.assert";
    private static final String LEGACY_SIGNAL_MERGE_TYPE = "utilities.assist.signal_merge";
    private static final String RELAY_TYPE = "utilities.assist.relay";
    private static final String VALIDATE_TYPE = "utilities.assist.validate";
    private static final String COALESCE_TYPE = "utilities.assist.coalesce";

    private static final String READ_IMAGE_TYPE = "utilities.fileio.read_image";
    private static final String IMAGE_SAMPLER_TYPE = "utilities.fileio.image_sampler";
    private static final String IMPORT_VOX_TYPE = "utilities.fileio.import_vox";

    private static final String BLOCK_LIST_MORPHOLOGY_TYPE = "utilities.morphology.block_list_morphology";

    private static final Set<String> MORPHOLOGY_V57_STRIP_STATE_KEYS = Set.of(
            "maxoutputblocks"
    );

    private static final Set<String> MORPHOLOGY_V57_DROP_PORTS = Set.of(
            "input_max_output_blocks"
    );

    private static final Set<String> FILEIO_V56_STRIP_STATE_KEYS = Set.of(
            "allowexternalpaths",
            "maxpixels",
            "maxvoxels",
            "defaultblock",
            "defaultblocktype"
    );

    private static final Set<String> READ_IMAGE_OBSOLETE_PORTS = Set.of(
            "input_max_pixels",
            "input_allow_external_paths"
    );

    private static final Set<String> IMPORT_VOX_OBSOLETE_PORTS = Set.of(
            "input_max_voxels",
            "input_allow_external_paths",
            "input_block_type",
            "output_placements"
    );

    private static final Set<String> IMAGE_SAMPLER_OBSOLETE_PORTS = Set.of(
            "input_pixel_colors",
            "input_image_width",
            "input_image_height",
            "input_width",
            "input_height"
    );

    /**
     * Assist Utilities v1: remap Reroute/Tag→Relay, Assert→Validate, Signal Merge→Coalesce;
     * drop Assert Passed wires; strip failHard.
     */
    private static SavedGraph migrateV54ToV55(SavedGraph graph) {
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                String remapped = remapAssistV55TypeId(node.typeId);
                if (!remapped.equals(node.typeId)) {
                    LOGGER.debug("Remapped {} -> {}", node.typeId, remapped);
                    node.typeId = remapped;
                }
                if (VALIDATE_TYPE.equalsIgnoreCase(node.typeId) && node.state instanceof Map<?, ?> state) {
                    Map<String, Object> cleaned = new HashMap<>();
                    for (Map.Entry<?, ?> entry : state.entrySet()) {
                        if (!(entry.getKey() instanceof String key)) {
                            continue;
                        }
                        if ("failhard".equals(key.toLowerCase(Locale.ROOT))) {
                            LOGGER.debug("Stripped Assert failHard from {}", node.nodeId);
                            continue;
                        }
                        cleaned.put(key, entry.getValue());
                    }
                    node.state = cleaned.isEmpty() ? null : cleaned;
                }
            }
        }

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
            if (connection == null || connection.sourcePortId == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            if (VALIDATE_TYPE.equals(sourceType)
                    && "output_passed".equals(connection.sourcePortId.toLowerCase(Locale.ROOT))) {
                LOGGER.debug("Dropped Assert Passed wire {}#{}", connection.sourceNodeId, connection.sourcePortId);
                return true;
            }
            return false;
        });

        return graph;
    }

    private static String remapAssistV55TypeId(String typeId) {
        String normalized = typeId.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case LEGACY_REROUTE_TYPE, LEGACY_TAG_RELAY_TYPE -> RELAY_TYPE;
            case LEGACY_ASSERT_TYPE -> VALIDATE_TYPE;
            case LEGACY_SIGNAL_MERGE_TYPE -> COALESCE_TYPE;
            default -> typeId;
        };
    }

    /**
     * FileIO v1: strip obsolete allow-external / max-pixels / max-voxels / default-block state;
     * drop wires to removed Read Image / Image Sampler / Import VOX ports.
     */
    private static SavedGraph migrateV55ToV56(SavedGraph graph) {
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                String type = node.typeId.toLowerCase(Locale.ROOT);
                if (!READ_IMAGE_TYPE.equals(type)
                        && !IMAGE_SAMPLER_TYPE.equals(type)
                        && !IMPORT_VOX_TYPE.equals(type)) {
                    continue;
                }
                if (!(node.state instanceof Map<?, ?> state)) {
                    continue;
                }
                Map<String, Object> cleaned = new HashMap<>();
                for (Map.Entry<?, ?> entry : state.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        continue;
                    }
                    if (FILEIO_V56_STRIP_STATE_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                        LOGGER.debug("Stripped FileIO state key {} from {}", key, node.nodeId);
                        continue;
                    }
                    cleaned.put(key, entry.getValue());
                }
                node.state = cleaned.isEmpty() ? null : cleaned;
            }
        }

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
            String sourcePort = connection.sourcePortId == null
                    ? null
                    : connection.sourcePortId.toLowerCase(Locale.ROOT);
            String targetPort = connection.targetPortId == null
                    ? null
                    : connection.targetPortId.toLowerCase(Locale.ROOT);

            if (isFileIoObsoleteEndpoint(sourceType, sourcePort)
                    || isFileIoObsoleteEndpoint(targetType, targetPort)) {
                LOGGER.debug(
                        "Dropped FileIO obsolete wire {}#{} -> {}#{}",
                        connection.sourceNodeId,
                        connection.sourcePortId,
                        connection.targetNodeId,
                        connection.targetPortId
                );
                return true;
            }
            return false;
        });

        return graph;
    }

    /**
     * Block Morphology v1: strip maxOutputBlocks state; drop max-output port wires;
     * rename Stopped Reason → Error port id.
     */
    private static SavedGraph migrateV56ToV57(SavedGraph graph) {
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                if (!BLOCK_LIST_MORPHOLOGY_TYPE.equalsIgnoreCase(node.typeId)) {
                    continue;
                }
                if (!(node.state instanceof Map<?, ?> state)) {
                    continue;
                }
                Map<String, Object> cleaned = new HashMap<>();
                for (Map.Entry<?, ?> entry : state.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        continue;
                    }
                    if (MORPHOLOGY_V57_STRIP_STATE_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                        LOGGER.debug("Stripped Morphology state key {} from {}", key, node.nodeId);
                        continue;
                    }
                    cleaned.put(key, entry.getValue());
                }
                node.state = cleaned.isEmpty() ? null : cleaned;
            }
        }

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

        for (SavedConnection connection : graph.connections) {
            if (connection == null) {
                continue;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            if (BLOCK_LIST_MORPHOLOGY_TYPE.equals(sourceType)
                    && "output_stopped_reason".equalsIgnoreCase(connection.sourcePortId)) {
                LOGGER.debug(
                        "Remapped Morphology output port {} -> output_error on {}",
                        connection.sourcePortId,
                        connection.sourceNodeId
                );
                connection.sourcePortId = "output_error";
            }
            if (BLOCK_LIST_MORPHOLOGY_TYPE.equals(targetType)
                    && "output_stopped_reason".equalsIgnoreCase(connection.targetPortId)) {
                LOGGER.debug(
                        "Remapped Morphology input port {} -> output_error on {}",
                        connection.targetPortId,
                        connection.targetNodeId
                );
                connection.targetPortId = "output_error";
            }
        }

        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            String sourcePort = connection.sourcePortId == null
                    ? null
                    : connection.sourcePortId.toLowerCase(Locale.ROOT);
            String targetPort = connection.targetPortId == null
                    ? null
                    : connection.targetPortId.toLowerCase(Locale.ROOT);

            if (BLOCK_LIST_MORPHOLOGY_TYPE.equals(sourceType)
                    && sourcePort != null
                    && MORPHOLOGY_V57_DROP_PORTS.contains(sourcePort)) {
                LOGGER.debug(
                        "Dropped Morphology obsolete wire {}#{}",
                        connection.sourceNodeId,
                        connection.sourcePortId
                );
                return true;
            }
            if (BLOCK_LIST_MORPHOLOGY_TYPE.equals(targetType)
                    && targetPort != null
                    && MORPHOLOGY_V57_DROP_PORTS.contains(targetPort)) {
                LOGGER.debug(
                        "Dropped Morphology obsolete wire {}#{}",
                        connection.targetNodeId,
                        connection.targetPortId
                );
                return true;
            }
            return false;
        });

        return graph;
    }

    private static final String SUBGRAPH_TYPE = "utilities.organization.subgraph";
    private static final String GRAPH_INPUT_TYPE = "utilities.organization.graph_input";
    private static final String GRAPH_OUTPUT_TYPE = "utilities.organization.graph_output";
    private static final String SUBGRAPH_REGISTER_TYPE = "utilities.organization.subgraph_register";
    private static final String NODE_PRESET_TYPE = "utilities.organization.preset";
    private static final String COMMENT_TYPE = "utilities.organization.comment";
    private static final String GROUP_TYPE = "utilities.organization.group";

    private static final Set<String> SUBGRAPH_V58_DROP_PORTS = Set.of(
            "input_subgraph_ref",
            "input_subgraph_graph",
            "input_value",
            "input_inputs",
            "input_outputs",
            "input_input_keys",
            "input_output_keys",
            "output_value",
            "output_inputs",
            "output_outputs",
            "output_mapped_outputs",
            "output_metadata",
            "output_debug_trace"
    );

    private static final Set<String> GRAPH_INPUT_V58_DROP_PORTS = Set.of("input_override");

    private static final Set<String> GRAPH_OUTPUT_V58_DROP_PORTS = Set.of(
            "input_name_override",
            "output_outputs"
    );

    private static final Set<String> SUBGRAPH_V58_STRIP_STATE_KEYS = Set.of(
            "inputkey",
            "outputkey",
            "strictmode",
            "maxcalldepth",
            "additionalinputkeys",
            "additionaloutputkeys",
            "emitdebugtrace",
            "embeddedgraphjson"
    );

    /**
     * Organization & Subgraph v1: lift embeddedGraphJson to subgraphDefinitions, migrate Comment/Group
     * to metadata, drop removed node types and obsolete port wires.
     */
    private static SavedGraph migrateV57ToV58(SavedGraph graph) {
        if (graph.subgraphDefinitions == null) {
            graph.subgraphDefinitions = new HashMap<>();
        }
        if (graph.comments == null) {
            graph.comments = new ArrayList<>();
        }
        if (graph.groups == null) {
            graph.groups = new ArrayList<>();
        }

        if (graph.nodes != null) {
            List<SavedNode> retained = new ArrayList<>();
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                String typeId = node.typeId.toLowerCase(Locale.ROOT);

                switch (typeId) {
                    case COMMENT_TYPE -> {
                        liftCommentNode(graph, node);
                        continue;
                    }
                    case GROUP_TYPE -> {
                        liftGroupNode(graph, node);
                        continue;
                    }
                    case SUBGRAPH_REGISTER_TYPE, NODE_PRESET_TYPE -> {
                        continue;
                    }
                    case SUBGRAPH_TYPE -> {
                        extractEmbeddedSubgraphDefinition(graph, node);
                        stripSubgraphNodeState(node);
                    }
                }

                if (GRAPH_INPUT_TYPE.equals(typeId)) {
                    remapGraphInputPorts(node);
                }
                if (GRAPH_OUTPUT_TYPE.equals(typeId)) {
                    stripGraphOutputState(node);
                }

                retained.add(node);
            }
            graph.nodes = retained;
        }

        if (graph.connections != null) {
            Map<String, String> nodeTypeBySavedId = new HashMap<>();
            if (graph.nodes != null) {
                for (SavedNode node : graph.nodes) {
                    if (node != null && node.nodeId != null && node.typeId != null) {
                        nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
                    }
                }
            }

            graph.connections = new ArrayList<>(graph.connections);
            graph.connections.removeIf(connection -> {
                if (connection == null) {
                    return false;
                }
                String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
                String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
                String sourcePort = normalizePortId(connection.sourcePortId);
                String targetPort = normalizePortId(connection.targetPortId);

                if (SUBGRAPH_TYPE.equals(sourceType) && sourcePort != null
                        && SUBGRAPH_V58_DROP_PORTS.contains(sourcePort)) {
                    return true;
                }
                if (SUBGRAPH_TYPE.equals(targetType) && targetPort != null
                        && SUBGRAPH_V58_DROP_PORTS.contains(targetPort)) {
                    return true;
                }
                if (GRAPH_INPUT_TYPE.equals(targetType) && targetPort != null
                        && GRAPH_INPUT_V58_DROP_PORTS.contains(targetPort)) {
                    return true;
                }
                if (GRAPH_OUTPUT_TYPE.equals(sourceType) && sourcePort != null
                        && GRAPH_OUTPUT_V58_DROP_PORTS.contains(sourcePort)) {
                    return true;
                }
                if (GRAPH_OUTPUT_TYPE.equals(targetType) && targetPort != null
                        && GRAPH_OUTPUT_V58_DROP_PORTS.contains(targetPort)) {
                    return true;
                }
                if (GRAPH_INPUT_TYPE.equals(sourceType) && "output_was_overridden".equals(sourcePort)) {
                    connection.sourcePortId = "output_was_provided";
                }
                return false;
            });
        }

        return graph;
    }

    private static void extractEmbeddedSubgraphDefinition(SavedGraph graph, SavedNode node) {
        if (!(node.state instanceof Map<?, ?> state)) {
            return;
        }
        Object embedded = state.get("embeddedGraphJson");
        if (!(embedded instanceof String json) || json.isBlank()) {
            return;
        }
        String ref = readStateString(state, "subgraphRef");
        if (ref == null || ref.isBlank()) {
            ref = "subgraph_" + node.nodeId;
        }
        try {
            SavedGraph definition = GraphSerializer.fromJson(json);
            if (definition != null) {
                graph.subgraphDefinitions.put(ref.trim(), definition);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed extracting embedded subgraph for node {}: {}", node.nodeId, e.getMessage());
        }
    }

    private static void stripSubgraphNodeState(SavedNode node) {
        if (!(node.state instanceof Map<?, ?> state)) {
            return;
        }
        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<?, ?> entry : state.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            if (SUBGRAPH_V58_STRIP_STATE_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                continue;
            }
            cleaned.put(key, entry.getValue());
        }
        if (!cleaned.containsKey("enabled")) {
            cleaned.put("enabled", true);
        }
        node.state = cleaned.isEmpty() ? null : cleaned;
    }

    private static void remapGraphInputPorts(SavedNode node) {
        if (!(node.state instanceof Map<?, ?> state)) {
            return;
        }
        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<?, ?> entry : state.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            cleaned.put(key, entry.getValue());
        }
        Object inferred = cleaned.get("inferredType");
        if (inferred instanceof String typeId && !typeId.isBlank()) {
            cleaned.putIfAbsent("declaredType", typeId.trim());
        }
        node.state = cleaned;
    }

    private static void stripGraphOutputState(SavedNode node) {
        if (!(node.state instanceof Map<?, ?> state)) {
            return;
        }
        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<?, ?> entry : state.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            cleaned.put(key, entry.getValue());
        }
        Object inferred = cleaned.get("inferredType");
        if (inferred instanceof String typeId && !typeId.isBlank()) {
            cleaned.putIfAbsent("declaredType", typeId.trim());
        }
        node.state = cleaned;
    }

    private static void liftCommentNode(SavedGraph graph, SavedNode node) {
        com.nodecraft.nodesystem.io.SavedGraphComment comment = new com.nodecraft.nodesystem.io.SavedGraphComment();
        comment.id = node.nodeId;
        if (node.state instanceof Object[] array && array.length >= 9) {
            comment.text = array[0] instanceof String value ? value : "";
            comment.textColor = array[1] instanceof String value ? value : "#000000";
            comment.backgroundColor = array[2] instanceof String value ? value : "#FFEB3B";
            comment.fontSize = array[3] instanceof Number value ? value.floatValue() : 14.0f;
            comment.bold = array[4] instanceof Boolean value && value;
            comment.italic = array[5] instanceof Boolean value && value;
            comment.style = array[6] instanceof String value ? value : "STICKY_NOTE";
            comment.width = array[7] instanceof Number value ? value.floatValue() : 200.0f;
            comment.height = array[8] instanceof Number value ? value.floatValue() : 120.0f;
        }
        if (graph.nodePositions != null && node.nodeId != null) {
            var pos = graph.nodePositions.get(node.nodeId);
            if (pos != null) {
                comment.x = pos.x;
                comment.y = pos.y;
            }
        }
        graph.comments.add(comment);
        if (graph.nodePositions != null && node.nodeId != null) {
            graph.nodePositions.remove(node.nodeId);
        }
    }

    private static void liftGroupNode(SavedGraph graph, SavedNode node) {
        com.nodecraft.nodesystem.io.SavedGraphGroup group = new com.nodecraft.nodesystem.io.SavedGraphGroup();
        group.id = node.nodeId;
        group.nodeIds = new ArrayList<>();
        if (node.state instanceof Object[] array && array.length >= 7) {
            group.title = array[0] instanceof String value ? value : "Group";
            group.color = array[1] instanceof String value ? value : "#3498db";
            group.collapsed = array[2] instanceof Boolean value && value;
            group.locked = array[3] instanceof Boolean value && value;
            group.width = array[4] instanceof Number value ? value.floatValue() : 300.0f;
            group.height = array[5] instanceof Number value ? value.floatValue() : 200.0f;
            if (array[6] instanceof UUID[] ids) {
                for (UUID id : ids) {
                    if (id != null) {
                        group.nodeIds.add(id.toString());
                    }
                }
            }
        }
        if (graph.nodePositions != null && node.nodeId != null) {
            var pos = graph.nodePositions.get(node.nodeId);
            if (pos != null) {
                group.x = pos.x;
                group.y = pos.y;
            }
        }
        graph.groups.add(group);
        if (graph.nodePositions != null && node.nodeId != null) {
            graph.nodePositions.remove(node.nodeId);
        }
    }

    @Nullable
    private static String readStateString(Map<?, ?> state, String key) {
        Object value = state.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }

    @Nullable
    private static String normalizePortId(@Nullable String portId) {
        return portId == null ? null : portId.toLowerCase(Locale.ROOT);
    }

    private static boolean isFileIoObsoleteEndpoint(@Nullable String nodeType, @Nullable String portId) {
        if (nodeType == null || portId == null) {
            return false;
        }
        return switch (nodeType) {
            case READ_IMAGE_TYPE -> READ_IMAGE_OBSOLETE_PORTS.contains(portId);
            case IMPORT_VOX_TYPE -> IMPORT_VOX_OBSOLETE_PORTS.contains(portId);
            case IMAGE_SAMPLER_TYPE -> IMAGE_SAMPLER_OBSOLETE_PORTS.contains(portId);
            default -> false;
        };
    }

    private static String remapBlockStateTypeId(String typeId) {
        String normalized = typeId.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case LEGACY_BLOCK_STATE_ASSIGN_TYPE -> APPLY_BLOCK_STATE_TYPE;
            case LEGACY_SLAB_AUTOFILL_TYPE -> SLAB_STAIR_AUTOFILL_TYPE;
            default -> typeId;
        };
    }

    private static boolean isBlockStateGeometryWire(
            @Nullable String sourceType,
            String sourcePort,
            @Nullable String targetType,
            String targetPort
    ) {
        if (APPLY_BLOCK_STATE_TYPE.equals(targetType) || STAIR_SHAPE_TYPE.equals(targetType)) {
            if (BLOCK_STATE_GEOMETRY_PORTS.contains(targetPort)) {
                return true;
            }
        }
        if (APPLY_BLOCK_STATE_TYPE.equals(sourceType) || STAIR_SHAPE_TYPE.equals(sourceType)) {
            return BLOCK_STATE_GEOMETRY_PORTS.contains(sourcePort);
        }
        return false;
    }

    private static boolean isBlockStateDeconstructOutputEndpoint(@Nullable String sourceType, String sourcePort) {
        if (sourceType == null || !sourceType.startsWith("material.block_state.")) {
            return false;
        }
        return BLOCK_STATE_DECONSTRUCT_OUTPUT_PORTS.contains(sourcePort);
    }

    private static @Nullable Object stripBlockStateIdentityFromNodeState(@Nullable Object state) {
        return stripBlockStateIdentityValue(state);
    }

    private static Object stripBlockStateIdentityValue(@Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> cleaned = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && !"blockId".equals(key) && !"id".equals(key)) {
                    cleaned.put(key, stripBlockStateIdentityValue(entry.getValue()));
                }
            }
            return cleaned;
        }
        if (value instanceof List<?> list) {
            List<Object> cleaned = new ArrayList<>(list.size());
            for (Object item : list) {
                cleaned.add(stripBlockStateIdentityValue(item));
            }
            return cleaned;
        }
        return value;
    }

    private static String remapInputValuesTypeId(String typeId) {
        String normalized = typeId.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case LEGACY_TEXT_INPUT_TYPE -> VALUES_TEXT_INPUT_TYPE;
            case LEGACY_COLOR_PICKER_TYPE -> VALUES_COLOR_PICKER_TYPE;
            case LEGACY_BOOLEAN_TOGGLE_TYPE -> VALUES_BOOLEAN_TOGGLE_TYPE;
            default -> typeId;
        };
    }

    private static void migrateBlockStateSelectorState(SavedNode node) {
        if (!(node.state instanceof Map<?, ?> state)) {
            return;
        }
        Map<String, Object> migrated = new HashMap<>();
        if (state.get("blockId") instanceof String blockId) {
            migrated.put("selectedBlock", blockId);
        }
        if (state.get("allowModded") instanceof Boolean allowModded) {
            migrated.put("allowModded", allowModded);
        }
        if (state.get("selectedCategory") instanceof String category) {
            migrated.put("selectedCategory", category);
        }
        if (state.get("minecraftOnly") instanceof Boolean minecraftOnly) {
            migrated.put("minecraftOnly", minecraftOnly);
        }
        node.state = migrated.isEmpty() ? null : migrated;
    }

    private static @Nullable Map<String, Object> buildBlockStateNodeStateFromLegacy(@Nullable Object legacyState) {
        if (!(legacyState instanceof Map<?, ?> state)) {
            return null;
        }
        Object properties = state.get("stateProperties");
        if (!(properties instanceof String text) || text.isBlank()) {
            return null;
        }
        return Map.of("propertiesText", text);
    }

    private static boolean hasNonEmptyStateProperties(@Nullable Object legacyState) {
        if (!(legacyState instanceof Map<?, ?> state)) {
            return false;
        }
        Object properties = state.get("stateProperties");
        return properties instanceof String text && !text.isBlank();
    }

    private static boolean isRandomV29TypeTightenedEndpoint(
            @Nullable String sourceType,
            String sourcePort,
            @Nullable String targetType,
            String targetPort
    ) {
        if (RANDOM_LIST_ITEM_TYPE.equals(targetType) && "input_list".equals(targetPort)) {
            return true;
        }
        if (RANDOM_LIST_ITEM_TYPE.equals(sourceType)
                && ("output_items".equals(sourcePort) || "output_item".equals(sourcePort))) {
            return true;
        }
        return RANDOM_NUMBERS_TYPE.equals(sourceType) && "output_values".equals(sourcePort);
    }

    private static boolean isDeclaredConnectionStillCompatible(
            @Nullable String sourceType,
            @Nullable String sourcePortId,
            @Nullable String targetType,
            @Nullable String targetPortId
    ) {
        NodeDataType sourceDataType = resolveDeclaredPortType(sourceType, sourcePortId, true);
        NodeDataType targetDataType = resolveDeclaredPortType(targetType, targetPortId, false);
        if (sourceDataType == null || targetDataType == null) {
            // Cannot verify — keep wire rather than drop blindly (pre-release orphan cleanup
            // already covers missing nodes elsewhere).
            return true;
        }
        return NodeDataType.isConnectableTo(sourceDataType, targetDataType);
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

    private static final String SET_VARIABLE_TYPE = "variable.set";
    private static final String VARIABLE_LIST_TYPE = "variable.list";
    private static final String CLEAR_VARIABLES_TYPE = "variable.clear";

    /**
     * Variable Scope v1: rename Set Variable Success→Valid, drop Variable List Entries port wires,
     * strip Clear Variables includeInternalVariables state.
     */
    private static SavedGraph migrateV58ToV59(SavedGraph graph) {
        applyVariableScopeV59ToGraph(graph);
        if (graph.subgraphDefinitions != null) {
            for (SavedGraph definition : graph.subgraphDefinitions.values()) {
                if (definition != null) {
                    applyVariableScopeV59ToGraph(definition);
                }
            }
        }
        return graph;
    }

    private static void applyVariableScopeV59ToGraph(SavedGraph graph) {
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node == null || node.typeId == null) {
                    continue;
                }
                if (CLEAR_VARIABLES_TYPE.equalsIgnoreCase(node.typeId)) {
                    stripClearVariablesState(node);
                }
            }
        }

        if (graph.connections == null) {
            return;
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node != null && node.nodeId != null && node.typeId != null) {
                    nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
                }
            }
        }

        graph.connections = new ArrayList<>(graph.connections);
        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String sourcePort = normalizePortId(connection.sourcePortId);

            if (SET_VARIABLE_TYPE.equals(sourceType) && "output_success".equals(sourcePort)) {
                connection.sourcePortId = "output_valid";
                return false;
            }
            return VARIABLE_LIST_TYPE.equals(sourceType) && "output_entries".equals(sourcePort);
        });
    }

    private static void stripClearVariablesState(SavedNode node) {
        if (!(node.state instanceof Map<?, ?> state)) {
            return;
        }
        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<?, ?> entry : state.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            if ("includeinternalvariables".equals(key.toLowerCase(Locale.ROOT))) {
                continue;
            }
            cleaned.put(key, entry.getValue());
        }
        node.state = cleaned;
    }

    private static final String FILTER_POINTS_BY_RULE_TYPE = "world.query.filter_points_by_rule";
    private static final String FILTER_GRID_POINTS_TYPE = "world.query.filter_grid_points";

    /**
     * World Query v1: drop removed Filter Points By Rule block outputs and Mode port wires,
     * drop Filter Grid Points Skipped Count port wires.
     */
    private static SavedGraph migrateV59ToV60(SavedGraph graph) {
        applyWorldQueryV60ToGraph(graph);
        if (graph.subgraphDefinitions != null) {
            for (SavedGraph definition : graph.subgraphDefinitions.values()) {
                if (definition != null) {
                    applyWorldQueryV60ToGraph(definition);
                }
            }
        }
        return graph;
    }

    private static void applyWorldQueryV60ToGraph(SavedGraph graph) {
        if (graph.connections == null) {
            return;
        }

        Map<String, String> nodeTypeBySavedId = new HashMap<>();
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node != null && node.nodeId != null && node.typeId != null) {
                    nodeTypeBySavedId.put(node.nodeId, node.typeId.toLowerCase(Locale.ROOT));
                }
            }
        }

        graph.connections = new ArrayList<>(graph.connections);
        graph.connections.removeIf(connection -> {
            if (connection == null) {
                return false;
            }
            String sourceType = nodeTypeBySavedId.get(connection.sourceNodeId);
            String targetType = nodeTypeBySavedId.get(connection.targetNodeId);
            String sourcePort = normalizePortId(connection.sourcePortId);
            String targetPort = normalizePortId(connection.targetPortId);

            if (FILTER_POINTS_BY_RULE_TYPE.equals(sourceType)) {
                return "output_filtered_blocks".equals(sourcePort)
                        || "output_removed_blocks".equals(sourcePort);
            }
            if (FILTER_POINTS_BY_RULE_TYPE.equals(targetType)) {
                return "input_mode".equals(targetPort);
            }
            if (FILTER_GRID_POINTS_TYPE.equals(sourceType)) {
                return "output_skipped_count".equals(sourcePort);
            }
            return false;
        });
    }
}

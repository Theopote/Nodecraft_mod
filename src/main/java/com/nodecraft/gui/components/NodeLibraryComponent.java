package com.nodecraft.gui.components;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.gui.style.MinecraftTheme;
import com.nodecraft.nodesystem.registry.NodeRegistry.NodeCategory;
import com.nodecraft.gui.utils.NodeIconManager;
import com.nodecraft.gui.utils.UserPreferences;
import com.nodecraft.gui.components.search.NodeSearchManager;
import org.jspecify.annotations.NonNull;
import org.lwjgl.opengl.GL11;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiDragDropFlags;
import imgui.ImDrawList;
import imgui.ImVec2;

/**
 * Node library panel used by the editor sidebar.
 * Handles category grouping, search, filtering, and drag/drop entry points.
 */
public class NodeLibraryComponent implements EditorComponent {

    private static final Map<String, Map<String, Integer>> CATEGORY_NODE_ORDER = createCategoryNodeOrder();

    // Inner record for display purposes
    private record DisplayCategory(NodeCategory originalCategory, List<NodeInfo> displayNodes) {
        String getDisplayName() { return originalCategory.getDisplayName(); }
        String getId() { return originalCategory.getId(); } // For ImGui IDs
        List<NodeInfo> getNodes() { return displayNodes; } // Returns filtered nodes
    }

    private static Map<String, Map<String, Integer>> createCategoryNodeOrder() {
        Map<String, Map<String, Integer>> categoryOrder = new HashMap<>();

        Map<String, Integer> previewOrder = getPreviewOrder();
        categoryOrder.put("output.preview", previewOrder);

        Map<String, Integer> outputExecuteOrder = getOutputExecuteOrder();
        categoryOrder.put("output.execute", outputExecuteOrder);

        Map<String, Integer> outputExportOrder = getOutputExportOrder();
        categoryOrder.put("output.export", outputExportOrder);

        Map<String, Integer> outputDebugOrder = getOutputDebugOrder();
        categoryOrder.put("output.debug", outputDebugOrder);

        Map<String, Integer> worldReadOrder = getWorldReadOrder();
        categoryOrder.put("world.read", worldReadOrder);

        Map<String, Integer> worldWriteOrder = getWorldWriteOrder();
        categoryOrder.put("world.write", worldWriteOrder);

        Map<String, Integer> materialBasicAssignmentOrder = getMaterialBasicAssignmentOrder();
        categoryOrder.put("material.basic_assignment", materialBasicAssignmentOrder);

        Map<String, Integer> inputNumericOrder = getInputNumericOrder();
        categoryOrder.put("input.numeric", inputNumericOrder);

        Map<String, Integer> inputContextOrder = getInputContextOrder();
        categoryOrder.put("input.context", inputContextOrder);

        Map<String, Integer> inputTypeSelectorsOrder = getInputTypeSelectorsOrder();
        categoryOrder.put("input.type_selectors", inputTypeSelectorsOrder);

        Map<String, Integer> worldSelectionOrder = getWorldSelectionOrder();
        categoryOrder.put("world.selection", worldSelectionOrder);

        Map<String, Integer> worldQueryOrder = getWorldQueryOrder();
        categoryOrder.put("world.query", worldQueryOrder);

        Map<String, Integer> referencePointsOrder = getReferencePointsOrder();
        categoryOrder.put("reference.points", referencePointsOrder);

        Map<String, Integer> referenceVectorsOrder = getReferenceVectorsOrder();
        categoryOrder.put("reference.vectors", referenceVectorsOrder);

        Map<String, Integer> referencePlanesOrder = getReferencePlanesOrder();
        categoryOrder.put("reference.planes", referencePlanesOrder);

        Map<String, Integer> referenceFramesOrder = getReferenceFramesOrder();
        categoryOrder.put("reference.frames", referenceFramesOrder);

        Map<String, Integer> geometryBooleanOrder = getGeometryBooleanOrder();
        categoryOrder.put("geometry.boolean", geometryBooleanOrder);

        Map<String, Integer> geometryCurvesOrder = getGeometryCurvesOrder();
        categoryOrder.put("geometry.curves", geometryCurvesOrder);

        Map<String, Integer> geometryPrimitivesOrder = getGeometryPrimitivesOrder();
        categoryOrder.put("geometry.primitives", geometryPrimitivesOrder);

        Map<String, Integer> geometryProfilesOrder = getGeometryProfilesOrder();
        categoryOrder.put("geometry.profiles", geometryProfilesOrder);

        Map<String, Integer> geometrySolidsOrder = getGeometrySolidsOrder();
        categoryOrder.put("geometry.solids", geometrySolidsOrder);

        Map<String, Integer> patternLinearOrder = getPatternLinearOrder();
        categoryOrder.put("pattern.linear", patternLinearOrder);

        Map<String, Integer> patternGridOrder = getPatternGridOrder();
        categoryOrder.put("pattern.grid", patternGridOrder);

        Map<String, Integer> patternRadialOrder = getPatternRadialOrder();
        categoryOrder.put("pattern.radial", patternRadialOrder);

        Map<String, Integer> patternSurfaceVolumeDistributionOrder = getPatternSurfaceVolumeDistributionOrder();
        categoryOrder.put("pattern.surface_volume_distribution", patternSurfaceVolumeDistributionOrder);

        Map<String, Integer> transformBasicTransformsOrder = getTransformBasicTransformsOrder();
        categoryOrder.put("transform.basic_transforms", transformBasicTransformsOrder);

        Map<String, Integer> transformDeformationsOrder = getTransformDeformationsOrder();
        categoryOrder.put("transform.deformations", transformDeformationsOrder);

        Map<String, Integer> transformOrientationOrder = getTransformOrientationOrder();
        categoryOrder.put("transform.orientation", transformOrientationOrder);

        Map<String, Integer> mathListSequenceOrder = getMathListSequenceOrder();
        categoryOrder.put("math.list_sequence", mathListSequenceOrder);

        Map<String, Integer> mathCompareOrder = getMathCompareOrder();
        categoryOrder.put("math.compare", mathCompareOrder);

        Map<String, Integer> mathScalarMathOrder = getMathScalarMathOrder();
        categoryOrder.put("math.scalar_math", mathScalarMathOrder);

        Map<String, Integer> mathRandomOrder = getMathRandomOrder();
        categoryOrder.put("math.random", mathRandomOrder);

        Map<String, Integer> mathTrigonometryOrder = getMathTrigonometryOrder();
        categoryOrder.put("math.trigonometry", mathTrigonometryOrder);

        Map<String, Integer> deferredMathOrder = getDeferredMathOrder();
        categoryOrder.put("deferred.math", deferredMathOrder);

        Map<String, Integer> spatialLegacyOrder = getStringIntegerMap();
        categoryOrder.put("spatial.legacy", spatialLegacyOrder);

        return categoryOrder;
    }

    private static @NonNull Map<String, Integer> getInputNumericOrder() {
        Map<String, Integer> inputNumericOrder = new HashMap<>();
        inputNumericOrder.put("input.numeric.integer", 0);
        inputNumericOrder.put("input.numeric.float", 1);
        inputNumericOrder.put("input.numeric.integer_slider", 2);
        inputNumericOrder.put("input.numeric.float_slider", 3);
        inputNumericOrder.put("input.numeric.angle", 4);
        inputNumericOrder.put("input.numeric.angle_picker", 5);
        inputNumericOrder.put("input.numeric.boolean_toggle", 6);
        return inputNumericOrder;
    }

    private static @NonNull Map<String, Integer> getInputContextOrder() {
        Map<String, Integer> inputContextOrder = new HashMap<>();
        inputContextOrder.put("input.context.player_position", 0);
        inputContextOrder.put("input.context.player_look_direction", 1);
        inputContextOrder.put("input.context.dimension_info", 2);
        inputContextOrder.put("input.context.current_time", 3);
        return inputContextOrder;
    }

    private static @NonNull Map<String, Integer> getInputTypeSelectorsOrder() {
        Map<String, Integer> inputTypeSelectorsOrder = new HashMap<>();
        inputTypeSelectorsOrder.put("input.type_selectors.block_type_selector", 0);
        return inputTypeSelectorsOrder;
    }

    private static @NonNull Map<String, Integer> getWorldSelectionOrder() {
        Map<String, Integer> worldSelectionOrder = new HashMap<>();
        worldSelectionOrder.put("world.selection.selected_block", 0);
        worldSelectionOrder.put("world.selection.selected_region", 1);
        worldSelectionOrder.put("world.selection.snap_point_to_block", 2);
        worldSelectionOrder.put("world.selection.snap_points_to_blocks", 3);
        worldSelectionOrder.put("world.selection.point_to_block_if_grid", 4);
        return worldSelectionOrder;
    }

    private static @NonNull Map<String, Integer> getWorldQueryOrder() {
        Map<String, Integer> worldQueryOrder = new HashMap<>();
        worldQueryOrder.put("world.query.get_light_level", 0);
        worldQueryOrder.put("world.query.get_fluid_level", 1);
        worldQueryOrder.put("world.query.is_grid_point", 2);
        worldQueryOrder.put("world.query.filter_grid_points", 3);
        worldQueryOrder.put("world.query.is_point_in_region", 4);
        return worldQueryOrder;
    }

    private static @NonNull Map<String, Integer> getReferencePointsOrder() {
        Map<String, Integer> referencePointsOrder = new HashMap<>();
        referencePointsOrder.put("reference.points.point_from_coordinates", 0);
        referencePointsOrder.put("reference.points.point_from_block", 1);
        referencePointsOrder.put("reference.points.point_along_vector", 2);
        referencePointsOrder.put("reference.points.deconstruct_point", 3);
        referencePointsOrder.put("reference.points.mid_point", 4);
        referencePointsOrder.put("reference.points.distance_between_points", 5);
        referencePointsOrder.put("reference.points.closest_point", 6);
        referencePointsOrder.put("reference.points.point_list_center", 7);
        referencePointsOrder.put("reference.points.point_list_bounds", 8);
        referencePointsOrder.put("reference.points.get_box_corner", 9);
        referencePointsOrder.put("reference.points.get_box_face", 10);
        referencePointsOrder.put("reference.points.get_face_edge", 11);
        referencePointsOrder.put("reference.points.deconstruct_face", 12);
        referencePointsOrder.put("reference.points.deconstruct_edge", 13);
        return referencePointsOrder;
    }

    private static @NonNull Map<String, Integer> getReferenceVectorsOrder() {
        Map<String, Integer> referenceVectorsOrder = new HashMap<>();
        referenceVectorsOrder.put("reference.vectors.vector", 0);
        referenceVectorsOrder.put("reference.vectors.deconstruct_vector", 1);
        referenceVectorsOrder.put("reference.vectors.normalize_vector", 2);
        referenceVectorsOrder.put("reference.vectors.cross_product", 3);
        referenceVectorsOrder.put("reference.vectors.dot_product", 4);
        referenceVectorsOrder.put("reference.vectors.vector_length", 5);
        referenceVectorsOrder.put("reference.vectors.vector_addition", 6);
        referenceVectorsOrder.put("reference.vectors.vector_subtraction", 7);
        referenceVectorsOrder.put("reference.vectors.vector_scalar_multiply", 8);
        referenceVectorsOrder.put("reference.vectors.vector_scalar_divide", 9);
        return referenceVectorsOrder;
    }

    private static @NonNull Map<String, Integer> getReferencePlanesOrder() {
        Map<String, Integer> referencePlanesOrder = new HashMap<>();
        referencePlanesOrder.put("reference.planes.world_plane", 0);
        referencePlanesOrder.put("reference.planes.construct_plane", 1);
        referencePlanesOrder.put("reference.planes.plane_from_points", 2);
        referencePlanesOrder.put("reference.planes.distance_point_to_plane", 3);
        referencePlanesOrder.put("reference.planes.block_face_plane", 4);
        return referencePlanesOrder;
    }

    private static @NonNull Map<String, Integer> getMathListSequenceOrder() {
        Map<String, Integer> mathListSequenceOrder = new HashMap<>();
        mathListSequenceOrder.put("math.list_sequence.create_list", 0);
        mathListSequenceOrder.put("math.list_sequence.range", 1);
        return mathListSequenceOrder;
    }

    private static @NonNull Map<String, Integer> getMathCompareOrder() {
        Map<String, Integer> mathCompareOrder = new HashMap<>();
        mathCompareOrder.put("math.compare.equals", 0);
        mathCompareOrder.put("math.compare.not_equals", 1);
        mathCompareOrder.put("math.compare.less_than", 2);
        mathCompareOrder.put("math.compare.less_than_or_equal", 3);
        mathCompareOrder.put("math.compare.greater_than", 4);
        mathCompareOrder.put("math.compare.greater_than_or_equal", 5);
        return mathCompareOrder;
    }

    private static @NonNull Map<String, Integer> getMathScalarMathOrder() {
        Map<String, Integer> mathScalarMathOrder = new HashMap<>();
        mathScalarMathOrder.put("math.scalar_math.addition", 0);
        mathScalarMathOrder.put("math.scalar_math.subtraction", 1);
        mathScalarMathOrder.put("math.scalar_math.multiplication", 2);
        mathScalarMathOrder.put("math.scalar_math.division", 3);
        mathScalarMathOrder.put("math.scalar_math.modulus", 4);
        mathScalarMathOrder.put("math.scalar_math.power", 5);
        mathScalarMathOrder.put("math.scalar_math.logarithm", 6);
        mathScalarMathOrder.put("math.scalar_math.absolute", 7);
        mathScalarMathOrder.put("math.scalar_math.min", 8);
        mathScalarMathOrder.put("math.scalar_math.max", 9);
        mathScalarMathOrder.put("math.scalar_math.clamp", 10);
        mathScalarMathOrder.put("math.scalar_math.remap", 11);
        mathScalarMathOrder.put("math.scalar_math.floor", 12);
        mathScalarMathOrder.put("math.scalar_math.ceiling", 13);
        mathScalarMathOrder.put("math.scalar_math.round", 14);
        return mathScalarMathOrder;
    }

    private static @NonNull Map<String, Integer> getMathRandomOrder() {
        Map<String, Integer> mathRandomOrder = new HashMap<>();
        mathRandomOrder.put("math.random.random_number", 0);
        mathRandomOrder.put("math.random.noise", 1);
        mathRandomOrder.put("math.random.random_list_item", 2);
        mathRandomOrder.put("math.random.random_vector", 3);
        return mathRandomOrder;
    }

    private static @NonNull Map<String, Integer> getMathTrigonometryOrder() {
        Map<String, Integer> mathTrigonometryOrder = new HashMap<>();
        mathTrigonometryOrder.put("math.trigonometry.sin", 0);
        mathTrigonometryOrder.put("math.trigonometry.cos", 1);
        mathTrigonometryOrder.put("math.trigonometry.tan", 2);
        mathTrigonometryOrder.put("math.trigonometry.deg_to_rad", 3);
        mathTrigonometryOrder.put("math.trigonometry.rad_to_deg", 4);
        mathTrigonometryOrder.put("math.trigonometry.asin", 5);
        mathTrigonometryOrder.put("math.trigonometry.acos", 6);
        mathTrigonometryOrder.put("math.trigonometry.atan", 7);
        mathTrigonometryOrder.put("math.trigonometry.pi", 8);
        return mathTrigonometryOrder;
    }

    private static @NonNull Map<String, Integer> getDeferredMathOrder() {
        Map<String, Integer> deferredMathOrder = new HashMap<>();
        deferredMathOrder.put("deferred.math.math_series", 0);
        return deferredMathOrder;
    }

    private static @NonNull Map<String, Integer> getGeometryBooleanOrder() {
        Map<String, Integer> geometryBooleanOrder = new HashMap<>();
        geometryBooleanOrder.put("geometry.boolean.bounding_box", 0);
        geometryBooleanOrder.put("geometry.boolean.geometry_bounds", 1);
        return geometryBooleanOrder;
    }

    private static @NonNull Map<String, Integer> getGeometryPrimitivesOrder() {
        Map<String, Integer> geometryPrimitivesOrder = new HashMap<>();
        geometryPrimitivesOrder.put("geometry.primitives.box", 0);
        geometryPrimitivesOrder.put("geometry.primitives.box_from_corner_size", 1);
        geometryPrimitivesOrder.put("geometry.primitives.box_from_corners", 2);
        geometryPrimitivesOrder.put("geometry.primitives.sphere", 3);
        geometryPrimitivesOrder.put("geometry.primitives.sphere_from_diameter", 4);
        geometryPrimitivesOrder.put("geometry.primitives.cylinder", 5);
        geometryPrimitivesOrder.put("geometry.primitives.cone", 6);
        geometryPrimitivesOrder.put("geometry.primitives.ellipsoid", 7);
        geometryPrimitivesOrder.put("geometry.primitives.octahedron", 8);
        geometryPrimitivesOrder.put("geometry.primitives.tetrahedron", 9);
        geometryPrimitivesOrder.put("geometry.primitives.deconstruct_box", 10);
        geometryPrimitivesOrder.put("geometry.primitives.deconstruct_sphere", 11);
        geometryPrimitivesOrder.put("geometry.primitives.deconstruct_cylinder", 12);
        geometryPrimitivesOrder.put("geometry.primitives.deconstruct_cone", 13);
        geometryPrimitivesOrder.put("geometry.primitives.deconstruct_ellipsoid", 14);
        geometryPrimitivesOrder.put("geometry.primitives.deconstruct_octahedron", 15);
        geometryPrimitivesOrder.put("geometry.primitives.deconstruct_tetrahedron", 16);
        geometryPrimitivesOrder.put("geometry.primitives.deconstruct_prism", 17);
        return geometryPrimitivesOrder;
    }

    private static @NonNull Map<String, Integer> getGeometryCurvesOrder() {
        Map<String, Integer> geometryCurvesOrder = new HashMap<>();
        geometryCurvesOrder.put("geometry.curves.curve_from_points", 0);
        geometryCurvesOrder.put("geometry.curves.divide_curve_to_points", 1);
        geometryCurvesOrder.put("geometry.curves.edge_to_curve", 2);
        geometryCurvesOrder.put("geometry.curves.face_boundary_curve", 3);
        return geometryCurvesOrder;
    }

    private static @NonNull Map<String, Integer> getGeometryProfilesOrder() {
        Map<String, Integer> geometryProfilesOrder = new HashMap<>();
        geometryProfilesOrder.put("geometry.profiles.rectangle_profile", 0);
        geometryProfilesOrder.put("geometry.profiles.polygon_profile", 1);
        geometryProfilesOrder.put("geometry.profiles.custom_profile", 2);
        geometryProfilesOrder.put("geometry.profiles.resample_profile", 3);
        geometryProfilesOrder.put("geometry.profiles.deconstruct_profile", 4);
        return geometryProfilesOrder;
    }

    private static @NonNull Map<String, Integer> getGeometrySolidsOrder() {
        Map<String, Integer> geometrySolidsOrder = new HashMap<>();
        geometrySolidsOrder.put("geometry.solids.extrude", 0);
        geometrySolidsOrder.put("geometry.solids.extrude_from_points", 1);
        geometrySolidsOrder.put("geometry.solids.extrude_box_face", 2);
        geometrySolidsOrder.put("geometry.solids.loft", 3);
        geometrySolidsOrder.put("geometry.solids.loft_from_points", 4);
        geometrySolidsOrder.put("geometry.solids.sweep", 5);
        geometrySolidsOrder.put("geometry.solids.sweep_from_points", 6);
        geometrySolidsOrder.put("geometry.solids.surface_strip_to_geometry", 7);
        geometrySolidsOrder.put("geometry.solids.push_pull_face", 8);
        geometrySolidsOrder.put("geometry.solids.extrude_profile", 9);
        geometrySolidsOrder.put("geometry.solids.extrude_profile_from_points", 10);
        return geometrySolidsOrder;
    }

    private static @NonNull Map<String, Integer> getTransformBasicTransformsOrder() {
        Map<String, Integer> transformBasicTransformsOrder = new HashMap<>();
        transformBasicTransformsOrder.put("transform.basic_transforms.move_point", 0);
        transformBasicTransformsOrder.put("transform.basic_transforms.move_points", 1);
        transformBasicTransformsOrder.put("transform.basic_transforms.rotate_points", 2);
        transformBasicTransformsOrder.put("transform.basic_transforms.scale_points", 3);
        transformBasicTransformsOrder.put("transform.basic_transforms.mirror_points", 4);
        transformBasicTransformsOrder.put("transform.basic_transforms.offset_face", 5);
        transformBasicTransformsOrder.put("transform.basic_transforms.inset_face", 6);
        return transformBasicTransformsOrder;
    }

    private static @NonNull Map<String, Integer> getTransformOrientationOrder() {
        Map<String, Integer> transformOrientationOrder = new HashMap<>();
        transformOrientationOrder.put("transform.orientation.project_to_plane", 0);
        transformOrientationOrder.put("transform.orientation.rotate_vector", 1);
        return transformOrientationOrder;
    }

    private static @NonNull Map<String, Integer> getTransformDeformationsOrder() {
        Map<String, Integer> transformDeformationsOrder = new HashMap<>();
        transformDeformationsOrder.put("transform.deformations.twist", 0);
        return transformDeformationsOrder;
    }

    private static @NonNull Map<String, Integer> getPreviewOrder() {
        Map<String, Integer> previewOrder = new HashMap<>();
        previewOrder.put("output.preview.geometry_viewer", 0);
        previewOrder.put("output.preview.preview_blocks", 1);
        previewOrder.put("output.preview.preview_points", 2);
        previewOrder.put("output.preview.preview_vectors", 3);
        previewOrder.put("output.preview.preview_plane", 4);
        previewOrder.put("output.preview.preview_frame", 5);
        previewOrder.put("output.preview.preview_curves", 6);
        previewOrder.put("output.preview.preview_regions", 7);
        previewOrder.put("output.preview.preview_labels", 8);
        previewOrder.put("output.preview.preview_surface_strip", 9);
        previewOrder.put("output.preview.preview_profiles", 10);
        return previewOrder;
    }

    private static @NonNull Map<String, Integer> getOutputExecuteOrder() {
        Map<String, Integer> outputExecuteOrder = new HashMap<>();
        outputExecuteOrder.put("output.execute.apply_changes", 0);
        outputExecuteOrder.put("output.execute.clear_preview", 1);
        outputExecuteOrder.put("output.execute.bake_geometry_to_blocks", 2);
        outputExecuteOrder.put("output.execute.bake_surface_strip_to_blocks", 3);
        outputExecuteOrder.put("output.execute.bake_box_to_blocks", 4);
        outputExecuteOrder.put("output.execute.bake_sphere_to_blocks", 5);
        outputExecuteOrder.put("output.execute.bake_cylinder_to_blocks", 6);
        outputExecuteOrder.put("output.execute.bake_cone_to_blocks", 7);
        outputExecuteOrder.put("output.execute.bake_ellipsoid_to_blocks", 8);
        outputExecuteOrder.put("output.execute.bake_prism_to_blocks", 9);
        outputExecuteOrder.put("output.execute.bake_octahedron_to_blocks", 10);
        outputExecuteOrder.put("output.execute.bake_tetrahedron_to_blocks", 11);
        outputExecuteOrder.put("output.execute.bake_torus_to_blocks", 12);
        return outputExecuteOrder;
    }

    private static @NonNull Map<String, Integer> getOutputExportOrder() {
        Map<String, Integer> outputExportOrder = new HashMap<>();
        outputExportOrder.put("output.export.export_schematic", 0);
        return outputExportOrder;
    }

    private static @NonNull Map<String, Integer> getOutputDebugOrder() {
        Map<String, Integer> outputDebugOrder = new HashMap<>();
        outputDebugOrder.put("output.debug.value_monitor", 0);
        outputDebugOrder.put("output.debug.print_to_chat", 1);
        outputDebugOrder.put("output.debug.execution_timer", 2);
        outputDebugOrder.put("output.debug.data_inspector", 3);
        return outputDebugOrder;
    }

    private static @NonNull Map<String, Integer> getWorldReadOrder() {
        Map<String, Integer> worldReadOrder = new HashMap<>();
        worldReadOrder.put("world.read.get_block", 0);
        worldReadOrder.put("world.read.get_blocks_in_region", 1);
        worldReadOrder.put("world.read.find_blocks", 2);
        worldReadOrder.put("world.read.get_biome", 3);
        worldReadOrder.put("world.read.biome_at_player", 4);
        worldReadOrder.put("world.read.get_points_in_region", 5);
        return worldReadOrder;
    }

    private static @NonNull Map<String, Integer> getWorldWriteOrder() {
        Map<String, Integer> worldWriteOrder = new HashMap<>();
        worldWriteOrder.put("world.write.set_block", 0);
        worldWriteOrder.put("world.write.set_blocks", 1);
        worldWriteOrder.put("world.write.fill_region", 2);
        worldWriteOrder.put("world.write.replace_blocks", 3);
        worldWriteOrder.put("world.write.clone_region", 4);
        worldWriteOrder.put("world.write.clear_region", 5);
        return worldWriteOrder;
    }

    private static @NonNull Map<String, Integer> getMaterialBasicAssignmentOrder() {
        Map<String, Integer> materialBasicAssignmentOrder = new HashMap<>();
        materialBasicAssignmentOrder.put("material.basic_assignment.replace_material", 0);
        return materialBasicAssignmentOrder;
    }

    private static @NonNull Map<String, Integer> getReferenceFramesOrder() {
        Map<String, Integer> referenceFramesOrder = new HashMap<>();
        referenceFramesOrder.put("reference.frames.frame_from_face", 0);
        referenceFramesOrder.put("reference.frames.frame_along_surface", 1);
        return referenceFramesOrder;
    }

    private static @NonNull Map<String, Integer> getPatternSurfaceVolumeDistributionOrder() {
        Map<String, Integer> patternSurfaceVolumeDistributionOrder = new HashMap<>();
        patternSurfaceVolumeDistributionOrder.put("pattern.surface_volume_distribution.populate_region", 0);
        patternSurfaceVolumeDistributionOrder.put("pattern.surface_volume_distribution.sample_surface", 1);
        patternSurfaceVolumeDistributionOrder.put("pattern.surface_volume_distribution.surface_scatter", 2);
        return patternSurfaceVolumeDistributionOrder;
    }

    private static @NonNull Map<String, Integer> getPatternLinearOrder() {
        Map<String, Integer> patternLinearOrder = new HashMap<>();
        patternLinearOrder.put("pattern.linear.linear_array", 0);
        return patternLinearOrder;
    }

    private static @NonNull Map<String, Integer> getPatternGridOrder() {
        Map<String, Integer> patternGridOrder = new HashMap<>();
        patternGridOrder.put("pattern.grid.grid_array", 0);
        return patternGridOrder;
    }

    private static @NonNull Map<String, Integer> getPatternRadialOrder() {
        Map<String, Integer> patternRadialOrder = new HashMap<>();
        patternRadialOrder.put("pattern.radial.polar_array", 0);
        return patternRadialOrder;
    }

    private static @NonNull Map<String, Integer> getStringIntegerMap() {
        Map<String, Integer> spatialLegacyOrder = new HashMap<>();
        spatialLegacyOrder.put("spatial.generators.line_blocks", 0);
        spatialLegacyOrder.put("spatial.generators.rectangle_blocks", 1);
        spatialLegacyOrder.put("spatial.generators.arc_blocks", 2);
        spatialLegacyOrder.put("spatial.generators.semicircle_blocks", 3);
        spatialLegacyOrder.put("spatial.generators.regular_polygon_blocks", 4);
        spatialLegacyOrder.put("spatial.generators.star_blocks", 5);
        spatialLegacyOrder.put("spatial.generators.ellipse_blocks", 6);
        spatialLegacyOrder.put("spatial.generators.circle_sphere_blocks", 7);
        spatialLegacyOrder.put("spatial.generators.box_blocks", 8);
        spatialLegacyOrder.put("spatial.generators.region_box_blocks", 9);
        spatialLegacyOrder.put("spatial.generators.cylinder_blocks", 10);
        spatialLegacyOrder.put("spatial.generators.cone_blocks", 11);
        spatialLegacyOrder.put("spatial.generators.ellipsoid_blocks", 12);
        spatialLegacyOrder.put("spatial.generators.torus_blocks", 13);
        spatialLegacyOrder.put("spatial.generators.octahedron_blocks", 14);
        spatialLegacyOrder.put("spatial.generators.tetrahedron_blocks", 15);
        spatialLegacyOrder.put("spatial.generators.triangular_prism_blocks", 16);
        spatialLegacyOrder.put("spatial.generators.triangular_pyramid_blocks", 17);
        spatialLegacyOrder.put("spatial.generators.polyline_blocks", 18);
        spatialLegacyOrder.put("spatial.generators.curve_blocks", 19);
        spatialLegacyOrder.put("spatial.analysis.geometry_info", 20);
        spatialLegacyOrder.put("spatial.analysis.select_sphere_band_sector", 21);
        spatialLegacyOrder.put("spatial.analysis.sphere_uv", 22);
        spatialLegacyOrder.put("spatial.analysis.sphere_point_info", 23);
        spatialLegacyOrder.put("spatial.analysis.deconstruct_surface_strip", 24);
        spatialLegacyOrder.put("spatial.points.point_between_two_points", 25);
        spatialLegacyOrder.put("spatial.points.randomize_coordinates", 26);
        spatialLegacyOrder.put("spatial.voxel.union_coords", 27);
        spatialLegacyOrder.put("spatial.voxel.intersection_coords", 28);
        spatialLegacyOrder.put("spatial.voxel.difference_coords", 29);
        spatialLegacyOrder.put("spatial.instancing.grow_along_normals", 30);
        spatialLegacyOrder.put("spatial.instancing.grow_along_sphere_normal", 31);
        spatialLegacyOrder.put("inputs.minecraft.selected_block_sequence", 32);
        spatialLegacyOrder.put("inputs.minecraft.selected_entity", 33);
        return spatialLegacyOrder;
    }

    // Internal UI constants.
    private static class NodeLibraryConstants {
        static final String PREF_DISPLAY_MODE_KEY = "node_library.display_mode";
        static final String PREF_GRID_TILE_SCALE_KEY = "node_library.grid_tile_scale";
        static final float CHILD_WINDOW_MIN_WIDTH = 50;
        static final float CHILD_WINDOW_MIN_HEIGHT = 50;
        static final float CATEGORY_INDENT = 10f;
        static final float CATEGORY_SPACING_EXPANDED = 3f; // Tighter spacing below expanded categories.
        static final float CATEGORY_SPACING_COLLAPSED = 2f;
        static final float CATEGORY_ITEM_SPACING = 2f; // Spacing between category items.
        static final float GRID_TILE_WIDTH = 92f;
        static final float GRID_TILE_HEIGHT = 84f;
        static final float GRID_ICON_SIZE = 28f;
        static final float GRID_TILE_SPACING_X = 8f;
        static final float GRID_TILE_SPACING_Y = 8f;
        static final float GRID_TILE_TEXT_PADDING = 6f;
        static final float GRID_TILE_SIZE_SCALE = 1.5f;
        static final float GRID_TILE_SIZE_SCALE_MIN = 1.0f;
        static final float GRID_TILE_SIZE_SCALE_MAX = 2.0f;
        static final String DRAG_DROP_PAYLOAD_TYPE = "DND_NODE_FROM_LIBRARY";

        static final Map<String, float[]> CATEGORY_COLORS_FLOAT = new HashMap<>();
        static final Map<String, Integer> CATEGORY_COLORS_INT = new HashMap<>();
        static final float[] DEFAULT_CATEGORY_COLOR_FLOAT = new float[]{0.75f, 0.75f, 0.75f, 1.0f};
        static final int DEFAULT_CATEGORY_COLOR_INT = ImGui.colorConvertFloat4ToU32(DEFAULT_CATEGORY_COLOR_FLOAT[0], DEFAULT_CATEGORY_COLOR_FLOAT[1], DEFAULT_CATEGORY_COLOR_FLOAT[2], DEFAULT_CATEGORY_COLOR_FLOAT[3]);

        static {
            // Top-level category colors. Lowercase IDs are the canonical lookup keys.
            CATEGORY_COLORS_FLOAT.put("inputs", new float[]{0.2f, 0.5f, 0.9f, 1.0f});          // Blue: input sources
            CATEGORY_COLORS_FLOAT.put("data", new float[]{0.95f, 0.6f, 0.2f, 1.0f});           // Orange: data processing
            CATEGORY_COLORS_FLOAT.put("math", new float[]{0.3f, 0.8f, 0.3f, 1.0f});            // Green: math and logic
            CATEGORY_COLORS_FLOAT.put("spatial", new float[]{0.9f, 0.9f, 0.2f, 1.0f});         // Yellow: legacy spatial domain
            CATEGORY_COLORS_FLOAT.put("world", new float[]{0.2f, 0.8f, 0.8f, 1.0f});           // Cyan: world interaction
            CATEGORY_COLORS_FLOAT.put("output", new float[]{0.85f, 0.2f, 0.5f, 1.0f});         // Pink: output and execution
            CATEGORY_COLORS_FLOAT.put("visualization", new float[]{0.85f, 0.2f, 0.5f, 1.0f});  // Legacy visualization alias
            CATEGORY_COLORS_FLOAT.put("deferred", new float[]{0.65f, 0.65f, 0.65f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("utilities", new float[]{0.7f, 0.7f, 0.7f, 1.0f});       // Gray: utility nodes
            CATEGORY_COLORS_FLOAT.put("flora", new float[]{0.2f, 0.6f, 0.2f, 1.0f});           // Dark green: flora generation
            CATEGORY_COLORS_FLOAT.put("animation", new float[]{0.8f, 0.3f, 0.3f, 1.0f});       // Red: animation
            CATEGORY_COLORS_FLOAT.put("workflow", new float[]{0.7f, 0.7f, 0.7f, 1.0f});        // Compatibility for utilities/workflow
            CATEGORY_COLORS_FLOAT.put("input", new float[]{0.2f, 0.5f, 0.9f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("reference", new float[]{0.95f, 0.9f, 0.25f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("transform", new float[]{0.95f, 0.55f, 0.25f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("geometry", new float[]{0.92f, 0.82f, 0.18f, 1.0f});
            
            // Add title-case variants for compatibility with older display labels.
            CATEGORY_COLORS_FLOAT.put("Inputs", new float[]{0.2f, 0.5f, 0.9f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("Data", new float[]{0.95f, 0.6f, 0.2f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("Math", new float[]{0.3f, 0.8f, 0.3f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("Spatial", new float[]{0.9f, 0.9f, 0.2f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("World", new float[]{0.2f, 0.8f, 0.8f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("Output", new float[]{0.85f, 0.2f, 0.5f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("Visualization", new float[]{0.85f, 0.2f, 0.5f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("Utilities", new float[]{0.7f, 0.7f, 0.7f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("Flora", new float[]{0.2f, 0.6f, 0.2f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("Animation", new float[]{0.8f, 0.3f, 0.3f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("Input", new float[]{0.2f, 0.5f, 0.9f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("Reference", new float[]{0.95f, 0.9f, 0.25f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("Transform", new float[]{0.95f, 0.55f, 0.25f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("Geometry", new float[]{0.92f, 0.82f, 0.18f, 1.0f});
            
            // Subcategory colors use slightly lighter variants of their parent colors.
            // Input subcategories.
            CATEGORY_COLORS_FLOAT.put("inputs.basic", new float[]{0.3f, 0.6f, 0.95f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("inputs.selectors", new float[]{0.4f, 0.7f, 1.0f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("inputs.sources", new float[]{0.45f, 0.75f, 1.0f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("input.numeric", new float[]{0.3f, 0.6f, 0.95f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("input.context", new float[]{0.35f, 0.65f, 1.0f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("input.type_selectors", new float[]{0.4f, 0.7f, 1.0f, 1.0f});
            
            // Data subcategories.
            CATEGORY_COLORS_FLOAT.put("data.conversion", new float[]{1.0f, 0.65f, 0.25f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("data.lists", new float[]{1.0f, 0.7f, 0.3f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("data.sequence", new float[]{1.0f, 0.75f, 0.35f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("data.text", new float[]{1.0f, 0.8f, 0.4f, 1.0f});
            
            // Math subcategories.
            CATEGORY_COLORS_FLOAT.put("math.logic", new float[]{0.45f, 0.9f, 0.45f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("math.randomness", new float[]{0.5f, 0.95f, 0.5f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("math.trigonometry", new float[]{0.55f, 1.0f, 0.55f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("math.vector", new float[]{0.6f, 1.0f, 0.6f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("math.list_sequence", new float[]{0.5f, 0.92f, 0.5f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("math.compare", new float[]{0.48f, 0.9f, 0.48f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("math.scalar_math", new float[]{0.42f, 0.87f, 0.42f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("math.random", new float[]{0.52f, 0.95f, 0.52f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("deferred.math", new float[]{0.72f, 0.72f, 0.72f, 1.0f});
            
            // Spatial and migrated-reference subcategories.
            CATEGORY_COLORS_FLOAT.put("spatial.arrays", new float[]{1.0f, 1.0f, 0.4f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("spatial.generators", new float[]{1.0f, 1.0f, 0.45f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("spatial.legacy", new float[]{0.85f, 0.85f, 0.45f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("spatial.shapes", new float[]{1.0f, 1.0f, 0.45f, 1.0f});   // Compatibility for old generators grouping.
            CATEGORY_COLORS_FLOAT.put("reference.points", new float[]{1.0f, 0.98f, 0.45f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("reference.vectors", new float[]{1.0f, 1.0f, 0.5f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("reference.planes", new float[]{1.0f, 1.0f, 0.55f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("reference.frames", new float[]{1.0f, 0.96f, 0.5f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("geometry.boolean", new float[]{0.98f, 0.88f, 0.28f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("geometry.curves", new float[]{1.0f, 0.9f, 0.32f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("geometry.primitives", new float[]{0.96f, 0.9f, 0.34f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("geometry.profiles", new float[]{1.0f, 0.94f, 0.4f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("geometry.solids", new float[]{0.98f, 0.86f, 0.3f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("transform.basic_transforms", new float[]{1.0f, 0.62f, 0.32f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("transform.deformations", new float[]{0.98f, 0.58f, 0.28f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("transform.orientation", new float[]{1.0f, 0.68f, 0.38f, 1.0f});
            
            // World subcategories.
            CATEGORY_COLORS_FLOAT.put("world.entity", new float[]{0.3f, 0.85f, 0.85f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("world.interaction", new float[]{0.35f, 0.9f, 0.9f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("world.inventory", new float[]{0.4f, 0.95f, 0.95f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("world.modify", new float[]{0.45f, 1.0f, 1.0f, 1.0f});     // Compatibility for old modification naming.
            CATEGORY_COLORS_FLOAT.put("world.modification", new float[]{0.45f, 1.0f, 1.0f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("world.nbt", new float[]{0.5f, 1.0f, 1.0f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("world.read", new float[]{0.5f, 0.95f, 1.0f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("world.query", new float[]{0.55f, 1.0f, 1.0f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("world.selection", new float[]{0.42f, 0.94f, 1.0f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("world.write", new float[]{0.45f, 1.0f, 1.0f, 1.0f});

            CATEGORY_COLORS_FLOAT.put("material", new float[]{0.8f, 0.55f, 0.2f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("material.basic_assignment", new float[]{0.88f, 0.6f, 0.24f, 1.0f});
            
            // Output and legacy visualization subcategories.
            CATEGORY_COLORS_FLOAT.put("output.debug", new float[]{0.9f, 0.3f, 0.6f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("output.execute", new float[]{0.95f, 0.35f, 0.65f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("output.export", new float[]{0.98f, 0.45f, 0.72f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("output.preview", new float[]{1.0f, 0.4f, 0.7f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("visualization.debugging", new float[]{0.9f, 0.3f, 0.6f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("visualization.debug", new float[]{0.9f, 0.3f, 0.6f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("visualization.execute", new float[]{0.95f, 0.35f, 0.65f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("visualization.preview", new float[]{1.0f, 0.4f, 0.7f, 1.0f});
            
            // Utilities and workflow compatibility subcategories.
            CATEGORY_COLORS_FLOAT.put("utilities.advanced", new float[]{0.75f, 0.75f, 0.75f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("utilities.experimental", new float[]{0.8f, 0.8f, 0.8f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("utilities.fileio", new float[]{0.85f, 0.85f, 0.85f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("utilities.organization", new float[]{0.9f, 0.9f, 0.9f, 1.0f});
            
            // Flora subcategories.
            CATEGORY_COLORS_FLOAT.put("flora.algorithms", new float[]{0.3f, 0.7f, 0.3f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("flora.generators", new float[]{0.35f, 0.75f, 0.35f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("flora.materials", new float[]{0.4f, 0.8f, 0.4f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("flora.modifiers", new float[]{0.45f, 0.85f, 0.45f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("flora.output", new float[]{0.5f, 0.9f, 0.5f, 1.0f});
            
            // Animation subcategories.
            CATEGORY_COLORS_FLOAT.put("animation.effects", new float[]{0.85f, 0.4f, 0.4f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("animation.interpolation", new float[]{0.9f, 0.45f, 0.45f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("animation.output", new float[]{0.95f, 0.5f, 0.5f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("animation.time", new float[]{1.0f, 0.55f, 0.55f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("animation.transforms", new float[]{1.0f, 0.6f, 0.6f, 1.0f});
            
            // Compatibility for the legacy workflow.* prefix.
            CATEGORY_COLORS_FLOAT.put("workflow.advanced", new float[]{0.75f, 0.75f, 0.75f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("workflow.experimental", new float[]{0.8f, 0.8f, 0.8f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("workflow.fileio", new float[]{0.85f, 0.85f, 0.85f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("workflow.organization", new float[]{0.9f, 0.9f, 0.9f, 1.0f});
            
            // Compatibility for older display labels.
            CATEGORY_COLORS_FLOAT.put("Params", new float[]{0.2f, 0.5f, 0.9f, 1.0f});        // Blue
            CATEGORY_COLORS_FLOAT.put("Maths", new float[]{0.3f, 0.8f, 0.3f, 1.0f});         // Green
            CATEGORY_COLORS_FLOAT.put("Sets", new float[]{0.95f, 0.6f, 0.2f, 1.0f});         // Orange
            CATEGORY_COLORS_FLOAT.put("Logic", new float[]{0.45f, 0.9f, 0.45f, 1.0f});       // Green
            CATEGORY_COLORS_FLOAT.put("Geometry", new float[]{0.9f, 0.9f, 0.2f, 1.0f});      // Yellow
            CATEGORY_COLORS_FLOAT.put("Minecraft", new float[]{0.2f, 0.8f, 0.8f, 1.0f});     // Cyan
            CATEGORY_COLORS_FLOAT.put("General", DEFAULT_CATEGORY_COLOR_FLOAT);              // Fallback default color

            // Convert float colors to packed ImGui colors.
            CATEGORY_COLORS_FLOAT.put("pattern", new float[]{0.98f, 0.74f, 0.22f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("Pattern", new float[]{0.98f, 0.74f, 0.22f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("pattern.linear", new float[]{0.98f, 0.78f, 0.28f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("pattern.grid", new float[]{1.0f, 0.82f, 0.32f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("pattern.radial", new float[]{1.0f, 0.76f, 0.26f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("pattern.surface_volume_distribution", new float[]{1.0f, 0.8f, 0.3f, 1.0f});
            for (Map.Entry<String, float[]> entry : CATEGORY_COLORS_FLOAT.entrySet()) {
                float[] c = entry.getValue();
                CATEGORY_COLORS_INT.put(entry.getKey(), ImGui.colorConvertFloat4ToU32(c[0], c[1], c[2], c[3]));
            }
        }
        
        // Helper for resolving the best packed color for a category name.
        static int getPackedColor(String categoryName) {
            // Try exact match first, preserving caller casing.
            if (CATEGORY_COLORS_INT.containsKey(categoryName)) {
                return CATEGORY_COLORS_INT.get(categoryName);
            }
            
            // Fall back to lowercase lookup because most IDs are lowercase.
            String lowerCaseName = categoryName.toLowerCase();
            if (CATEGORY_COLORS_INT.containsKey(lowerCaseName)) {
                return CATEGORY_COLORS_INT.get(lowerCaseName);
            }

            // Handle display names in the form "Parent / Child".
            if (categoryName.contains(" / ")) {
                String mainPart = categoryName.substring(0, categoryName.indexOf(" / "));
                
                String mainPartLower = mainPart.toLowerCase();
                if (CATEGORY_COLORS_INT.containsKey(mainPartLower)) {
                    return CATEGORY_COLORS_INT.get(mainPartLower);
                }
                
                // Fall back to the original parent casing.
                if (CATEGORY_COLORS_INT.containsKey(mainPart)) {
                    return CATEGORY_COLORS_INT.get(mainPart);
                }
            }
            
            // For dotted IDs such as math.basic, try the top-level category next.
            if (categoryName.contains(".")) {
                String mainCategory = categoryName.substring(0, categoryName.indexOf('.'));
                
                if (CATEGORY_COLORS_INT.containsKey(mainCategory)) {
                    return CATEGORY_COLORS_INT.get(mainCategory);
                }
                
                // Then try a title-cased top-level name.
                String capitalized = mainCategory.substring(0, 1).toUpperCase() + mainCategory.substring(1);
                if (CATEGORY_COLORS_INT.containsKey(capitalized)) {
                    return CATEGORY_COLORS_INT.get(capitalized);
                }
                
                // Finally try the exact dotted subcategory key.
                if (CATEGORY_COLORS_INT.containsKey(categoryName)) {
                    return CATEGORY_COLORS_INT.get(categoryName);
                }
            }
            
            // Use the longest matching prefix as a final fallback.
            String bestMatch = null;
            int bestMatchLength = 0;
            
            for (String key : CATEGORY_COLORS_INT.keySet()) {
                if (categoryName.toLowerCase().startsWith(key.toLowerCase()) && key.length() > bestMatchLength) {
                    bestMatch = key;
                    bestMatchLength = key.length();
                }
            }
            
            if (bestMatch != null) {
                return CATEGORY_COLORS_INT.get(bestMatch);
            }
            
            // Default fallback color.
            return DEFAULT_CATEGORY_COLOR_INT;
        }
    }

    // Node library state
    public enum DisplayMode {
        LIST,
        GRID
    }

    private final List<NodeCategory> allCategories; // All categories exposed by the registry.
    private final Map<String, Boolean> expandedCategories = new HashMap<>();
    private List<DisplayCategory> filteredCategories; // Filtered categories for the current search term.
    private boolean visible = true;
    private DisplayMode displayMode = DisplayMode.LIST;
    private float gridTileSizeScale = NodeLibraryConstants.GRID_TILE_SIZE_SCALE;

    // Icon manager.
    private final NodeIconManager iconManager = NodeIconManager.getInstance();
    
    // Search manager.
    private final NodeSearchManager searchManager = new NodeSearchManager();
    
    /**
     * Callback used when the user selects a node from the library.
     */
    public interface NodeSelectCallback {
        void onNodeSelected(String nodeId, String nodeTitle);
    }
    
    private final NodeSelectCallback selectCallback;
    
    /**
     * Creates the node library component.
     *
     * @param selectCallback node selection callback
     */
    public NodeLibraryComponent(NodeSelectCallback selectCallback) {
        this.selectCallback = selectCallback;
        // Read categories directly from the registry.
        List<NodeCategory> categoriesFromRegistry = NodeRegistry.getInstance().getAllCategories();
        
        // Validate registry output before building local state.
        if (categoriesFromRegistry == null || categoriesFromRegistry.isEmpty()) {
            NodeCraft.LOGGER.warn("NodeRegistry returned an empty or invalid category list.");
            this.allCategories = new ArrayList<>();
        } else {
            // Normalize category hierarchy so top-level and subcategory relationships stay consistent.
            List<NodeCategory> processedCategories = new ArrayList<>();
            
            // Collect top-level categories first.
            Map<String, NodeCategory> topLevelCategories = new HashMap<>();
            for (NodeCategory cat : categoriesFromRegistry) {
                String catId = cat.getId();
                
                // Top-level categories do not contain dots.
                if (!catId.contains(".")) {
                    topLevelCategories.put(catId, cat);
                    processedCategories.add(cat);
                }
            }
            
            // Then process subcategories and validate their parent categories.
            for (NodeCategory cat : categoriesFromRegistry) {
                String catId = cat.getId();
                
                // Dotted IDs are treated as subcategories.
                if (catId.contains(".") && !catId.endsWith(".")) {
                    String parentId = catId.substring(0, catId.lastIndexOf('.'));
                    
                    if (topLevelCategories.containsKey(parentId)) {
                        processedCategories.add(cat);
                    } else {
                        NodeCraft.LOGGER.warn("Subcategory {} is missing parent {}. Treating it as top-level for display.", catId, parentId);
                        processedCategories.add(cat);
                    }
                }
            }
            
            this.allCategories = processedCategories;
        }
        
        // Show the full category list before any search input is applied.
        updateFilteredCategories("");
        
        // Expand top-level categories by default and collapse subcategories.
        for (NodeCategory cat : allCategories) {
            boolean isSubCategory = cat.getId().contains(".") && !cat.getId().endsWith(".");
            
            if (isSubCategory) {
                expandedCategories.put(cat.getId(), false);
            } else {
                expandedCategories.put(cat.getId(), false);
            }
        }
        
        // Keep the main categories expanded even when they are nested.
        String[] keyCategories = {
            "geometry", "input", "material", "math", "output", "pattern", "reference", "transform", "world", "utilities", "deferred", "spatial",
            "input.numeric", "input.context", "input.type_selectors", "reference.points", "reference.vectors", "reference.planes", "reference.frames", "world.selection", "world.read", "world.query", "world.write",
            "geometry.boolean", "geometry.curves", "geometry.primitives", "geometry.profiles", "geometry.solids",
            "pattern.linear", "pattern.grid", "pattern.radial", "pattern.surface_volume_distribution",
            "transform.basic_transforms", "transform.deformations", "transform.orientation",
            "math.scalar_math", "math.compare", "math.random", "math.trigonometry", "math.list_sequence", "deferred.math", "deferred.out_of_scope", "spatial.legacy",
            "output.preview", "output.execute", "output.export", "output.debug", "utilities.organization", "utilities.assist"
        };
        
        for (String key : keyCategories) {
            expandedCategories.put(key, true);
        }

        // Restore persisted display mode preferences.
        String storedMode = UserPreferences.getString(NodeLibraryConstants.PREF_DISPLAY_MODE_KEY, DisplayMode.LIST.name());
        try {
            this.displayMode = DisplayMode.valueOf(storedMode);
        } catch (IllegalArgumentException e) {
            this.displayMode = DisplayMode.LIST;
        }

        float storedGridScale = UserPreferences.getFloat(
            NodeLibraryConstants.PREF_GRID_TILE_SCALE_KEY,
            NodeLibraryConstants.GRID_TILE_SIZE_SCALE
        );
        setGridTileSizeScale(storedGridScale);
        
        // Initialize icon resources.
        iconManager.initialize();
    }
    
    /**
     * Renders the node library panel.
     *
     * @param contentStartY content start Y
     * @param nodePanelWidth panel width
     * @param contentHeight content height
     * @param windowPaddingX horizontal window padding
     */
    public void render(float contentStartY, float nodePanelWidth, float contentHeight, float windowPaddingX) {
        if (!visible) {
            return;
        }
        
        boolean nodeLibraryChildBegin = false;
        
        try {
            // Clamp layout inputs to safe minimums.
            nodePanelWidth = Math.max(NodeLibraryConstants.CHILD_WINDOW_MIN_WIDTH, nodePanelWidth);
            contentHeight = Math.max(NodeLibraryConstants.CHILD_WINDOW_MIN_HEIGHT, contentHeight);
            
            // Create the child window and disable the native scrollbar.
            int windowFlags = ImGuiWindowFlags.NoScrollbar | 
                            ImGuiWindowFlags.NoMove |
                            ImGuiWindowFlags.NoResize |
                            ImGuiWindowFlags.NoCollapse |
                            ImGuiWindowFlags.NoTitleBar;
            
            nodeLibraryChildBegin = ImGui.beginChild("nodeLibrary", nodePanelWidth, contentHeight, true, windowFlags);
            
            if (!nodeLibraryChildBegin) {
                NodeCraft.LOGGER.warn("Failed to begin nodeLibrary child window");
                return;
            }
            
            try {
                // Draw a distinct panel background for the node library.
                ImDrawList drawList = ImGui.getWindowDrawList();
                ImVec2 windowPos = ImGui.getWindowPos();
                int nodeLibraryBgColor = ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.2f, MinecraftTheme.getPanelAlpha());
                drawList.addRectFilled(
                    windowPos.x, 
                    windowPos.y, 
                    windowPos.x + nodePanelWidth, 
                    windowPos.y + contentHeight, 
                    nodeLibraryBgColor
                );
                
                // Search bar.
                renderSearchBar();
                
                ImGui.separator();
                ImGui.spacing();
                
                // Node categories.
                renderNodeCategories();
            } finally {
                // Only end the child window after a successful beginChild call.
                ImGui.endChild();
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to render node library: {}", e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void render(float x, float y, float width, float height, float paddingX, float paddingY) {
        render(y, width, height, paddingX);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void init() {
        // No extra initialization is required here.
        if (NodeCraft.LOGGER.isDebugEnabled()) {
            NodeCraft.LOGGER.debug("Initializing node library component");
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup() {
        // Release icon resources.
        iconManager.cleanup();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isVisible() {
        return visible;
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(DisplayMode mode) {
        if (mode == null || this.displayMode == mode) {
            return;
        }
        this.displayMode = mode;
        UserPreferences.setString(NodeLibraryConstants.PREF_DISPLAY_MODE_KEY, mode.name());
    }

    public float getGridTileSizeScale() {
        return gridTileSizeScale;
    }

    public void setGridTileSizeScale(float scale) {
        float clamped = Math.max(NodeLibraryConstants.GRID_TILE_SIZE_SCALE_MIN,
            Math.min(NodeLibraryConstants.GRID_TILE_SIZE_SCALE_MAX, scale));
        if (Math.abs(this.gridTileSizeScale - clamped) < 0.001f) {
            return;
        }
        this.gridTileSizeScale = clamped;
        UserPreferences.setFloat(NodeLibraryConstants.PREF_GRID_TILE_SCALE_KEY, clamped);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getComponentId() {
        return "nodeLibrary";
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleEvent(String eventType, Object data) {
        // This component does not currently handle external events.
        return false;
    }
    
    /**
     * Renders the search bar.
     */
    private void renderSearchBar() {
        // Delegate search UI rendering to the search manager.
        boolean searchChanged = searchManager.renderSearchBar(this::updateFilteredCategories);
        
        if (searchChanged) {
            NodeCraft.LOGGER.info("Search term changed. Node library will refresh on the next frame.");
        }
    }
    
    /**
     * Updates the filtered category list for the active search term.
     *
     * @param searchTerm search term
     */
    private void updateFilteredCategories(String searchTerm) {
        NodeCraft.LOGGER.info("Updating node library search results for term: '{}'", searchTerm);
        
        // Empty search shows all categories and nodes.
        if (searchTerm == null || searchTerm.isEmpty()) {
            NodeCraft.LOGGER.debug("Search term is empty. Showing all categories.");
            this.filteredCategories = this.allCategories.stream()
                .map(cat -> new DisplayCategory(cat, new ArrayList<>(cat.getNodes())))
                .collect(Collectors.toList());
            NodeCraft.LOGGER.debug("Filtered category count for empty search: {}", this.filteredCategories.size());
            return;
        }

        // Normalize the search term before matching.
        String processedTerm = searchTerm.toLowerCase().trim();
        NodeCraft.LOGGER.info("Normalized search term: '{}'", processedTerm);

        // Scan all categories and nodes directly.
        List<DisplayCategory> searchResults = new ArrayList<>();
        Set<String> parentCategoriesToExpand = new HashSet<>();
        
        for (NodeCategory category : allCategories) {
            String categoryId = category.getId();
            String categoryName = category.getDisplayName().toLowerCase();
            boolean categoryMatches = categoryName.contains(processedTerm) || categoryId.toLowerCase().contains(processedTerm);
            
            // Collect matching nodes in the current category.
            List<NodeInfo> matchingNodes = new ArrayList<>();
            for (NodeInfo node : category.getNodes()) {
                if (matchesNode(node, processedTerm)) {
                    matchingNodes.add(node);
                    NodeCraft.LOGGER.debug("Node matched search term: {} ({}) in category {}", 
                        node.getDisplayName(), node.getId(), categoryId);
                }
            }
            
            // 1. Category name matched, so keep all nodes in that category.
            if (categoryMatches) {
                searchResults.add(new DisplayCategory(category, new ArrayList<>(category.getNodes())));
                NodeCraft.LOGGER.debug("Category matched search term '{}': {} ({}), keeping all nodes", 
                    processedTerm, category.getDisplayName(), categoryId);
                
                expandedCategories.put(categoryId, true);
                
                if (categoryId.contains(".")) {
                    String parentId = categoryId.substring(0, categoryId.lastIndexOf('.'));
                    parentCategoriesToExpand.add(parentId);
                }
                
                continue;
            }
            
            // 2. Category did not match, but some nodes did.
            if (!matchingNodes.isEmpty()) {
                searchResults.add(new DisplayCategory(category, matchingNodes));
                NodeCraft.LOGGER.debug("Category {} contains {} matching nodes", categoryId, matchingNodes.size());
                
                expandedCategories.put(categoryId, true);
                
                if (categoryId.contains(".")) {
                    String parentId = categoryId.substring(0, categoryId.lastIndexOf('.'));
                    parentCategoriesToExpand.add(parentId);
                }
            }
        }
        
        // Expand all parents of matching subcategories.
        for (String parentId : parentCategoriesToExpand) {
            expandedCategories.put(parentId, true);
            NodeCraft.LOGGER.debug("Expanded parent category {}", parentId);
        }
        
        NodeCraft.LOGGER.info("Search '{}' matched {} categories", processedTerm, searchResults.size());
        
        if (!searchResults.isEmpty()) {
            // Ensure parent categories remain visible even when only child categories match.
            List<DisplayCategory> completeResults = new ArrayList<>(searchResults);
            
            for (String parentId : parentCategoriesToExpand) {
                if (!parentId.contains(".")) {
                    boolean alreadyIncluded = searchResults.stream()
                        .anyMatch(dc -> dc.getId().equals(parentId));
                    
                    if (!alreadyIncluded) {
                        for (NodeCategory cat : allCategories) {
                            if (cat.getId().equals(parentId)) {
                                completeResults.add(new DisplayCategory(cat, new ArrayList<>()));
                                NodeCraft.LOGGER.debug("Added missing parent category {}", parentId);
                                break;
                            }
                        }
                    }
                }
            }
            
            this.filteredCategories = completeResults;
        } else {
            NodeCraft.LOGGER.info("No matches found. Showing empty top-level categories.");
            this.filteredCategories = allCategories.stream()
                .filter(cat -> !cat.getId().contains("."))
                .map(cat -> new DisplayCategory(cat, new ArrayList<>()))
                .collect(Collectors.toList());
        }
        
        NodeCraft.LOGGER.info("Filtered category count: {}", this.filteredCategories.size());
    }
    
    /**
     * Returns whether a node matches the current search term.
     */
    private boolean matchesNode(NodeInfo node, String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty() || node == null) {
            return false;
        }
        
        String displayName = node.getDisplayName() != null ? node.getDisplayName().toLowerCase() : "";
        String nodeId = node.getId() != null ? node.getId().toLowerCase() : "";
        String description = node.getDescription() != null ? node.getDescription().toLowerCase() : "";
        
        return displayName.contains(searchTerm) || 
               nodeId.contains(searchTerm) || 
               description.contains(searchTerm);
    }
    
    /**
     * Renders the category list and its visible nodes.
     */
    private void renderNodeCategories() {
        // Use zero height so the child region consumes the remaining vertical space.
        ImGui.beginChild("##nodeListScrollingRegion", 0, 0, false, ImGuiWindowFlags.NoScrollbar);

        // Show empty-state feedback when nothing can be displayed.
        if (filteredCategories.isEmpty() && !searchManager.getSearchTerm().isEmpty()) {
            searchManager.renderNoMatchesMessage();
        } else if (allCategories.isEmpty()) {
            ImGui.textDisabled("  No nodes registered."); 
            ImGui.spacing();
            ImGui.textDisabled("  Please restart the application or check logs.");
        }

        // Control vertical spacing between categories.
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), NodeLibraryConstants.CATEGORY_ITEM_SPACING);

        // Rebuild the display hierarchy into top-level and child categories.
        Map<String, List<DisplayCategory>> childCategoriesMap = new HashMap<>();
        List<DisplayCategory> topLevelCategories = new ArrayList<>();

        // Split categories into top-level and one-level child groupings.
        for (DisplayCategory category : filteredCategories) {
            String id = category.getId();
            
            if (!id.contains(".")) {
                topLevelCategories.add(category);
            } else {
                String parentId = id.substring(0, id.lastIndexOf('.'));
                childCategoriesMap
                    .computeIfAbsent(parentId, k -> new ArrayList<>())
                    .add(category);
            }
        }
        
        // Keep display order stable.
        topLevelCategories.sort((cat1, cat2) -> cat1.getDisplayName().compareToIgnoreCase(cat2.getDisplayName()));
        
        for (List<DisplayCategory> childList : childCategoriesMap.values()) {
            childList.sort((cat1, cat2) -> cat1.getDisplayName().compareToIgnoreCase(cat2.getDisplayName()));
        }

        boolean isSearching = searchManager.getSearchTerm() != null && !searchManager.getSearchTerm().isEmpty();

        // In search mode, force open the categories that contain matching nodes.
        if (isSearching) {
            for (DisplayCategory category : filteredCategories) {
                if (!category.getNodes().isEmpty()) {
                    String categoryId = category.getId();
                    expandedCategories.put(categoryId, true);
                    
                    if (categoryId.contains(".")) {
                        String parentId = categoryId.substring(0, categoryId.lastIndexOf('.'));
                        expandedCategories.put(parentId, true);
                        NodeCraft.LOGGER.debug("Forced parent category open during search: {}", parentId);
                    }
                }
            }
        }

        // Render top-level categories first, then any visible children.
        for (DisplayCategory topCategory : topLevelCategories) {
            renderCategory(topCategory, 0, isSearching);
            
            if (expandedCategories.getOrDefault(topCategory.getId(), true)) {
                List<DisplayCategory> children = childCategoriesMap.get(topCategory.getId());
                
                if (children != null && !children.isEmpty()) {
                    ImGui.indent(NodeLibraryConstants.CATEGORY_INDENT);
                    
                    for (DisplayCategory childCategory : children) {
                        renderCategory(childCategory, 1, isSearching);
                    }
                    
                    ImGui.unindent(NodeLibraryConstants.CATEGORY_INDENT);
                }
            }
        }
        
        ImGui.popStyleVar();
        ImGui.endChild();
    }
    
    /**
     * Renders a single category header and its visible nodes.
     *
     * @param displayCategory category to render
     * @param level nesting depth, where 0 is top-level
     * @param shouldExpand whether the category should be forced open
     */
    private void renderCategory(DisplayCategory displayCategory, int level, boolean shouldExpand) {
        // Default to expanded when a state entry is missing.
        boolean isExpanded = expandedCategories.getOrDefault(displayCategory.getId(), true);
        
        boolean hasNodes = !displayCategory.getNodes().isEmpty();
        
        // Matching categories stay expanded in search mode.
        if (shouldExpand && hasNodes) {
            isExpanded = true;
            expandedCategories.put(displayCategory.getId(), true);
        }
        
        // Prefer category IDs over display names for color lookup.
        int packedColor;
        String categoryId = displayCategory.getId();
        
        if (NodeLibraryConstants.CATEGORY_COLORS_INT.containsKey(categoryId)) {
            packedColor = NodeLibraryConstants.CATEGORY_COLORS_INT.get(categoryId);
        } else {
            packedColor = NodeLibraryConstants.getPackedColor(categoryId);
        }
        
        // Derive background tones from the category color.
        imgui.ImVec4 colorVec = new imgui.ImVec4();
        ImGui.colorConvertU32ToFloat4(packedColor, colorVec);
        float[] color = new float[]{colorVec.x, colorVec.y, colorVec.z, colorVec.w};
        
        float alphaBase = level == 0 ? 0.7f : 0.5f;
        float colorIntensity = level == 0 ? 0.7f : 0.6f;
        
        // Compute colors for normal, hover, and active states.
        int bgColor = ImGui.colorConvertFloat4ToU32(
            color[0] * colorIntensity, 
            color[1] * colorIntensity, 
            color[2] * colorIntensity, 
            alphaBase);
            
        int hoverBgColor = ImGui.colorConvertFloat4ToU32(
            Math.min(1.0f, color[0] * 1.2f * colorIntensity), 
            Math.min(1.0f, color[1] * 1.2f * colorIntensity), 
            Math.min(1.0f, color[2] * 1.2f * colorIntensity), 
            alphaBase + 0.2f);
            
        int activeBgColor = ImGui.colorConvertFloat4ToU32(
            Math.min(1.0f, color[0] * 1.3f * colorIntensity), 
            Math.min(1.0f, color[1] * 1.3f * colorIntensity), 
            Math.min(1.0f, color[2] * 1.3f * colorIntensity), 
            alphaBase + 0.3f);

        // Styling setup.
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), 0);
        
        // Increase text contrast against the colored headers.
        float[] saturatedColor = new float[4];
        System.arraycopy(color, 0, saturatedColor, 0, 4);
        
        for (int i = 0; i < 3; i++) {
            if (saturatedColor[i] < 0.5f) {
                saturatedColor[i] = Math.min(1.0f, saturatedColor[i] * 1.6f);
            } else {
                saturatedColor[i] = Math.min(1.0f, 0.8f + saturatedColor[i] * 0.2f);
            }
        }
        
        float textLuminance = 0.299f * saturatedColor[0] + 0.587f * saturatedColor[1] + 0.114f * saturatedColor[2];
        int enhancedTextColor;
        if (textLuminance < 0.6f) {
            enhancedTextColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            enhancedTextColor = ImGui.colorConvertFloat4ToU32(saturatedColor[0], saturatedColor[1], saturatedColor[2], 1.0f);
        }
        
        ImGui.pushStyleColor(ImGuiCol.Text, enhancedTextColor);
        ImGui.pushStyleColor(ImGuiCol.Header, bgColor);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, hoverBgColor);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, activeBgColor);

        // Give top-level categories a little more visual weight.
        if (level == 0) {
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, 4, 6);
        }

        // Build the label shown in the collapsing header.
        String displayTitle = getString(displayCategory, level, categoryId);

        int headerFlags = ImGuiSelectableFlags.None;
        
        if (shouldExpand && hasNodes) {
            ImGui.setNextItemOpen(true);
        }
        
        boolean headerClicked = ImGui.collapsingHeader(displayTitle + "##header_" + displayCategory.getId(), headerFlags);

        if (level == 0) {
            ImGui.popStyleVar();
        }
        ImGui.popStyleColor(4);
        ImGui.popStyleVar();

        // Persist the latest expansion state.
        expandedCategories.put(displayCategory.getId(), headerClicked);

        // Render nodes only while the category is open.
        if (headerClicked) {
            ImGui.indent(NodeLibraryConstants.CATEGORY_INDENT);
            
            ImGui.dummy(0, 2.0f);

            boolean hasSubcategories = false;
            String catId = displayCategory.getId();
            
            for (DisplayCategory otherCategory : filteredCategories) {
                if (otherCategory != displayCategory && 
                    otherCategory.getId().startsWith(catId + ".")) {
                    hasSubcategories = true;
                    break;
                }
            }
            
            List<NodeInfo> nodesToRender = getSortedNodesForDisplay(displayCategory);
            
            NodeCraft.LOGGER.debug("Category {} has {} nodes to render", displayCategory.getDisplayName(), nodesToRender.size());
            
            if (!searchManager.getSearchTerm().isEmpty()) {
                NodeCraft.LOGGER.debug("Search mode '{}', visible nodes in category {}: {}", 
                    searchManager.getSearchTerm(), 
                    displayCategory.getDisplayName(), 
                    nodesToRender.stream().map(NodeInfo::getDisplayName).collect(Collectors.joining(", ")));
            }

            if (nodesToRender.isEmpty() && searchManager.getSearchTerm().isEmpty() && !hasSubcategories) {
                ImGui.textDisabled("  (No nodes in this category)");
            } else if (nodesToRender.isEmpty() && !searchManager.getSearchTerm().isEmpty()) {
                // Category name matched, but there are no visible nodes to draw.
            } else {
                if (displayMode == DisplayMode.GRID) {
                    renderNodesAsGrid(nodesToRender, displayCategory);
                } else {
                    renderNodesAsList(nodesToRender, displayCategory);
                }
            }
            ImGui.unindent(NodeLibraryConstants.CATEGORY_INDENT);
            ImGui.dummy(0, NodeLibraryConstants.CATEGORY_SPACING_EXPANDED);
        } else {
             // Keep a small spacer below collapsed headers for readability.
             ImGui.dummy(0, NodeLibraryConstants.CATEGORY_SPACING_COLLAPSED);
        }
    }

    private void renderNodesAsList(List<NodeInfo> nodesToRender, DisplayCategory displayCategory) {
        for (NodeInfo node : nodesToRender) {
            String nodeCategory = node.getCategoryId();
            float lineHeight = ImGui.getTextLineHeight();
            float iconPadding = 4.0f;
            float availableWidth = ImGui.getContentRegionAvailX();

            boolean selected = ImGui.selectable("##node_selector_" + node.getId(), false,
                ImGuiSelectableFlags.AllowItemOverlap, availableWidth, lineHeight);

            ImVec2 rectMin = ImGui.getItemRectMin();
            ImDrawList drawList = ImGui.getWindowDrawList();

            renderNode(drawList, rectMin, new ImVec2(lineHeight, lineHeight), node, nodeCategory, lineHeight, iconPadding);
            handleNodeInteraction(node, nodeCategory, displayCategory, lineHeight);

            if (selected && selectCallback != null) {
                selectCallback.onNodeSelected(node.getId(), node.getDisplayName());
                if (NodeCraft.LOGGER.isDebugEnabled()) {
                    NodeCraft.LOGGER.debug("Node selected: {} ({})", node.getDisplayName(), node.getId());
                }
            }
        }
    }

    private void renderNodesAsGrid(List<NodeInfo> nodesToRender, DisplayCategory displayCategory) {
        float listLikeSpacing = ImGui.getStyle().getItemSpacingY();
        // Use frame height as the 1x baseline for grid tiles.
        float tileSide = ImGui.getFrameHeight() * gridTileSizeScale;
        float spacingX = listLikeSpacing;
        float spacingY = listLikeSpacing;
        float availableWidth = Math.max(tileSide, ImGui.getContentRegionAvailX());
        int columns = Math.max(1, (int) ((availableWidth + spacingX) / (tileSide + spacingX)));

        for (int i = 0; i < nodesToRender.size(); i++) {
            NodeInfo node = nodesToRender.get(i);
            String nodeCategory = node.getCategoryId();

            boolean selected = ImGui.selectable("##node_tile_" + node.getId(), false,
                ImGuiSelectableFlags.AllowItemOverlap, tileSide, tileSide);

            ImVec2 rectMin = ImGui.getItemRectMin();
            ImDrawList drawList = ImGui.getWindowDrawList();

            drawGridPlaceholderIcon(drawList, rectMin, tileSide, nodeCategory);
            handleNodeInteraction(node, nodeCategory, displayCategory, tileSide);

            if (selected && selectCallback != null) {
                selectCallback.onNodeSelected(node.getId(), node.getDisplayName());
                if (NodeCraft.LOGGER.isDebugEnabled()) {
                    NodeCraft.LOGGER.debug("Node selected: {} ({})", node.getDisplayName(), node.getId());
                }
            }

            boolean isEndOfRow = (i + 1) % columns == 0;
            boolean isLastItem = i == nodesToRender.size() - 1;
            if (!isEndOfRow && !isLastItem) {
                ImGui.sameLine(0.0f, spacingX);
            }
        }
    }

    private void drawGridPlaceholderIcon(ImDrawList drawList, ImVec2 topLeft, float tileSide, String nodeCategory) {
        int categoryColor = NodeLibraryConstants.getPackedColor(nodeCategory);
        imgui.ImVec4 colorVec = new imgui.ImVec4();
        ImGui.colorConvertU32ToFloat4(categoryColor, colorVec);

        int fillColor = ImGui.colorConvertFloat4ToU32(
            colorVec.x * 0.55f,
            colorVec.y * 0.55f,
            colorVec.z * 0.55f,
            0.90f
        );
        int borderColor = ImGui.colorConvertFloat4ToU32(
            Math.min(1.0f, colorVec.x * 1.15f),
            Math.min(1.0f, colorVec.y * 1.15f),
            Math.min(1.0f, colorVec.z * 1.15f),
            1.0f
        );

        drawList.addRectFilled(
            topLeft.x,
            topLeft.y,
            topLeft.x + tileSide,
            topLeft.y + tileSide,
            fillColor,
            0.0f
        );
        drawList.addRect(
            topLeft.x,
            topLeft.y,
            topLeft.x + tileSide,
            topLeft.y + tileSide,
            borderColor,
            0.0f,
            0,
            1.0f
        );
    }

    private void handleNodeInteraction(NodeInfo node, String nodeCategory, DisplayCategory displayCategory, float dragIconSizeRef) {
        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
            byte[] payloadBytes = node.getId().getBytes(StandardCharsets.UTF_8);
            ImGui.setDragDropPayload(NodeLibraryConstants.DRAG_DROP_PAYLOAD_TYPE, payloadBytes);

            float dragIconSize = dragIconSizeRef * 0.8f;
            ImGui.dummy(dragIconSize, dragIconSize);
            ImVec2 iconPos = ImGui.getItemRectMin();

            ImDrawList dragDrawList = ImGui.getWindowDrawList();
            drawNodeIcon(dragDrawList, iconPos, node, nodeCategory, dragIconSize);

            ImGui.sameLine(0, 5.0f);
            ImGui.text(node.getDisplayName());
            ImGui.endDragDropSource();
        }

        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();

            float tooltipIconSize = ImGui.getTextLineHeight() * 1.2f;
            ImVec2 tooltipPos = ImGui.getCursorScreenPos();

            ImDrawList tooltipDrawList = ImGui.getWindowDrawList();
            drawNodeIcon(tooltipDrawList, tooltipPos, node, nodeCategory, tooltipIconSize);

            ImGui.dummy(tooltipIconSize, tooltipIconSize);
            ImGui.sameLine();
            ImGui.textUnformatted(node.getDisplayName());

            ImGui.textDisabled(node.getId());

            String description = node.getDescription() != null ? node.getDescription() : "";
            if (!description.isEmpty()) {
                ImGui.separator();
                ImGui.textWrapped(description);
            }

            ImGui.separator();
            ImGui.textDisabled("Category: " + displayCategory.getDisplayName());

            ImGui.endTooltip();
        }
    }

    private String ellipsizeText(String text, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (ImGui.calcTextSize(text).x <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        String candidate = text;
        while (!candidate.isEmpty() && ImGui.calcTextSize(candidate + ellipsis).x > maxWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate.isEmpty() ? ellipsis : candidate + ellipsis;
    }

    private List<NodeInfo> getSortedNodesForDisplay(DisplayCategory displayCategory) {
        List<NodeInfo> nodes = new ArrayList<>(displayCategory.getNodes());

        Map<String, Integer> explicitOrder = CATEGORY_NODE_ORDER.get(displayCategory.getId());
        if (explicitOrder != null && !explicitOrder.isEmpty()) {
            nodes.sort(Comparator
                .comparingInt((NodeInfo node) -> explicitOrder.getOrDefault(node.getId(), Integer.MAX_VALUE))
                .thenComparing(NodeInfo::getDisplayName, String.CASE_INSENSITIVE_ORDER));
            return nodes;
        }

        nodes.sort(Comparator.comparing(NodeInfo::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return nodes;
    }

    private static String getString(DisplayCategory displayCategory, int level, String categoryId) {
        String displayTitle;

        if (level > 0 && categoryId.contains(".")) {
            // Build a short subcategory label in the UI layer.
            String subCategoryPart = categoryId.substring(categoryId.lastIndexOf('.') + 1);
            // Title-case the first letter for display.
            subCategoryPart = subCategoryPart.substring(0, 1).toUpperCase() + subCategoryPart.substring(1);
            displayTitle = subCategoryPart;
        } else {
            // Top-level categories use their stored display names directly.
            displayTitle = displayCategory.getDisplayName();
        }
        return displayTitle;
    }

    /**
     * Renders a node row and highlights matching text while searching.
     */
    private void renderNode(ImDrawList drawList, ImVec2 rectMin, ImVec2 textSize, NodeInfo node, String nodeCategory, float iconSize, float iconPadding) {
        float actualLineHeight = ImGui.getTextLineHeight();
        float yOffset = 0;
        if (iconSize < actualLineHeight) {
            yOffset = (actualLineHeight - iconSize) * 0.5f;
        }
        drawNodeIcon(drawList, new ImVec2(rectMin.x, rectMin.y + yOffset), node, nodeCategory, iconSize);
        
        // Draw text only for the normal row view, not for drag previews or tooltips.
        if (iconPadding > 0) {
            // Compute text placement.
            float textStartX = rectMin.x + iconSize + iconPadding;
            float textPosY = rectMin.y + (actualLineHeight - ImGui.getTextLineHeight()) * 0.5f;
            
            String searchTerm = searchManager.getSearchTerm();
            String nodeDisplayName = node.getDisplayName();
            
            // Highlight matching text while searching.
            int textColor = ImGui.getColorU32(ImGuiCol.Text);
            
            if (searchTerm != null && !searchTerm.isEmpty()) {
                String lowerNodeName = nodeDisplayName.toLowerCase();
                String lowerSearchTerm = searchTerm.toLowerCase();
                
                if (lowerNodeName.contains(lowerSearchTerm)) {
                    int highlightColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.0f, 1.0f);
                    
                    int matchStart = lowerNodeName.indexOf(lowerSearchTerm);
                    int matchEnd = matchStart + lowerSearchTerm.length();
                    
                    if (matchStart > 0) {
                        String prefix = nodeDisplayName.substring(0, matchStart);
                        drawList.addText(textStartX, textPosY, textColor, prefix);
                        textStartX += ImGui.calcTextSize(prefix).x;
                    }
                    
                    String highlight = nodeDisplayName.substring(matchStart, matchEnd);
                    drawList.addText(textStartX, textPosY, highlightColor, highlight);
                    textStartX += ImGui.calcTextSize(highlight).x;
                    
                    if (matchEnd < nodeDisplayName.length()) {
                        String suffix = nodeDisplayName.substring(matchEnd);
                        drawList.addText(textStartX, textPosY, textColor, suffix);
                    }
                } else {
                    // The node matched by ID or description rather than display name.
                    drawList.addText(textStartX, textPosY, textColor, nodeDisplayName);
                    
                    float indicatorSize = 3.0f;
                    float indicatorX = textStartX - indicatorSize - 2.0f;
                    float indicatorY = textPosY + ImGui.getTextLineHeight() / 2.0f - indicatorSize / 2.0f;
                    
                    int indicatorColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.0f, 1.0f);
                    drawList.addRectFilled(
                        indicatorX, indicatorY,
                        indicatorX + indicatorSize, indicatorY + indicatorSize,
                        indicatorColor
                    );
                }
            } else {
                // Normal mode: draw the text directly.
                drawList.addText(textStartX, textPosY, textColor, nodeDisplayName);
            }
        }
    }

    private void drawNodeIcon(ImDrawList drawList, ImVec2 topLeft, NodeInfo node, String nodeCategory, float iconSize) {
        String nodeId = node.getId();
        int textureId = iconManager.loadNodeIcon(nodeId, nodeCategory);

        if (textureId > 0) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            drawList.addImage(
                textureId,
                topLeft.x, topLeft.y,
                topLeft.x + iconSize, topLeft.y + iconSize,
                0.0f, 0.0f, 1.0f, 1.0f
            );
        } else {
            int bgColor = ImGui.colorConvertFloat4ToU32(0.7f, 0.7f, 0.7f, 0.7f);
            drawList.addRectFilled(
                topLeft.x, topLeft.y,
                topLeft.x + iconSize, topLeft.y + iconSize,
                bgColor, 0.0f
            );
        }
    }
} 

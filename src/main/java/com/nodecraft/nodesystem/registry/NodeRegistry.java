package com.nodecraft.nodesystem.registry;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.spi.INodeProvider;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一的节点注册表。
 * 负责发现、管理和实例化所有可用的节点类型。
 * 支持通过 {@link INodeProvider} SPI 进行插件化节点注册。
 */
public class NodeRegistry {

    private static NodeRegistry instance;
    private static final Map<String, String> NODE_ID_ALIASES = createNodeIdAliases();
    private static final Map<String, String> NODE_CATEGORY_OVERRIDES = createNodeCategoryOverrides();

    private final Map<String, NodeInfo> nodeInfoMap = new ConcurrentHashMap<>();
    private final Map<String, NodeCategory> categoryMap = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    private NodeRegistry() {
        // 私有构造函数，确保单例
    }

    /**
     * 获取 NodeRegistry 的单例实例。
     *
     * @return NodeRegistry 实例。
     */
    public static synchronized NodeRegistry getInstance() {
        if (instance == null) {
            instance = new NodeRegistry();
        }
        return instance;
    }

    private static Map<String, String> createNodeIdAliases() {
        Map<String, String> aliases = new HashMap<>();
        addAlias(aliases, "spatial.points.offsetcoordinates", "spatial.points.offset_coordinates");
        addAlias(aliases, "spatial.points.rotatecoordinates", "spatial.points.rotate_coordinates");
        addAlias(aliases, "spatial.points.scalecoordinates", "spatial.points.scale_coordinates");
        addAlias(aliases, "spatial.points.mirrorcoordinates", "spatial.points.mirror_coordinates");
        addAlias(aliases, "spatial.points.randomizecoordinates", "spatial.points.randomize_coordinates");

        addMovedNodeAlias(aliases, "spatial.generators.box_center_size", "geometry.primitives.box");
        addMovedNodeAlias(aliases, "spatial.generators.box_corners", "geometry.primitives.box_from_corners");
        addMovedNodeAlias(aliases, "spatial.generators.box_corner_size", "geometry.primitives.box_from_corner_size");
        addMovedNodeAlias(aliases, "spatial.generators.sphere_by_center_radius", "geometry.primitives.sphere");
        addMovedNodeAlias(aliases, "spatial.generators.sphere_by_diameter", "geometry.primitives.sphere_from_diameter");
        addMovedNodeAlias(aliases, "spatial.generators.push_pull_box_face", "geometry.solids.push_pull_face");
        addMovedNodeAlias(aliases, "spatial.generators.extrude_box_face", "geometry.solids.extrude_box_face");
        addMovedNodeAlias(aliases, "spatial.generators.grow_along_normals", "spatial.instancing.grow_along_normals");
        addMovedNodeAlias(aliases, "spatial.generators.grow_along_sphere_normal", "spatial.instancing.grow_along_sphere_normal");

        addCompactLegacyAlias(aliases, "spatial.generators.line_blocks");
        addCompactLegacyAlias(aliases, "spatial.generators.rectangle_blocks");
        addCompactLegacyAlias(aliases, "spatial.generators.box_blocks");
        addCompactLegacyAlias(aliases, "spatial.generators.circle_sphere_blocks");
        addCompactLegacyAlias(aliases, "spatial.generators.cylinder_blocks");
        addCompactLegacyAlias(aliases, "spatial.generators.polyline_blocks");
        addCompactLegacyAlias(aliases, "spatial.generators.curve_blocks");
        addCompactLegacyAlias(aliases, "spatial.generators.ellipsoid_blocks");
        addCompactLegacyAlias(aliases, "spatial.generators.cone_blocks");
        addCompactLegacyAlias(aliases, "spatial.generators.torus_blocks");
        addCompactLegacyAlias(aliases, "spatial.generators.octahedron_blocks");
        addCompactLegacyAlias(aliases, "spatial.generators.tetrahedron_blocks");
        addCompactLegacyAlias(aliases, "spatial.generators.triangular_pyramid_blocks");
        addCompactLegacyAlias(aliases, "spatial.generators.triangular_prism_blocks");

        addMovedNodeAlias(aliases, "visualization.preview.geometry_viewer", "output.preview.geometry_viewer");
        addMovedNodeAlias(aliases, "visualization.preview.preview_geometry", "output.preview.preview_geometry");
        addMovedNodeAlias(aliases, "visualization.preview.preview_blocks", "output.preview.preview_blocks");
        addMovedNodeAlias(aliases, "visualization.preview.preview_points", "output.preview.preview_points");
        addMovedNodeAlias(aliases, "visualization.preview.preview_vectors", "output.preview.preview_vectors");
        addMovedNodeAlias(aliases, "visualization.preview.preview_plane", "output.preview.preview_plane");
        addMovedNodeAlias(aliases, "visualization.preview.preview_frame", "output.preview.preview_frame");
        addMovedNodeAlias(aliases, "visualization.preview.preview_paths", "output.preview.preview_curves");
        addMovedNodeAlias(aliases, "visualization.preview.preview_regions", "output.preview.preview_regions");
        addMovedNodeAlias(aliases, "visualization.preview.preview_labels", "output.preview.preview_labels");
        addMovedNodeAlias(aliases, "visualization.preview.preview_surface_strip", "output.preview.preview_surface_strip");
        addMovedNodeAlias(aliases, "visualization.preview.preview_polygon_profiles", "output.preview.preview_profiles");
        addMovedNodeAlias(aliases, "visualization.preview.clear_all_previews", "output.execute.clear_preview");
        addMovedNodeAlias(aliases, "visualization.execute.apply_changes", "output.execute.apply_changes");
        addMovedNodeAlias(aliases, "visualization.execute.export_schematic", "output.export.export_schematic");
        addMovedNodeAlias(aliases, "visualization.debugging.value_monitor", "output.debug.value_monitor");
        addMovedNodeAlias(aliases, "visualization.debugging.print_to_chat", "output.debug.print_to_chat");
        addMovedNodeAlias(aliases, "visualization.debugging.execution_timer", "output.debug.execution_timer");
        addMovedNodeAlias(aliases, "visualization.debugging.panel", "output.debug.data_inspector");

        addMovedNodeAlias(aliases, "world.query.get_block", "world.read.get_block");
        addMovedNodeAlias(aliases, "world.query.get_blocks_in_region", "world.read.get_blocks_in_region");
        addMovedNodeAlias(aliases, "world.query.find_blocks", "world.read.find_blocks");
        addMovedNodeAlias(aliases, "world.query.get_biome", "world.read.get_biome");
        addMovedNodeAlias(aliases, "world.modification.set_block", "world.write.set_block");
        addMovedNodeAlias(aliases, "world.modification.set_blocks", "world.write.set_blocks");
        addMovedNodeAlias(aliases, "world.modification.fill_region", "world.write.fill_region");
        addMovedNodeAlias(aliases, "world.modification.replace_blocks", "world.write.replace_blocks");
        addMovedNodeAlias(aliases, "world.modification.clone_region", "world.write.clone_region");
        addMovedNodeAlias(aliases, "world.modification.remove_blocks", "world.write.clear_region");
        addMovedNodeAlias(aliases, "world.modification.material_mapper", "material.basic_assignment.replace_material");
        addMovedNodeAlias(aliases, "inputs.basic.integer_input", "input.numeric.integer");
        addMovedNodeAlias(aliases, "inputs.basic.float_input", "input.numeric.float");
        addMovedNodeAlias(aliases, "inputs.basic.integer_slider", "input.numeric.integer_slider");
        addMovedNodeAlias(aliases, "inputs.basic.float_slider", "input.numeric.float_slider");
        addMovedNodeAlias(aliases, "inputs.basic.angle_slider", "input.numeric.angle");
        addMovedNodeAlias(aliases, "inputs.basic.circular_angle", "input.numeric.angle_picker");
        addMovedNodeAlias(aliases, "inputs.basic.boolean_toggle", "input.numeric.boolean_toggle");
        addMovedNodeAlias(aliases, "inputs.basic.vector_input", "reference.vectors.vector");
        addMovedNodeAlias(aliases, "inputs.basic.coordinate_input", "reference.points.point_from_coordinates");
        addMovedNodeAlias(aliases, "inputs.basic.plane_selector", "reference.planes.world_plane");
        addMovedNodeAlias(aliases, "inputs.minecraft.player_position", "input.context.player_position");
        addMovedNodeAlias(aliases, "inputs.minecraft.player_look_at", "input.context.player_look_direction");
        addMovedNodeAlias(aliases, "inputs.minecraft.selected_block", "world.selection.selected_block");
        addMovedNodeAlias(aliases, "inputs.minecraft.selected_region", "world.selection.selected_region");
        addMovedNodeAlias(aliases, "inputs.minecraft.biome_at_player", "world.read.biome_at_player");
        addMovedNodeAlias(aliases, "inputs.minecraft.current_time", "input.context.current_time");
        addMovedNodeAlias(aliases, "inputs.minecraft.dimension_info", "input.context.dimension_info");
        addMovedNodeAlias(aliases, "inputs.selectors.block_type_selector", "input.type_selectors.block_type_selector");
        addMovedNodeAlias(aliases, "inputs.sources.create_list", "math.list_sequence.create_list");
        addMovedNodeAlias(aliases, "math.vector.construct", "reference.vectors.vector");
        addMovedNodeAlias(aliases, "math.vector.cross_product", "reference.vectors.cross_product");
        addMovedNodeAlias(aliases, "math.vector.dot_product", "reference.vectors.dot_product");
        addMovedNodeAlias(aliases, "math.vector.normalize", "reference.vectors.normalize_vector");
        addMovedNodeAlias(aliases, "math.vector.normalize_vector", "reference.vectors.normalize_vector");
        addMovedNodeAlias(aliases, "math.vector.length", "reference.vectors.vector_length");
        addMovedNodeAlias(aliases, "math.vector.addition", "reference.vectors.vector_addition");
        addMovedNodeAlias(aliases, "math.vector.subtraction", "reference.vectors.vector_subtraction");
        addMovedNodeAlias(aliases, "math.vector.scalar_multiply", "reference.vectors.vector_scalar_multiply");
        addMovedNodeAlias(aliases, "math.vector.scalar_divide", "reference.vectors.vector_scalar_divide");
        addMovedNodeAlias(aliases, "math.vector.deconstruct", "reference.vectors.deconstruct_vector");
        addMovedNodeAlias(aliases, "math.vector.construct_coordinate", "reference.points.point_from_coordinates");
        addMovedNodeAlias(aliases, "math.vector.deconstruct_coordinate", "reference.points.deconstruct_point");
        addMovedNodeAlias(aliases, "math.vector.midpoint", "reference.points.mid_point");
        addMovedNodeAlias(aliases, "math.vector.distance", "reference.points.distance_between_points");
        addMovedNodeAlias(aliases, "math.vector.construct_plane", "reference.planes.construct_plane");
        addMovedNodeAlias(aliases, "math.vector.construct_plane_from_points", "reference.planes.plane_from_points");
        addMovedNodeAlias(aliases, "math.vector.rotate", "transform.orientation.rotate_vector");
        addMovedNodeAlias(aliases, "math.vector.rotate_vector", "transform.orientation.rotate_vector");
        addMovedNodeAlias(aliases, "spatial.points.block_to_point", "reference.points.point_from_block");
        addMovedNodeAlias(aliases, "spatial.points.point_between_two_points", "reference.points.mid_point");
        addMovedNodeAlias(aliases, "spatial.points.point_along_vector", "reference.points.point_along_vector");
        addMovedNodeAlias(aliases, "spatial.points.distance_point_to_plane", "reference.planes.distance_point_to_plane");
        addMovedNodeAlias(aliases, "spatial.analysis.box_face_to_plane", "reference.planes.block_face_plane");
        addMovedNodeAlias(aliases, "spatial.analysis.deconstruct_box_face", "reference.points.deconstruct_face");
        addMovedNodeAlias(aliases, "spatial.analysis.deconstruct_face_edge", "reference.points.deconstruct_edge");
        addMovedNodeAlias(aliases, "spatial.analysis.closest_point", "reference.points.closest_point");
        addMovedNodeAlias(aliases, "spatial.analysis.point_list_center", "reference.points.point_list_center");
        addMovedNodeAlias(aliases, "spatial.analysis.point_list_bounds", "reference.points.point_list_bounds");
        addMovedNodeAlias(aliases, "spatial.analysis.get_box_corner", "reference.points.get_box_corner");
        addMovedNodeAlias(aliases, "spatial.analysis.get_box_face", "reference.points.get_box_face");
        addMovedNodeAlias(aliases, "spatial.analysis.get_face_edge", "reference.points.get_face_edge");
        addMovedNodeAlias(aliases, "spatial.analysis.face_center_frame", "reference.frames.frame_from_face");
        addMovedNodeAlias(aliases, "spatial.analysis.sphere_surface_frame", "reference.frames.frame_along_surface");
        addMovedNodeAlias(aliases, "spatial.points.snap_point_to_block", "world.selection.snap_point_to_block");
        addMovedNodeAlias(aliases, "spatial.points.snap_point_list_to_blocks", "world.selection.snap_points_to_blocks");
        addMovedNodeAlias(aliases, "spatial.points.point_to_block_if_grid", "world.selection.point_to_block_if_grid");
        addMovedNodeAlias(aliases, "spatial.points.is_grid_point", "world.query.is_grid_point");
        addMovedNodeAlias(aliases, "spatial.points.filter_grid_points", "world.query.filter_grid_points");
        addMovedNodeAlias(aliases, "spatial.points.offset_coordinate", "transform.basic_transforms.move_point");
        addMovedNodeAlias(aliases, "spatial.points.offset_coordinates", "transform.basic_transforms.move_points");
        addMovedNodeAlias(aliases, "spatial.points.rotate_coordinates", "transform.basic_transforms.rotate_points");
        addMovedNodeAlias(aliases, "spatial.points.scale_coordinates", "transform.basic_transforms.scale_points");
        addMovedNodeAlias(aliases, "spatial.points.mirror_coordinates", "transform.basic_transforms.mirror_points");
        addMovedNodeAlias(aliases, "spatial.points.project_point_to_plane", "transform.orientation.project_to_plane");
        addMovedNodeAlias(aliases, "spatial.analysis.offset_box_face", "transform.basic_transforms.offset_face");
        addMovedNodeAlias(aliases, "spatial.analysis.inset_box_face", "transform.basic_transforms.inset_face");
        addMovedNodeAlias(aliases, "spatial.analysis.bounding_box", "geometry.boolean.bounding_box");
        addMovedNodeAlias(aliases, "spatial.analysis.geometry_bounds", "geometry.boolean.geometry_bounds");
        addMovedNodeAlias(aliases, "spatial.analysis.deconstruct_box_geometry", "geometry.primitives.deconstruct_box");
        addMovedNodeAlias(aliases, "spatial.analysis.deconstruct_sphere", "geometry.primitives.deconstruct_sphere");
        addMovedNodeAlias(aliases, "spatial.analysis.deconstruct_cylinder", "geometry.primitives.deconstruct_cylinder");
        addMovedNodeAlias(aliases, "spatial.analysis.deconstruct_cone", "geometry.primitives.deconstruct_cone");
        addMovedNodeAlias(aliases, "spatial.analysis.deconstruct_ellipsoid", "geometry.primitives.deconstruct_ellipsoid");
        addMovedNodeAlias(aliases, "spatial.analysis.deconstruct_octahedron", "geometry.primitives.deconstruct_octahedron");
        addMovedNodeAlias(aliases, "spatial.analysis.deconstruct_tetrahedron", "geometry.primitives.deconstruct_tetrahedron");
        addMovedNodeAlias(aliases, "spatial.analysis.deconstruct_prism", "geometry.primitives.deconstruct_prism");
        addMovedNodeAlias(aliases, "spatial.analysis.deconstruct_polygon_profile", "geometry.profiles.deconstruct_profile");
        addMovedNodeAlias(aliases, "spatial.analysis.face_edge_to_path", "geometry.curves.edge_to_curve");
        addMovedNodeAlias(aliases, "spatial.analysis.box_face_boundary_path", "geometry.curves.face_boundary_curve");
        addMovedNodeAlias(aliases, "spatial.points.points_to_path", "geometry.curves.curve_from_points");
        addMovedNodeAlias(aliases, "spatial.points.path_to_points", "geometry.curves.divide_curve_to_points");
        addMovedNodeAlias(aliases, "spatial.analysis.get_points_in_region", "world.read.get_points_in_region");
        addMovedNodeAlias(aliases, "spatial.analysis.is_point_in_region", "world.query.is_point_in_region");
        addMovedNodeAlias(aliases, "spatial.analysis.sample_sphere_surface", "pattern.surface_volume_distribution.sample_surface");
        addMovedNodeAlias(aliases, "spatial.analysis.scatter_on_sphere_surface", "pattern.surface_volume_distribution.surface_scatter");
        addMovedNodeAlias(aliases, "spatial.arrays.linear_array", "pattern.linear.linear_array");
        addMovedNodeAlias(aliases, "spatial.arrays.grid_array", "pattern.grid.grid_array");
        addMovedNodeAlias(aliases, "spatial.arrays.polar_array", "pattern.radial.polar_array");
        addMovedNodeAlias(aliases, "spatial.arrays.populate_region", "pattern.surface_volume_distribution.populate_region");
        addMovedNodeAlias(aliases, "spatial.construct.box_center_size", "geometry.primitives.box");
        addMovedNodeAlias(aliases, "spatial.construct.box_corner_size", "geometry.primitives.box_from_corner_size");
        addMovedNodeAlias(aliases, "spatial.construct.box_corners", "geometry.primitives.box_from_corners");
        addMovedNodeAlias(aliases, "spatial.construct.sphere_by_center_radius", "geometry.primitives.sphere");
        addMovedNodeAlias(aliases, "spatial.construct.sphere_by_diameter", "geometry.primitives.sphere_from_diameter");
        addMovedNodeAlias(aliases, "spatial.construct.cylinder_by_axis_radius", "geometry.primitives.cylinder");
        addMovedNodeAlias(aliases, "spatial.construct.cone_by_base_apex_radius", "geometry.primitives.cone");
        addMovedNodeAlias(aliases, "spatial.construct.ellipsoid_by_center_radii", "geometry.primitives.ellipsoid");
        addMovedNodeAlias(aliases, "spatial.construct.octahedron_by_center_size", "geometry.primitives.octahedron");
        addMovedNodeAlias(aliases, "spatial.construct.tetrahedron_by_center_edge", "geometry.primitives.tetrahedron");
        addMovedNodeAlias(aliases, "spatial.construct.rectangle_on_plane", "geometry.profiles.rectangle_profile");
        addMovedNodeAlias(aliases, "spatial.construct.regular_polygon_on_plane", "geometry.profiles.polygon_profile");
        addMovedNodeAlias(aliases, "spatial.construct.polygon_by_points", "geometry.profiles.custom_profile");
        addMovedNodeAlias(aliases, "spatial.construct.prism_by_profile_vector", "geometry.solids.extrude_profile");
        addMovedNodeAlias(aliases, "spatial.construct.prism_by_base_points_vector", "geometry.solids.extrude_profile_from_points");
        addMovedNodeAlias(aliases, "spatial.modeling.extrude_profile", "geometry.solids.extrude");
        addMovedNodeAlias(aliases, "spatial.modeling.extrude_point_list", "geometry.solids.extrude_from_points");
        addMovedNodeAlias(aliases, "spatial.modeling.extrude_box_face", "geometry.solids.extrude_box_face");
        addMovedNodeAlias(aliases, "spatial.modeling.loft_profiles", "geometry.solids.loft");
        addMovedNodeAlias(aliases, "spatial.modeling.loft_point_lists", "geometry.solids.loft_from_points");
        addMovedNodeAlias(aliases, "spatial.modeling.sweep_profile_along_path", "geometry.solids.sweep");
        addMovedNodeAlias(aliases, "spatial.modeling.sweep_point_list_along_path", "geometry.solids.sweep_from_points");
        addMovedNodeAlias(aliases, "spatial.modeling.resample_polygon_profile", "geometry.profiles.resample_profile");
        addMovedNodeAlias(aliases, "spatial.modeling.surface_strip_to_geometry", "geometry.solids.surface_strip_to_geometry");
        addMovedNodeAlias(aliases, "spatial.modeling.push_pull_box_face", "geometry.solids.push_pull_face");
        addMovedNodeAlias(aliases, "spatial.modeling.twist_point_list", "transform.deformations.twist");
        addMovedNodeAlias(aliases, "spatial.voxel.geometry_to_blocks", "output.execute.bake_geometry_to_blocks");
        addMovedNodeAlias(aliases, "spatial.voxel.surface_strip_to_blocks", "output.execute.bake_surface_strip_to_blocks");
        addMovedNodeAlias(aliases, "spatial.voxel.box_geometry_voxelizer", "output.execute.bake_box_to_blocks");
        addMovedNodeAlias(aliases, "spatial.voxel.sphere_geometry_voxelizer", "output.execute.bake_sphere_to_blocks");
        addMovedNodeAlias(aliases, "spatial.voxel.cylinder_geometry_voxelizer", "output.execute.bake_cylinder_to_blocks");
        addMovedNodeAlias(aliases, "spatial.voxel.cone_geometry_voxelizer", "output.execute.bake_cone_to_blocks");
        addMovedNodeAlias(aliases, "spatial.voxel.ellipsoid_geometry_voxelizer", "output.execute.bake_ellipsoid_to_blocks");
        addMovedNodeAlias(aliases, "spatial.voxel.prism_geometry_voxelizer", "output.execute.bake_prism_to_blocks");
        addMovedNodeAlias(aliases, "spatial.voxel.octahedron_geometry_voxelizer", "output.execute.bake_octahedron_to_blocks");
        addMovedNodeAlias(aliases, "spatial.voxel.tetrahedron_geometry_voxelizer", "output.execute.bake_tetrahedron_to_blocks");
        addMovedNodeAlias(aliases, "spatial.voxel.torus_geometry_voxelizer", "output.execute.bake_torus_to_blocks");
        return Collections.unmodifiableMap(aliases);
    }

    private static void addAlias(Map<String, String> aliases, String legacyId, String canonicalId) {
        aliases.put(legacyId, canonicalId);
    }

    private static void addMovedNodeAlias(Map<String, String> aliases, String legacyId, String canonicalId) {
        addAlias(aliases, legacyId, canonicalId);
    }

    private static void addCompactLegacyAlias(Map<String, String> aliases, String canonicalId) {
        int lastDot = canonicalId.lastIndexOf('.');
        if (lastDot < 0 || lastDot >= canonicalId.length() - 1) {
            return;
        }
        String prefix = canonicalId.substring(0, lastDot + 1);
        String leaf = canonicalId.substring(lastDot + 1);
        addAlias(aliases, prefix + leaf.replace("_", ""), canonicalId);
    }

    private static Map<String, String> createNodeCategoryOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("spatial.analysis.geometry_info", "spatial.legacy");
        overrides.put("spatial.analysis.select_sphere_band_sector", "spatial.legacy");
        overrides.put("spatial.analysis.sphere_uv", "spatial.legacy");
        overrides.put("spatial.analysis.sphere_point_info", "spatial.legacy");
        overrides.put("spatial.analysis.deconstruct_surface_strip", "spatial.legacy");
        overrides.put("spatial.points.point_between_two_points", "spatial.legacy");
        overrides.put("spatial.points.randomize_coordinates", "spatial.legacy");
        overrides.put("spatial.voxel.union_coords", "spatial.legacy");
        overrides.put("spatial.voxel.intersection_coords", "spatial.legacy");
        overrides.put("spatial.voxel.difference_coords", "spatial.legacy");
        overrides.put("spatial.instancing.grow_along_normals", "spatial.legacy");
        overrides.put("spatial.instancing.grow_along_sphere_normal", "spatial.legacy");
        overrides.put("inputs.minecraft.selected_block_sequence", "spatial.legacy");
        overrides.put("inputs.minecraft.selected_entity", "spatial.legacy");
        return Map.copyOf(overrides);
    }

    private String normalizeNodeId(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        String normalizedId = nodeId.toLowerCase();
        return NODE_ID_ALIASES.getOrDefault(normalizedId, normalizedId);
    }

    public String resolveCanonicalNodeId(String nodeId) {
        return normalizeNodeId(nodeId);
    }

    private String remapCategory(String normalizedNodeId, String categoryId) {
        String normalizedCategoryId = categoryId == null ? "" : categoryId.toLowerCase();
        String explicitOverride = NODE_CATEGORY_OVERRIDES.get(normalizedNodeId);
        if (explicitOverride != null) {
            return explicitOverride;
        }
        if ("spatial.generators".equals(normalizedCategoryId) && isLegacyDirectOutputNode(normalizedNodeId)) {
            return "spatial.legacy";
        }
        return normalizedCategoryId;
    }

    private boolean isLegacyDirectOutputNode(String normalizedNodeId) {
        return normalizedNodeId.endsWith("_blocks")
            || normalizedNodeId.endsWith("blocks")
            || normalizedNodeId.contains(".circle_sphere_blocks")
            || normalizedNodeId.contains(".line_blocks")
            || normalizedNodeId.contains(".polyline_blocks")
            || normalizedNodeId.contains(".curve_blocks");
    }

    private String remapDescription(String targetCategoryId, String description) {
        String safeDescription = description == null ? "" : description;
        if (!"spatial.legacy".equals(targetCategoryId)) {
            return safeDescription;
        }
        String prefix = "Legacy compatibility node. ";
        return safeDescription.startsWith(prefix) ? safeDescription : prefix + safeDescription;
    }

    /**
     * 初始化节点注册表。
     * 此方法会清空当前注册信息，并使用 {@link ServiceLoader} 重新加载所有 {@link INodeProvider}。
     * 应该在应用程序启动时调用一次。
     */
    public synchronized void initialize() {
        if (initialized) {
            NodeCraft.LOGGER.warn("NodeRegistry 已初始化，跳过重复初始化。如需重新加载，请先调用 clear()。");
            return;
        }

        NodeCraft.LOGGER.debug("开始初始化 NodeRegistry...");
        this.clearInternal(); // 确保在初始化前清空内部状态

        // 加载所有节点提供者
        ServiceLoader<INodeProvider> loader = ServiceLoader.load(INodeProvider.class);
        int providerCount = 0;
        int initialNodes; // 用于计算每个提供者注册的节点数量
        int initialCategories; // 用于计算每个提供者注册的分类数量

        for (INodeProvider provider : loader) {
            try {
                NodeCraft.LOGGER.debug("加载节点提供者: {}", provider.getClass().getName());
                initialNodes = nodeInfoMap.size(); // 记录调用前数量
                initialCategories = categoryMap.size(); // 记录调用前数量

                provider.registerNodes(this);
                providerCount++;

                NodeCraft.LOGGER.debug("提供者 {} 注册了 {} 个节点和 {} 个分类",
                        provider.getClass().getSimpleName(),
                        nodeInfoMap.size() - initialNodes,
                        categoryMap.size() - initialCategories);

            } catch (Exception e) {
                NodeCraft.LOGGER.error("加载节点提供者 {} 时出错: {}",
                        provider.getClass().getName(), e.getMessage(), e);
            }
        }

        if (providerCount == 0) {
            NodeCraft.LOGGER.error("警告：没有找到任何节点提供者！请检查 META-INF/services/com.nodecraft.nodesystem.spi.INodeProvider 文件。");
        }

        initialized = true;
        NodeCraft.LOGGER.info("NodeRegistry 初始化完成。加载了 {} 个节点提供者，共注册了 {} 个类别和 {} 个节点。",
                providerCount, categoryMap.size(), nodeInfoMap.size());
    }

    /**
     * 注册一个节点类别。
     * 如果类别ID已存在，则此操作无效。
     *
     * @param category 要注册的节点类别。
     */
    public synchronized void registerCategory(NodeCategory category) {
        if (category == null || category.getId() == null) {
            NodeCraft.LOGGER.warn("尝试注册无效的节点类别 (null 或 null ID)。");
            return;
        }

        // 标准化分类ID和显示名称
        String normalizedId = category.getId().toLowerCase();
        String displayName = category.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = formatCategoryName(normalizedId); // 使用新的格式化方法
        }

        // 检查是否为子分类，并自动创建父分类
        if (normalizedId.contains(".") && !normalizedId.endsWith(".")) {
            String parentId = normalizedId.substring(0, normalizedId.lastIndexOf('.'));

            // 递归注册父分类，确保层级关系
            // 避免无限循环，只注册直接父级，更上级由其父级的注册处理
            if (!categoryMap.containsKey(parentId)) {
                NodeCategory parentCategory = new NodeCategory(parentId, formatCategoryName(parentId));
                // 递归调用 registerCategory 确保父分类被完整处理
                registerCategory(parentCategory); // 确保父分类也经过标准化和日志
            }
        }

        // 检查是否已存在
        if (categoryMap.containsKey(normalizedId)) {
            NodeCraft.LOGGER.debug("节点类别 {} 已存在，无需重复注册。", normalizedId);
            return;
        }

        // 创建新的分类对象
        NodeCategory newCategory = new NodeCategory(normalizedId, displayName);
        categoryMap.put(normalizedId, newCategory);
        NodeCraft.LOGGER.debug("注册节点类别: {} (ID: {})", displayName, normalizedId);
    }

    /**
     * 注册一个节点。
     * 如果节点ID已存在，则会警告并且不进行覆盖。
     * 如果节点所属的类别不存在，会自动创建一个默认类别。
     *
     * @param nodeInfo 要注册的节点信息。
     * @return 如果成功注册，则返回 true；如果节点信息无效或已存在，则返回 false。
     */
    public synchronized boolean registerNode(NodeInfo nodeInfo) {
        if (nodeInfo == null || nodeInfo.getId() == null || nodeInfo.getNodeClass() == null) {
            NodeCraft.LOGGER.warn("尝试注册无效的节点信息 (null, null ID, 或 null NodeClass)。");
            return false;
        }

        // 标准化节点ID
        String normalizedId = nodeInfo.getId().toLowerCase();
        String categoryId = remapCategory(normalizedId, nodeInfo.getCategoryId());
        String description = remapDescription(categoryId, nodeInfo.getDescription());
        NodeInfo normalizedNodeInfo = new NodeInfo(
                normalizedId,
                nodeInfo.getDisplayName(),
                description,
                categoryId,
                nodeInfo.getNodeClass());
        normalizedNodeInfo.setIcon(nodeInfo.getIcon());

        // 检查是否已存在
        if (nodeInfoMap.containsKey(normalizedId)) {
            NodeCraft.LOGGER.warn("尝试重复注册节点 ID: {} (标题: {}). 跳过。",
                    normalizedId, nodeInfo.getDisplayName());
            return false;
        }

        // 注册节点
        nodeInfoMap.put(normalizedId, normalizedNodeInfo);

        // 确保分类存在 (调用 registerCategory 确保其父分类链也被处理)
        if (!categoryMap.containsKey(categoryId)) {
            // 如果分类不存在，则注册它，这将自动处理父分类链
            NodeCategory newCategoryForNode = new NodeCategory(categoryId, formatCategoryName(categoryId));
            registerCategory(newCategoryForNode); // 确保这个分类及其父类被正确注册
        }

        // 将节点添加到分类中
        NodeCategory category = categoryMap.get(categoryId);
        if (category != null) { // 确保分类确实存在（理论上应存在，因为上面已注册）
            category.addNode(normalizedNodeInfo);
            NodeCraft.LOGGER.debug("注册节点: {} (ID: {}) 到类别 [{}]",
                    normalizedNodeInfo.getDisplayName(), normalizedId, category.getDisplayName());
            return true;
        } else {
            NodeCraft.LOGGER.error("节点 {} (ID: {}) 注册失败：无法找到或创建分类 {}",
                    normalizedNodeInfo.getDisplayName(), normalizedId, categoryId);
            nodeInfoMap.remove(normalizedId); // 回滚注册
            return false;
        }
    }

    /**
     * 根据节点类型 ID 创建一个新的节点实例。
     *
     * @param nodeId 要创建实例的节点类型 ID。
     * @return 新创建的 {@link INode} 实例。
     * @throws IllegalArgumentException 如果节点 ID 未注册或节点信息缺少实现类。
     * @throws RuntimeException         如果实例化节点时发生错误 (如构造函数问题)。
     */
    public INode createNodeInstance(String nodeId) {
        String resolvedNodeId = normalizeNodeId(nodeId);
        NodeInfo nodeInfo = nodeInfoMap.get(resolvedNodeId);
        if (nodeInfo == null) {
            throw new IllegalArgumentException("未注册的节点类型 ID: " + nodeId);
        }

        Class<? extends INode> nodeClass = nodeInfo.getNodeClass();
        if (nodeClass == null) {
            // 此情况理论上不应发生，因为 registerNode 会检查 nodeClass
            throw new IllegalArgumentException("节点类型 '" + nodeId + "' (" + nodeInfo.getDisplayName() + ") 没有关联的实现类，无法实例化。");
        }

        try {
            return nodeClass.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            NodeCraft.LOGGER.error("节点类 {} 缺少无参构造函数。", nodeClass.getName(), e);
            throw new RuntimeException("节点类 " + nodeClass.getName() + " 缺少无参构造函数。", e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            NodeCraft.LOGGER.error("实例化节点 {} ({}) 时出错。", nodeId, nodeClass.getName(), e);
            throw new RuntimeException("无法实例化节点 " + nodeId + ": " + e.getMessage(), e);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("实例化节点 {} ({}) 时发生未知错误。", nodeId, nodeClass.getName(), e);
            throw new RuntimeException("实例化节点 " + nodeId + " 时发生未知错误: " + e.getMessage(), e);
        }
    }

    /**
     * 根据 ID 获取节点类别。
     *
     * @param categoryId 类别 ID。
     * @return {@link NodeCategory}，如果不存在则返回 null。
     */
    public NodeCategory getCategory(String categoryId) {
        return categoryMap.get(categoryId);
    }

    /**
     * 获取所有已注册的节点类别。
     * 列表按类别显示名称排序。
     *
     * @return 已排序的节点类别列表。
     */
    public List<NodeCategory> getAllCategories() {
        List<NodeCategory> sortedCategories = new ArrayList<>(categoryMap.values());
        // 按显示名称排序
        sortedCategories.sort((c1, c2) -> c1.getDisplayName().compareToIgnoreCase(c2.getDisplayName()));
        return Collections.unmodifiableList(sortedCategories); // 返回不可修改列表
    }

    /**
     * 获取所有已注册节点ID的列表。
     * @return 已注册节点ID的不可修改列表。
     */
    public List<String> getAllNodeIds() {
        return List.copyOf(nodeInfoMap.keySet());
    }

    /**
     * 根据节点ID获取节点信息。
     * @param nodeId 节点ID。
     * @return 对应的 NodeInfo 对象，如果不存在则返回 null。
     */
    public NodeInfo getNodeInfo(String nodeId) {
        return nodeInfoMap.get(normalizeNodeId(nodeId));
    }

    /**
     * 获取已注册的类别数量。
     * @return 类别数量。
     */
    public int getCategoryCount() {
        return categoryMap.size();
    }

    /**
     * 获取已注册的节点数量。
     * @return 节点数量。
     */
    public int getNodeCount() {
        return nodeInfoMap.size();
    }

    /**
     * 检查注册表是否已初始化。
     *
     * @return 如果已调用 {@link #initialize()} 并且未被 {@link #clear()}，则返回 true。
     */
    public boolean isInitialized() {
        return initialized;
    }

    private void clearInternal() {
        nodeInfoMap.clear();
        categoryMap.clear();
        // initialized 标志由 initialize 和 clear 控制
    }

    /**
     * 清空注册表并重置初始化状态。
     * 在重新加载配置或插件时可能有用。
     */
    public synchronized void clear() {
        clearInternal();
        initialized = false;
        NodeCraft.LOGGER.info("NodeRegistry 已清空并重置。");
    }

    /**
     * 代表一个节点类别，包含该类别下的多个节点。
     */
    public static class NodeCategory {
        private final String id;
        private final String displayName;
        private final List<NodeInfo> nodes = new ArrayList<>();

        public NodeCategory(String id, String displayName) {
            this.id = Objects.requireNonNull(id, "类别 ID 不能为空");
            this.displayName = Objects.requireNonNull(displayName, "类别显示名称不能为空");
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        void addNode(NodeInfo nodeInfo) {
            if (nodeInfo != null && !nodes.contains(nodeInfo)) {
                nodes.add(nodeInfo);
                // 按显示名称排序节点
                nodes.sort((n1, n2) -> n1.getDisplayName().compareToIgnoreCase(n2.getDisplayName()));
            }
        }

        public List<NodeInfo> getNodes() {
            return Collections.unmodifiableList(nodes);
        }

        public boolean isEmpty() {
            return nodes.isEmpty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NodeCategory that = (NodeCategory) o;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "NodeCategory{id='" + id + "', displayName='" + displayName + "', nodeCount=" + nodes.size() + "}";
        }
    }

    /**
     * 将分类ID的一部分格式化为更好的显示名称。
     * 支持点分式ID，例如 "inputs.basic" -> "Inputs / Basic"。
     * @param categoryId 分类ID。
     * @return 格式化后的显示名称。
     */
    private String formatCategoryName(String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) {
            return "General"; // 或其他默认值
        }
        String[] parts = categoryId.split("\\.");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!formatted.isEmpty()) {
                    formatted.append(" / ");
                }
                // 首字母大写
                formatted.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
            }
        }
        return formatted.toString();
    }
}

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
import com.nodecraft.nodesystem.registry.NodeRegistry.NodeCategory;
import com.nodecraft.gui.utils.NodeIconManager;
import com.nodecraft.gui.components.search.NodeSearchManager;
import org.lwjgl.opengl.GL11;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiDragDropFlags;
import imgui.ImDrawList;
import imgui.ImVec2;

/**
 * NodeCraft编辑器节点库组件
 * 负责渲染左侧节点库面板，包含分类和搜索功能
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

        Map<String, Integer> previewOrder = new HashMap<>();
        previewOrder.put("visualization.preview.geometry_viewer", 0);
        previewOrder.put("visualization.preview.preview_blocks", 1);
        previewOrder.put("visualization.preview.preview_points", 2);
        previewOrder.put("visualization.preview.preview_vectors", 3);
        previewOrder.put("visualization.preview.preview_plane", 4);
        previewOrder.put("visualization.preview.preview_frame", 5);
        previewOrder.put("visualization.preview.preview_paths", 6);
        previewOrder.put("visualization.preview.preview_regions", 7);
        previewOrder.put("visualization.preview.preview_labels", 8);
        previewOrder.put("visualization.preview.preview_surface_strip", 9);
        previewOrder.put("visualization.preview.preview_polygon_profiles", 10);
        previewOrder.put("visualization.preview.clear_all_previews", 11);
        categoryOrder.put("visualization.preview", previewOrder);

        Map<String, Integer> inputsMinecraftOrder = new HashMap<>();
        inputsMinecraftOrder.put("inputs.minecraft.selected_block", 0);
        inputsMinecraftOrder.put("inputs.minecraft.selected_block_sequence", 1);
        inputsMinecraftOrder.put("inputs.minecraft.selected_region", 2);
        inputsMinecraftOrder.put("inputs.minecraft.selected_entity", 3);
        inputsMinecraftOrder.put("inputs.minecraft.player_position", 4);
        inputsMinecraftOrder.put("inputs.minecraft.player_look_at", 5);
        inputsMinecraftOrder.put("inputs.minecraft.dimension_info", 6);
        inputsMinecraftOrder.put("inputs.minecraft.current_time", 7);
        inputsMinecraftOrder.put("inputs.minecraft.biome_at_player", 8);
        categoryOrder.put("inputs.minecraft", inputsMinecraftOrder);

        Map<String, Integer> spatialGeneratorsOrder = new HashMap<>();
        spatialGeneratorsOrder.put("spatial.generators.box_blocks", 0);
        spatialGeneratorsOrder.put("spatial.generators.region_box_blocks", 1);
        categoryOrder.put("spatial.generators", spatialGeneratorsOrder);

        Map<String, Integer> spatialConstructOrder = new HashMap<>();
        spatialConstructOrder.put("spatial.construct.rectangle_on_plane", 0);
        spatialConstructOrder.put("spatial.construct.regular_polygon_on_plane", 1);
        spatialConstructOrder.put("spatial.construct.polygon_by_points", 2);
        spatialConstructOrder.put("spatial.construct.prism_by_profile_vector", 3);
        spatialConstructOrder.put("spatial.construct.prism_by_base_points_vector", 4);
        spatialConstructOrder.put("spatial.construct.cylinder_by_axis_radius", 5);
        spatialConstructOrder.put("spatial.construct.cone_by_base_apex_radius", 6);
        spatialConstructOrder.put("spatial.construct.ellipsoid_by_center_radii", 7);
        spatialConstructOrder.put("spatial.construct.octahedron_by_center_size", 8);
        spatialConstructOrder.put("spatial.construct.tetrahedron_by_center_edge", 9);
        spatialConstructOrder.put("spatial.construct.box_center_size", 10);
        spatialConstructOrder.put("spatial.construct.box_corners", 11);
        spatialConstructOrder.put("spatial.construct.box_corner_size", 12);
        spatialConstructOrder.put("spatial.construct.sphere_by_center_radius", 13);
        spatialConstructOrder.put("spatial.construct.sphere_by_diameter", 14);
        categoryOrder.put("spatial.construct", spatialConstructOrder);

        Map<String, Integer> spatialAnalysisOrder = new HashMap<>();
        spatialAnalysisOrder.put("spatial.analysis.bounding_box", 0);
        spatialAnalysisOrder.put("spatial.analysis.geometry_bounds", 1);
        spatialAnalysisOrder.put("spatial.analysis.deconstruct_box_geometry", 2);
        spatialAnalysisOrder.put("spatial.analysis.get_box_corner", 3);
        spatialAnalysisOrder.put("spatial.analysis.get_box_face", 4);
        spatialAnalysisOrder.put("spatial.analysis.deconstruct_box_face", 5);
        spatialAnalysisOrder.put("spatial.analysis.box_face_to_plane", 6);
        spatialAnalysisOrder.put("spatial.analysis.face_center_frame", 7);
        spatialAnalysisOrder.put("spatial.analysis.box_face_boundary_path", 8);
        spatialAnalysisOrder.put("spatial.analysis.inset_box_face", 9);
        spatialAnalysisOrder.put("spatial.analysis.offset_box_face", 10);
        spatialAnalysisOrder.put("spatial.analysis.get_face_edge", 11);
        spatialAnalysisOrder.put("spatial.analysis.face_edge_to_path", 12);
        spatialAnalysisOrder.put("spatial.analysis.deconstruct_face_edge", 13);
        spatialAnalysisOrder.put("spatial.analysis.point_list_bounds", 14);
        spatialAnalysisOrder.put("spatial.analysis.point_list_center", 15);
        categoryOrder.put("spatial.analysis", spatialAnalysisOrder);

        Map<String, Integer> spatialPointsOrder = new HashMap<>();
        spatialPointsOrder.put("spatial.points.block_to_point", 0);
        spatialPointsOrder.put("spatial.points.project_point_to_plane", 1);
        spatialPointsOrder.put("spatial.points.distance_point_to_plane", 2);
        spatialPointsOrder.put("spatial.points.point_along_vector", 3);
        spatialPointsOrder.put("spatial.points.point_between_two_points", 4);
        spatialPointsOrder.put("spatial.points.points_to_path", 5);
        spatialPointsOrder.put("spatial.points.path_to_points", 6);
        spatialPointsOrder.put("spatial.points.is_grid_point", 7);
        spatialPointsOrder.put("spatial.points.point_to_block_if_grid", 8);
        spatialPointsOrder.put("spatial.points.snap_point_to_block", 9);
        spatialPointsOrder.put("spatial.points.filter_grid_points", 10);
        spatialPointsOrder.put("spatial.points.snap_point_list_to_blocks", 11);
        spatialPointsOrder.put("spatial.points.offset_coordinates", 12);
        spatialPointsOrder.put("spatial.points.rotate_coordinates", 13);
        spatialPointsOrder.put("spatial.points.scale_coordinates", 14);
        spatialPointsOrder.put("spatial.points.mirror_coordinates", 15);
        spatialPointsOrder.put("spatial.points.randomize_coordinates", 16);
        categoryOrder.put("spatial.points", spatialPointsOrder);

        Map<String, Integer> spatialModelingOrder = new HashMap<>();
        spatialModelingOrder.put("spatial.modeling.resample_polygon_profile", 0);
        spatialModelingOrder.put("spatial.modeling.extrude_profile", 1);
        spatialModelingOrder.put("spatial.modeling.loft_profiles", 2);
        spatialModelingOrder.put("spatial.modeling.sweep_profile_along_path", 3);
        spatialModelingOrder.put("spatial.modeling.twist_point_list", 4);
        spatialModelingOrder.put("spatial.modeling.extrude_point_list", 5);
        spatialModelingOrder.put("spatial.modeling.loft_point_lists", 6);
        spatialModelingOrder.put("spatial.modeling.sweep_point_list_along_path", 7);
        spatialModelingOrder.put("spatial.modeling.surface_strip_to_geometry", 8);
        spatialModelingOrder.put("spatial.modeling.push_pull_box_face", 9);
        spatialModelingOrder.put("spatial.modeling.extrude_box_face", 10);
        categoryOrder.put("spatial.modeling", spatialModelingOrder);

        Map<String, Integer> spatialInstancingOrder = new HashMap<>();
        spatialInstancingOrder.put("spatial.instancing.grow_along_normals", 0);
        spatialInstancingOrder.put("spatial.instancing.grow_along_sphere_normal", 1);
        categoryOrder.put("spatial.instancing", spatialInstancingOrder);

        Map<String, Integer> spatialVoxelOrder = new HashMap<>();
        spatialVoxelOrder.put("spatial.voxel.geometry_to_blocks", 0);
        spatialVoxelOrder.put("spatial.voxel.surface_strip_to_blocks", 1);
        spatialVoxelOrder.put("spatial.voxel.box_geometry_voxelizer", 2);
        spatialVoxelOrder.put("spatial.voxel.sphere_geometry_voxelizer", 3);
        spatialVoxelOrder.put("spatial.voxel.cylinder_geometry_voxelizer", 4);
        spatialVoxelOrder.put("spatial.voxel.cone_geometry_voxelizer", 5);
        spatialVoxelOrder.put("spatial.voxel.ellipsoid_geometry_voxelizer", 6);
        spatialVoxelOrder.put("spatial.voxel.prism_geometry_voxelizer", 7);
        spatialVoxelOrder.put("spatial.voxel.octahedron_geometry_voxelizer", 8);
        spatialVoxelOrder.put("spatial.voxel.tetrahedron_geometry_voxelizer", 9);
        spatialVoxelOrder.put("spatial.voxel.torus_geometry_voxelizer", 10);
        categoryOrder.put("spatial.voxel", spatialVoxelOrder);

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
        categoryOrder.put("spatial.legacy", spatialLegacyOrder);

        return categoryOrder;
    }

    // 内部常量类
    private static class NodeLibraryConstants {
        static final float CHILD_WINDOW_MIN_WIDTH = 50;
        static final float CHILD_WINDOW_MIN_HEIGHT = 50;
        static final int CHILD_BG_COLOR = ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.2f, 1.0f);
        static final float CATEGORY_INDENT = 10f;
        static final float CATEGORY_SPACING_EXPANDED = 3f; // 减小展开类别下方的间距
        static final float CATEGORY_SPACING_COLLAPSED = 2f;
        static final float CATEGORY_ITEM_SPACING = 2f; // 新增：类别项目之间的间距
        static final String DRAG_DROP_PAYLOAD_TYPE = "DND_NODE_FROM_LIBRARY";

        static final Map<String, float[]> CATEGORY_COLORS_FLOAT = new HashMap<>();
        static final Map<String, Integer> CATEGORY_COLORS_INT = new HashMap<>();
        static final float[] DEFAULT_CATEGORY_COLOR_FLOAT = new float[]{0.75f, 0.75f, 0.75f, 1.0f};
        static final int DEFAULT_CATEGORY_COLOR_INT = ImGui.colorConvertFloat4ToU32(DEFAULT_CATEGORY_COLOR_FLOAT[0], DEFAULT_CATEGORY_COLOR_FLOAT[1], DEFAULT_CATEGORY_COLOR_FLOAT[2], DEFAULT_CATEGORY_COLOR_FLOAT[3]);

        static {
            // 主分类颜色配置 (RGBA float 格式) - 使用更鲜明的颜色
            // 现在使用小写的分类ID作为键，以匹配实际使用的分类ID
            CATEGORY_COLORS_FLOAT.put("inputs", new float[]{0.2f, 0.5f, 0.9f, 1.0f});          // 蓝色 - 输入源
            CATEGORY_COLORS_FLOAT.put("data", new float[]{0.95f, 0.6f, 0.2f, 1.0f});           // 橙色 - 数据处理
            CATEGORY_COLORS_FLOAT.put("math", new float[]{0.3f, 0.8f, 0.3f, 1.0f});            // 绿色 - 数学运算
            CATEGORY_COLORS_FLOAT.put("spatial", new float[]{0.9f, 0.9f, 0.2f, 1.0f});         // 黄色 - 空间相关
            CATEGORY_COLORS_FLOAT.put("world", new float[]{0.2f, 0.8f, 0.8f, 1.0f});           // 青色 - 世界相关
            CATEGORY_COLORS_FLOAT.put("visualization", new float[]{0.85f, 0.2f, 0.5f, 1.0f});  // 粉色 - 可视化
            CATEGORY_COLORS_FLOAT.put("utilities", new float[]{0.7f, 0.7f, 0.7f, 1.0f});       // 灰色 - 工具类
            CATEGORY_COLORS_FLOAT.put("flora", new float[]{0.2f, 0.6f, 0.2f, 1.0f});           // 深绿色 - 植物生成
            CATEGORY_COLORS_FLOAT.put("animation", new float[]{0.8f, 0.3f, 0.3f, 1.0f});       // 红色 - 动画
            CATEGORY_COLORS_FLOAT.put("workflow", new float[]{0.7f, 0.7f, 0.7f, 1.0f});        // 兼容utilities/workflow
            
            // 同时为首字母大写的版本添加相同的颜色（兼容性）
            CATEGORY_COLORS_FLOAT.put("Inputs", new float[]{0.2f, 0.5f, 0.9f, 1.0f});          // 蓝色
            CATEGORY_COLORS_FLOAT.put("Data", new float[]{0.95f, 0.6f, 0.2f, 1.0f});           // 橙色
            CATEGORY_COLORS_FLOAT.put("Math", new float[]{0.3f, 0.8f, 0.3f, 1.0f});            // 绿色
            CATEGORY_COLORS_FLOAT.put("Spatial", new float[]{0.9f, 0.9f, 0.2f, 1.0f});         // 黄色
            CATEGORY_COLORS_FLOAT.put("World", new float[]{0.2f, 0.8f, 0.8f, 1.0f});           // 青色
            CATEGORY_COLORS_FLOAT.put("Visualization", new float[]{0.85f, 0.2f, 0.5f, 1.0f});  // 粉色
            CATEGORY_COLORS_FLOAT.put("Utilities", new float[]{0.7f, 0.7f, 0.7f, 1.0f});       // 灰色
            CATEGORY_COLORS_FLOAT.put("Flora", new float[]{0.2f, 0.6f, 0.2f, 1.0f});           // 深绿色
            CATEGORY_COLORS_FLOAT.put("Animation", new float[]{0.8f, 0.3f, 0.3f, 1.0f});       // 红色
            
            // 子分类颜色配置 - 使用略微淡化的主分类颜色
            // inputs子分类
            CATEGORY_COLORS_FLOAT.put("inputs.basic", new float[]{0.3f, 0.6f, 0.95f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("inputs.minecraft", new float[]{0.35f, 0.65f, 1.0f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("inputs.selectors", new float[]{0.4f, 0.7f, 1.0f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("inputs.sources", new float[]{0.45f, 0.75f, 1.0f, 1.0f});
            
            // data子分类
            CATEGORY_COLORS_FLOAT.put("data.conversion", new float[]{1.0f, 0.65f, 0.25f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("data.lists", new float[]{1.0f, 0.7f, 0.3f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("data.sequence", new float[]{1.0f, 0.75f, 0.35f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("data.text", new float[]{1.0f, 0.8f, 0.4f, 1.0f});
            
            // math子分类
            CATEGORY_COLORS_FLOAT.put("math.basic", new float[]{0.4f, 0.85f, 0.4f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("math.logic", new float[]{0.45f, 0.9f, 0.45f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("math.randomness", new float[]{0.5f, 0.95f, 0.5f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("math.trigonometry", new float[]{0.55f, 1.0f, 0.55f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("math.vector", new float[]{0.6f, 1.0f, 0.6f, 1.0f});
            
            // spatial子分类
            CATEGORY_COLORS_FLOAT.put("spatial.analysis", new float[]{0.95f, 0.95f, 0.35f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("spatial.arrays", new float[]{1.0f, 1.0f, 0.4f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("spatial.construct", new float[]{1.0f, 0.95f, 0.35f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("spatial.generators", new float[]{1.0f, 1.0f, 0.45f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("spatial.legacy", new float[]{0.85f, 0.85f, 0.45f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("spatial.instancing", new float[]{0.95f, 0.9f, 0.55f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("spatial.modeling", new float[]{1.0f, 0.98f, 0.5f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("spatial.shapes", new float[]{1.0f, 1.0f, 0.45f, 1.0f});   // 兼容generators
            CATEGORY_COLORS_FLOAT.put("spatial.points", new float[]{1.0f, 1.0f, 0.5f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("spatial.voxel", new float[]{1.0f, 1.0f, 0.55f, 1.0f});
            
            // world子分类
            CATEGORY_COLORS_FLOAT.put("world.entity", new float[]{0.3f, 0.85f, 0.85f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("world.interaction", new float[]{0.35f, 0.9f, 0.9f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("world.inventory", new float[]{0.4f, 0.95f, 0.95f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("world.modify", new float[]{0.45f, 1.0f, 1.0f, 1.0f});     // 兼容modification
            CATEGORY_COLORS_FLOAT.put("world.modification", new float[]{0.45f, 1.0f, 1.0f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("world.nbt", new float[]{0.5f, 1.0f, 1.0f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("world.query", new float[]{0.55f, 1.0f, 1.0f, 1.0f});
            
            // visualization子分类
            CATEGORY_COLORS_FLOAT.put("visualization.debugging", new float[]{0.9f, 0.3f, 0.6f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("visualization.debug", new float[]{0.9f, 0.3f, 0.6f, 1.0f}); // 兼容debugging
            CATEGORY_COLORS_FLOAT.put("visualization.execute", new float[]{0.95f, 0.35f, 0.65f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("visualization.preview", new float[]{1.0f, 0.4f, 0.7f, 1.0f});
            
            // utilities/workflow子分类
            CATEGORY_COLORS_FLOAT.put("utilities.advanced", new float[]{0.75f, 0.75f, 0.75f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("utilities.experimental", new float[]{0.8f, 0.8f, 0.8f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("utilities.fileio", new float[]{0.85f, 0.85f, 0.85f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("utilities.organization", new float[]{0.9f, 0.9f, 0.9f, 1.0f});
            
            // flora子分类 - 使用深绿色系
            CATEGORY_COLORS_FLOAT.put("flora.algorithms", new float[]{0.3f, 0.7f, 0.3f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("flora.generators", new float[]{0.35f, 0.75f, 0.35f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("flora.materials", new float[]{0.4f, 0.8f, 0.4f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("flora.modifiers", new float[]{0.45f, 0.85f, 0.45f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("flora.output", new float[]{0.5f, 0.9f, 0.5f, 1.0f});
            
            // animation子分类 - 使用红色系
            CATEGORY_COLORS_FLOAT.put("animation.effects", new float[]{0.85f, 0.4f, 0.4f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("animation.interpolation", new float[]{0.9f, 0.45f, 0.45f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("animation.output", new float[]{0.95f, 0.5f, 0.5f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("animation.time", new float[]{1.0f, 0.55f, 0.55f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("animation.transforms", new float[]{1.0f, 0.6f, 0.6f, 1.0f});
            
            // 兼容workflow前缀
            CATEGORY_COLORS_FLOAT.put("workflow.advanced", new float[]{0.75f, 0.75f, 0.75f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("workflow.experimental", new float[]{0.8f, 0.8f, 0.8f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("workflow.fileio", new float[]{0.85f, 0.85f, 0.85f, 1.0f});
            CATEGORY_COLORS_FLOAT.put("workflow.organization", new float[]{0.9f, 0.9f, 0.9f, 1.0f});
            
            // 兼容旧分类
            CATEGORY_COLORS_FLOAT.put("Params", new float[]{0.2f, 0.5f, 0.9f, 1.0f});        // 蓝色
            CATEGORY_COLORS_FLOAT.put("Maths", new float[]{0.3f, 0.8f, 0.3f, 1.0f});         // 绿色
            CATEGORY_COLORS_FLOAT.put("Sets", new float[]{0.95f, 0.6f, 0.2f, 1.0f});         // 橙色
            CATEGORY_COLORS_FLOAT.put("Logic", new float[]{0.45f, 0.9f, 0.45f, 1.0f});       // 绿色
            CATEGORY_COLORS_FLOAT.put("Geometry", new float[]{0.9f, 0.9f, 0.2f, 1.0f});      // 黄色
            CATEGORY_COLORS_FLOAT.put("Minecraft", new float[]{0.2f, 0.8f, 0.8f, 1.0f});     // 青色
            CATEGORY_COLORS_FLOAT.put("General", DEFAULT_CATEGORY_COLOR_FLOAT);              // 通用默认颜色

            // 转换浮点颜色为ImGui使用的打包整数颜色
            for (Map.Entry<String, float[]> entry : CATEGORY_COLORS_FLOAT.entrySet()) {
                float[] c = entry.getValue();
                CATEGORY_COLORS_INT.put(entry.getKey(), ImGui.colorConvertFloat4ToU32(c[0], c[1], c[2], c[3]));
            }
        }
        
        // 获取颜色的辅助方法
        static int getPackedColor(String categoryName) {
            // 首先尝试直接查找精确匹配（包括大小写）
            if (CATEGORY_COLORS_INT.containsKey(categoryName)) {
                return CATEGORY_COLORS_INT.get(categoryName);
            }
            
            // 转换为小写再查找（因为大部分ID都是小写的）
            String lowerCaseName = categoryName.toLowerCase();
            if (CATEGORY_COLORS_INT.containsKey(lowerCaseName)) {
                return CATEGORY_COLORS_INT.get(lowerCaseName);
            }

            // 处理格式为"主分类 / 子分类"的显示名称
            if (categoryName.contains(" / ")) {
                // 提取主分类部分
                String mainPart = categoryName.substring(0, categoryName.indexOf(" / "));
                
                // 转换为小写查找
                String mainPartLower = mainPart.toLowerCase();
                if (CATEGORY_COLORS_INT.containsKey(mainPartLower)) {
                    return CATEGORY_COLORS_INT.get(mainPartLower);
                }
                
                // 尝试使用主分类原始大小写
                if (CATEGORY_COLORS_INT.containsKey(mainPart)) {
                    return CATEGORY_COLORS_INT.get(mainPart);
                }
            }
            
            // 如果是点号分隔的ID格式（例如：math.basic），尝试提取主分类
            if (categoryName.contains(".")) {
                String mainCategory = categoryName.substring(0, categoryName.indexOf('.'));
                
                // 尝试查找主分类（小写）
                if (CATEGORY_COLORS_INT.containsKey(mainCategory)) {
                    return CATEGORY_COLORS_INT.get(mainCategory);
                }
                
                // 尝试查找主分类（首字母大写）
                String capitalized = mainCategory.substring(0, 1).toUpperCase() + mainCategory.substring(1);
                if (CATEGORY_COLORS_INT.containsKey(capitalized)) {
                    return CATEGORY_COLORS_INT.get(capitalized);
                }
                
                // 直接查找精确的子分类ID
                if (CATEGORY_COLORS_INT.containsKey(categoryName)) {
                    return CATEGORY_COLORS_INT.get(categoryName);
                }
            }
            
            // 通过简单的字符串比较查找最匹配的颜色
            // 例如，"math.something_unknown" 会匹配到 "math"
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
            
            // 默认返回通用颜色
            return DEFAULT_CATEGORY_COLOR_INT;
        }
    }

    // Node library state
    private final List<NodeCategory> allCategories; // 所有分类
    private final Map<String, Boolean> expandedCategories = new HashMap<>();
    private List<DisplayCategory> filteredCategories; // 过滤后的分类
    private boolean visible = true;

    // 图标管理器
    private final NodeIconManager iconManager = NodeIconManager.getInstance();
    
    // 搜索管理器
    private final NodeSearchManager searchManager = new NodeSearchManager();
    
    /**
     * 节点选择回调接口
     */
    public interface NodeSelectCallback {
        void onNodeSelected(String nodeId, String nodeTitle);
    }
    
    private final NodeSelectCallback selectCallback;
    
    /**
     * 构造函数
     * @param selectCallback 节点选择回调
     */
    public NodeLibraryComponent(NodeSelectCallback selectCallback) {
        this.selectCallback = selectCallback;
        // 直接获取NodeRegistry中的分类列表
        List<NodeCategory> categoriesFromRegistry = NodeRegistry.getInstance().getAllCategories();
        
        // 验证 NodeRegistry 返回的数据
        if (categoriesFromRegistry == null || categoriesFromRegistry.isEmpty()) {
            NodeCraft.LOGGER.warn("NodeRegistry 返回空或无效的分类列表");
            this.allCategories = new ArrayList<>(); // 使用空列表
        } else {
            // 处理分类列表，确保正确的层级关系
            List<NodeCategory> processedCategories = new ArrayList<>();
            
            // 先找出所有的顶级分类
            Map<String, NodeCategory> topLevelCategories = new HashMap<>();
            for (NodeCategory cat : categoriesFromRegistry) {
                String catId = cat.getId();
                
                // 判断是否为顶级分类（不包含点号）
                if (!catId.contains(".")) {
                    topLevelCategories.put(catId, cat);
                    processedCategories.add(cat);
                }
            }
            
            // 然后处理所有子分类，确保它们有正确的顶级父分类
            for (NodeCategory cat : categoriesFromRegistry) {
                String catId = cat.getId();
                
                // 如果包含点号，说明是子分类
                if (catId.contains(".") && !catId.endsWith(".")) {
                    // 提取父分类ID
                    String parentId = catId.substring(0, catId.lastIndexOf('.'));
                    
                    // 如果父分类存在于顶级分类中，则加入处理后的列表
                    if (topLevelCategories.containsKey(parentId)) {
                        processedCategories.add(cat);
                    } else {
                        // 如果父分类不存在，则将该分类作为顶级分类处理
                        NodeCraft.LOGGER.warn("子分类 {} 的父分类 {} 不存在，将其作为顶级分类处理", catId, parentId);
                        processedCategories.add(cat);
                    }
                }
            }
            
            this.allCategories = processedCategories;
        }
        
        // 初始化过滤结果，开始时显示所有分类
        updateFilteredCategories("");
        
        // 初始化分类展开状态
        // 默认展开顶级分类，折叠子分类
        for (NodeCategory cat : allCategories) {
            // 检查是否为子分类
            boolean isSubCategory = cat.getId().contains(".") && !cat.getId().endsWith(".");
            
            if (isSubCategory) {
                // 子分类默认折叠
                expandedCategories.put(cat.getId(), false);
            } else {
                // 顶级分类默认展开
                expandedCategories.put(cat.getId(), true);
            }
        }
        
        // 确保主要分类始终展开，即使它们是子分类
        String[] keyCategories = {
            "inputs", "data", "math", "spatial", "world", "visualization", "utilities",
            "inputs.basic", "math.basic", "data.lists", "spatial.points", "world.entity", 
            "visualization.preview", "utilities.organization"
        };
        
        for (String key : keyCategories) {
            expandedCategories.put(key, true);
        }
        
        // 初始化图标管理器
        iconManager.initialize();
    }
    
    /**
     * 渲染节点库面板
     * @param contentStartY 内容起始Y坐标
     * @param nodePanelWidth 面板宽度
     * @param contentHeight 内容高度
     * @param windowPaddingX 窗口水平内边距
     */
    public void render(float contentStartY, float nodePanelWidth, float contentHeight, float windowPaddingX) {
        if (!visible) {
            return;
        }
        
        boolean nodeLibraryChildBegin = false;
        
        try {
            // 确保以下参数有合理值
            nodePanelWidth = Math.max(NodeLibraryConstants.CHILD_WINDOW_MIN_WIDTH, nodePanelWidth);
            contentHeight = Math.max(NodeLibraryConstants.CHILD_WINDOW_MIN_HEIGHT, contentHeight);
            
            // 创建节点库窗口，设置边框，禁用滚动条(我们将使用内部自定义滚动)
            int windowFlags = ImGuiWindowFlags.NoScrollbar | 
                            ImGuiWindowFlags.NoMove |
                            ImGuiWindowFlags.NoResize |
                            ImGuiWindowFlags.NoCollapse |
                            ImGuiWindowFlags.NoTitleBar;
            
            // 使用带边框的子窗口，第四个参数为true表示显示边框
            nodeLibraryChildBegin = ImGui.beginChild("nodeLibrary", nodePanelWidth, contentHeight, true, windowFlags);
            
            // 关键修复：如果窗口未成功开始，则直接返回，不尝试渲染内容或结束窗口
            if (!nodeLibraryChildBegin) {
                NodeCraft.LOGGER.warn("Failed to begin nodeLibrary child window");
                return;
            }
            
            try {
                // 设置节点库背景颜色，提高可见性
                ImDrawList drawList = ImGui.getWindowDrawList();
                ImVec2 windowPos = ImGui.getWindowPos();
                drawList.addRectFilled(
                    windowPos.x, 
                    windowPos.y, 
                    windowPos.x + nodePanelWidth, 
                    windowPos.y + contentHeight, 
                    NodeLibraryConstants.CHILD_BG_COLOR
                );
                
                // 搜索栏
                renderSearchBar();
                
                ImGui.separator();
                ImGui.spacing();
                
                // 节点类别
                renderNodeCategories();
            } finally {
                // 只有当窗口成功开始时才结束它
                ImGui.endChild();
            }
        } catch (Exception e) {
            // 错误日志保留，因为这些只在异常情况下触发，不会造成高频日志
            NodeCraft.LOGGER.error("渲染节点库时出错: {}", e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 实现EditorComponent接口的render方法
     */
    @Override
    public void render(float x, float y, float width, float height, float paddingX, float paddingY) {
        render(y, width, height, paddingX);
    }
    
    /**
     * 实现EditorComponent接口的init方法
     */
    @Override
    public void init() {
        // 节点库不需要特殊初始化
        if (NodeCraft.LOGGER.isDebugEnabled()) {
            NodeCraft.LOGGER.debug("初始化节点库组件");
        }
    }
    
    /**
     * 实现EditorComponent接口的cleanup方法
     */
    @Override
    public void cleanup() {
        // 清理图标资源
        iconManager.cleanup();
        
        // 节点库不需要特殊清理操作
    }
    
    /**
     * 实现EditorComponent接口的setVisible方法
     */
    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    /**
     * 实现EditorComponent接口的isVisible方法
     */
    @Override
    public boolean isVisible() {
        return visible;
    }
    
    /**
     * 实现EditorComponent接口的getComponentId方法
     */
    @Override
    public String getComponentId() {
        return "nodeLibrary";
    }
    
    /**
     * 实现EditorComponent接口的handleEvent方法
     */
    @Override
    public boolean handleEvent(String eventType, Object data) {
        // 默认实现不处理任何事件
        return false;
    }
    
    /**
     * 渲染搜索栏
     */
    private void renderSearchBar() {
        // 使用搜索管理器渲染搜索栏
        boolean searchChanged = searchManager.renderSearchBar(this::updateFilteredCategories);
        
        // 如果搜索发生变化，强制重新渲染
        if (searchChanged) {
            NodeCraft.LOGGER.info("搜索发生变化，强制重新渲染节点库");
            // 当前帧已经开始渲染，需要在下一帧重新过滤和渲染
            // 在实际运行中，这个变化会在下一帧生效
        }
    }
    
    /**
     * 更新过滤后的分类列表
     * @param searchTerm 搜索关键词
     */
    private void updateFilteredCategories(String searchTerm) {
        // 调试输出
        NodeCraft.LOGGER.info("开始搜索处理，搜索词: '{}'", searchTerm);
        
        // 如果搜索词为空，直接显示所有分类
        if (searchTerm == null || searchTerm.isEmpty()) {
            NodeCraft.LOGGER.debug("搜索词为空，显示所有分类");
            this.filteredCategories = this.allCategories.stream()
                .map(cat -> new DisplayCategory(cat, new ArrayList<>(cat.getNodes())))
                .collect(Collectors.toList());
            NodeCraft.LOGGER.debug("过滤后分类数量 (空搜索词): {}", this.filteredCategories.size());
            return;
        }

        // 对搜索词进行更宽松的处理
        String processedTerm = searchTerm.toLowerCase().trim();
        NodeCraft.LOGGER.info("处理后的搜索词: '{}'", processedTerm);

        // 直接遍历所有分类和节点
        List<DisplayCategory> searchResults = new ArrayList<>();
        Set<String> parentCategoriesToExpand = new HashSet<>();
        
        for (NodeCategory category : allCategories) {
            String categoryId = category.getId();
            String categoryName = category.getDisplayName().toLowerCase();
            boolean categoryMatches = categoryName.contains(processedTerm) || categoryId.toLowerCase().contains(processedTerm);
            
            // 查找匹配的节点
            List<NodeInfo> matchingNodes = new ArrayList<>();
            for (NodeInfo node : category.getNodes()) {
                // 检查节点是否匹配
                if (matchesNode(node, processedTerm)) {
                    matchingNodes.add(node);
                    NodeCraft.LOGGER.debug("节点匹配搜索词: {} ({}) in category {}", 
                        node.getDisplayName(), node.getId(), categoryId);
                }
            }
            
            // 1. 如果分类名称匹配，保留所有节点
            if (categoryMatches) {
                searchResults.add(new DisplayCategory(category, new ArrayList<>(category.getNodes())));
                NodeCraft.LOGGER.debug("分类名称匹配搜索词 '{}': {} ({}), 保留所有节点", 
                    processedTerm, category.getDisplayName(), categoryId);
                
                // 确保展开状态
                expandedCategories.put(categoryId, true);
                
                // 如果是子分类，记录父分类
                if (categoryId.contains(".")) {
                    String parentId = categoryId.substring(0, categoryId.lastIndexOf('.'));
                    parentCategoriesToExpand.add(parentId);
                }
                
                continue;
            }
            
            // 2. 如果有匹配的节点，保留这些节点
            if (!matchingNodes.isEmpty()) {
                searchResults.add(new DisplayCategory(category, matchingNodes));
                NodeCraft.LOGGER.debug("分类 {} 包含 {} 个匹配节点", categoryId, matchingNodes.size());
                
                // 确保展开状态
                expandedCategories.put(categoryId, true);
                
                // 如果是子分类，记录父分类
                if (categoryId.contains(".")) {
                    String parentId = categoryId.substring(0, categoryId.lastIndexOf('.'));
                    parentCategoriesToExpand.add(parentId);
                }
            }
        }
        
        // 确保所有父分类都被展开
        for (String parentId : parentCategoriesToExpand) {
            expandedCategories.put(parentId, true);
            NodeCraft.LOGGER.debug("设置父分类 {} 为展开状态", parentId);
        }
        
        NodeCraft.LOGGER.info("搜索 '{}' 找到 {} 个匹配的分类", processedTerm, searchResults.size());
        
        if (!searchResults.isEmpty()) {
            // 确保顶级分类始终包含在列表中
            List<DisplayCategory> completeResults = new ArrayList<>(searchResults);
            
            // 找出所有包含子分类但自身不在结果中的顶级分类
            for (String parentId : parentCategoriesToExpand) {
                if (!parentId.contains(".")) { // 只处理顶级分类
                    // 检查该分类是否已在结果中
                    boolean alreadyIncluded = searchResults.stream()
                        .anyMatch(dc -> dc.getId().equals(parentId));
                    
                    if (!alreadyIncluded) {
                        // 找到原始分类并添加空节点列表
                        for (NodeCategory cat : allCategories) {
                            if (cat.getId().equals(parentId)) {
                                completeResults.add(new DisplayCategory(cat, new ArrayList<>()));
                                NodeCraft.LOGGER.debug("添加缺失的父分类: {}", parentId);
                                break;
                            }
                        }
                    }
                }
            }
            
            // 如果有匹配结果，使用这些结果
            this.filteredCategories = completeResults;
        } else {
            // 如果没有匹配结果，保留所有顶级分类但不显示任何节点
            NodeCraft.LOGGER.info("没有找到匹配项，显示空的顶级分类");
            this.filteredCategories = allCategories.stream()
                .filter(cat -> !cat.getId().contains(".")) // 只保留顶级分类
                .map(cat -> new DisplayCategory(cat, new ArrayList<>()))
                .collect(Collectors.toList());
        }
        
        NodeCraft.LOGGER.info("过滤后分类总数: {}", this.filteredCategories.size());
    }
    
    /**
     * 检查节点是否匹配搜索词
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
     * 渲染节点分类
     */
    private void renderNodeCategories() {
        // 使用 0 高度让子窗口填充所有可用垂直空间，移除底部边距
        ImGui.beginChild("##nodeListScrollingRegion", 0, 0, false, ImGuiWindowFlags.NoScrollbar);

        // 没有匹配的节点或没有注册节点时显示提示信息
        if (filteredCategories.isEmpty() && !searchManager.getSearchTerm().isEmpty()) {
            searchManager.renderNoMatchesMessage();
        } else if (allCategories.isEmpty()) {
            // Check registry status indirectly
            ImGui.textDisabled("  No nodes registered."); 
            ImGui.spacing();
            ImGui.textDisabled("  Please restart the application or check logs.");
        }

        // 设置分类之间的垂直间距
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), NodeLibraryConstants.CATEGORY_ITEM_SPACING);

        // 整理分类层级关系
        Map<String, List<DisplayCategory>> childCategoriesMap = new HashMap<>();
        List<DisplayCategory> topLevelCategories = new ArrayList<>();
        
        // 规则：
        // 1. 如果分类ID不包含点号，则为顶级分类
        // 2. 如果分类ID格式为"x.y"，则将其作为"x"的子分类

        // 首先识别顶级分类和子分类
        for (DisplayCategory category : filteredCategories) {
            String id = category.getId();
            
            if (!id.contains(".")) {
                // 顶级分类
                topLevelCategories.add(category);
            } else {
                // 子分类 - 获取父分类ID
                String parentId = id.substring(0, id.lastIndexOf('.'));
                childCategoriesMap
                    .computeIfAbsent(parentId, k -> new ArrayList<>())
                    .add(category);
            }
        }
        
        // 按显示名称对顶级分类排序
        topLevelCategories.sort((cat1, cat2) -> cat1.getDisplayName().compareToIgnoreCase(cat2.getDisplayName()));
        
        // 对每个子分类列表进行排序
        for (List<DisplayCategory> childList : childCategoriesMap.values()) {
            childList.sort((cat1, cat2) -> cat1.getDisplayName().compareToIgnoreCase(cat2.getDisplayName()));
        }

        // 获取当前搜索状态
        boolean isSearching = searchManager.getSearchTerm() != null && !searchManager.getSearchTerm().isEmpty();

        // 预处理：如果是搜索状态，确保包含非空节点的分类和它们的父分类都标记为展开
        if (isSearching) {
            // 遍历所有分类，找出包含节点的分类
            for (DisplayCategory category : filteredCategories) {
                if (!category.getNodes().isEmpty()) {
                    String categoryId = category.getId();
                    // 标记该分类为展开状态
                    expandedCategories.put(categoryId, true);
                    
                    // 如果是子分类，确保父分类也是展开状态
                    if (categoryId.contains(".")) {
                        String parentId = categoryId.substring(0, categoryId.lastIndexOf('.'));
                        expandedCategories.put(parentId, true);
                        NodeCraft.LOGGER.debug("搜索模式下强制展开父分类: {}", parentId);
                    }
                }
            }
        }

        // 先渲染所有顶级分类
        for (DisplayCategory topCategory : topLevelCategories) {
            // 渲染顶级分类，并根据搜索状态决定是否展开
            renderCategory(topCategory, 0, isSearching);
            
            // 如果此顶级分类展开且有子分类，则渲染其子分类
            if (expandedCategories.getOrDefault(topCategory.getId(), true)) {
                List<DisplayCategory> children = childCategoriesMap.get(topCategory.getId());
                
                if (children != null && !children.isEmpty()) {
                    ImGui.indent(NodeLibraryConstants.CATEGORY_INDENT);
                    
                    for (DisplayCategory childCategory : children) {
                        // 子分类使用相同的搜索状态
                        renderCategory(childCategory, 1, isSearching);
                    }
                    
                    ImGui.unindent(NodeLibraryConstants.CATEGORY_INDENT);
                }
            }
        }
        
        ImGui.popStyleVar(); // 恢复ItemSpacing
        ImGui.endChild(); // End ##nodeListScrollingRegion
    }
    
    /**
     * 渲染单个分类及其节点
     * @param displayCategory 要渲染的分类
     * @param level 层级深度，0为顶级分类
     * @param shouldExpand 是否应该强制展开
     */
    private void renderCategory(DisplayCategory displayCategory, int level, boolean shouldExpand) {
        // 获取当前状态，如果不存在则默认为true (already handled in constructor)
        boolean isExpanded = expandedCategories.getOrDefault(displayCategory.getId(), true);
        
        // 搜索模式下检查此分类是否包含节点
        boolean hasNodes = !displayCategory.getNodes().isEmpty();
        
        // 在搜索状态下，如果分类包含节点，那么状态应该为true
        if (shouldExpand && hasNodes) {
            isExpanded = true;
            // 记住搜索状态下的展开状态
            expandedCategories.put(displayCategory.getId(), true);
        }
        
        // 获取分类的颜色 - 优先使用原始分类ID以确保使用正确的颜色映射
        int packedColor;
        String categoryId = displayCategory.getId(); // 使用ID而不是显示名称
        
        // 直接使用分类ID获取颜色
        if (NodeLibraryConstants.CATEGORY_COLORS_INT.containsKey(categoryId)) {
            packedColor = NodeLibraryConstants.CATEGORY_COLORS_INT.get(categoryId);
        } else {
            // 如果找不到精确匹配，使用getPackedColor寻找最佳匹配
            packedColor = NodeLibraryConstants.getPackedColor(categoryId);
        }
        
        // 计算背景颜色 - 使用更鲜明的分类颜色
        imgui.ImVec4 colorVec = new imgui.ImVec4();
        ImGui.colorConvertU32ToFloat4(packedColor, colorVec);
        float[] color = new float[]{colorVec.x, colorVec.y, colorVec.z, colorVec.w};
        
        // 根据类别级别调整颜色强度
        float alphaBase = level == 0 ? 0.7f : 0.5f; // 顶级分类更明显
        float colorIntensity = level == 0 ? 0.7f : 0.6f; // 顶级分类颜色更强
        
        // 计算不同状态下的颜色
        int bgColor = ImGui.colorConvertFloat4ToU32(
            color[0] * colorIntensity, 
            color[1] * colorIntensity, 
            color[2] * colorIntensity, 
            alphaBase); // 标准背景色
            
        int hoverBgColor = ImGui.colorConvertFloat4ToU32(
            Math.min(1.0f, color[0] * 1.2f * colorIntensity), 
            Math.min(1.0f, color[1] * 1.2f * colorIntensity), 
            Math.min(1.0f, color[2] * 1.2f * colorIntensity), 
            alphaBase + 0.2f); // 悬停时更亮
            
        int activeBgColor = ImGui.colorConvertFloat4ToU32(
            Math.min(1.0f, color[0] * 1.3f * colorIntensity), 
            Math.min(1.0f, color[1] * 1.3f * colorIntensity), 
            Math.min(1.0f, color[2] * 1.3f * colorIntensity), 
            alphaBase + 0.3f); // 激活时最亮

        // --- 样式设置 ---
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), 0);
        
        // 增强文本颜色的饱和度和对比度
        float[] saturatedColor = new float[4];
        System.arraycopy(color, 0, saturatedColor, 0, 4);
        
        // 增加色彩鲜艳度和对比度
        for (int i = 0; i < 3; i++) {
            // 对于深色，增加亮度；对于亮色，增加饱和度
            if (saturatedColor[i] < 0.5f) {
                saturatedColor[i] = Math.min(1.0f, saturatedColor[i] * 1.6f); // 更大的增益
            } else {
                saturatedColor[i] = Math.min(1.0f, 0.8f + saturatedColor[i] * 0.2f); // 更亮
            }
        }
        
        // 确保文本在背景上有足够的对比度
        float textLuminance = 0.299f * saturatedColor[0] + 0.587f * saturatedColor[1] + 0.114f * saturatedColor[2];
        // 如果颜色太暗，则使用白色文本；否则使用更亮的颜色
        int enhancedTextColor;
        if (textLuminance < 0.6f) {
            enhancedTextColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 1.0f); // 白色文本
        } else {
            enhancedTextColor = ImGui.colorConvertFloat4ToU32(saturatedColor[0], saturatedColor[1], saturatedColor[2], 1.0f);
        }
        
        ImGui.pushStyleColor(ImGuiCol.Text, enhancedTextColor);
        ImGui.pushStyleColor(ImGuiCol.Header, bgColor);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, hoverBgColor);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, activeBgColor);

        // 对顶级分类使用稍大一些的字体
        if (level == 0) {
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, 4, 6);
        }

        // 获取合适的显示标题
        String displayTitle = getString(displayCategory, level, categoryId);

        // 定义折叠头部标志
        int headerFlags = ImGuiSelectableFlags.None;
        
        // 如果是搜索模式下且分类包含节点，强制展开
        if (shouldExpand && hasNodes) {
            ImGui.setNextItemOpen(true); // 强制下一个折叠头部为展开状态
        }
        
        // 使用标准的可折叠标题
        boolean headerClicked = ImGui.collapsingHeader(displayTitle + "##header_" + displayCategory.getId(), headerFlags);

        // 恢复样式
        if (level == 0) {
            ImGui.popStyleVar();
        }
        ImGui.popStyleColor(4);
        ImGui.popStyleVar();

        // 更新存储的状态
        expandedCategories.put(displayCategory.getId(), headerClicked);

        // 渲染节点 - 仅当分类展开时
        if (headerClicked) {
            ImGui.indent(NodeLibraryConstants.CATEGORY_INDENT);
            
            // 根据层级添加适当的垂直间距
            ImGui.dummy(0, 2.0f);

            // 检查该分类是否有子分类
            boolean hasSubcategories = false;
            String catId = displayCategory.getId();
            
            // 检查是否有以此分类ID为前缀的其他分类
            for (DisplayCategory otherCategory : filteredCategories) {
                if (otherCategory != displayCategory && 
                    otherCategory.getId().startsWith(catId + ".")) {
                    hasSubcategories = true;
                    break;
                }
            }
            
            // 'displayCategory.getNodes()' here already contains the filtered list if searching
            List<NodeInfo> nodesToRender = getSortedNodesForDisplay(displayCategory);
            
            // 打印该分类中节点数量的调试信息
            NodeCraft.LOGGER.debug("分类 {} 有 {} 个节点需要渲染", displayCategory.getDisplayName(), nodesToRender.size());
            
            // 如果是搜索模式，添加更多的调试信息
            if (!searchManager.getSearchTerm().isEmpty()) {
                NodeCraft.LOGGER.debug("搜索模式: '{}'，分类 {} 中可见节点: {}", 
                    searchManager.getSearchTerm(), 
                    displayCategory.getDisplayName(), 
                    nodesToRender.stream().map(NodeInfo::getDisplayName).collect(Collectors.joining(", ")));
            }

            // 只有当分类没有子分类且节点列表为空时才显示"此分类中没有节点"
            if (nodesToRender.isEmpty() && searchManager.getSearchTerm().isEmpty() && !hasSubcategories) {
                ImGui.textDisabled("  (No nodes in this category)");
            } else if (nodesToRender.isEmpty() && !searchManager.getSearchTerm().isEmpty()) {
                // If searching and no nodes matched in this category (but category name might have matched)
                // Do nothing, the main "No matching nodes found" message handles this.
            } else {
                for (NodeInfo node : nodesToRender) {
                    // 为节点获取颜色 - 使用分类颜色作为基础
                    String nodeCategory = node.getCategoryId();
                    int nodeColorPacked;
                    if (NodeLibraryConstants.CATEGORY_COLORS_INT.containsKey(nodeCategory)) {
                        nodeColorPacked = NodeLibraryConstants.CATEGORY_COLORS_INT.get(nodeCategory);
                    } else {
                        nodeColorPacked = NodeLibraryConstants.getPackedColor(nodeCategory);
                    }
                    
                    // 计算图标位置和大小
                    float lineHeight = ImGui.getTextLineHeight();
                    // 确保图标大小与行高一致
                    float iconPadding = 4.0f; // 图标与文本间距
                    
                    ImVec2 cursorPos = ImGui.getCursorScreenPos();
                    float textStartX = cursorPos.x + lineHeight + iconPadding;
                    
                    // 计算可用宽度
                    float availableWidth = ImGui.getContentRegionAvailX();
                    
                    // 创建一个透明的选择器，高度设置为行高，确保正好容纳图标
                    boolean selected = ImGui.selectable("##node_selector_" + node.getId(), false, 
                        ImGuiSelectableFlags.AllowItemOverlap, availableWidth, lineHeight);
                    
                    // 获取选择器区域以计算图标位置
                    ImVec2 rectMin = ImGui.getItemRectMin();
                    ImVec2 rectMax = ImGui.getItemRectMax();
                    
                    // 在相同位置绘制图标和文本
                    ImDrawList drawList = ImGui.getWindowDrawList();
                    
                    // 绘制图标纹理
                    renderNode(drawList, rectMin, new ImVec2(lineHeight, lineHeight), node, nodeCategory, lineHeight, iconPadding);
                    
                    // 处理选择事件
                    if (selected) {
                        // 节点被选中
                        if (selectCallback != null) {
                            selectCallback.onNodeSelected(node.getId(), node.getDisplayName());
                            // 节点选择事件值得记录，但也应该在调试模式下
                            if (NodeCraft.LOGGER.isDebugEnabled()) {
                                NodeCraft.LOGGER.debug("节点选择: {} ({})", node.getDisplayName(), node.getId());
                            }
                        }
                    }

                    // --- 拖放源设置 --- 使用常量定义载荷类型
                    if (ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
                        // 载荷需要是字节数组
                        byte[] payloadBytes = node.getId().getBytes(StandardCharsets.UTF_8);
                        ImGui.setDragDropPayload(NodeLibraryConstants.DRAG_DROP_PAYLOAD_TYPE, payloadBytes);
                        
                        // 在拖拽预览中显示图标和节点标题
                        float dragIconSize = lineHeight * 0.8f; // 拖拽时略小的图标
                        ImGui.dummy(dragIconSize, dragIconSize); // 创建占位符
                        ImVec2 iconPos = ImGui.getItemRectMin();
                        
                        // 绘制拖拽时的图标
                        ImDrawList dragDrawList = ImGui.getWindowDrawList();
                        renderNode(dragDrawList, iconPos, new ImVec2(dragIconSize, dragIconSize), node, nodeCategory, dragIconSize, 0);
                        
                        ImGui.sameLine(0, 5.0f);
                        ImGui.text(node.getDisplayName()); // 在图标旁显示文本
                        ImGui.endDragDropSource();
                    }

                    // --- 工具提示 ---
                    if (ImGui.isItemHovered()) {
                        ImGui.beginTooltip();
                        
                        // 在工具提示标题中也绘制带颜色的图标
                        float tooltipIconSize = ImGui.getTextLineHeight() * 1.2f; // 提示中稍大的图标
                        ImVec2 tooltipPos = ImGui.getCursorScreenPos();
                        
                        ImDrawList tooltipDrawList = ImGui.getWindowDrawList();
                        renderNode(tooltipDrawList, tooltipPos, new ImVec2(tooltipIconSize, tooltipIconSize), node, nodeCategory, tooltipIconSize, 0);
                        
                        ImGui.dummy(tooltipIconSize, tooltipIconSize); // 创建与图标相同大小的空间
                        ImGui.sameLine();
                        ImGui.textUnformatted(node.getDisplayName());
                        
                        ImGui.textDisabled(node.getId());
                        
                        // 显示节点描述（如果有）
                        String description = node.getDescription() != null ? node.getDescription() : "";
                        if (!description.isEmpty()) {
                            ImGui.separator();
                            ImGui.textWrapped(description);
                        }
                        
                        // 显示节点分类
                        ImGui.separator();
                        ImGui.textDisabled("分类: " + displayCategory.getDisplayName());
                        
                        ImGui.endTooltip();
                    }
                }
            }
            ImGui.unindent(NodeLibraryConstants.CATEGORY_INDENT);
            ImGui.dummy(0, NodeLibraryConstants.CATEGORY_SPACING_EXPANDED);
        } else {
             // Optional: Add very small spacing below collapsed header for visual separation
             ImGui.dummy(0, NodeLibraryConstants.CATEGORY_SPACING_COLLAPSED);
        }
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
            // 对于子分类，我们在UI层动态构建"父分类 / 子分类"格式的显示名称
            String subCategoryPart = categoryId.substring(categoryId.lastIndexOf('.') + 1);
            // 首字母大写
            subCategoryPart = subCategoryPart.substring(0, 1).toUpperCase() + subCategoryPart.substring(1);
            displayTitle = subCategoryPart;
        } else {
            // 对于顶级分类，直接使用原始显示名称
            displayTitle = displayCategory.getDisplayName();
        }
        return displayTitle;
    }

    /**
     * 修改renderNode方法，使用搜索管理器高亮文本
     */
    private void renderNode(ImDrawList drawList, ImVec2 rectMin, ImVec2 textSize, NodeInfo node, String nodeCategory, float iconSize, float iconPadding) {
        // 获取节点ID和分类
        String nodeId = node.getId();
        
        // 首先尝试加载节点特定的图标
        int textureId = iconManager.loadNodeIcon(nodeId, nodeCategory);
        
        // 计算图标的UV坐标 (对于纹理图标，UV坐标范围是0-1)
        float u0 = 0.0f;
        float v0 = 0.0f;
        float u1 = 1.0f;
        float v1 = 1.0f;
        
        // 确保图标位置精确对齐文本行高
        float actualLineHeight = ImGui.getTextLineHeight();
        float yOffset = 0;
        if (iconSize < actualLineHeight) {
            // 如果图标小于行高，居中显示
            yOffset = (actualLineHeight - iconSize) * 0.5f;
        }
        
        // 绘制图标纹理
        if (textureId > 0) {
            // 绑定纹理
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            
            // 绘制纹理图标
            drawList.addImage(
                textureId, 
                rectMin.x, rectMin.y + yOffset, 
                rectMin.x + iconSize, rectMin.y + yOffset + iconSize,
                u0, v0, u1, v1
            );
        } else {
            // 如果纹理加载失败，回退到使用矩形
            int bgColor = ImGui.colorConvertFloat4ToU32(0.7f, 0.7f, 0.7f, 0.7f);
            drawList.addRectFilled(
                rectMin.x, rectMin.y + yOffset, 
                rectMin.x + iconSize, rectMin.y + yOffset + iconSize,
                bgColor, 0.0f // 方形矩形，无圆角
            );
        }
        
        // 如果需要绘制文本（只有在正常显示节点时，拖放和工具提示时不需要）
        if (iconPadding > 0) {
            // 计算文本位置
            float textStartX = rectMin.x + iconSize + iconPadding;
            float textPosY = rectMin.y + (actualLineHeight - ImGui.getTextLineHeight()) * 0.5f;
            
            // 获取当前搜索词
            String searchTerm = searchManager.getSearchTerm();
            String nodeDisplayName = node.getDisplayName();
            
            // 绘制文本 - 在搜索模式下高亮显示匹配部分
            int textColor = ImGui.getColorU32(ImGuiCol.Text);
            
            if (searchTerm != null && !searchTerm.isEmpty()) {
                // 检查节点名称是否包含搜索词（不区分大小写）
                String lowerNodeName = nodeDisplayName.toLowerCase();
                String lowerSearchTerm = searchTerm.toLowerCase();
                
                if (lowerNodeName.contains(lowerSearchTerm)) {
                    // 高亮显示匹配部分
                    int highlightColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.0f, 1.0f); // 亮黄色高亮
                    
                    // 分段绘制文本
                    int matchStart = lowerNodeName.indexOf(lowerSearchTerm);
                    int matchEnd = matchStart + lowerSearchTerm.length();
                    
                    // 前部分
                    if (matchStart > 0) {
                        String prefix = nodeDisplayName.substring(0, matchStart);
                        drawList.addText(textStartX, textPosY, textColor, prefix);
                        textStartX += ImGui.calcTextSize(prefix).x;
                    }
                    
                    // 高亮部分
                    String highlight = nodeDisplayName.substring(matchStart, matchEnd);
                    drawList.addText(textStartX, textPosY, highlightColor, highlight);
                    textStartX += ImGui.calcTextSize(highlight).x;
                    
                    // 后部分
                    if (matchEnd < nodeDisplayName.length()) {
                        String suffix = nodeDisplayName.substring(matchEnd);
                        drawList.addText(textStartX, textPosY, textColor, suffix);
                    }
                } else {
                    // 节点通过ID或描述匹配，但名称不匹配
                    drawList.addText(textStartX, textPosY, textColor, nodeDisplayName);
                    
                    // 添加一个小的指示器，表示此节点是通过其他属性匹配的
                    float indicatorSize = 3.0f;
                    float indicatorX = textStartX - indicatorSize - 2.0f;
                    float indicatorY = textPosY + ImGui.getTextLineHeight() / 2.0f - indicatorSize / 2.0f;
                    
                    // 绘制一个指示器
                    int indicatorColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.0f, 1.0f);
                    drawList.addRectFilled(
                        indicatorX, indicatorY,
                        indicatorX + indicatorSize, indicatorY + indicatorSize,
                        indicatorColor
                    );
                }
            } else {
                // 普通模式下直接绘制文本
                drawList.addText(textStartX, textPosY, textColor, nodeDisplayName);
            }
        }
    }
} 

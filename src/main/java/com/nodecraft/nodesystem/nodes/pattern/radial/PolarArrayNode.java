package com.nodecraft.nodesystem.nodes.pattern.radial;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Polar Array 节点: 将Coordinate列表绕中心点重复旋转
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.radial.polar_array",
    displayName = "Polar Array",
    description = "将坐标列表绕中心点重复旋转排列",
    category = "pattern.radial",
    order = 0
)
public class PolarArrayNode extends BaseNode {

    // --- 节点属性 ---
    private boolean includeOriginal = true; // 默认包含原始坐标

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_TOTAL_ANGLE_ID = "input_total_angle";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_ARRAY_COORDINATES_ID = "output_array_coordinates";
    private static final String OUTPUT_ARRAY_TREE_ID = "output_array_tree";

    // --- 构造函数 ---
    public PolarArrayNode() {
        super(UUID.randomUUID(), "pattern.radial.polar_array");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", 
                "The coordinates to rotate in a circular pattern", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", 
                "Center point of rotation", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", 
                "Axis of rotation", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", 
                "Total number of rotated instance groups (including the original at 0°)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_TOTAL_ANGLE_ID, "Total Angle", 
                "Total angle span in degrees. Full circles never emit a duplicate at 360°.", NodeDataType.DOUBLE, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_ARRAY_COORDINATES_ID, "Array Coordinates", 
                "The resulting polar array of coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ARRAY_TREE_ID, "Array Tree",
                "One branch per emitted coordinate copy", NodeDataType.DATA_TREE, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Creates a circular pattern by rotating coordinates around a center point";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Polar Array";
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object axisObj = inputValues.get(INPUT_AXIS_ID);
        Object countObj = inputValues.get(INPUT_COUNT_ID);
        Object totalAngleObj = inputValues.get(INPUT_TOTAL_ANGLE_ID);
        
        // 默认空的坐标列表
        BlockPosList result = new BlockPosList();
        List<DataTreeData.Branch> branches = new ArrayList<>();
        
        // 检查输入是否合法
        if (coordinatesObj instanceof BlockPosList coordinates &&
                centerObj instanceof BlockPos centerPos &&
                axisObj instanceof Vector3d axis &&
            countObj instanceof Number && 
            totalAngleObj instanceof Number) {

            int count = ((Number) countObj).intValue();
            double totalAngleDegrees = ((Number) totalAngleObj).doubleValue();
            
            // 如果输入坐标列表为空，直接返回空结果
            if (coordinates.isEmpty()) {
                outputValues.put(OUTPUT_ARRAY_COORDINATES_ID, result);
                outputValues.put(OUTPUT_ARRAY_TREE_ID, DataTreeData.empty());
                return;
            }
            
            // 确保旋转轴不是零向量
            if (axis.length() < 0.0001) {
                axis = new Vector3d(0, 1, 0); // 默认使用Y轴
            } else {
                axis.normalize(); // 标准化旋转轴
            }
            
            // 确保计数为正数 — Count = total emitted instance groups (including original at 0°)
            count = GenerationLimits.clampRepeatCount(Math.max(1, count), coordinates.size());
            
            // 创建极坐标阵列（full-circle never emits a duplicate at 360°）
            createPolarArray(coordinates, centerPos, axis, count, totalAngleDegrees, result, branches);
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_ARRAY_COORDINATES_ID, result);
        outputValues.put(OUTPUT_ARRAY_TREE_ID, new DataTreeData(branches));
    }
    
    /**
     * 创建极坐标阵列. Count = total instance groups at angles {@code totalAngle * i / count}.
     */
    private void createPolarArray(BlockPosList sourceCoords, BlockPos center, Vector3d axis, 
                               int count, double totalAngleDegrees,
                               BlockPosList result, List<DataTreeData.Branch> branches) {
        Vector3d centerVec = new Vector3d(center.getX(), center.getY(), center.getZ());
        
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(totalAngleDegrees * i / (double) count);
            List<BlockPos> copyPositions = new ArrayList<>();
            
            if (Math.abs(angle) <= 1.0e-12d) {
                result.addAll(sourceCoords.getPositions());
                addCopyBranch(branches, sourceCoords.getPositions());
                continue;
            }
            
            Matrix4d rotationMatrix = createRotationMatrix(centerVec, axis, angle);
            
            for (BlockPos pos : sourceCoords) {
                Vector3d posVec = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
                Vector3d rotatedVec = transformPoint(posVec, rotationMatrix);
                
                BlockPos rotatedPos = new BlockPos(
                    (int) Math.round(rotatedVec.x),
                    (int) Math.round(rotatedVec.y),
                    (int) Math.round(rotatedVec.z)
                );
                
                result.add(rotatedPos);
                copyPositions.add(rotatedPos);
            }
            addCopyBranch(branches, copyPositions);
        }
    }

    private void addCopyBranch(List<DataTreeData.Branch> branches, List<BlockPos> positions) {
        branches.add(new DataTreeData.Branch(List.of(branches.size()), new ArrayList<>(positions)));
    }
    
    /**
     * 创建绕轴旋转的变换矩阵
     * @param center 旋转中心
     * @param axis 旋转轴
     * @param angle 旋转角度（弧度）
     * @return 变换矩阵
     */
    private Matrix4d createRotationMatrix(Vector3d center, Vector3d axis, double angle) {
        // 创建基于轴角的旋转四元数
        Quaterniond rotation = new Quaterniond(new AxisAngle4d(angle, axis.x, axis.y, axis.z));
        
        // 创建变换矩阵
        Matrix4d matrix = new Matrix4d();
        
        // 构建变换矩阵：平移到原点 -> 旋转 -> 平移回去
        matrix.translate(-center.x, -center.y, -center.z)  // 平移到原点
              .rotate(rotation)                           // 应用旋转
              .translate(center.x, center.y, center.z);   // 平移回原位置
        
        return matrix;
    }
    
    /**
     * 使用变换矩阵变换点
     * @param point 要变换的点
     * @param matrix 变换矩阵
     * @return 变换后的点
     */
    private Vector3d transformPoint(Vector3d point, Matrix4d matrix) {
        // 创建点的副本
        Vector3d result = new Vector3d(point);
        
        // 应用变换
        result.mulPosition(matrix);
        
        return result;
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isIncludeOriginal() {
        return includeOriginal;
    }
    
    public void setIncludeOriginal(boolean includeOriginal) {
        this.includeOriginal = includeOriginal;
        markDirty();
    }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("includeOriginal", includeOriginal);
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;

            if (stateMap.containsKey("includeOriginal")) {
                Object includeOriginalObj = stateMap.get("includeOriginal");
                if (includeOriginalObj instanceof Boolean) {
                    setIncludeOriginal((Boolean) includeOriginalObj);
                }
            }
        }
    }
} 

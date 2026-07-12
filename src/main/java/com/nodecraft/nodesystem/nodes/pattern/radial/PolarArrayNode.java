package com.nodecraft.nodesystem.nodes.pattern.radial;

import com.nodecraft.nodesystem.api.NodeDataType;
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
                "Number of copies to create", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_TOTAL_ANGLE_ID, "Total Angle", 
                "Total angle to distribute copies (degrees)", NodeDataType.DOUBLE, this));

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
        if (coordinatesObj instanceof BlockPosList && 
            centerObj instanceof BlockPos && 
            axisObj instanceof Vector3d && 
            countObj instanceof Number && 
            totalAngleObj instanceof Number) {
            
            BlockPosList coordinates = (BlockPosList) coordinatesObj;
            BlockPos centerPos = (BlockPos) centerObj;
            Vector3d axis = (Vector3d) axisObj;
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
            
            // 确保计数为正数
            count = GenerationLimits.clampRepeatCount(Math.max(1, count), coordinates.size());
            
            // 计算每个实例的旋转角度（弧度）
            double angleIncrement = Math.toRadians(totalAngleDegrees) / count;
            
            // 创建极坐标阵列
            createPolarArray(coordinates, centerPos, axis, count, angleIncrement, includeOriginal, result, branches);
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_ARRAY_COORDINATES_ID, result);
        outputValues.put(OUTPUT_ARRAY_TREE_ID, new DataTreeData(branches));
    }
    
    /**
     * 创建极坐标阵列
     * @param sourceCoords 源坐标列表
     * @param center 旋转中心
     * @param axis 旋转轴
     * @param count 重复次数
     * @param angleIncrement 每次增加的角度（弧度）
     * @param includeOriginal 是否包含原始坐标
     * @param result 结果坐标列表
     */
    private void createPolarArray(BlockPosList sourceCoords, BlockPos center, Vector3d axis, 
                               int count, double angleIncrement, boolean includeOriginal,
                               BlockPosList result, List<DataTreeData.Branch> branches) {
        // 将中心点转换为向量
        Vector3d centerVec = new Vector3d(center.getX(), center.getY(), center.getZ());
        
        // 如果需要，添加原始坐标
        if (includeOriginal) {
            result.addAll(sourceCoords.getPositions());
            addCopyBranch(branches, sourceCoords.getPositions());
        }
        
        // 对于每个实例，创建一个旋转副本
        for (int i = 1; i <= count; i++) {
            // 计算当前实例的旋转角度
            double angle = angleIncrement * i;
            
            // 创建旋转矩阵
            Matrix4d rotationMatrix = createRotationMatrix(centerVec, axis, angle);
            List<BlockPos> copyPositions = new ArrayList<>();
            
            // 对源坐标列表中的每个坐标应用旋转
            for (BlockPos pos : sourceCoords) {
                // 将坐标转换为向量
                Vector3d posVec = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
                
                // 应用旋转
                Vector3d rotatedVec = transformPoint(posVec, rotationMatrix);
                
                // 转换回方块坐标（四舍五入到最接近的整数）
                BlockPos rotatedPos = new BlockPos(
                    (int) Math.round(rotatedVec.x),
                    (int) Math.round(rotatedVec.y),
                    (int) Math.round(rotatedVec.z)
                );
                
                // 添加到结果列表
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

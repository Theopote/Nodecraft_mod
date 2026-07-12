package com.nodecraft.nodesystem.nodes.pattern.linear;

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
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Linear Array 节点: 将Coordinate列表沿直线方向重复
 */
@NodeInfo(
    id = "pattern.linear.linear_array",
    displayName = "Linear Array",
    description = "将坐标列表沿直线方向重复排列",
    category = "pattern.linear",
    order = 0
)
public class LinearArrayNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "将坐标列表沿直线方向重复";
    private boolean useDirection = true; // 默认使用方向向量和距离
    private boolean includeOriginal = true; // 默认包含原始坐标

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_DIRECTION_ID = "input_direction";
    private static final String INPUT_DISTANCE_ID = "input_distance";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_END_POINT_ID = "input_end_point";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_ARRAY_COORDINATES_ID = "output_array_coordinates";
    private static final String OUTPUT_ARRAY_TREE_ID = "output_array_tree";

    // --- 构造函数 ---
    public LinearArrayNode() {
        super(UUID.randomUUID(), "pattern.linear.linear_array");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", 
                "The coordinates to repeat", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_DIRECTION_ID, "Direction", 
                "Direction vector for the array", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", 
                "Distance between repeated instances", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", 
                "Number of repetitions to create", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_END_POINT_ID, "End Point", 
                "Alternative: end point for the array (if not using direction)", NodeDataType.BLOCK_POS, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_ARRAY_COORDINATES_ID, "Array Coordinates", 
                "The resulting array of coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ARRAY_TREE_ID, "Array Tree",
                "One branch per emitted coordinate copy", NodeDataType.DATA_TREE, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object directionObj = inputValues.get(INPUT_DIRECTION_ID);
        Object distanceObj = inputValues.get(INPUT_DISTANCE_ID);
        Object countObj = inputValues.get(INPUT_COUNT_ID);
        Object endPointObj = inputValues.get(INPUT_END_POINT_ID);
        
        // 默认空的坐标列表
        BlockPosList result = new BlockPosList();
        List<DataTreeData.Branch> branches = new ArrayList<>();
        
        // 检查输入是否为方块坐标列表
        if (coordinatesObj instanceof BlockPosList) {
            BlockPosList coordinates = (BlockPosList) coordinatesObj;
            
            // 如果输入坐标列表为空，直接返回空结果
            if (coordinates.isEmpty()) {
                outputValues.put(OUTPUT_ARRAY_COORDINATES_ID, result);
                outputValues.put(OUTPUT_ARRAY_TREE_ID, DataTreeData.empty());
                return;
            }
            
            // 默认参数
            Vector3d direction = new Vector3d(1, 0, 0); // 默认X轴方向
            double distance = 1.0; // 默认距离1个方块
            int count = 1; // 默认复制1次（总共2个实例，包括原始实例）
            
            // 计算方向和距离
            if (useDirection && directionObj instanceof Vector3d && 
                distanceObj instanceof Number && countObj instanceof Number) {
                // 使用方向向量和距离
                direction = (Vector3d) directionObj;
                distance = ((Number) distanceObj).doubleValue();
                count = ((Number) countObj).intValue();
                
                // 确保合理的参数
                if (direction.length() < 0.0001) {
                    direction = new Vector3d(1, 0, 0); // 防止零向量
                } else {
                    direction.normalize(); // 标准化方向向量
                }
                
                distance = Math.max(0.1, distance); // 确保距离为正
                count = GenerationLimits.clampRepeatCount(Math.max(1, count), coordinates.size()); // 确保至少复制一次
                
                // 创建线性阵列
                createLinearArray(coordinates, direction, distance, count, includeOriginal, result, branches);
            } 
            else if (!useDirection && endPointObj instanceof BlockPos && countObj instanceof Number) {
                // 使用终点和计数
                BlockPos endPoint = (BlockPos) endPointObj;
                count = ((Number) countObj).intValue();
                
                // 计算阵列方向向量（从第一个坐标到终点）
                BlockPos firstPos = coordinates.getPositions().get(0);
                Vector3d startVec = new Vector3d(firstPos.getX(), firstPos.getY(), firstPos.getZ());
                Vector3d endVec = new Vector3d(endPoint.getX(), endPoint.getY(), endPoint.getZ());
                
                // 计算方向和距离
                Vector3d diff = new Vector3d(endVec).sub(startVec);
                double totalDistance = diff.length();
                
                // 如果总距离太小，直接返回原始坐标
                if (totalDistance < 0.0001 || count < 1) {
                    if (includeOriginal) {
                        result.addAll(coordinates.getPositions());
                        addCopyBranch(branches, coordinates.getPositions());
                    }
                    outputValues.put(OUTPUT_ARRAY_COORDINATES_ID, result);
                    outputValues.put(OUTPUT_ARRAY_TREE_ID, new DataTreeData(branches));
                    return;
                }
                
                // 计算标准化方向和单位距离
                direction = diff.normalize();
                distance = totalDistance / count;
                
                // 创建线性阵列
                createLinearArray(coordinates, direction, distance, count, includeOriginal, result, branches);
            }
            else {
                // 输入不完整，但仍然可能使用默认值创建阵列
                if (countObj instanceof Number) {
                    count = ((Number) countObj).intValue();
                    count = GenerationLimits.clampRepeatCount(Math.max(1, count), coordinates.size());
                }
                
                // 使用默认参数创建线性阵列
                createLinearArray(coordinates, direction, distance, count, includeOriginal, result, branches);
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_ARRAY_COORDINATES_ID, result);
        outputValues.put(OUTPUT_ARRAY_TREE_ID, new DataTreeData(branches));
    }
    
    /**
     * 创建线性阵列
     * @param sourceCoords 源坐标列表
     * @param direction 方向向量
     * @param distance 距离
     * @param count 重复次数
     * @param includeOriginal 是否包含原始坐标
     * @param result 结果坐标列表
     */
    private void createLinearArray(BlockPosList sourceCoords, Vector3d direction, 
                                double distance, int count, boolean includeOriginal,
                                BlockPosList result, List<DataTreeData.Branch> branches) {
        // 将原始坐标添加到结果中（如果需要）
        if (includeOriginal) {
            result.addAll(sourceCoords.getPositions());
            addCopyBranch(branches, sourceCoords.getPositions());
        }
        
        // 计算实际的位移向量（方向 * 距离）
        Vector3d displacement = new Vector3d(direction).mul(distance);
        
        // 对于每个实例，将源坐标平移相应的距离
        for (int i = 1; i <= count; i++) {
            // 计算当前实例的位移
            Vector3d currentDisplacement = new Vector3d(displacement).mul(i);
            List<BlockPos> copyPositions = new ArrayList<>();
            
            // 添加平移后的每个坐标
            for (BlockPos pos : sourceCoords) {
                // 计算新坐标
                BlockPos newPos = new BlockPos(
                    (int) Math.round(pos.getX() + currentDisplacement.x),
                    (int) Math.round(pos.getY() + currentDisplacement.y),
                    (int) Math.round(pos.getZ() + currentDisplacement.z)
                );
                
                // 添加到结果列表
                result.add(newPos);
                copyPositions.add(newPos);
            }
            addCopyBranch(branches, copyPositions);
        }
    }

    private void addCopyBranch(List<DataTreeData.Branch> branches, List<BlockPos> positions) {
        branches.add(new DataTreeData.Branch(List.of(branches.size()), new ArrayList<>(positions)));
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isUseDirection() {
        return useDirection;
    }
    
    public void setUseDirection(boolean useDirection) {
        this.useDirection = useDirection;
        markDirty();
    }
    
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
        state.put("useDirection", useDirection);
        state.put("includeOriginal", includeOriginal);
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("useDirection")) {
                Object useDirectionObj = stateMap.get("useDirection");
                if (useDirectionObj instanceof Boolean) {
                    setUseDirection((Boolean) useDirectionObj);
                }
            }
            
            if (stateMap.containsKey("includeOriginal")) {
                Object includeOriginalObj = stateMap.get("includeOriginal");
                if (includeOriginalObj instanceof Boolean) {
                    setIncludeOriginal((Boolean) includeOriginalObj);
                }
            }
        }
    }
} 

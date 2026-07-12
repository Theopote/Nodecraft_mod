package com.nodecraft.nodesystem.nodes.pattern.grid;

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
 * Grid Array 节点: 在平面或三维网格上重复Coordinate列表
 */
@NodeInfo(
    id = "pattern.grid.grid_array",
    displayName = "Grid Array",
    description = "在平面或三维网格上重复坐标列表",
    category = "pattern.grid",
    order = 0
)
public class GridArrayNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "在平面或三维网格上重复坐标列表";
    public enum GridType {
        GRID_2D,  // 2D平面网格
        GRID_3D   // 3D空间网格
    }
    
    private GridType gridType = GridType.GRID_2D; // 默认为2D网格
    private boolean includeOriginal = true; // 默认包含原始坐标

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_X_DIRECTION_ID = "input_x_direction";
    private static final String INPUT_X_DISTANCE_ID = "input_x_distance";
    private static final String INPUT_X_COUNT_ID = "input_x_count";
    private static final String INPUT_Y_DIRECTION_ID = "input_y_direction";
    private static final String INPUT_Y_DISTANCE_ID = "input_y_distance";
    private static final String INPUT_Y_COUNT_ID = "input_y_count";
    private static final String INPUT_Z_DIRECTION_ID = "input_z_direction";
    private static final String INPUT_Z_DISTANCE_ID = "input_z_distance";
    private static final String INPUT_Z_COUNT_ID = "input_z_count";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_GRID_COORDINATES_ID = "output_grid_coordinates";
    private static final String OUTPUT_GRID_TREE_ID = "output_grid_tree";

    // --- 构造函数 ---
    public GridArrayNode() {
        super(UUID.randomUUID(), "pattern.grid.grid_array");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", 
                "The coordinates to repeat in a grid", NodeDataType.BLOCK_LIST, this));
        
        // X轴参数
        addInputPort(new BasePort(INPUT_X_DIRECTION_ID, "X Direction", 
                "Direction vector for X axis of the grid", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_X_DISTANCE_ID, "X Distance", 
                "Distance between instances along X axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_X_COUNT_ID, "X Count", 
                "Number of repetitions along X axis", NodeDataType.INTEGER, this));
        
        // Y轴参数
        addInputPort(new BasePort(INPUT_Y_DIRECTION_ID, "Y Direction", 
                "Direction vector for Y axis of the grid", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Y_DISTANCE_ID, "Y Distance", 
                "Distance between instances along Y axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Y_COUNT_ID, "Y Count", 
                "Number of repetitions along Y axis", NodeDataType.INTEGER, this));
        
        // Z轴参数 (仅用于3D网格)
        addInputPort(new BasePort(INPUT_Z_DIRECTION_ID, "Z Direction", 
                "Direction vector for Z axis (3D grid only)", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Z_DISTANCE_ID, "Z Distance", 
                "Distance between instances along Z axis (3D grid only)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Z_COUNT_ID, "Z Count", 
                "Number of repetitions along Z axis (3D grid only)", NodeDataType.INTEGER, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_GRID_COORDINATES_ID, "Grid Coordinates", 
                "The resulting grid array of coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_GRID_TREE_ID, "Grid Tree",
                "One branch per grid cell using {x;y} or {x;y;z} paths", NodeDataType.DATA_TREE, this));
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
        
        // 默认空的坐标列表
        BlockPosList result = new BlockPosList();
        List<DataTreeData.Branch> branches = new ArrayList<>();
        
        // 检查输入是否为方块坐标列表
        if (coordinatesObj instanceof BlockPosList) {
            BlockPosList coordinates = (BlockPosList) coordinatesObj;
            
            // 如果输入坐标列表为空，直接返回空结果
            if (coordinates.isEmpty()) {
                outputValues.put(OUTPUT_GRID_COORDINATES_ID, result);
                outputValues.put(OUTPUT_GRID_TREE_ID, DataTreeData.empty());
                return;
            }
            
            // 获取X轴参数
            Vector3d xDirection = getVectorInput(INPUT_X_DIRECTION_ID, new Vector3d(1, 0, 0));
            double xDistance = getDoubleInput(INPUT_X_DISTANCE_ID, 1.0);
            int xCount = getIntInput(INPUT_X_COUNT_ID, 1);
            
            // 获取Y轴参数
            Vector3d yDirection = getVectorInput(INPUT_Y_DIRECTION_ID, new Vector3d(0, 1, 0));
            double yDistance = getDoubleInput(INPUT_Y_DISTANCE_ID, 1.0);
            int yCount = getIntInput(INPUT_Y_COUNT_ID, 1);
            
            // 对于3D网格，获取Z轴参数
            Vector3d zDirection = getVectorInput(INPUT_Z_DIRECTION_ID, new Vector3d(0, 0, 1));
            double zDistance = getDoubleInput(INPUT_Z_DISTANCE_ID, 1.0);
            int zCount = getIntInput(INPUT_Z_COUNT_ID, 1);
            
            // 确保参数合法
            xDirection = validateVector(xDirection);
            yDirection = validateVector(yDirection);
            zDirection = validateVector(zDirection);
            
            xDistance = Math.max(0.1, xDistance);
            yDistance = Math.max(0.1, yDistance);
            zDistance = Math.max(0.1, zDistance);

            GenerationLimits.GridAxisCounts gridCounts = GenerationLimits.clampGridCounts(
                xCount,
                yCount,
                gridType == GridType.GRID_3D ? zCount : 0,
                coordinates.size()
            );
            xCount = gridCounts.xCount();
            yCount = gridCounts.yCount();
            zCount = gridCounts.zCount();
            
            // 创建网格阵列
            if (gridType == GridType.GRID_3D) {
                // 3D网格
                create3DGridArray(coordinates, 
                                 xDirection, xDistance, xCount,
                                 yDirection, yDistance, yCount,
                                 zDirection, zDistance, zCount,
                                 includeOriginal, result, branches);
            } else {
                // 2D网格
                create2DGridArray(coordinates, 
                                 xDirection, xDistance, xCount,
                                 yDirection, yDistance, yCount,
                                 includeOriginal, result, branches);
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_GRID_COORDINATES_ID, result);
        outputValues.put(OUTPUT_GRID_TREE_ID, new DataTreeData(branches));
    }
    
    /**
     * 创建2D网格阵列
     */
    private void create2DGridArray(BlockPosList sourceCoords,
                                Vector3d xDir, double xDist, int xCount,
                                Vector3d yDir, double yDist, int yCount,
                                boolean includeOriginal, BlockPosList result, List<DataTreeData.Branch> branches) {
        // 创建X和Y方向的位移向量
        Vector3d xDisplacement = new Vector3d(xDir).mul(xDist);
        Vector3d yDisplacement = new Vector3d(yDir).mul(yDist);
        
        // 生成网格
        for (int y = 0; y <= yCount; y++) {
            // 计算Y位移
            Vector3d yOffset = new Vector3d(yDisplacement).mul(y);
            
            for (int x = 0; x <= xCount; x++) {
                // 如果是原点且不包含原始坐标，则跳过
                if (x == 0 && y == 0 && !includeOriginal) {
                    continue;
                }
                List<BlockPos> copyPositions = new ArrayList<>();
                
                // 计算X位移
                Vector3d xOffset = new Vector3d(xDisplacement).mul(x);
                
                // 计算总位移
                Vector3d totalOffset = new Vector3d(xOffset).add(yOffset);
                
                // 对源坐标列表中的每个坐标应用位移
                for (BlockPos pos : sourceCoords) {
                    BlockPos newPos = new BlockPos(
                        (int) Math.round(pos.getX() + totalOffset.x),
                        (int) Math.round(pos.getY() + totalOffset.y),
                        (int) Math.round(pos.getZ() + totalOffset.z)
                    );
                    
                    // 添加到结果列表
                    result.add(newPos);
                    copyPositions.add(newPos);
                }
                addCopyBranch(branches, List.of(x, y), copyPositions);
            }
        }
    }
    
    /**
     * 创建3D网格阵列
     */
    private void create3DGridArray(BlockPosList sourceCoords,
                                Vector3d xDir, double xDist, int xCount,
                                Vector3d yDir, double yDist, int yCount,
                                Vector3d zDir, double zDist, int zCount,
                                boolean includeOriginal, BlockPosList result, List<DataTreeData.Branch> branches) {
        // 创建X、Y和Z方向的位移向量
        Vector3d xDisplacement = new Vector3d(xDir).mul(xDist);
        Vector3d yDisplacement = new Vector3d(yDir).mul(yDist);
        Vector3d zDisplacement = new Vector3d(zDir).mul(zDist);
        
        // 生成3D网格
        for (int z = 0; z <= zCount; z++) {
            // 计算Z位移
            Vector3d zOffset = new Vector3d(zDisplacement).mul(z);
            
            for (int y = 0; y <= yCount; y++) {
                // 计算Y位移
                Vector3d yOffset = new Vector3d(yDisplacement).mul(y);
                
                for (int x = 0; x <= xCount; x++) {
                    // 如果是原点且不包含原始坐标，则跳过
                    if (x == 0 && y == 0 && z == 0 && !includeOriginal) {
                        continue;
                    }
                    List<BlockPos> copyPositions = new ArrayList<>();
                    
                    // 计算X位移
                    Vector3d xOffset = new Vector3d(xDisplacement).mul(x);
                    
                    // 计算总位移
                    Vector3d totalOffset = new Vector3d(xOffset).add(yOffset).add(zOffset);
                    
                    // 对源坐标列表中的每个坐标应用位移
                    for (BlockPos pos : sourceCoords) {
                        BlockPos newPos = new BlockPos(
                            (int) Math.round(pos.getX() + totalOffset.x),
                            (int) Math.round(pos.getY() + totalOffset.y),
                            (int) Math.round(pos.getZ() + totalOffset.z)
                        );
                        
                        // 添加到结果列表
                        result.add(newPos);
                        copyPositions.add(newPos);
                    }
                    addCopyBranch(branches, List.of(x, y, z), copyPositions);
                }
            }
        }
    }

    private void addCopyBranch(List<DataTreeData.Branch> branches, List<Integer> path, List<BlockPos> positions) {
        branches.add(new DataTreeData.Branch(path, new ArrayList<>(positions)));
    }
    
    /**
     * 获取向量输入
     */
    private Vector3d getVectorInput(String portId, Vector3d defaultValue) {
        Object inputObj = inputValues.get(portId);
        if (inputObj instanceof Vector3d) {
            return (Vector3d) inputObj;
        }
        return defaultValue;
    }
    
    /**
     * 获取双精度浮点数输入
     */
    private double getDoubleInput(String portId, double defaultValue) {
        Object inputObj = inputValues.get(portId);
        if (inputObj instanceof Number) {
            return ((Number) inputObj).doubleValue();
        }
        return defaultValue;
    }
    
    /**
     * 获取整数输入
     */
    private int getIntInput(String portId, int defaultValue) {
        Object inputObj = inputValues.get(portId);
        if (inputObj instanceof Number) {
            return ((Number) inputObj).intValue();
        }
        return defaultValue;
    }
    
    /**
     * 验证向量，确保不是零向量
     */
    private Vector3d validateVector(Vector3d vector) {
        if (vector.length() < 0.0001) {
            // 如果是零向量，返回默认向量
            switch (vector.hashCode() % 3) {
                case 0:
                    return new Vector3d(1, 0, 0);
                case 1:
                    return new Vector3d(0, 1, 0);
                default:
                    return new Vector3d(0, 0, 1);
            }
        }
        // 标准化向量
        return vector.normalize();
    }
    
    // --- Getters/Setters for Properties ---
    
    public GridType getGridType() {
        return gridType;
    }
    
    public void setGridType(GridType gridType) {
        this.gridType = gridType;
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
        state.put("gridType", gridType.name());
        state.put("includeOriginal", includeOriginal);
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("gridType")) {
                Object gridTypeObj = stateMap.get("gridType");
                if (gridTypeObj instanceof String) {
                    try {
                        setGridType(GridType.valueOf((String) gridTypeObj));
                    } catch (IllegalArgumentException e) {
                        // 忽略无效的枚举值
                    }
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

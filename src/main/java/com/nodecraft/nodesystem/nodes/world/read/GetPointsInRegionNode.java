package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Get Points in Region 节点: 从一个 Region 获取其包含的所有 Coordinate
 */
@NodeInfo(
    id = "world.read.get_points_in_region",
    displayName = "获取区域内点",
    description = "获取区域内的所有坐标点",
    category = "world.read",
    order = 5
)
public class GetPointsInRegionNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "获取区域内的所有坐标点";
    private boolean filterFromCoordinates = false; // 默认不从坐标列表筛选

    // --- 输入端口 IDs ---
    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_MAX_POINTS_ID = "input_max_points";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";

    // --- 构造函数 ---
    public GetPointsInRegionNode() {
        super(UUID.randomUUID(), "world.read.get_points_in_region");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_REGION_ID, "Region", 
                "要获取点的区域", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", 
                "（可选）用于筛选的坐标列表", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_MAX_POINTS_ID, "Max Points", 
                "最大返回点数（0表示无限制）", NodeDataType.INTEGER, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", 
                "区域内的坐标点", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", 
                "坐标点数量", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object maxPointsObj = inputValues.get(INPUT_MAX_POINTS_ID);
        
        // 默认空的坐标列表
        BlockPosList result = new BlockPosList();
        
        // 检查区域输入是否合法
        if (regionObj instanceof RegionData) {
            RegionData region = (RegionData) regionObj;
            
            // 确保区域完整
            if (region.isComplete()) {
                // 获取最大点数（0表示无限制）
                int maxPoints = (maxPointsObj instanceof Number) 
                    ? ((Number) maxPointsObj).intValue() 
                    : 0;
                maxPoints = Math.max(0, maxPoints); // 确保非负
                
                if (filterFromCoordinates && coordinatesObj instanceof BlockPosList) {
                    // 从坐标列表中筛选区域内的点
                    BlockPosList coordinates = (BlockPosList) coordinatesObj;
                    filterPointsInRegion(coordinates, region, maxPoints, result);
                } else {
                    // 获取区域内的所有点
                    generatePointsInRegion(region, maxPoints, result);
                }
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_POINTS_ID, result);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
    }
    
    /**
     * 从坐标列表中筛选出区域内的点
     * @param coordinates 坐标列表
     * @param region 区域
     * @param maxPoints 最大点数（0表示无限制）
     * @param result 结果列表
     */
    private void filterPointsInRegion(BlockPosList coordinates, RegionData region, 
                                     int maxPoints, BlockPosList result) {
        // 获取区域的最小最大坐标
        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        
        // 遍历坐标列表
        for (BlockPos pos : coordinates) {
            // 检查点是否在区域内
            if (isPointInRegion(pos, minCorner, maxCorner)) {
                // 添加到结果列表
                result.add(pos);
                
                // 如果达到最大点数，停止添加
                if (maxPoints > 0 && result.size() >= maxPoints) {
                    break;
                }
            }
        }
    }
    
    /**
     * 生成区域内的所有点
     * @param region 区域
     * @param maxPoints 最大点数（0表示无限制）
     * @param result 结果列表
     */
    private void generatePointsInRegion(RegionData region, int maxPoints, BlockPosList result) {
        // 获取区域的最小最大坐标
        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        
        // 计算区域尺寸
        int sizeX = maxCorner.getX() - minCorner.getX() + 1;
        int sizeY = maxCorner.getY() - minCorner.getY() + 1;
        int sizeZ = maxCorner.getZ() - minCorner.getZ() + 1;
        
        // 计算总点数
        long totalPoints = (long) sizeX * sizeY * sizeZ;
        
        // 如果需要限制点数且总点数很大，使用采样策略
        if (maxPoints > 0 && totalPoints > maxPoints && totalPoints > 1000000) {
            // 大区域采样策略
            sampleLargeRegion(minCorner, maxCorner, maxPoints, result);
        } else {
            // 直接遍历所有点
            for (BlockPos pos : region.getAllBlocks()) {
                result.add(pos);
                
                // 如果达到最大点数，停止添加
                if (maxPoints > 0 && result.size() >= maxPoints) {
                    break;
                }
            }
        }
    }
    
    /**
     * 对大区域进行采样
     * @param minCorner 最小角坐标
     * @param maxCorner 最大角坐标
     * @param maxPoints 最大点数
     * @param result 结果列表
     */
    private void sampleLargeRegion(BlockPos minCorner, BlockPos maxCorner, 
                                  int maxPoints, BlockPosList result) {
        // 计算区域尺寸
        int sizeX = maxCorner.getX() - minCorner.getX() + 1;
        int sizeY = maxCorner.getY() - minCorner.getY() + 1;
        int sizeZ = maxCorner.getZ() - minCorner.getZ() + 1;
        
        // 计算总点数
        long totalPoints = (long) sizeX * sizeY * sizeZ;
        
        // 计算采样步长（确保均匀分布）
        double stepFactor = Math.cbrt((double) totalPoints / maxPoints);
        int stepX = Math.max(1, (int) Math.ceil(stepFactor));
        int stepY = Math.max(1, (int) Math.ceil(stepFactor));
        int stepZ = Math.max(1, (int) Math.ceil(stepFactor));
        
        // 采样区域
        for (int x = minCorner.getX(); x <= maxCorner.getX(); x += stepX) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y += stepY) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z += stepZ) {
                    result.add(new BlockPos(x, y, z));
                    
                    // 如果达到最大点数，停止添加
                    if (result.size() >= maxPoints) {
                        return;
                    }
                }
            }
        }
    }
    
    /**
     * 检查点是否在区域内
     * @param pos 需要检查的点
     * @param minCorner 区域最小角坐标
     * @param maxCorner 区域最大角坐标
     * @return 如果点在区域内返回true
     */
    private boolean isPointInRegion(BlockPos pos, BlockPos minCorner, BlockPos maxCorner) {
        return pos.getX() >= minCorner.getX() && pos.getX() <= maxCorner.getX()
            && pos.getY() >= minCorner.getY() && pos.getY() <= maxCorner.getY()
            && pos.getZ() >= minCorner.getZ() && pos.getZ() <= maxCorner.getZ();
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isFilterFromCoordinates() {
        return filterFromCoordinates;
    }
    
    public void setFilterFromCoordinates(boolean filterFromCoordinates) {
        this.filterFromCoordinates = filterFromCoordinates;
        markDirty();
    }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("filterFromCoordinates", filterFromCoordinates);
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("filterFromCoordinates")) {
                Object filterObj = stateMap.get("filterFromCoordinates");
                if (filterObj instanceof Boolean) {
                    setFilterFromCoordinates((Boolean) filterObj);
                }
            }
        }
    }
} 

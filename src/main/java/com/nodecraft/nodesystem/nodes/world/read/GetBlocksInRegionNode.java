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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Get Blocks in Region 节点: 获取区域内所有方块的列表。
 */
@NodeInfo(
    id = "world.read.get_blocks_in_region",
    displayName = "获取区域内方块",
    description = "获取区域内所有方块的列表",
    category = "world.read"
)
public class GetBlocksInRegionNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "获取区域内所有方块的列表";
    private boolean excludeAir = true; // 默认排除空气方块

    // --- 输入端口 IDs ---
    private static final String INPUT_REGION_ID = "input_region";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_BLOCKS_LIST_ID = "output_blocks_list";
    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";
    private static final String OUTPUT_BLOCK_COUNT_ID = "output_block_count";
    private static final String OUTPUT_AIR_COUNT_ID = "output_air_count";
    private static final String OUTPUT_SOLID_COUNT_ID = "output_solid_count";

    // --- 构造函数 ---
    public GetBlocksInRegionNode() {
        super(UUID.randomUUID(), "world.read.get_blocks_in_region");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_REGION_ID, "Region", 
                "要查询的区域", NodeDataType.REGION, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_BLOCKS_LIST_ID, "Blocks List", 
                "区域内的方块列表", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", 
                "方块坐标列表", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_COUNT_ID, "Block Count", 
                "方块总数", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_AIR_COUNT_ID, "Air Count", 
                "空气方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SOLID_COUNT_ID, "Solid Count", 
                "非空气方块数量", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 默认输出值
        List<Object> blocksList = new ArrayList<>();
        BlockPosList coordsList = new BlockPosList();
        int blockCount = 0;
        int airCount = 0;
        int solidCount = 0;
        
        // 获取输入区域
        Object regionObj = inputValues.get(INPUT_REGION_ID);
        
        // 检查执行上下文和区域输入是否有效
        if (context != null && context.getWorld() != null && regionObj instanceof RegionData) {
            RegionData region = (RegionData) regionObj;
            
            // 确保区域完整且有效
            if (region.isComplete()) {
                BlockPos minCorner = region.getMinCorner();
                BlockPos maxCorner = region.getMaxCorner();
                
                // 遍历区域内的所有方块
                for (BlockPos pos : BlockPos.iterate(minCorner, maxCorner)) {
                    BlockPos immutablePos = pos.toImmutable();
                    
                    try {
                        // 获取方块状态
                        Object blockState = context.getWorld().getBlockState(immutablePos);
                        
                        // 检查是否为空气
                        boolean isAir = context.getWorld().isAir(immutablePos);
                        
                        // 根据设置决定是否包括空气方块
                        if (!excludeAir || !isAir) {
                            blocksList.add(blockState);
                            coordsList.add(immutablePos);
                        }
                        
                        // 更新计数
                        blockCount++;
                        if (isAir) {
                            airCount++;
                        } else {
                            solidCount++;
                        }
                    } catch (Exception e) {
                        // 记录错误但继续处理其他方块
                        System.err.println("Error getting block at " + pos + ": " + e.getMessage());
                    }
                }
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_BLOCKS_LIST_ID, blocksList);
        outputValues.put(OUTPUT_COORDINATES_ID, coordsList);
        outputValues.put(OUTPUT_BLOCK_COUNT_ID, blockCount);
        outputValues.put(OUTPUT_AIR_COUNT_ID, airCount);
        outputValues.put(OUTPUT_SOLID_COUNT_ID, solidCount);
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isExcludeAir() {
        return excludeAir;
    }
    
    public void setExcludeAir(boolean excludeAir) {
        this.excludeAir = excludeAir;
        markDirty();
    }
} 

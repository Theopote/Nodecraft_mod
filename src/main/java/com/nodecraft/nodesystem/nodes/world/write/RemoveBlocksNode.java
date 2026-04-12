package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Remove Blocks 节点: 将坐标列表中的方块设置为空气方块
 */
@NodeInfo(
    id = "world.write.clear_region",
    displayName = "移除方块",
    description = "移除指定坐标的方块",
    category = "world.write",
    order = 5
)
public class RemoveBlocksNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "将坐标列表中的方块设置为空气方块";
    private boolean notifyUpdate = true; // 是否通知更新（触发更新事件）
    private boolean spawnDrops = true; // 移除方块时是否生成掉落物
    private boolean batchUpdates = true; // 是否批量更新，提高性能
    private int maxBlocks = 32768; // 最大操作方块数（防止过大区域导致性能问题）

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_NOTIFY_ID = "input_notify";
    private static final String INPUT_SPAWN_DROPS_ID = "input_spawn_drops";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_REMOVED_BLOCKS_ID = "output_removed_blocks";
    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_PREVIOUS_BLOCKS_ID = "output_previous_blocks";

    // --- 构造函数 ---
    public RemoveBlocksNode() {
        super(UUID.randomUUID(), "world.write.clear_region");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", 
                "要移除方块的坐标列表", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", 
                "是否通知更新（触发更新事件）", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", 
                "移除方块时是否生成掉落物", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", 
                "最大操作方块数", NodeDataType.INTEGER, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_REMOVED_BLOCKS_ID, "Removed Blocks", 
                "成功移除的方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", 
                "成功操作的方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", 
                "尝试操作的方块总数", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PREVIOUS_BLOCKS_ID, "Previous Blocks", 
                "被移除的方块信息列表", NodeDataType.BLOCK_INFO_LIST, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 默认输出值
        int removedBlocks = 0;
        int successCount = 0;
        int totalCount = 0;
        Object previousBlocks = new java.util.ArrayList<>(); // 用于存储被移除的方块信息
        
        // 获取输入值
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        
        // 获取布尔值参数
        boolean notifyUpdateValue = this.notifyUpdate;
        Object notifyUpdateObj = inputValues.get(INPUT_NOTIFY_ID);
        if (notifyUpdateObj instanceof Boolean) {
            notifyUpdateValue = (Boolean) notifyUpdateObj;
        }
        
        boolean spawnDropsValue = this.spawnDrops;
        Object spawnDropsObj = inputValues.get(INPUT_SPAWN_DROPS_ID);
        if (spawnDropsObj instanceof Boolean) {
            spawnDropsValue = (Boolean) spawnDropsObj;
        }
        
        // 获取最大方块数
        int maxBlocksValue = this.maxBlocks;
        Object maxBlocksObj = inputValues.get(INPUT_MAX_BLOCKS_ID);
        if (maxBlocksObj instanceof Number) {
            maxBlocksValue = ((Number) maxBlocksObj).intValue();
            if (maxBlocksValue <= 0) {
                maxBlocksValue = Integer.MAX_VALUE;
            }
        }
        
        // 检查执行上下文和坐标列表输入是否有效
        if (context != null && context.getWorld() != null && 
                coordinatesObj instanceof BlockPosList) {
            BlockPosList coordinates = (BlockPosList) coordinatesObj;
            
            // 检查坐标数量是否超过最大方块数
            if (coordinates.size() > maxBlocksValue) {
                System.err.println("Coordinate list size (" + coordinates.size() + ") exceeds max blocks limit (" + maxBlocksValue + ").");
                
                // 设置输出值并返回
                outputValues.put(OUTPUT_REMOVED_BLOCKS_ID, removedBlocks);
                outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
                outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
                outputValues.put(OUTPUT_PREVIOUS_BLOCKS_ID, previousBlocks);
                return;
            }
            
            // 创建存储被移除方块信息的列表
            java.util.List<Object> previousBlocksList = new java.util.ArrayList<>();
            
            // 在实际实现中，开始批量更新（如果启用）
            if (batchUpdates) {
                // 开始批量更新，例如 context.getWorld().beginBatchBlockUpdate();
            }
            
            try {
                // 获取空气方块状态
                BlockState airState = Blocks.AIR.getDefaultState();
                
                // 遍历所有坐标
                for (BlockPos pos : coordinates) {
                    totalCount++;
                    
                    try {
                        // 获取当前方块状态（用于返回被替换的方块）
                        BlockState currentBlockState = context.getWorld().getBlockState(pos);
                        previousBlocksList.add(currentBlockState);
                        
                        // 检查当前位置是否已经是空气
                        boolean isAir = currentBlockState.isAir();
                        
                        // 如果不是空气，进行移除
                        if (!isAir) {
                            if (spawnDropsValue) {
                                // 先破坏方块产生掉落物，再设置为空气
                                context.getWorld().breakBlock(pos, true);
                                successCount++;
                                removedBlocks++;
                            } else {
                                int flags = Block.NOTIFY_ALL;
                                boolean result = context.getWorld().setBlockState(pos, airState, flags);
                                if (result) {
                                    successCount++;
                                    removedBlocks++;
                                }
                            }
                        } else {
                            // 已经是空气，考虑为操作成功
                            successCount++;
                        }
                    } catch (Exception e) {
                        // 记录单个方块移除错误但继续执行
                        System.err.println("Error removing block at " + pos + ": " + e.getMessage());
                    }
                }
                
                // 设置被移除的方块信息列表
                previousBlocks = previousBlocksList;
                
            } finally {
                // 完成批量更新（如果启用）
                if (batchUpdates) {
                    // 例如: context.getWorld().endBatchBlockUpdate();
                }
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_REMOVED_BLOCKS_ID, removedBlocks);
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_PREVIOUS_BLOCKS_ID, previousBlocks);
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isNotifyUpdate() {
        return notifyUpdate;
    }
    
    public void setNotifyUpdate(boolean notifyUpdate) {
        this.notifyUpdate = notifyUpdate;
        markDirty();
    }
    
    public boolean isSpawnDrops() {
        return spawnDrops;
    }
    
    public void setSpawnDrops(boolean spawnDrops) {
        this.spawnDrops = spawnDrops;
        markDirty();
    }
    
    public boolean isBatchUpdates() {
        return batchUpdates;
    }
    
    public void setBatchUpdates(boolean batchUpdates) {
        this.batchUpdates = batchUpdates;
        markDirty();
    }
    
    public int getMaxBlocks() {
        return maxBlocks;
    }
    
    public void setMaxBlocks(int maxBlocks) {
        if (maxBlocks > 0) {
            this.maxBlocks = maxBlocks;
            markDirty();
        }
    }
} 

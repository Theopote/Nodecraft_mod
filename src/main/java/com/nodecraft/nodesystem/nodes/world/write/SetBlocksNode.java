package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Set Blocks 节点: 在坐标列表上批量放置方块信息(可输入方块信息列表进行一对一或列表循环)
 */
@NodeInfo(
    id = "world.write.set_blocks",
    displayName = "设置方块",
    description = "在坐标列表上批量放置方块",
    category = "world.write"
)
public class SetBlocksNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "在坐标列表上批量放置方块信息(可输入方块信息列表进行一对一或列表循环)";
    private boolean notifyUpdate = true; // 是否通知更新（触发更新事件）
    private boolean spawnDrops = false; // 放置方块时是否生成掉落物
    private boolean batchUpdates = true; // 是否批量更新，提高性能

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_BLOCK_INFO_ID = "input_block_info";
    private static final String INPUT_BLOCK_INFO_LIST_ID = "input_block_info_list";
    private static final String INPUT_NOTIFY_ID = "input_notify";
    private static final String INPUT_SPAWN_DROPS_ID = "input_spawn_drops";
    private static final String INPUT_BATCH_UPDATES_ID = "input_batch_updates";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_ALL_SUCCESS_ID = "output_all_success";

    // --- 构造函数 ---
    public SetBlocksNode() {
        super(UUID.randomUUID(), "world.write.set_blocks");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", 
                "目标坐标列表", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_ID, "Block Info", 
                "要放置的方块信息", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_LIST_ID, "Block Info List", 
                "要放置的方块信息列表", NodeDataType.BLOCK_INFO_LIST, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", 
                "是否通知更新（触发更新事件）", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", 
                "放置方块时是否生成掉落物", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_BATCH_UPDATES_ID, "Batch Updates", 
                "是否批量更新（提高性能）", NodeDataType.BOOLEAN, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", 
                "成功放置的方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", 
                "尝试放置的方块总数", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ALL_SUCCESS_ID, "All Success", 
                "是否所有方块都成功放置", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        int successCount = 0;
        int totalCount = 0;
        boolean allSuccess = true;
        
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object blockInfoObj = inputValues.get(INPUT_BLOCK_INFO_ID);
        Object blockInfoListObj = inputValues.get(INPUT_BLOCK_INFO_LIST_ID);
        
        boolean notifyUpdateValue = this.notifyUpdate;
        Object notifyUpdateObj = inputValues.get(INPUT_NOTIFY_ID);
        if (notifyUpdateObj instanceof Boolean) { notifyUpdateValue = (Boolean) notifyUpdateObj; }
        
        boolean spawnDropsValue = this.spawnDrops;
        Object spawnDropsObj = inputValues.get(INPUT_SPAWN_DROPS_ID);
        if (spawnDropsObj instanceof Boolean) { spawnDropsValue = (Boolean) spawnDropsObj; }
        
        if (context != null && context.getWorld() != null && 
                coordinatesObj instanceof BlockPosList) {
            BlockPosList coordinates = (BlockPosList) coordinatesObj;
            
            List<?> blockInfoList = null;
            if (blockInfoListObj instanceof List) { blockInfoList = (List<?>) blockInfoListObj; }
            
            int flags = notifyUpdateValue ? Block.NOTIFY_ALL : Block.FORCE_STATE;
            
            // 单一方块信息模式
            if ((blockInfoList == null || blockInfoList.isEmpty()) && blockInfoObj != null) {
                BlockState targetState = resolveBlockState(blockInfoObj);
                if (targetState != null) {
                    for (BlockPos pos : coordinates) {
                        totalCount++;
                        try {
                            if (spawnDropsValue && !context.getWorld().isAir(pos)) {
                                context.getWorld().breakBlock(pos, true);
                            }
                            boolean success = context.getWorld().setBlockState(pos, targetState, flags);
                            if (success) { successCount++; } else { allSuccess = false; }
                        } catch (Exception e) {
                            System.err.println("Error setting block at " + pos + ": " + e.getMessage());
                            allSuccess = false;
                        }
                    }
                }
            } 
            // 方块信息列表模式
            else if (blockInfoList != null && !blockInfoList.isEmpty()) {
                int infoListSize = blockInfoList.size();
                boolean oneToOne = (infoListSize == coordinates.size());
                int i = 0;
                for (BlockPos pos : coordinates) {
                    totalCount++;
                    try {
                        Object currentBlockInfo = oneToOne ? blockInfoList.get(i) : blockInfoList.get(i % infoListSize);
                        i++;
                        BlockState targetState = resolveBlockState(currentBlockInfo);
                        if (targetState != null) {
                            if (spawnDropsValue && !context.getWorld().isAir(pos)) {
                                context.getWorld().breakBlock(pos, true);
                            }
                            boolean success = context.getWorld().setBlockState(pos, targetState, flags);
                            if (success) { successCount++; } else { allSuccess = false; }
                        } else { allSuccess = false; }
                    } catch (Exception e) {
                        System.err.println("Error setting block at " + pos + ": " + e.getMessage());
                        allSuccess = false;
                    }
                }
            }
        }
        
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_ALL_SUCCESS_ID, allSuccess);
    }
    
    /**
     * 将输入的方块信息对象解析为 BlockState。
     * 支持：BlockState 直接传入、String 方块ID（如 "minecraft:stone"）
     */
    private BlockState resolveBlockState(Object blockInfoObj) {
        if (blockInfoObj instanceof BlockState) {
            return (BlockState) blockInfoObj;
        }
        if (blockInfoObj instanceof String blockId) {
            try {
                Identifier id = Identifier.of(blockId);
                Block block = Registries.BLOCK.get(id);
                if (block != null) {
                    return block.getDefaultState();
                }
            } catch (Exception e) {
                System.err.println("Invalid block ID: " + blockId);
            }
        }
        return null;
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
} 

package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Replace Blocks 节点: 在区域或坐标列表中替换指定方块。
 */
@NodeInfo(
    id = "world.write.replace_blocks",
    displayName = "替换方块",
    description = "在区域或坐标列表中替换指定方块",
    category = "world.write"
)
public class ReplaceBlocksNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "在区域或坐标列表中替换指定方块为新的方块";
    private boolean notifyUpdate = true; // 是否通知更新（触发更新事件）
    private boolean spawnDrops = false; // 放置方块时是否生成掉落物
    private boolean batchUpdates = true; // 是否批量更新，提高性能
    private boolean exactMatch = false; // 是否要求方块状态完全匹配
    private int maxBlocks = 32768; // 最大操作方块数（防止过大区域导致性能问题）

    // --- 输入端口 IDs ---
    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_TARGET_BLOCK_ID = "input_target_block";
    private static final String INPUT_REPLACEMENT_BLOCK_ID = "input_replacement_block";
    private static final String INPUT_EXACT_MATCH_ID = "input_exact_match";
    private static final String INPUT_NOTIFY_ID = "input_notify";
    private static final String INPUT_SPAWN_DROPS_ID = "input_spawn_drops";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_REPLACED_BLOCKS_ID = "output_replaced_blocks";
    private static final String OUTPUT_CHECKED_BLOCKS_ID = "output_checked_blocks";
    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_AFFECTED_COORDINATES_ID = "output_affected_coordinates";

    // --- 构造函数 ---
    public ReplaceBlocksNode() {
        super(UUID.randomUUID(), "world.write.replace_blocks");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_REGION_ID, "Region", 
                "要处理的区域", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", 
                "要处理的坐标列表", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_TARGET_BLOCK_ID, "Target Block", 
                "要替换的方块信息", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_REPLACEMENT_BLOCK_ID, "Replacement Block", 
                "替换成的方块信息", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_EXACT_MATCH_ID, "Exact Match", 
                "是否要求方块状态完全匹配", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", 
                "是否通知更新（触发更新事件）", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", 
                "放置方块时是否生成掉落物", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", 
                "最大操作方块数", NodeDataType.INTEGER, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_REPLACED_BLOCKS_ID, "Replaced Blocks", 
                "替换的方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_CHECKED_BLOCKS_ID, "Checked Blocks", 
                "检查的方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", 
                "成功放置的方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", 
                "尝试放置的方块总数", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_AFFECTED_COORDINATES_ID, "Affected Coordinates", 
                "受影响的坐标列表", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 默认输出值
        int replacedBlocks = 0;
        int checkedBlocks = 0;
        int successCount = 0;
        int totalCount = 0;
        BlockPosList affectedCoordinates = new BlockPosList();
        
        // 获取输入值
        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object targetBlockObj = inputValues.get(INPUT_TARGET_BLOCK_ID);
        Object replacementBlockObj = inputValues.get(INPUT_REPLACEMENT_BLOCK_ID);
        
        // 获取布尔值参数
        boolean exactMatchValue = this.exactMatch;
        Object exactMatchObj = inputValues.get(INPUT_EXACT_MATCH_ID);
        if (exactMatchObj instanceof Boolean) {
            exactMatchValue = (Boolean) exactMatchObj;
        }
        
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
        
        // 检查执行上下文和必要输入是否有效
        if (context != null && context.getWorld() != null && 
                targetBlockObj != null && replacementBlockObj != null) {
            
            // 创建要处理的坐标列表
            BlockPosList positionsToProcess = new BlockPosList();
            
            // 从区域获取坐标
            if (regionObj instanceof RegionData region) {

                // 确保区域完整且有效
                if (region.isComplete()) {
                    BlockPos minCorner = region.getMinCorner();
                    BlockPos maxCorner = region.getMaxCorner();
                    
                    // 计算区域体积
                    int volume = getVolume(maxCorner, minCorner);

                    // 检查体积是否超过最大方块数
                    if (volume > maxBlocksValue) {
                        System.err.println("Region volume (" + volume + ") exceeds max blocks limit (" + maxBlocksValue + ").");
                        
                        // 设置输出值并返回
                        outputValues.put(OUTPUT_REPLACED_BLOCKS_ID, replacedBlocks);
                        outputValues.put(OUTPUT_CHECKED_BLOCKS_ID, checkedBlocks);
                        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
                        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
                        outputValues.put(OUTPUT_AFFECTED_COORDINATES_ID, affectedCoordinates);
                        return;
                    }
                    
                    // 遍历区域内的所有方块
                    if (minCorner != null) {
                        if (maxCorner != null) {
                            for (BlockPos pos : BlockPos.iterate(minCorner, maxCorner)) {
                                positionsToProcess.add(pos.toImmutable());
                            }
                        }
                    }
                }
            }
            
            // 从坐标列表获取坐标
            if (coordinatesObj instanceof BlockPosList coordinates) {

                // 检查坐标数量是否超过最大方块数
                if (coordinates.size() > maxBlocksValue) {
                    System.err.println("Coordinate list size (" + coordinates.size() + ") exceeds max blocks limit (" + maxBlocksValue + ").");
                    
                    // 设置输出值并返回
                    outputValues.put(OUTPUT_REPLACED_BLOCKS_ID, replacedBlocks);
                    outputValues.put(OUTPUT_CHECKED_BLOCKS_ID, checkedBlocks);
                    outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
                    outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
                    outputValues.put(OUTPUT_AFFECTED_COORDINATES_ID, affectedCoordinates);
                    return;
                }
                
                // 如果之前没有从区域添加坐标，使用输入的坐标列表
                if (positionsToProcess.isEmpty()) {
                    positionsToProcess = coordinates;
                }
                // 否则添加额外的坐标（可能会有重复，但不影响功能）
                else {
                    for (BlockPos pos : coordinates) {
                        positionsToProcess.add(pos);
                    }
                }
            }
            
            // 如果有坐标需要处理
            if (!positionsToProcess.isEmpty()) {
                // 在实际实现中，开始批量更新（如果启用）
                if (batchUpdates) {
                    // 开始批量更新，例如 context.getWorld().beginBatchBlockUpdate();
                }
                
                try {
                    // 遍历所有坐标
                    for (BlockPos pos : positionsToProcess) {
                        checkedBlocks++;
                        totalCount++;
                        
                        try {
                            // 获取当前方块状态
                            BlockState currentState = context.getWorld().getBlockState(pos);
                            
                            // 解析目标方块状态
                            BlockState targetState = resolveBlockState(targetBlockObj);
                            BlockState replacementState = resolveBlockState(replacementBlockObj);
                            
                            if (targetState == null || replacementState == null) {
                                continue;
                            }
                            
                            // 判断当前方块是否符合目标方块（需要替换的方块）
                            boolean shouldReplace;
                            
                            if (exactMatchValue) {
                                // 精确匹配（方块类型和状态都必须匹配）
                                shouldReplace = currentState.equals(targetState);
                            } else {
                                // 只匹配方块类型，不考虑状态
                                shouldReplace = currentState.getBlock() == targetState.getBlock();
                            }
                            
                            // 如果符合替换条件
                            if (shouldReplace) {
                                int flags = Block.NOTIFY_ALL;
                                if (spawnDropsValue) {
                                    context.getWorld().breakBlock(pos, true);
                                }
                                boolean success = context.getWorld().setBlockState(pos, replacementState, flags);
                                
                                if (success) {
                                    successCount++;
                                    replacedBlocks++;
                                    affectedCoordinates.add(pos);
                                }
                            }
                        } catch (Exception e) {
                            // 记录单个方块替换错误但继续执行
                            System.err.println("Error replacing block at " + pos + ": " + e.getMessage());
                        }
                    }
                } finally {
                    // 完成批量更新（如果启用）
                    if (batchUpdates) {
                        // 例如: context.getWorld().endBatchBlockUpdate();
                    }
                }
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_REPLACED_BLOCKS_ID, replacedBlocks);
        outputValues.put(OUTPUT_CHECKED_BLOCKS_ID, checkedBlocks);
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_AFFECTED_COORDINATES_ID, affectedCoordinates);
    }

    private static int getVolume(BlockPos maxCorner, BlockPos minCorner) {
        int width = 0;
        if (maxCorner != null) {
            if (minCorner != null) {
                width = maxCorner.getX() - minCorner.getX() + 1;
            }
        }
        int height = 0;
        if (maxCorner != null) {
            if (minCorner != null) {
                height = maxCorner.getY() - minCorner.getY() + 1;
            }
        }
        int depth = 0;
        if (maxCorner != null) {
            if (minCorner != null) {
                depth = maxCorner.getZ() - minCorner.getZ() + 1;
            }
        }
        return width * height * depth;
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
    
    public boolean isExactMatch() {
        return exactMatch;
    }
    
    public void setExactMatch(boolean exactMatch) {
        this.exactMatch = exactMatch;
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
    
    /**
     * 将输入的方块信息对象解析为 BlockState。
     */
    private BlockState resolveBlockState(Object blockInfoObj) {
        if (blockInfoObj instanceof BlockState) {
            return (BlockState) blockInfoObj;
        }
        if (blockInfoObj instanceof String blockId) {
            try {
                Identifier id = Identifier.of(blockId);
                Block block = Registries.BLOCK.get(id);
                return block.getDefaultState();
            } catch (Exception e) {
                System.err.println("Invalid block ID: " + blockId);
            }
        }
        return null;
    }
}

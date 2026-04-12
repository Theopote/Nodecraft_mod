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
 * Fill Region 节点: 用指定方块填充区域。
 */
@NodeInfo(
    id = "world.write.fill_region",
    displayName = "填充区域",
    description = "用指定方块填充区域",
    category = "world.write",
    order = 2
)
public class FillRegionNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "用指定方块信息填充区域（类似 /fill 命令）";
    private boolean notifyUpdate = true; // 是否通知更新（触发更新事件）
    private boolean spawnDrops = false; // 放置方块时是否生成掉落物
    private boolean batchUpdates = true; // 是否批量更新，提高性能
    private boolean excludeAir = false; // 是否排除已存在的空气方块（只替换非空气方块）
    private boolean hollow = false; // 是否创建中空结构（仅边界有方块）
    private int maxBlocks = 32768; // 最大操作方块数（防止过大区域导致性能问题）

    // --- 输入端口 IDs ---
    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_BLOCK_INFO_ID = "input_block_info";
    private static final String INPUT_HOLLOW_ID = "input_hollow";
    private static final String INPUT_EXCLUDE_AIR_ID = "input_exclude_air";
    private static final String INPUT_NOTIFY_ID = "input_notify";
    private static final String INPUT_SPAWN_DROPS_ID = "input_spawn_drops";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_FILLED_BLOCKS_ID = "output_filled_blocks";
    private static final String OUTPUT_AFFECTED_BLOCKS_ID = "output_affected_blocks";
    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";

    // --- 构造函数 ---
    public FillRegionNode() {
        super(UUID.randomUUID(), "world.write.fill_region");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_REGION_ID, "Region", 
                "要填充的区域", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_ID, "Block Info", 
                "填充的方块信息", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_HOLLOW_ID, "Hollow", 
                "是否创建中空结构", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_EXCLUDE_AIR_ID, "Exclude Air", 
                "是否排除已存在的空气方块", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", 
                "是否通知更新（触发更新事件）", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", 
                "放置方块时是否生成掉落物", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", 
                "最大操作方块数", NodeDataType.INTEGER, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_FILLED_BLOCKS_ID, "Filled Blocks", 
                "填充的方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_AFFECTED_BLOCKS_ID, "Affected Blocks", 
                "受影响的方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", 
                "成功放置的方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", 
                "尝试放置的方块总数", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", 
                "被填充的坐标列表", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 默认输出值
        int filledBlocks = 0;
        int affectedBlocks = 0;
        int successCount = 0;
        int totalCount = 0;
        BlockPosList coordinates = new BlockPosList();
        
        // 获取输入值
        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Object blockInfoObj = inputValues.get(INPUT_BLOCK_INFO_ID);
        
        // 获取布尔值参数
        boolean hollowValue = this.hollow;
        Object hollowObj = inputValues.get(INPUT_HOLLOW_ID);
        if (hollowObj instanceof Boolean) {
            hollowValue = (Boolean) hollowObj;
        }
        
        boolean excludeAirValue = this.excludeAir;
        Object excludeAirObj = inputValues.get(INPUT_EXCLUDE_AIR_ID);
        if (excludeAirObj instanceof Boolean) {
            excludeAirValue = (Boolean) excludeAirObj;
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
        
        // 检查执行上下文和区域输入是否有效
        if (context != null && context.getWorld() != null && 
                regionObj instanceof RegionData && blockInfoObj != null) {
            RegionData region = (RegionData) regionObj;
            
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
                    outputValues.put(OUTPUT_FILLED_BLOCKS_ID, filledBlocks);
                    outputValues.put(OUTPUT_AFFECTED_BLOCKS_ID, affectedBlocks);
                    outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
                    outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
                    outputValues.put(OUTPUT_COORDINATES_ID, coordinates);
                    return;
                }
                
                // 在实际实现中，开始批量更新（如果启用）
                if (batchUpdates) {
                    // 开始批量更新，例如 context.getWorld().beginBatchBlockUpdate();
                }
                
                try {
                    // 遍历区域内的所有方块
                    if (minCorner != null) {
                        if (maxCorner != null) {
                            for (BlockPos pos : BlockPos.iterate(minCorner, maxCorner)) {
                                totalCount++;
                                affectedBlocks++;

                                // 检查是否为中空结构
                                boolean isShell;
                                if (hollowValue) {
                                    isShell = pos.getX() == minCorner.getX() || pos.getX() == maxCorner.getX() ||
                                            pos.getY() == minCorner.getY() || pos.getY() == maxCorner.getY() ||
                                            pos.getZ() == minCorner.getZ() || pos.getZ() == maxCorner.getZ();

                                    // 如果是中空结构的内部，跳过
                                    if (!isShell) {
                                        continue;
                                    }
                                }

                                try {
                                    BlockPos immutablePos = pos.toImmutable();

                                    // 检查是否排除空气方块
                                    if (excludeAirValue) {
                                        boolean isAir = context.getWorld().isAir(immutablePos);
                                        if (isAir) {
                                            continue; // 跳过空气方块
                                        }
                                    }

                                    // 解析目标方块状态并放置
                                    BlockState targetState = resolveBlockState(blockInfoObj);
                                    if (targetState == null) {
                                        System.err.println("FillRegionNode: Cannot resolve block state from input");
                                        continue;
                                    }

                                    int flags = Block.NOTIFY_ALL;
                                    if (spawnDropsValue) {
                                        context.getWorld().breakBlock(immutablePos, true);
                                    }
                                    boolean success = context.getWorld().setBlockState(immutablePos, targetState, flags);

                                    if (success) {
                                        successCount++;
                                        filledBlocks++;
                                        coordinates.add(immutablePos);
                                    }
                                } catch (Exception e) {
                                    // 记录单个方块放置错误但继续执行
                                    System.err.println("Error filling block at " + pos + ": " + e.getMessage());
                                }
                            }
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
        outputValues.put(OUTPUT_FILLED_BLOCKS_ID, filledBlocks);
        outputValues.put(OUTPUT_AFFECTED_BLOCKS_ID, affectedBlocks);
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_COORDINATES_ID, coordinates);
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
        int volume = width * height * depth;
        return volume;
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
    
    public boolean isExcludeAir() {
        return excludeAir;
    }
    
    public void setExcludeAir(boolean excludeAir) {
        this.excludeAir = excludeAir;
        markDirty();
    }
    
    public boolean isHollow() {
        return hollow;
    }
    
    public void setHollow(boolean hollow) {
        this.hollow = hollow;
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
                return block.getDefaultState();
            } catch (Exception e) {
                System.err.println("Invalid block ID: " + blockId);
            }
        }
        return null;
    }
} 

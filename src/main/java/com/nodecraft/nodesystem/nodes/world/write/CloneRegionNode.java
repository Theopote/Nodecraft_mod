package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Clone Region 节点: 将一个区域的方块克隆到另一个位置
 */
@NodeInfo(
    id = "world.write.clone_region",
    displayName = "Clone Region",
    description = "复制区域到另一个位置",
    category = "world.write",
    order = 4
)
public class CloneRegionNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "将一个区域的方块克隆到另一个位置";
    private boolean notifyUpdate = true; // 是否通知更新（触发更新事件）
    @NodeProperty(
            displayName = "Include Entities",
            category = "Clone",
            order = 3,
            readOnly = true,
            description = "Entity cloning is not implemented yet"
    )
    private boolean includeEntities = false;
    private boolean includeAir = true;
    @NodeProperty(
            displayName = "Batch Updates",
            category = "Performance",
            order = 4,
            readOnly = true,
            description = "World batch-update API is not wired yet"
    )
    private boolean batchUpdates = true;
    private CloneMode cloneMode = CloneMode.NORMAL; // 克隆模式
    private int maxBlocks = 32768; // 最大操作方块数（防止过大区域导致性能问题）
    @NodeProperty(displayName = "Record Undo", category = "Execution", order = 1)
    private boolean recordUndo = true;

    // --- 克隆模式枚举 ---
    public enum CloneMode {
        NORMAL("正常", "原样复制源区域"),
        FORCE("强制", "即使源区域有不可移动方块也强制克隆"),
        MOVE("移动", "克隆后将源区域设置为空气"),
        MASKED("蒙版", "只克隆非空气方块");
        
        private final String displayName;
        private final String description;
        
        CloneMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
    }

    // --- 输入端口 IDs ---
    private static final String INPUT_SOURCE_REGION_ID = "input_source_region";
    private static final String INPUT_DESTINATION_POS_ID = "input_destination_pos";
    private static final String INPUT_INCLUDE_ENTITIES_ID = "input_include_entities";
    private static final String INPUT_INCLUDE_AIR_ID = "input_include_air";
    private static final String INPUT_CLONE_MODE_ID = "input_clone_mode";
    private static final String INPUT_NOTIFY_ID = "input_notify";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_CLONED_BLOCKS_ID = "output_cloned_blocks";
    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_DESTINATION_REGION_ID = "output_destination_region";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    // --- 构造函数 ---
    public CloneRegionNode() {
        super(UUID.randomUUID(), "world.write.clone_region");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_SOURCE_REGION_ID, "Source Region", 
                "要克隆的源区域", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_DESTINATION_POS_ID, "Destination Position", 
                "目标位置（左下角坐标）", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_INCLUDE_ENTITIES_ID, "Include Entities", 
                "是否包括实体", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_INCLUDE_AIR_ID, "Include Air", 
                "是否克隆空气方块", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_CLONE_MODE_ID, "Clone Mode", 
                "克隆模式 (0=正常, 1=强制, 2=移动, 3=蒙版)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", 
                "是否通知更新（触发更新事件）", NodeDataType.BOOLEAN, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_CLONED_BLOCKS_ID, "Cloned Blocks", 
                "克隆的方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", 
                "成功放置的方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", 
                "尝试放置的方块总数", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_DESTINATION_REGION_ID, "Destination Region", 
                "目标区域", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", 
                "是否操作成功", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", 
                "Why clone did not run or the first failure reason", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 默认输出值
        int clonedBlocks = 0;
        int successCount = 0;
        int totalCount = 0;
        RegionData destinationRegion = null;
        boolean success = false;
        String error = "";
        
        // 获取输入值
        Object sourceRegionObj = inputValues.get(INPUT_SOURCE_REGION_ID);
        Object destinationPosObj = inputValues.get(INPUT_DESTINATION_POS_ID);
        
        // 获取布尔值参数
        boolean includeEntitiesValue = this.includeEntities;
        Object includeEntitiesObj = inputValues.get(INPUT_INCLUDE_ENTITIES_ID);
        if (includeEntitiesObj instanceof Boolean) {
            includeEntitiesValue = (Boolean) includeEntitiesObj;
        }
        
        boolean includeAirValue = this.includeAir;
        Object includeAirObj = inputValues.get(INPUT_INCLUDE_AIR_ID);
        if (includeAirObj instanceof Boolean) {
            includeAirValue = (Boolean) includeAirObj;
        }
        
        boolean notifyUpdateValue = this.notifyUpdate;
        Object notifyUpdateObj = inputValues.get(INPUT_NOTIFY_ID);
        if (notifyUpdateObj instanceof Boolean) {
            notifyUpdateValue = (Boolean) notifyUpdateObj;
        }
        
        // 获取克隆模式
        CloneMode cloneModeValue = this.cloneMode;
        Object cloneModeObj = inputValues.get(INPUT_CLONE_MODE_ID);
        if (cloneModeObj instanceof Number) {
            int modeIndex = ((Number) cloneModeObj).intValue();
            if (modeIndex >= 0 && modeIndex < CloneMode.values().length) {
                cloneModeValue = CloneMode.values()[modeIndex];
            }
        }
        
        if (context == null || context.getWorld() == null) {
            error = "Missing execution world";
        } else if (!(sourceRegionObj instanceof RegionData sourceRegion)) {
            error = "Invalid source region";
        } else if (!(destinationPosObj instanceof BlockPos destinationPos)) {
            error = "Invalid destination position";
        } else if (!sourceRegion.isComplete()) {
            error = "Incomplete source region";
        } else {
            BlockPos sourceMinCorner = sourceRegion.getMinCorner();
            BlockPos sourceMaxCorner = sourceRegion.getMaxCorner();

            long volume = WorldWriteUtils.volume(sourceMinCorner, sourceMaxCorner);
            if (volume > maxBlocks) {
                error = "Region volume " + volume + " exceeds max blocks " + maxBlocks;
            } else {
                BlockPos destMaxCorner = getBlockPos(destinationPos, sourceMaxCorner, sourceMinCorner);

                destinationRegion = new RegionData(destinationPos, destMaxCorner);

                boolean regionsOverlap = checkRegionsOverlap(sourceRegion, destinationRegion);
                if (regionsOverlap && cloneModeValue != CloneMode.MOVE) {
                    error = "Source and destination regions overlap; use MOVE mode";
                } else {
                    try {
                        Map<BlockPos, BlockState> blocksToCopy = new HashMap<>();
                        WorldWriteHistoryService.UndoRecord undoRecord = recordUndo
                                ? new WorldWriteHistoryService.UndoRecord()
                                : null;

                        if (sourceMinCorner != null) {
                            if (sourceMaxCorner != null) {
                                for (BlockPos pos : BlockPos.iterate(sourceMinCorner, sourceMaxCorner)) {
                                    totalCount++;
                                    BlockPos immutablePos = pos.toImmutable();

                                    try {
                                        BlockState blockState = context.getWorld().getBlockState(immutablePos);
                                        boolean isAir = context.getWorld().isAir(immutablePos);
                                        if (isAir && !includeAirValue) {
                                            continue;
                                        }
                                        if (cloneModeValue == CloneMode.MASKED && isAir) {
                                            continue;
                                        }

                                        BlockPos destPos = getPos(destinationPos, immutablePos, sourceMinCorner);
                                        blocksToCopy.put(destPos, blockState);
                                    } catch (Exception e) {
                                        if (error.isEmpty()) {
                                            error = "Error reading block at " + immutablePos + ": " + e.getMessage();
                                        }
                                        com.nodecraft.core.NodeCraft.LOGGER.warn("Error reading block at {}", immutablePos, e);
                                    }
                                }
                            }
                        }

                        int flags = WorldWriteUtils.flags(notifyUpdateValue);
                        for (Map.Entry<BlockPos, BlockState> entry : blocksToCopy.entrySet()) {
                            BlockPos pos = entry.getKey();
                            BlockState blockState = entry.getValue();

                            try {
                                BlockState previousState = context.getWorld().getBlockState(pos);
                                boolean blockSuccess = context.getWorld().setBlockState(pos, blockState, flags);

                                if (blockSuccess) {
                                    successCount++;
                                    clonedBlocks++;
                                    if (undoRecord != null) {
                                        undoRecord.add(pos, previousState);
                                    }
                                }
                            } catch (Exception e) {
                                if (error.isEmpty()) {
                                    error = "Error placing block at " + pos + ": " + e.getMessage();
                                }
                                com.nodecraft.core.NodeCraft.LOGGER.warn("Error placing block at {}", pos, e);
                            }
                        }

                        if (cloneModeValue == CloneMode.MOVE) {
                            BlockState airState = Blocks.AIR.getDefaultState();
                            if (sourceMinCorner != null) {
                                if (sourceMaxCorner != null) {
                                    for (BlockPos pos : BlockPos.iterate(sourceMinCorner, sourceMaxCorner)) {
                                        BlockPos immutablePos = pos.toImmutable();
                                        if (destinationRegion.contains(immutablePos)) {
                                            continue;
                                        }

                                        try {
                                            BlockState previousState = context.getWorld().getBlockState(immutablePos);
                                            boolean clearSuccess = context.getWorld().setBlockState(
                                                    immutablePos,
                                                    airState,
                                                    flags
                                            );
                                            if (clearSuccess && undoRecord != null) {
                                                undoRecord.add(immutablePos, previousState);
                                            }
                                        } catch (Exception e) {
                                            if (error.isEmpty()) {
                                                error = "Error clearing block at " + immutablePos + ": " + e.getMessage();
                                            }
                                            com.nodecraft.core.NodeCraft.LOGGER.warn("Error clearing block at {}", immutablePos, e);
                                        }
                                    }
                                }
                            }
                        }

                        if (includeEntitiesValue && error.isEmpty()) {
                            error = "Include entities is not implemented yet";
                        }

                        success = error.isEmpty();
                        if (success && undoRecord != null) {
                            WorldWriteHistoryService.getInstance().push(undoRecord);
                        }
                    } catch (RuntimeException e) {
                        error = e.getMessage() == null ? "Clone failed" : e.getMessage();
                        com.nodecraft.core.NodeCraft.LOGGER.warn("Clone region failed", e);
                    }
                }
            }
        }

        outputValues.put(OUTPUT_CLONED_BLOCKS_ID, clonedBlocks);
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_DESTINATION_REGION_ID, destinationRegion);
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private static @NotNull BlockPos getPos(BlockPos destinationPos, BlockPos immutablePos, BlockPos sourceMinCorner) {
        int offsetX = immutablePos.getX() - sourceMinCorner.getX();
        int offsetY = immutablePos.getY() - sourceMinCorner.getY();
        int offsetZ = immutablePos.getZ() - sourceMinCorner.getZ();
        return new BlockPos(
                destinationPos.getX() + offsetX,
                destinationPos.getY() + offsetY,
                destinationPos.getZ() + offsetZ
        );
    }

    private static @NotNull BlockPos getBlockPos(BlockPos destinationPos, BlockPos sourceMaxCorner, BlockPos sourceMinCorner) {
        int width = 0;
        if (sourceMaxCorner != null) {
            if (sourceMinCorner != null) {
                width = sourceMaxCorner.getX() - sourceMinCorner.getX() + 1;
            }
        }
        int height = 0;
        if (sourceMaxCorner != null) {
            if (sourceMinCorner != null) {
                height = sourceMaxCorner.getY() - sourceMinCorner.getY() + 1;
            }
        }
        int depth = 0;
        if (sourceMinCorner != null) {
            if (sourceMaxCorner != null) {
                depth = sourceMaxCorner.getZ() - sourceMinCorner.getZ() + 1;
            }
        }

        return new BlockPos(
                destinationPos.getX() + width - 1,
                destinationPos.getY() + height - 1,
                destinationPos.getZ() + depth - 1
        );
    }

    /**
     * 检查两个区域是否重叠
     * @param region1 第一个区域
     * @param region2 第二个区域
     * @return 如果区域重叠则返回true
     */
    private boolean checkRegionsOverlap(RegionData region1, RegionData region2) {
        if (!region1.isComplete() || !region2.isComplete()) {
            return false;
        }
        
        BlockPos min1 = region1.getMinCorner();
        BlockPos max1 = region1.getMaxCorner();
        BlockPos min2 = region2.getMinCorner();
        BlockPos max2 = region2.getMaxCorner();
        
        // 检查任一维度是否不重叠
        return !(max1.getX() < min2.getX() || min1.getX() > max2.getX() ||
                max1.getY() < min2.getY() || min1.getY() > max2.getY() ||
                max1.getZ() < min2.getZ() || min1.getZ() > max2.getZ());
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isNotifyUpdate() {
        return notifyUpdate;
    }
    
    public void setNotifyUpdate(boolean notifyUpdate) {
        this.notifyUpdate = notifyUpdate;
        markDirty();
    }
    
    public boolean isIncludeEntities() {
        return includeEntities;
    }
    
    public void setIncludeEntities(boolean includeEntities) {
        this.includeEntities = includeEntities;
        markDirty();
    }
    
    public boolean isIncludeAir() {
        return includeAir;
    }
    
    public void setIncludeAir(boolean includeAir) {
        this.includeAir = includeAir;
        markDirty();
    }
    
    public boolean isBatchUpdates() {
        return batchUpdates;
    }
    
    public void setBatchUpdates(boolean batchUpdates) {
        this.batchUpdates = batchUpdates;
        markDirty();
    }
    
    public CloneMode getCloneMode() {
        return cloneMode;
    }
    
    public void setCloneMode(CloneMode cloneMode) {
        this.cloneMode = cloneMode;
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

    public boolean isRecordUndo() {
        return recordUndo;
    }

    public void setRecordUndo(boolean recordUndo) {
        this.recordUndo = recordUndo;
        markDirty();
    }
} 

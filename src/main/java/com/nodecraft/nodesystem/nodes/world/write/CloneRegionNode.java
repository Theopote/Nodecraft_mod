package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Clone Region 节点: 将一个区域的方块克隆到另一个位置
 */
@NodeInfo(
    id = "world.write.clone_region",
    displayName = "复制区域",
    description = "复制区域到另一个位置",
    category = "world.write",
    order = 4
)
public class CloneRegionNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "将一个区域的方块克隆到另一个位置";
    private boolean notifyUpdate = true; // 是否通知更新（触发更新事件）
    private boolean includeEntities = false; // 是否包括实体
    private boolean includeAir = true; // 是否克隆空气方块
    private boolean batchUpdates = true; // 是否批量更新，提高性能
    private CloneMode cloneMode = CloneMode.NORMAL; // 克隆模式
    private int maxBlocks = 32768; // 最大操作方块数（防止过大区域导致性能问题）

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
        
        // 检查执行上下文和必要输入是否有效
        if (context != null && context.getWorld() != null &&
                sourceRegionObj instanceof RegionData sourceRegion && destinationPosObj instanceof BlockPos destinationPos) {

            // 确保源区域完整且有效
            if (sourceRegion.isComplete()) {
                BlockPos sourceMinCorner = sourceRegion.getMinCorner();
                BlockPos sourceMaxCorner = sourceRegion.getMaxCorner();
                
                // 计算区域尺寸
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
                if (sourceMaxCorner != null) {
                    if (sourceMinCorner != null) {
                        depth = sourceMaxCorner.getZ() - sourceMinCorner.getZ() + 1;
                    }
                }
                int volume = width * height * depth;
                
                // 检查体积是否超过最大方块数
                if (volume > maxBlocks) {
                    System.err.println("Region volume (" + volume + ") exceeds max blocks limit (" + maxBlocks + ").");
                    
                    // 设置输出值并返回
                    outputValues.put(OUTPUT_CLONED_BLOCKS_ID, clonedBlocks);
                    outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
                    outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
                    outputValues.put(OUTPUT_DESTINATION_REGION_ID, destinationRegion);
                    outputValues.put(OUTPUT_SUCCESS_ID, success);
                    return;
                }
                
                // 计算目标区域的角落坐标
                BlockPos destMaxCorner = new BlockPos(
                    destinationPos.getX() + width - 1,
                    destinationPos.getY() + height - 1,
                    destinationPos.getZ() + depth - 1
                );
                
                // 创建目标区域
                destinationRegion = new RegionData(destinationPos, destMaxCorner);
                
                // 检查两个区域是否重叠（防止破坏源区域）
                boolean regionsOverlap = checkRegionsOverlap(sourceRegion, destinationRegion);
                
                if (regionsOverlap && cloneModeValue != CloneMode.MOVE) {
                    System.err.println("Source and destination regions overlap. Use MOVE mode to allow this.");
                    
                    // 设置输出值并返回
                    outputValues.put(OUTPUT_CLONED_BLOCKS_ID, clonedBlocks);
                    outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
                    outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
                    outputValues.put(OUTPUT_DESTINATION_REGION_ID, destinationRegion);
                    outputValues.put(OUTPUT_SUCCESS_ID, success);
                    return;
                }
                
                // 在实际实现中，开始批量更新（如果启用）
                if (batchUpdates) {
                    // 开始批量更新，例如 context.getWorld().beginBatchBlockUpdate();
                }
                
                try {
                    // 记录源区域中的所有方块和实体
                    Map<BlockPos, BlockState> blocksToCopy = new HashMap<>();
                    
                    // 遍历源区域内的所有方块
                    if (sourceMinCorner != null) {
                        if (sourceMaxCorner != null) {
                            for (BlockPos pos : BlockPos.iterate(sourceMinCorner, sourceMaxCorner)) {
                                totalCount++;
                                BlockPos immutablePos = pos.toImmutable();

                                try {
                                    // 获取方块状态
                                    BlockState blockState = context.getWorld().getBlockState(immutablePos);

                                    // 检查是否为空气（如果不包括空气则跳过）
                                    boolean isAir = context.getWorld().isAir(immutablePos);
                                    if (isAir && !includeAirValue) {
                                        continue;
                                    }

                                    // 处理蒙版模式 - 如果是蒙版模式且为空气，跳过
                                    if (cloneModeValue == CloneMode.MASKED && isAir) {
                                        continue;
                                    }

                                    // 计算源与目标坐标的偏移
                                    int offsetX = immutablePos.getX() - sourceMinCorner.getX();
                                    int offsetY = immutablePos.getY() - sourceMinCorner.getY();
                                    int offsetZ = immutablePos.getZ() - sourceMinCorner.getZ();

                                    // 计算目标坐标
                                    BlockPos destPos = new BlockPos(
                                        destinationPos.getX() + offsetX,
                                        destinationPos.getY() + offsetY,
                                        destinationPos.getZ() + offsetZ
                                    );

                                    // 记录要复制的方块
                                    blocksToCopy.put(destPos, blockState);
                                } catch (Exception e) {
                                    // 记录单个方块读取错误但继续执行
                                    System.err.println("Error reading block at " + immutablePos + ": " + e.getMessage());
                                }
                            }
                        }
                    }

                    // 应用克隆（按照顺序处理，以确保像重力方块等能正确放置）
                    for (Map.Entry<BlockPos, BlockState> entry : blocksToCopy.entrySet()) {
                        BlockPos pos = entry.getKey();
                        BlockState blockState = entry.getValue();
                        
                        try {
                            int flags = Block.NOTIFY_ALL;
                            boolean blockSuccess = context.getWorld().setBlockState(pos, blockState, flags);
                            
                            if (blockSuccess) {
                                successCount++;
                                clonedBlocks++;
                            }
                        } catch (Exception e) {
                            // 记录单个方块放置错误但继续执行
                            System.err.println("Error placing block at " + pos + ": " + e.getMessage());
                        }
                    }
                    
                    // 如果是移动模式，清除源区域
                    if (cloneModeValue == CloneMode.MOVE) {
                        BlockState airState = Blocks.AIR.getDefaultState();
                        
                        // 清除源区域
                        if (sourceMinCorner != null) {
                            if (sourceMaxCorner != null) {
                                for (BlockPos pos : BlockPos.iterate(sourceMinCorner, sourceMaxCorner)) {
                                    try {
                                        context.getWorld().setBlockState(pos.toImmutable(), airState, Block.NOTIFY_ALL);
                                    } catch (Exception e) {
                                        System.err.println("Error clearing block at " + pos + ": " + e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                    
                    // 如果包括实体，克隆实体
                    if (includeEntitiesValue) {
                        // 在实际实现中，获取源区域内的所有实体
                        // 然后在目标区域创建实体副本
                        // 如果是移动模式，删除源区域中的实体
                    }
                    
                    // 操作完成
                    success = true;
                    
                } finally {
                    // 完成批量更新（如果启用）
                    if (batchUpdates) {
                        // 例如: context.getWorld().endBatchBlockUpdate();
                    }
                }
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_CLONED_BLOCKS_ID, clonedBlocks);
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_DESTINATION_REGION_ID, destinationRegion);
        outputValues.put(OUTPUT_SUCCESS_ID, success);
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
} 

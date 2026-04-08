package com.nodecraft.nodesystem.nodes.flora.output;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlantStructure;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Place Plant 节点: 将一个PlantStructure实例转换为最终要放置的MinecraftBlock列表
 */
@NodeInfo(
    id = "flora.output.place_plant",
    displayName = "Place Plant",
    description = "Converts PlantStructure to placeable Minecraft blocks",
    category = "flora.output"
)
public class PlacePlantNode extends BaseNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlacePlantNode.class);
    
    // --- 节点属性 ---
    private boolean includeAir = false;            // 是否包含空气方块
    private boolean removeDuplicates = true;       // 是否移除重复坐标的方块
    private boolean sortByHeight = false;          // 是否按高度排序（从下到上）
    private String description = "将一个PlantStructure实例转换为最终要放置的MinecraftBlock列表，并准备好放置";
    
    // --- 输入端口 IDs ---
    private static final String INPUT_PLANT_STRUCTURE_ID = "input_plant_structure";
    private static final String INPUT_TARGET_POSITION_ID = "input_target_position";
    private static final String INPUT_INCLUDE_AIR_ID = "input_include_air";
    private static final String INPUT_REMOVE_DUPLICATES_ID = "input_remove_duplicates";
    private static final String INPUT_SORT_BY_HEIGHT_ID = "input_sort_by_height";
    
    // --- 输出端口 IDs ---
    private static final String OUTPUT_BLOCK_LIST_ID = "output_block_list";
    private static final String OUTPUT_BLOCK_COUNT_ID = "output_block_count";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_BOUNDS_MIN_ID = "output_bounds_min";
    private static final String OUTPUT_BOUNDS_MAX_ID = "output_bounds_max";
    
    /**
     * 构造一个新的放置植物节点
     */
    public PlacePlantNode() {
        super(UUID.randomUUID(), "flora.output.place_plant");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_PLANT_STRUCTURE_ID, "Plant Structure", 
                "要转换的植物结构", NodeDataType.PLANT_STRUCTURE, this));
        addInputPort(new BasePort(INPUT_TARGET_POSITION_ID, "Target Position", 
                "植物放置的目标位置（可选，用于位置偏移）", NodeDataType.COORDINATE, this));
        addInputPort(new BasePort(INPUT_INCLUDE_AIR_ID, "Include Air", 
                "是否包含空气方块", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_REMOVE_DUPLICATES_ID, "Remove Duplicates", 
                "是否移除重复坐标的方块", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SORT_BY_HEIGHT_ID, "Sort By Height", 
                "是否按高度排序（从下到上放置）", NodeDataType.BOOLEAN, this));
        
        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_BLOCK_LIST_ID, "Block List", 
                "所有要放置的方块列表（可直接连接到Set Blocks节点）", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_COUNT_ID, "Block Count", 
                "生成的方块总数", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", 
                "转换是否成功", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDS_MIN_ID, "Bounds Min", 
                "植物的最小边界坐标", NodeDataType.COORDINATE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDS_MAX_ID, "Bounds Max", 
                "植物的最大边界坐标", NodeDataType.COORDINATE, this));
    }
    
    @Override
    public String getDescription() {
        return this.description;
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 默认输出值
        List<MinecraftBlockInfo> blockList = new ArrayList<>();
        int blockCount = 0;
        boolean success = false;
        BlockPos boundsMin = new BlockPos(0, 0, 0);
        BlockPos boundsMax = new BlockPos(0, 0, 0);
        
        // 获取输入值
        PlantStructure plantStructure = getInputValue(INPUT_PLANT_STRUCTURE_ID, null);
        BlockPos targetPosition = getInputValue(INPUT_TARGET_POSITION_ID, null);
        Boolean includeAirValue = getInputValue(INPUT_INCLUDE_AIR_ID, this.includeAir);
        Boolean removeDuplicatesValue = getInputValue(INPUT_REMOVE_DUPLICATES_ID, this.removeDuplicates);
        Boolean sortByHeightValue = getInputValue(INPUT_SORT_BY_HEIGHT_ID, this.sortByHeight);
        
        if (plantStructure != null && !plantStructure.isEmpty()) {
            try {
                // 获取所有植物方块
                List<PlantStructure.PlantBlock> allPlantBlocks = plantStructure.getAllBlocks();
                
                // 计算位置偏移
                BlockPos offset = targetPosition != null ? targetPosition : new BlockPos(0, 0, 0);
                
                // 转换为MinecraftBlockInfo列表
                List<MinecraftBlockInfo> convertedBlocks = new ArrayList<>();
                for (PlantStructure.PlantBlock plantBlock : allPlantBlocks) {
                    String blockType = plantBlock.getBlockType();
                    
                    // 检查是否包含空气方块
                    if (!includeAirValue && (blockType == null || blockType.equals("minecraft:air"))) {
                        continue;
                    }
                    
                    // 计算最终位置
                    BlockPos originalPos = plantBlock.getPosition();
                    BlockPos finalPos = new BlockPos(
                        originalPos.getX() + offset.getX(),
                        originalPos.getY() + offset.getY(),
                        originalPos.getZ() + offset.getZ()
                    );
                    
                    // 创建方块信息
                    MinecraftBlockInfo blockInfo = new MinecraftBlockInfo(
                        finalPos,
                        blockType != null ? blockType : "minecraft:air",
                        plantBlock.getNbtData(),
                        plantBlock.getThickness()
                    );
                    
                    convertedBlocks.add(blockInfo);
                }
                
                // 移除重复项（如果启用）
                if (removeDuplicatesValue) {
                    convertedBlocks = removeDuplicateBlocks(convertedBlocks);
                }
                
                // 按高度排序（如果启用）
                if (sortByHeightValue) {
                    convertedBlocks.sort((a, b) -> Integer.compare(a.position.getY(), b.position.getY()));
                }
                
                // 计算边界
                if (!convertedBlocks.isEmpty()) {
                    boundsMin = calculateBoundsMin(convertedBlocks);
                    boundsMax = calculateBoundsMax(convertedBlocks);
                }
                
                blockList = convertedBlocks;
                blockCount = blockList.size();
                success = true;
                
            } catch (Exception e) {
                LOGGER.error("Error in Place Plant conversion", e);
                success = false;
            }
        }
        
        // 设置输出
        outputValues.put(OUTPUT_BLOCK_LIST_ID, blockList);
        outputValues.put(OUTPUT_BLOCK_COUNT_ID, blockCount);
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_BOUNDS_MIN_ID, boundsMin);
        outputValues.put(OUTPUT_BOUNDS_MAX_ID, boundsMax);
    }
    
    /**
     * MinecraftBlockInfo类，表示一个要放置的方块信息
     */
    public static class MinecraftBlockInfo {
        public final BlockPos position;
        public final String blockType;
        public final Object nbtData;
        public final float thickness;
        
        public MinecraftBlockInfo(BlockPos position, String blockType, Object nbtData, float thickness) {
            this.position = position;
            this.blockType = blockType;
            this.nbtData = nbtData;
            this.thickness = thickness;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            
            MinecraftBlockInfo that = (MinecraftBlockInfo) obj;
            return position.equals(that.position);
        }
        
        @Override
        public int hashCode() {
            return position.hashCode();
        }
        
        @Override
        public String toString() {
            return String.format("Block{pos=%s, type=%s, thickness=%.2f}", 
                               position, blockType, thickness);
        }
    }
    
    /**
     * 移除重复的方块（相同位置）
     */
    private List<MinecraftBlockInfo> removeDuplicateBlocks(List<MinecraftBlockInfo> blocks) {
        List<MinecraftBlockInfo> uniqueBlocks = new ArrayList<>();
        List<BlockPos> seenPositions = new ArrayList<>();
        
        for (MinecraftBlockInfo block : blocks) {
            if (!seenPositions.contains(block.position)) {
                seenPositions.add(block.position);
                uniqueBlocks.add(block);
            }
        }
        
        return uniqueBlocks;
    }
    
    /**
     * 计算最小边界
     */
    private BlockPos calculateBoundsMin(List<MinecraftBlockInfo> blocks) {
        if (blocks.isEmpty()) return new BlockPos(0, 0, 0);
        
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        
        for (MinecraftBlockInfo block : blocks) {
            BlockPos pos = block.position;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        
        return new BlockPos(minX, minY, minZ);
    }
    
    /**
     * 计算最大边界
     */
    private BlockPos calculateBoundsMax(List<MinecraftBlockInfo> blocks) {
        if (blocks.isEmpty()) return new BlockPos(0, 0, 0);
        
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        
        for (MinecraftBlockInfo block : blocks) {
            BlockPos pos = block.position;
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        
        return new BlockPos(maxX, maxY, maxZ);
    }
    
    /**
     * 获取输入值的辅助方法
     */
    @SuppressWarnings("unchecked")
    private <T> T getInputValue(String portId, T defaultValue) {
        Object value = inputValues.get(portId);
        if (value != null) {
            try {
                return (T) value;
            } catch (ClassCastException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    // --- Getters and Setters ---
    
    public boolean isIncludeAir() {
        return includeAir;
    }
    
    public void setIncludeAir(boolean includeAir) {
        this.includeAir = includeAir;
        markDirty();
    }
    
    public boolean isRemoveDuplicates() {
        return removeDuplicates;
    }
    
    public void setRemoveDuplicates(boolean removeDuplicates) {
        this.removeDuplicates = removeDuplicates;
        markDirty();
    }
    
    public boolean isSortByHeight() {
        return sortByHeight;
    }
    
    public void setSortByHeight(boolean sortByHeight) {
        this.sortByHeight = sortByHeight;
        markDirty();
    }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("includeAir", isIncludeAir());
        state.put("removeDuplicates", isRemoveDuplicates());
        state.put("sortByHeight", isSortByHeight());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("includeAir")) {
                Object includeAirObj = stateMap.get("includeAir");
                if (includeAirObj instanceof Boolean) {
                    setIncludeAir((Boolean) includeAirObj);
                }
            }
            
            if (stateMap.containsKey("removeDuplicates")) {
                Object removeDuplicatesObj = stateMap.get("removeDuplicates");
                if (removeDuplicatesObj instanceof Boolean) {
                    setRemoveDuplicates((Boolean) removeDuplicatesObj);
                }
            }
            
            if (stateMap.containsKey("sortByHeight")) {
                Object sortByHeightObj = stateMap.get("sortByHeight");
                if (sortByHeightObj instanceof Boolean) {
                    setSortByHeight((Boolean) sortByHeightObj);
                }
            }
        }
    }
} 
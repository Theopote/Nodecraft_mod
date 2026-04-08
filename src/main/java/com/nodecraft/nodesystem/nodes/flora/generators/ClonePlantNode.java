package com.nodecraft.nodesystem.nodes.flora.generators;

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
import java.util.Random;
import java.util.UUID;

/**
 * Clone Plant 节点: 复制现有的植物结构
 */
@NodeInfo(
    id = "flora.generators.clone_plant",
    displayName = "Clone Plant",
    description = "Creates copies of existing plant structures",
    category = "flora.generators"
)
public class ClonePlantNode extends BaseNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClonePlantNode.class);
    
    /**
     * 复制模式枚举
     */
    public enum ClonePattern {
        GRID("网格", "按网格模式排列"),
        CIRCLE("圆形", "按圆形模式排列"),
        SPIRAL("螺旋", "按螺旋模式排列"),
        RANDOM("随机", "随机分布"),
        LINE("直线", "沿直线排列"),
        TRIANGLE("三角", "按三角形模式排列");
        
        private final String displayName;
        private final String description;
        
        ClonePattern(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public String getId() { return name().toLowerCase(); }
        
        public static ClonePattern fromString(String str) {
            for (ClonePattern pattern : values()) {
                if (pattern.getId().equalsIgnoreCase(str) || pattern.displayName.equals(str)) {
                    return pattern;
                }
            }
            return GRID;
        }
    }
    
    // --- 节点属性 ---
    private ClonePattern clonePattern = ClonePattern.GRID;
    private int cloneCount = 9;                        // 复制数量
    private float spacing = 5.0f;                      // 间距
    private float offsetX = 0.0f;                      // X轴偏移
    private float offsetY = 0.0f;                      // Y轴偏移
    private float offsetZ = 0.0f;                      // Z轴偏移
    private boolean randomRotation = false;            // 随机旋转
    private boolean randomScale = false;               // 随机缩放
    private float scaleVariation = 0.2f;               // 缩放变化范围
    private boolean mergePlants = false;               // 是否合并为单个植物
    private int randomSeed = 12345;                    // 随机种子
    private String description = "复制植物并按模式分布";
    
    // --- 输入端口 IDs ---
    private static final String INPUT_PLANT_STRUCTURE_ID = "input_plant_structure";
    private static final String INPUT_CLONE_PATTERN_ID = "input_clone_pattern";
    private static final String INPUT_CLONE_COUNT_ID = "input_clone_count";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_OFFSET_X_ID = "input_offset_x";
    private static final String INPUT_OFFSET_Y_ID = "input_offset_y";
    private static final String INPUT_OFFSET_Z_ID = "input_offset_z";
    private static final String INPUT_RANDOM_ROTATION_ID = "input_random_rotation";
    private static final String INPUT_RANDOM_SCALE_ID = "input_random_scale";
    private static final String INPUT_SCALE_VARIATION_ID = "input_scale_variation";
    private static final String INPUT_MERGE_PLANTS_ID = "input_merge_plants";
    private static final String INPUT_RANDOM_SEED_ID = "input_random_seed";
    
    // --- 输出端口 IDs ---
    private static final String OUTPUT_PLANT_STRUCTURE_ID = "output_plant_structure";
    private static final String OUTPUT_PLANT_LIST_ID = "output_plant_list";
    private static final String OUTPUT_CLONE_INFO_ID = "output_clone_info";
    
    /**
     * 构造一个新的复制植物节点
     */
    public ClonePlantNode() {
        super(UUID.randomUUID(), "flora.generators.clone_plant");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_PLANT_STRUCTURE_ID, "Plant Structure", 
                "要复制的植物结构", NodeDataType.PLANT_STRUCTURE, this));
        addInputPort(new BasePort(INPUT_CLONE_PATTERN_ID, "Clone Pattern", 
                "复制模式（Grid、Circle、Spiral等）", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_CLONE_COUNT_ID, "Clone Count", 
                "复制数量", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing", 
                "植物间距", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_OFFSET_X_ID, "Offset X", 
                "X轴偏移", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_OFFSET_Y_ID, "Offset Y", 
                "Y轴偏移", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_OFFSET_Z_ID, "Offset Z", 
                "Z轴偏移", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_RANDOM_ROTATION_ID, "Random Rotation", 
                "是否随机旋转", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_RANDOM_SCALE_ID, "Random Scale", 
                "是否随机缩放", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SCALE_VARIATION_ID, "Scale Variation", 
                "缩放变化范围（0-1）", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_MERGE_PLANTS_ID, "Merge Plants", 
                "是否合并为单个植物", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_RANDOM_SEED_ID, "Random Seed", 
                "随机种子", NodeDataType.INTEGER, this));
        
        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_PLANT_STRUCTURE_ID, "Merged Plant", 
                "合并后的植物结构（如果启用合并）", NodeDataType.PLANT_STRUCTURE, this));
        addOutputPort(new BasePort(OUTPUT_PLANT_LIST_ID, "Plant List", 
                "复制的植物列表", NodeDataType.PLANT_STRUCTURE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CLONE_INFO_ID, "Clone Info", 
                "复制操作的详细信息", NodeDataType.STRING, this));
    }
    
    @Override
    public String getDescription() {
        return this.description;
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        PlantStructure inputPlant = getInputValue(INPUT_PLANT_STRUCTURE_ID, null);
        String clonePatternStr = getInputValue(INPUT_CLONE_PATTERN_ID, this.clonePattern.getId());
        Integer cloneCountValue = getInputValue(INPUT_CLONE_COUNT_ID, this.cloneCount);
        Float spacingValue = getInputValue(INPUT_SPACING_ID, this.spacing);
        Float offsetXValue = getInputValue(INPUT_OFFSET_X_ID, this.offsetX);
        Float offsetYValue = getInputValue(INPUT_OFFSET_Y_ID, this.offsetY);
        Float offsetZValue = getInputValue(INPUT_OFFSET_Z_ID, this.offsetZ);
        Boolean randomRotationValue = getInputValue(INPUT_RANDOM_ROTATION_ID, this.randomRotation);
        Boolean randomScaleValue = getInputValue(INPUT_RANDOM_SCALE_ID, this.randomScale);
        Float scaleVariationValue = getInputValue(INPUT_SCALE_VARIATION_ID, this.scaleVariation);
        Boolean mergePlantsValue = getInputValue(INPUT_MERGE_PLANTS_ID, this.mergePlants);
        Integer randomSeedValue = getInputValue(INPUT_RANDOM_SEED_ID, this.randomSeed);
        
        // 默认输出值
        PlantStructure mergedPlant = new PlantStructure();
        List<PlantStructure> plantList = new ArrayList<>();
        String cloneInfo = "No plant to clone";
        
        if (inputPlant != null && !inputPlant.isEmpty()) {
            try {
                // 验证复制参数
                ClonePattern currentPattern = ClonePattern.fromString(clonePatternStr);
                cloneCountValue = Math.max(1, Math.min(100, cloneCountValue != null ? cloneCountValue : 9));
                spacingValue = Math.max(0.1f, spacingValue != null ? spacingValue : 5.0f);
                offsetXValue = offsetXValue != null ? offsetXValue : 0.0f;
                offsetYValue = offsetYValue != null ? offsetYValue : 0.0f;
                offsetZValue = offsetZValue != null ? offsetZValue : 0.0f;
                randomRotationValue = randomRotationValue != null ? randomRotationValue : false;
                randomScaleValue = randomScaleValue != null ? randomScaleValue : false;
                scaleVariationValue = Math.max(0.0f, Math.min(1.0f, scaleVariationValue != null ? scaleVariationValue : 0.2f));
                mergePlantsValue = mergePlantsValue != null ? mergePlantsValue : false;
                randomSeedValue = randomSeedValue != null ? randomSeedValue : 12345;
                
                // 执行复制
                plantList = clonePlants(inputPlant, currentPattern, cloneCountValue, spacingValue,
                                      offsetXValue, offsetYValue, offsetZValue, randomRotationValue,
                                      randomScaleValue, scaleVariationValue, randomSeedValue);
                
                // 合并植物（如果需要）
                if (mergePlantsValue) {
                    mergedPlant = mergePlantList(plantList);
                    
                    // 设置合并元数据
                    if (inputPlant.getMetadata() != null) {
                        for (java.util.Map.Entry<String, Object> entry : inputPlant.getMetadata().entrySet()) {
                            mergedPlant.setMetadata(entry.getKey(), entry.getValue());
                        }
                    }
                    mergedPlant.setMetadata("clone_pattern", currentPattern.getId());
                    mergedPlant.setMetadata("clone_count", cloneCountValue);
                    mergedPlant.setMetadata("spacing", spacingValue);
                    mergedPlant.setMetadata("merged_from_clones", true);
                }
                
                // 生成复制信息
                int totalBlocks = 0;
                for (PlantStructure plant : plantList) {
                    totalBlocks += plant.getTotalBlockCount();
                }
                
                cloneInfo = String.format("Cloned: %s pattern, %d plants, Spacing: %.1f, Total blocks: %d, Merged: %s",
                    currentPattern.getDisplayName(), plantList.size(), spacingValue, totalBlocks,
                    mergePlantsValue ? "Yes" : "No");
                
            } catch (Exception e) {
                LOGGER.error("Error in Clone Plant", e);
                cloneInfo = "Error during cloning";
            }
        }
        
        // 设置输出
        outputValues.put(OUTPUT_PLANT_STRUCTURE_ID, mergedPlant);
        outputValues.put(OUTPUT_PLANT_LIST_ID, plantList);
        outputValues.put(OUTPUT_CLONE_INFO_ID, cloneInfo);
    }
    
    /**
     * 复制植物
     */
    private List<PlantStructure> clonePlants(PlantStructure original, ClonePattern pattern, int count,
                                           float spacing, float offsetX, float offsetY, float offsetZ,
                                           boolean randomRotation, boolean randomScale, float scaleVariation,
                                           int seed) {
        List<PlantStructure> clones = new ArrayList<>();
        Random random = new Random(seed);
        
        // 生成位置列表
        List<float[]> positions = generatePositions(pattern, count, spacing, offsetX, offsetY, offsetZ, random);
        
        for (int i = 0; i < positions.size(); i++) {
            float[] pos = positions.get(i);
            
            // 复制植物
            PlantStructure clone = clonePlantToPosition(original, pos[0], pos[1], pos[2]);
            
            // 应用随机变换
            if (randomRotation || randomScale) {
                clone = applyRandomTransforms(clone, randomRotation, randomScale, scaleVariation, random);
            }
            
            // 添加复制元数据
            clone.setMetadata("clone_index", i);
            clone.setMetadata("clone_position", pos);
            
            clones.add(clone);
        }
        
        return clones;
    }
    
    /**
     * 根据模式生成位置
     */
    private List<float[]> generatePositions(ClonePattern pattern, int count, float spacing,
                                          float offsetX, float offsetY, float offsetZ, Random random) {
        List<float[]> positions = new ArrayList<>();
        
        switch (pattern) {
            case GRID:
                generateGridPositions(positions, count, spacing, offsetX, offsetY, offsetZ);
                break;
            case CIRCLE:
                generateCirclePositions(positions, count, spacing, offsetX, offsetY, offsetZ);
                break;
            case SPIRAL:
                generateSpiralPositions(positions, count, spacing, offsetX, offsetY, offsetZ);
                break;
            case RANDOM:
                generateRandomPositions(positions, count, spacing, offsetX, offsetY, offsetZ, random);
                break;
            case LINE:
                generateLinePositions(positions, count, spacing, offsetX, offsetY, offsetZ);
                break;
            case TRIANGLE:
                generateTrianglePositions(positions, count, spacing, offsetX, offsetY, offsetZ);
                break;
        }
        
        return positions;
    }
    
    /**
     * 生成网格位置
     */
    private void generateGridPositions(List<float[]> positions, int count, float spacing,
                                     float offsetX, float offsetY, float offsetZ) {
        int gridSize = (int) Math.ceil(Math.sqrt(count));
        float startX = -(gridSize - 1) * spacing / 2.0f;
        float startZ = -(gridSize - 1) * spacing / 2.0f;
        
        int generated = 0;
        for (int x = 0; x < gridSize && generated < count; x++) {
            for (int z = 0; z < gridSize && generated < count; z++) {
                positions.add(new float[]{
                    startX + x * spacing + offsetX,
                    offsetY,
                    startZ + z * spacing + offsetZ
                });
                generated++;
            }
        }
    }
    
    /**
     * 生成圆形位置
     */
    private void generateCirclePositions(List<float[]> positions, int count, float spacing,
                                       float offsetX, float offsetY, float offsetZ) {
        float radius = spacing * count / (2 * (float) Math.PI);
        
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            positions.add(new float[]{
                (float) (Math.cos(angle) * radius) + offsetX,
                offsetY,
                (float) (Math.sin(angle) * radius) + offsetZ
            });
        }
    }
    
    /**
     * 生成螺旋位置
     */
    private void generateSpiralPositions(List<float[]> positions, int count, float spacing,
                                       float offsetX, float offsetY, float offsetZ) {
        float spiralSpacing = spacing * 0.3f;
        
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i * 0.618; // 黄金角
            float radius = spiralSpacing * i;
            positions.add(new float[]{
                (float) (Math.cos(angle) * radius) + offsetX,
                offsetY,
                (float) (Math.sin(angle) * radius) + offsetZ
            });
        }
    }
    
    /**
     * 生成随机位置
     */
    private void generateRandomPositions(List<float[]> positions, int count, float spacing,
                                       float offsetX, float offsetY, float offsetZ, Random random) {
        float area = spacing * count * 0.5f;
        
        for (int i = 0; i < count; i++) {
            positions.add(new float[]{
                (random.nextFloat() - 0.5f) * area + offsetX,
                offsetY + (random.nextFloat() - 0.5f) * spacing * 0.2f,
                (random.nextFloat() - 0.5f) * area + offsetZ
            });
        }
    }
    
    /**
     * 生成直线位置
     */
    private void generateLinePositions(List<float[]> positions, int count, float spacing,
                                     float offsetX, float offsetY, float offsetZ) {
        float startX = -(count - 1) * spacing / 2.0f;
        
        for (int i = 0; i < count; i++) {
            positions.add(new float[]{
                startX + i * spacing + offsetX,
                offsetY,
                offsetZ
            });
        }
    }
    
    /**
     * 生成三角形位置
     */
    private void generateTrianglePositions(List<float[]> positions, int count, float spacing,
                                         float offsetX, float offsetY, float offsetZ) {
        int currentRow = 0;
        int positionsInRow = 1;
        int positionsPlaced = 0;
        
        while (positionsPlaced < count) {
            float rowStartX = -(positionsInRow - 1) * spacing / 2.0f;
            float rowZ = currentRow * spacing * 0.866f; // sin(60°)
            
            for (int i = 0; i < positionsInRow && positionsPlaced < count; i++) {
                positions.add(new float[]{
                    rowStartX + i * spacing + offsetX,
                    offsetY,
                    rowZ + offsetZ
                });
                positionsPlaced++;
            }
            
            currentRow++;
            positionsInRow++;
        }
    }
    
    /**
     * 将植物复制到指定位置
     */
    private PlantStructure clonePlantToPosition(PlantStructure original, float x, float y, float z) {
        PlantStructure clone = new PlantStructure();
        
        // 计算偏移量
        BlockPos offset = new BlockPos((int) Math.round(x), (int) Math.round(y), (int) Math.round(z));
        
        // 复制主干
        for (PlantStructure.PlantBlock block : original.getTrunkBlocks()) {
            BlockPos newPos = block.getPosition().add(offset);
            clone.addTrunkBlock(newPos, block.getBlockType(), block.getThickness());
        }
        
        // 复制分支
        for (PlantStructure.PlantBlock block : original.getBranchBlocks()) {
            BlockPos newPos = block.getPosition().add(offset);
            clone.addBranchBlock(newPos, block.getBlockType(), block.getThickness());
        }
        
        // 复制叶子
        for (PlantStructure.PlantBlock block : original.getLeafBlocks()) {
            BlockPos newPos = block.getPosition().add(offset);
            clone.addLeafBlock(newPos, block.getBlockType());
        }
        
        // 复制花朵
        for (PlantStructure.PlantBlock block : original.getFlowerBlocks()) {
            BlockPos newPos = block.getPosition().add(offset);
            clone.addFlowerBlock(newPos, block.getBlockType());
        }
        
        // 复制根系
        for (PlantStructure.PlantBlock block : original.getRootBlocks()) {
            BlockPos newPos = block.getPosition().add(offset);
            clone.addRootBlock(newPos, block.getBlockType());
        }
        
        // 复制元数据
        if (original.getMetadata() != null) {
            for (java.util.Map.Entry<String, Object> entry : original.getMetadata().entrySet()) {
                clone.setMetadata(entry.getKey(), entry.getValue());
            }
        }
        
        return clone;
    }
    
    /**
     * 应用随机变换
     */
    private PlantStructure applyRandomTransforms(PlantStructure plant, boolean randomRotation,
                                               boolean randomScale, float scaleVariation, Random random) {
        // 简化的随机变换实现
        // 在实际应用中，可以使用TransformPlantNode的功能
        
        if (randomRotation) {
            float rotY = random.nextFloat() * 360.0f;
            plant.setMetadata("random_rotation_y", rotY);
        }
        
        if (randomScale) {
            float scale = 1.0f + (random.nextFloat() - 0.5f) * 2 * scaleVariation;
            scale = Math.max(0.5f, Math.min(2.0f, scale));
            plant.setMetadata("random_scale", scale);
        }
        
        return plant;
    }
    
    /**
     * 合并植物列表
     */
    private PlantStructure mergePlantList(List<PlantStructure> plants) {
        PlantStructure merged = new PlantStructure();
        
        for (PlantStructure plant : plants) {
            merged.addTrunkBlocks(plant.getTrunkBlocks());
            merged.addBranchBlocks(plant.getBranchBlocks());
            merged.addLeafBlocks(plant.getLeafBlocks());
            merged.addFlowerBlocks(plant.getFlowerBlocks());
            merged.addRootBlocks(plant.getRootBlocks());
        }
        
        return merged;
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
    
    public ClonePattern getClonePattern() {
        return clonePattern;
    }
    
    public void setClonePattern(ClonePattern clonePattern) {
        this.clonePattern = clonePattern != null ? clonePattern : ClonePattern.GRID;
        markDirty();
    }
    
    public int getCloneCount() { return cloneCount; }
    public void setCloneCount(int cloneCount) { this.cloneCount = Math.max(1, Math.min(100, cloneCount)); markDirty(); }
    
    public float getSpacing() { return spacing; }
    public void setSpacing(float spacing) { this.spacing = Math.max(0.1f, spacing); markDirty(); }
    
    public float getOffsetX() { return offsetX; }
    public void setOffsetX(float offsetX) { this.offsetX = offsetX; markDirty(); }
    
    public float getOffsetY() { return offsetY; }
    public void setOffsetY(float offsetY) { this.offsetY = offsetY; markDirty(); }
    
    public float getOffsetZ() { return offsetZ; }
    public void setOffsetZ(float offsetZ) { this.offsetZ = offsetZ; markDirty(); }
    
    public boolean isRandomRotation() { return randomRotation; }
    public void setRandomRotation(boolean randomRotation) { this.randomRotation = randomRotation; markDirty(); }
    
    public boolean isRandomScale() { return randomScale; }
    public void setRandomScale(boolean randomScale) { this.randomScale = randomScale; markDirty(); }
    
    public float getScaleVariation() { return scaleVariation; }
    public void setScaleVariation(float scaleVariation) { this.scaleVariation = Math.max(0.0f, Math.min(1.0f, scaleVariation)); markDirty(); }
    
    public boolean isMergePlants() { return mergePlants; }
    public void setMergePlants(boolean mergePlants) { this.mergePlants = mergePlants; markDirty(); }
    
    public int getRandomSeed() { return randomSeed; }
    public void setRandomSeed(int randomSeed) { this.randomSeed = randomSeed; markDirty(); }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("clonePattern", getClonePattern().getId());
        state.put("cloneCount", getCloneCount());
        state.put("spacing", getSpacing());
        state.put("offsetX", getOffsetX());
        state.put("offsetY", getOffsetY());
        state.put("offsetZ", getOffsetZ());
        state.put("randomRotation", isRandomRotation());
        state.put("randomScale", isRandomScale());
        state.put("scaleVariation", getScaleVariation());
        state.put("mergePlants", isMergePlants());
        state.put("randomSeed", getRandomSeed());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("clonePattern")) {
                Object patternObj = stateMap.get("clonePattern");
                if (patternObj instanceof String) {
                    setClonePattern(ClonePattern.fromString((String) patternObj));
                }
            }
            
            if (stateMap.containsKey("cloneCount")) {
                Object obj = stateMap.get("cloneCount");
                if (obj instanceof Number) setCloneCount(((Number) obj).intValue());
            }
            
            if (stateMap.containsKey("spacing")) {
                Object obj = stateMap.get("spacing");
                if (obj instanceof Number) setSpacing(((Number) obj).floatValue());
            }
            
            if (stateMap.containsKey("offsetX")) {
                Object obj = stateMap.get("offsetX");
                if (obj instanceof Number) setOffsetX(((Number) obj).floatValue());
            }
            
            if (stateMap.containsKey("offsetY")) {
                Object obj = stateMap.get("offsetY");
                if (obj instanceof Number) setOffsetY(((Number) obj).floatValue());
            }
            
            if (stateMap.containsKey("offsetZ")) {
                Object obj = stateMap.get("offsetZ");
                if (obj instanceof Number) setOffsetZ(((Number) obj).floatValue());
            }
            
            if (stateMap.containsKey("randomRotation")) {
                Object obj = stateMap.get("randomRotation");
                if (obj instanceof Boolean) setRandomRotation((Boolean) obj);
            }
            
            if (stateMap.containsKey("randomScale")) {
                Object obj = stateMap.get("randomScale");
                if (obj instanceof Boolean) setRandomScale((Boolean) obj);
            }
            
            if (stateMap.containsKey("scaleVariation")) {
                Object obj = stateMap.get("scaleVariation");
                if (obj instanceof Number) setScaleVariation(((Number) obj).floatValue());
            }
            
            if (stateMap.containsKey("mergePlants")) {
                Object obj = stateMap.get("mergePlants");
                if (obj instanceof Boolean) setMergePlants((Boolean) obj);
            }
            
            if (stateMap.containsKey("randomSeed")) {
                Object obj = stateMap.get("randomSeed");
                if (obj instanceof Number) setRandomSeed(((Number) obj).intValue());
            }
        }
    }
} 
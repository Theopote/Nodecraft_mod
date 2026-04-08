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
 * Flower Preset 节点: 提供快速生成各种花朵类型的方式
 */
@NodeInfo(
    id = "flora.generators.flower_preset",
    displayName = "Flower Preset",
    description = "Provides quick generation of various flower types",
    category = "flora.generators"
)
public class FlowerPresetNode extends BaseNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowerPresetNode.class);
    
    /**
     * 花朵类型枚举
     */
    public enum FlowerType {
        SIMPLE_FLOWER("Simple Flower", "简单花朵"),
        ROSE("Rose", "玫瑰"),
        SUNFLOWER("Sunflower", "向日葵"),
        TULIP("Tulip", "郁金香"),
        DANDELION("Dandelion", "蒲公英"),
        POPPY("Poppy", "罂粟"),
        ORCHID("Orchid", "兰花"),
        LILY("Lily", "百合");
        
        private final String id;
        private final String displayName;
        
        FlowerType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
        
        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        
        public static FlowerType fromString(String str) {
            for (FlowerType type : values()) {
                if (type.id.equalsIgnoreCase(str) || type.displayName.equals(str)) {
                    return type;
                }
            }
            return SIMPLE_FLOWER; // 默认返回简单花朵
        }
    }
    
    // --- 节点属性 ---
    private FlowerType flowerType = FlowerType.SIMPLE_FLOWER; // 花朵类型
    private float size = 1.0f;                                // 花朵大小倍数
    private int petalCount = 5;                               // 花瓣数量
    private int randomSeed = 12345;                           // 随机种子
    private String description = "生成各种花朵";
    
    // --- 输入端口 IDs ---
    private static final String INPUT_FLOWER_TYPE_ID = "input_flower_type";
    private static final String INPUT_SIZE_ID = "input_size";
    private static final String INPUT_PETAL_COUNT_ID = "input_petal_count";
    private static final String INPUT_RANDOM_SEED_ID = "input_random_seed";
    private static final String INPUT_BASE_POSITION_ID = "input_base_position";
    
    // --- 输出端口 IDs ---
    private static final String OUTPUT_PLANT_STRUCTURE_ID = "output_plant_structure";
    private static final String OUTPUT_FLOWER_INFO_ID = "output_flower_info";
    
    /**
     * 构造一个新的花朵预设节点
     */
    public FlowerPresetNode() {
        super(UUID.randomUUID(), "flora.generators.flower_preset");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_FLOWER_TYPE_ID, "Flower Type", 
                "花朵类型（Rose、Sunflower、Tulip等）", NodeDataType.FLOWER_TYPE, this));
        addInputPort(new BasePort(INPUT_SIZE_ID, "Size", 
                "花朵大小倍数", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_PETAL_COUNT_ID, "Petal Count", 
                "花瓣数量（适用于某些花朵类型）", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_RANDOM_SEED_ID, "Random Seed", 
                "用于生成不同变体的随机种子", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_BASE_POSITION_ID, "Base Position", 
                "花朵的底部中心坐标", NodeDataType.COORDINATE, this));
        
        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_PLANT_STRUCTURE_ID, "Plant Structure", 
                "生成的花朵结构", NodeDataType.PLANT_STRUCTURE, this));
        addOutputPort(new BasePort(OUTPUT_FLOWER_INFO_ID, "Flower Info", 
                "花朵的详细信息", NodeDataType.STRING, this));
    }
    
    @Override
    public String getDescription() {
        return this.description;
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        String flowerTypeStr = getInputValue(INPUT_FLOWER_TYPE_ID, this.flowerType.getId());
        Float sizeValue = getInputValue(INPUT_SIZE_ID, this.size);
        Integer petalCountValue = getInputValue(INPUT_PETAL_COUNT_ID, this.petalCount);
        Integer seedValue = getInputValue(INPUT_RANDOM_SEED_ID, this.randomSeed);
        BlockPos basePositionValue = getInputValue(INPUT_BASE_POSITION_ID, new BlockPos(0, 0, 0));
        
        // 验证输入
        FlowerType currentFlowerType = FlowerType.fromString(flowerTypeStr);
        sizeValue = Math.max(0.1f, Math.min(5.0f, sizeValue != null ? sizeValue : 1.0f));
        petalCountValue = Math.max(3, Math.min(12, petalCountValue != null ? petalCountValue : 5));
        seedValue = seedValue != null ? seedValue : 12345;
        
        if (basePositionValue == null) {
            basePositionValue = new BlockPos(0, 0, 0);
        }
        
        try {
            // 生成花朵结构
            PlantStructure plantStructure = generateFlower(currentFlowerType, sizeValue, petalCountValue, seedValue, basePositionValue);
            
            // 设置元数据
            plantStructure.setMetadata("flower_type", currentFlowerType.getId());
            plantStructure.setMetadata("size", sizeValue);
            plantStructure.setMetadata("petal_count", petalCountValue);
            plantStructure.setMetadata("seed", seedValue);
            
            // 创建花朵信息字符串
            String flowerInfo = String.format("Flower: %s, Size: %.1fx, Petals: %d, Blocks: %d",
                currentFlowerType.getDisplayName(), sizeValue, petalCountValue, 
                plantStructure.getTotalBlockCount());
            
            // 设置输出
            outputValues.put(OUTPUT_PLANT_STRUCTURE_ID, plantStructure);
            outputValues.put(OUTPUT_FLOWER_INFO_ID, flowerInfo);
            
        } catch (Exception e) {
            // 出错时输出空的植物结构
            LOGGER.error("Error in Flower Preset generation", e);
            
            PlantStructure emptyPlant = new PlantStructure();
            outputValues.put(OUTPUT_PLANT_STRUCTURE_ID, emptyPlant);
            outputValues.put(OUTPUT_FLOWER_INFO_ID, "Error generating flower");
        }
    }
    
    /**
     * 生成花朵结构
     */
    private PlantStructure generateFlower(FlowerType flowerType, float size, int petalCount, int seed, BlockPos basePosition) {
        PlantStructure plant = new PlantStructure();
        Random random = new Random(seed);
        
        switch (flowerType) {
            case SIMPLE_FLOWER:
                generateSimpleFlower(plant, basePosition, size);
                break;
            case ROSE:
                generateRose(plant, basePosition, size, random);
                break;
            case SUNFLOWER:
                generateSunflower(plant, basePosition, size, random);
                break;
            case TULIP:
                generateTulip(plant, basePosition, size);
                break;
            case DANDELION:
                generateDandelion(plant, basePosition, size, random);
                break;
            case POPPY:
                generatePoppy(plant, basePosition, size);
                break;
            case ORCHID:
                generateOrchid(plant, basePosition, size, random);
                break;
            case LILY:
                generateLily(plant, basePosition, size, petalCount);
                break;
            default:
                generateSimpleFlower(plant, basePosition, size);
                break;
        }
        
        return plant;
    }
    
    /**
     * 生成简单花朵
     */
    private void generateSimpleFlower(PlantStructure plant, BlockPos base, float size) {
        int height = Math.max(1, (int) (2 * size));
        
        // 茎
        for (int y = 0; y < height; y++) {
            BlockPos pos = new BlockPos(base.getX(), base.getY() + y, base.getZ());
            plant.addTrunkBlock(pos, "minecraft:grass", 0.5f);
        }
        
        // 花朵中心
        BlockPos flowerPos = new BlockPos(base.getX(), base.getY() + height, base.getZ());
        plant.addFlowerBlock(flowerPos, "minecraft:poppy");
    }
    
    /**
     * 生成玫瑰
     */
    private void generateRose(PlantStructure plant, BlockPos base, float size, Random random) {
        int height = Math.max(2, (int) (3 * size));
        
        // 茎（带刺）
        for (int y = 0; y < height; y++) {
            BlockPos pos = new BlockPos(base.getX(), base.getY() + y, base.getZ());
            plant.addTrunkBlock(pos, "minecraft:cactus", 0.3f);
        }
        
        // 花朵 - 多层花瓣
        BlockPos flowerCenter = new BlockPos(base.getX(), base.getY() + height, base.getZ());
        plant.addFlowerBlock(flowerCenter, "minecraft:red_tulip");
        
        // 花瓣层
        for (int layer = 0; layer < 2; layer++) {
            int y = height + layer;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (random.nextFloat() < 0.7f) {
                        BlockPos petalPos = new BlockPos(base.getX() + dx, base.getY() + y, base.getZ() + dz);
                        plant.addFlowerBlock(petalPos, "minecraft:rose_bush");
                    }
                }
            }
        }
    }
    
    /**
     * 生成向日葵
     */
    private void generateSunflower(PlantStructure plant, BlockPos base, float size, Random random) {
        int height = Math.max(3, (int) (4 * size));
        
        // 粗茎
        for (int y = 0; y < height; y++) {
            BlockPos pos = new BlockPos(base.getX(), base.getY() + y, base.getZ());
            plant.addTrunkBlock(pos, "minecraft:bamboo", 0.8f);
        }
        
        // 大花盘
        BlockPos center = new BlockPos(base.getX(), base.getY() + height, base.getZ());
        plant.addFlowerBlock(center, "minecraft:sunflower");
        
        // 花瓣环
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance >= 1.5 && distance <= 2.5) {
                    BlockPos petalPos = new BlockPos(base.getX() + dx, base.getY() + height, base.getZ() + dz);
                    plant.addFlowerBlock(petalPos, "minecraft:yellow_concrete");
                }
            }
        }
    }
    
    /**
     * 生成郁金香
     */
    private void generateTulip(PlantStructure plant, BlockPos base, float size) {
        int height = Math.max(1, (int) (2 * size));
        
        // 茎
        for (int y = 0; y < height; y++) {
            BlockPos pos = new BlockPos(base.getX(), base.getY() + y, base.getZ());
            plant.addTrunkBlock(pos, "minecraft:grass", 0.3f);
        }
        
        // 郁金香花朵
        BlockPos flowerPos = new BlockPos(base.getX(), base.getY() + height, base.getZ());
        plant.addFlowerBlock(flowerPos, "minecraft:pink_tulip");
        
        // 叶子
        if (size > 0.5f) {
            BlockPos leafPos1 = new BlockPos(base.getX() + 1, base.getY() + height - 1, base.getZ());
            BlockPos leafPos2 = new BlockPos(base.getX() - 1, base.getY() + height - 1, base.getZ());
            plant.addLeafBlock(leafPos1, "minecraft:grass");
            plant.addLeafBlock(leafPos2, "minecraft:grass");
        }
    }
    
    /**
     * 生成蒲公英
     */
    private void generateDandelion(PlantStructure plant, BlockPos base, float size, Random random) {
        int height = Math.max(1, (int) (1 * size));
        
        // 短茎
        BlockPos stemPos = new BlockPos(base.getX(), base.getY() + height, base.getZ());
        plant.addTrunkBlock(stemPos, "minecraft:grass", 0.2f);
        
        // 蒲公英花朵
        BlockPos flowerPos = new BlockPos(base.getX(), base.getY() + height + 1, base.getZ());
        plant.addFlowerBlock(flowerPos, "minecraft:dandelion");
        
        // 绒球效果（种子）
        if (random.nextFloat() < 0.3f) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (random.nextFloat() < 0.4f) {
                        BlockPos seedPos = new BlockPos(base.getX() + dx, base.getY() + height + 2, base.getZ() + dz);
                        plant.addFlowerBlock(seedPos, "minecraft:white_wool");
                    }
                }
            }
        }
    }
    
    /**
     * 生成罂粟
     */
    private void generatePoppy(PlantStructure plant, BlockPos base, float size) {
        int height = Math.max(1, (int) (2 * size));
        
        // 茎
        for (int y = 0; y < height; y++) {
            BlockPos pos = new BlockPos(base.getX(), base.getY() + y, base.getZ());
            plant.addTrunkBlock(pos, "minecraft:grass", 0.3f);
        }
        
        // 罂粟花朵
        BlockPos flowerPos = new BlockPos(base.getX(), base.getY() + height, base.getZ());
        plant.addFlowerBlock(flowerPos, "minecraft:poppy");
    }
    
    /**
     * 生成兰花
     */
    private void generateOrchid(PlantStructure plant, BlockPos base, float size, Random random) {
        int height = Math.max(2, (int) (3 * size));
        
        // 细茎
        for (int y = 0; y < height; y++) {
            BlockPos pos = new BlockPos(base.getX(), base.getY() + y, base.getZ());
            plant.addTrunkBlock(pos, "minecraft:bamboo", 0.2f);
        }
        
        // 兰花花朵
        BlockPos flowerPos = new BlockPos(base.getX(), base.getY() + height, base.getZ());
        plant.addFlowerBlock(flowerPos, "minecraft:blue_orchid");
        
        // 额外花朵（成串）
        if (size > 1.0f) {
            for (int i = 1; i <= 2; i++) {
                if (random.nextFloat() < 0.6f) {
                    BlockPos extraFlower = new BlockPos(base.getX(), base.getY() + height - i, base.getZ());
                    plant.addFlowerBlock(extraFlower, "minecraft:blue_orchid");
                }
            }
        }
    }
    
    /**
     * 生成百合
     */
    private void generateLily(PlantStructure plant, BlockPos base, float size, int petalCount) {
        int height = Math.max(2, (int) (3 * size));
        
        // 茎
        for (int y = 0; y < height; y++) {
            BlockPos pos = new BlockPos(base.getX(), base.getY() + y, base.getZ());
            plant.addTrunkBlock(pos, "minecraft:grass", 0.4f);
        }
        
        // 百合花朵中心
        BlockPos center = new BlockPos(base.getX(), base.getY() + height, base.getZ());
        plant.addFlowerBlock(center, "minecraft:white_tulip");
        
        // 花瓣（根据petalCount参数）
        double angleStep = 2 * Math.PI / petalCount;
        for (int i = 0; i < petalCount; i++) {
            double angle = i * angleStep;
            int dx = (int) Math.round(Math.cos(angle) * size);
            int dz = (int) Math.round(Math.sin(angle) * size);
            
            BlockPos petalPos = new BlockPos(base.getX() + dx, base.getY() + height, base.getZ() + dz);
            plant.addFlowerBlock(petalPos, "minecraft:white_concrete");
        }
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
    
    public FlowerType getFlowerType() {
        return flowerType;
    }
    
    public void setFlowerType(FlowerType flowerType) {
        this.flowerType = flowerType != null ? flowerType : FlowerType.SIMPLE_FLOWER;
        markDirty();
    }
    
    public void setFlowerType(String flowerTypeStr) {
        setFlowerType(FlowerType.fromString(flowerTypeStr));
    }
    
    public float getSize() {
        return size;
    }
    
    public void setSize(float size) {
        this.size = Math.max(0.1f, Math.min(5.0f, size));
        markDirty();
    }
    
    public int getPetalCount() {
        return petalCount;
    }
    
    public void setPetalCount(int petalCount) {
        this.petalCount = Math.max(3, Math.min(12, petalCount));
        markDirty();
    }
    
    public int getRandomSeed() {
        return randomSeed;
    }
    
    public void setRandomSeed(int randomSeed) {
        this.randomSeed = randomSeed;
        markDirty();
    }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("flowerType", getFlowerType().getId());
        state.put("size", getSize());
        state.put("petalCount", getPetalCount());
        state.put("randomSeed", getRandomSeed());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("flowerType")) {
                Object flowerTypeObj = stateMap.get("flowerType");
                if (flowerTypeObj instanceof String) {
                    setFlowerType((String) flowerTypeObj);
                }
            }
            
            if (stateMap.containsKey("size")) {
                Object sizeObj = stateMap.get("size");
                if (sizeObj instanceof Number) {
                    setSize(((Number) sizeObj).floatValue());
                }
            }
            
            if (stateMap.containsKey("petalCount")) {
                Object petalCountObj = stateMap.get("petalCount");
                if (petalCountObj instanceof Number) {
                    setPetalCount(((Number) petalCountObj).intValue());
                }
            }
            
            if (stateMap.containsKey("randomSeed")) {
                Object seedObj = stateMap.get("randomSeed");
                if (seedObj instanceof Number) {
                    setRandomSeed(((Number) seedObj).intValue());
                }
            }
        }
    }
} 
package com.nodecraft.nodesystem.nodes.flora.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import com.nodecraft.nodesystem.datatypes.PlantStructure;
import com.nodecraft.nodesystem.nodes.flora.algorithms.LSystemInterpreter;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bush Preset 节点: 提供快速生成各种灌木类型的方式
 */
@NodeInfo(
    id = "flora.generators.bush_preset",
    displayName = "Bush Preset",
    description = "Provides quick generation of various bush types",
    category = "flora.generators"
)
public class BushPresetNode extends BaseNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(BushPresetNode.class);
    
    /**
     * 灌木类型枚举
     */
    public enum BushType {
        DENSE_BUSH("Dense Bush", "密集灌木"),
        SPRAWLING_BUSH("Sprawling Bush", "蔓延灌木"),
        FLOWER_BUSH("Flower Bush", "开花灌木"),
        BERRY_BUSH("Berry Bush", "浆果灌木"),
        THORN_BUSH("Thorn Bush", "荆棘灌木");
        
        private final String id;
        private final String displayName;
        
        BushType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
        
        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        
        public static BushType fromString(String str) {
            for (BushType type : values()) {
                if (type.id.equalsIgnoreCase(str) || type.displayName.equals(str)) {
                    return type;
                }
            }
            return DENSE_BUSH; // 默认返回密集灌木
        }
    }
    
    // --- 节点属性 ---
    private BushType bushType = BushType.DENSE_BUSH;  // 灌木类型
    private float radius = 3.0f;                      // 灌木的大致半径
    private float density = 0.7f;                     // 密集度
    private int randomSeed = 12345;                   // 随机种子
    private String description = "生成各种灌木丛";
    
    // --- 输入端口 IDs ---
    private static final String INPUT_BUSH_TYPE_ID = "input_bush_type";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_DENSITY_ID = "input_density";
    private static final String INPUT_RANDOM_SEED_ID = "input_random_seed";
    private static final String INPUT_BASE_POSITION_ID = "input_base_position";
    
    // --- 输出端口 IDs ---
    private static final String OUTPUT_PLANT_STRUCTURE_ID = "output_plant_structure";
    private static final String OUTPUT_BUSH_INFO_ID = "output_bush_info";
    
    /**
     * 构造一个新的灌木预设节点
     */
    public BushPresetNode() {
        super(UUID.randomUUID(), "flora.generators.bush_preset");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_BUSH_TYPE_ID, "Bush Type", 
                "灌木类型（Dense Bush、Sprawling Bush等）", NodeDataType.BUSH_TYPE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", 
                "灌木的大致半径", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_DENSITY_ID, "Density", 
                "密集度（0-1）", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_RANDOM_SEED_ID, "Random Seed", 
                "用于生成不同变体的随机种子", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_BASE_POSITION_ID, "Base Position", 
                "灌木的底部中心坐标", NodeDataType.COORDINATE, this));
        
        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_PLANT_STRUCTURE_ID, "Plant Structure", 
                "生成的灌木结构", NodeDataType.PLANT_STRUCTURE, this));
        addOutputPort(new BasePort(OUTPUT_BUSH_INFO_ID, "Bush Info", 
                "灌木的详细信息", NodeDataType.STRING, this));
    }
    
    @Override
    public String getDescription() {
        return this.description;
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        String bushTypeStr = getInputValue(INPUT_BUSH_TYPE_ID, this.bushType.getId());
        Float radiusValue = getInputValue(INPUT_RADIUS_ID, this.radius);
        Float densityValue = getInputValue(INPUT_DENSITY_ID, this.density);
        Integer seedValue = getInputValue(INPUT_RANDOM_SEED_ID, this.randomSeed);
        BlockPos basePositionValue = getInputValue(INPUT_BASE_POSITION_ID, new BlockPos(0, 0, 0));
        
        // 验证输入
        BushType currentBushType = BushType.fromString(bushTypeStr);
        radiusValue = Math.max(0.5f, Math.min(10.0f, radiusValue != null ? radiusValue : 3.0f));
        densityValue = Math.max(0.1f, Math.min(1.0f, densityValue != null ? densityValue : 0.7f));
        seedValue = seedValue != null ? seedValue : 12345;
        
        if (basePositionValue == null) {
            basePositionValue = new BlockPos(0, 0, 0);
        }
        
        try {
            // 根据灌木类型生成对应的L-系统参数
            BushConfig config = getBushConfig(currentBushType, radiusValue, densityValue);
            
            // 生成L-系统字符串
            String generatedString = LSystemInterpreter.generateString(
                config.axiom, config.rules, config.iterations, seedValue
            );
            
            // 解释L-系统字符串，生成植物结构
            PlantStructure plantStructure = LSystemInterpreter.interpret(
                generatedString,
                basePositionValue,
                config.segmentLength,
                config.segmentWidth,
                config.angle,
                config.lengthDecay,
                config.widthDecay
            );
            
            // 应用灌木特定的方块类型
            applyBushMaterials(plantStructure, currentBushType);
            
            // 设置元数据
            plantStructure.setMetadata("bush_type", currentBushType.getId());
            plantStructure.setMetadata("radius", radiusValue);
            plantStructure.setMetadata("density", densityValue);
            plantStructure.setMetadata("seed", seedValue);
            plantStructure.setMetadata("generated_string", generatedString);
            
            // 创建灌木信息字符串
            String bushInfo = String.format("Bush: %s, Radius: %.1f, Density: %.1f, Blocks: %d",
                currentBushType.getDisplayName(), radiusValue, densityValue, 
                plantStructure.getTotalBlockCount());
            
            // 设置输出
            outputValues.put(OUTPUT_PLANT_STRUCTURE_ID, plantStructure);
            outputValues.put(OUTPUT_BUSH_INFO_ID, bushInfo);
            
        } catch (Exception e) {
            // 出错时输出空的植物结构
            LOGGER.error("Error in Bush Preset generation", e);
            
            PlantStructure emptyPlant = new PlantStructure();
            outputValues.put(OUTPUT_PLANT_STRUCTURE_ID, emptyPlant);
            outputValues.put(OUTPUT_BUSH_INFO_ID, "Error generating bush");
        }
    }
    
    /**
     * 灌木配置信息
     */
    private static class BushConfig {
        String axiom;
        List<LSystemRule> rules;
        int iterations;
        float segmentLength;
        float segmentWidth;
        float angle;
        float lengthDecay;
        float widthDecay;
    }
    
    /**
     * 根据灌木类型获取配置
     */
    private BushConfig getBushConfig(BushType bushType, float radius, float density) {
        BushConfig config = new BushConfig();
        config.rules = new ArrayList<>();
        
        // 计算基础参数
        float baseLength = radius / 3.0f;
        float baseAngle = 30.0f + (density * 30.0f); // 30-60度
        int iterations = Math.max(2, Math.min(4, (int) (radius / 1.5f)));
        
        switch (bushType) {
            case DENSE_BUSH:
                config.axiom = "F";
                config.rules.add(new LSystemRule("F", "F[+F][F][-F]", 0.8f + density * 0.2f));
                config.rules.add(new LSystemRule("F", "F[+F][-F]F[L]", 0.6f));
                config.iterations = iterations;
                config.segmentLength = baseLength;
                config.segmentWidth = 0.8f;
                config.angle = baseAngle;
                config.lengthDecay = 0.7f;
                config.widthDecay = 0.8f;
                break;
                
            case SPRAWLING_BUSH:
                config.axiom = "F";
                config.rules.add(new LSystemRule("F", "F[++F][--F]f[L]", 0.7f));
                config.rules.add(new LSystemRule("F", "F[&+F][&-F]F", 0.5f));
                config.iterations = iterations + 1;
                config.segmentLength = baseLength * 1.3f;
                config.segmentWidth = 0.6f;
                config.angle = baseAngle * 1.5f; // 更大角度，更蔓延
                config.lengthDecay = 0.8f;
                config.widthDecay = 0.9f;
                break;
                
            case FLOWER_BUSH:
                config.axiom = "F";
                config.rules.add(new LSystemRule("F", "F[+F[W]][F][-F[W]]", 0.7f));
                config.rules.add(new LSystemRule("F", "F[+F][-F]F[L][W]", 0.6f));
                config.iterations = iterations;
                config.segmentLength = baseLength;
                config.segmentWidth = 0.7f;
                config.angle = baseAngle;
                config.lengthDecay = 0.75f;
                config.widthDecay = 0.85f;
                break;
                
            case BERRY_BUSH:
                config.axiom = "F";
                config.rules.add(new LSystemRule("F", "F[+F[L]]F[-F[L]]", 0.8f));
                config.rules.add(new LSystemRule("F", "F[+F][-F]F[W]", 0.4f)); // W代表浆果
                config.iterations = iterations;
                config.segmentLength = baseLength * 0.8f;
                config.segmentWidth = 0.9f;
                config.angle = baseAngle * 0.8f;
                config.lengthDecay = 0.7f;
                config.widthDecay = 0.8f;
                break;
                
            case THORN_BUSH:
                config.axiom = "F";
                config.rules.add(new LSystemRule("F", "F[++F][--F]F", 0.9f));
                config.rules.add(new LSystemRule("F", "F[+F][-F][L]", 0.5f));
                config.iterations = iterations;
                config.segmentLength = baseLength * 0.9f;
                config.segmentWidth = 1.0f;
                config.angle = baseAngle * 1.2f;
                config.lengthDecay = 0.8f;
                config.widthDecay = 0.9f;
                break;
                
            default:
                // 使用密集灌木作为默认
                config = getBushConfig(BushType.DENSE_BUSH, radius, density);
                break;
        }
        
        return config;
    }
    
    /**
     * 应用灌木特定的材质
     */
    private void applyBushMaterials(PlantStructure plant, BushType bushType) {
        String logType, leafType, flowerType;
        
        switch (bushType) {
            case DENSE_BUSH:
                logType = "minecraft:oak_log";
                leafType = "minecraft:oak_leaves";
                flowerType = "minecraft:grass";
                break;
            case SPRAWLING_BUSH:
                logType = "minecraft:birch_log";
                leafType = "minecraft:birch_leaves";
                flowerType = "minecraft:fern";
                break;
            case FLOWER_BUSH:
                logType = "minecraft:oak_log";
                leafType = "minecraft:azalea_leaves";
                flowerType = "minecraft:flowering_azalea_leaves";
                break;
            case BERRY_BUSH:
                logType = "minecraft:oak_log";
                leafType = "minecraft:oak_leaves";
                flowerType = "minecraft:sweet_berry_bush";
                break;
            case THORN_BUSH:
                logType = "minecraft:dark_oak_log";
                leafType = "minecraft:dark_oak_leaves";
                flowerType = "minecraft:dead_bush";
                break;
            default:
                logType = "minecraft:oak_log";
                leafType = "minecraft:oak_leaves";
                flowerType = "minecraft:grass";
                break;
        }
        
        // 更新方块类型
        for (PlantStructure.PlantBlock block : plant.getTrunkBlocks()) {
            block.setBlockType(logType);
        }
        for (PlantStructure.PlantBlock block : plant.getBranchBlocks()) {
            block.setBlockType(logType);
        }
        for (PlantStructure.PlantBlock block : plant.getLeafBlocks()) {
            block.setBlockType(leafType);
        }
        for (PlantStructure.PlantBlock block : plant.getFlowerBlocks()) {
            block.setBlockType(flowerType);
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
    
    public BushType getBushType() {
        return bushType;
    }
    
    public void setBushType(BushType bushType) {
        this.bushType = bushType != null ? bushType : BushType.DENSE_BUSH;
        markDirty();
    }
    
    public void setBushType(String bushTypeStr) {
        setBushType(BushType.fromString(bushTypeStr));
    }
    
    public float getRadius() {
        return radius;
    }
    
    public void setRadius(float radius) {
        this.radius = Math.max(0.5f, Math.min(10.0f, radius));
        markDirty();
    }
    
    public float getDensity() {
        return density;
    }
    
    public void setDensity(float density) {
        this.density = Math.max(0.1f, Math.min(1.0f, density));
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
        state.put("bushType", getBushType().getId());
        state.put("radius", getRadius());
        state.put("density", getDensity());
        state.put("randomSeed", getRandomSeed());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("bushType")) {
                Object bushTypeObj = stateMap.get("bushType");
                if (bushTypeObj instanceof String) {
                    setBushType((String) bushTypeObj);
                }
            }
            
            if (stateMap.containsKey("radius")) {
                Object radiusObj = stateMap.get("radius");
                if (radiusObj instanceof Number) {
                    setRadius(((Number) radiusObj).floatValue());
                }
            }
            
            if (stateMap.containsKey("density")) {
                Object densityObj = stateMap.get("density");
                if (densityObj instanceof Number) {
                    setDensity(((Number) densityObj).floatValue());
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
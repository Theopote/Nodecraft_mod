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
 * Tree Preset 节点: 提供快速生成常见树木类型的方式，隐藏L-系统复杂性
 */
@NodeInfo(
    id = "flora.generators.tree_preset",
    displayName = "Tree Preset",
    description = "Provides quick generation of common tree types, hiding L-system complexity",
    category = "flora.generators"
)
public class TreePresetNode extends BaseNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(TreePresetNode.class);
    
    /**
     * 树木类型枚举
     */
    public enum TreeType {
        OAK("Oak", "橡树"),
        BIRCH("Birch", "白桦"),
        SPRUCE("Spruce", "云杉"),
        JUNGLE("Jungle", "丛林"),
        ACACIA("Acacia", "金合欢"),
        DARK_OAK("Dark Oak", "深色橡木"),
        CHERRY("Cherry", "樱花树");
        
        private final String id;
        private final String displayName;
        
        TreeType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
        
        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        
        public static TreeType fromString(String str) {
            for (TreeType type : values()) {
                if (type.id.equalsIgnoreCase(str) || type.displayName.equals(str)) {
                    return type;
                }
            }
            return OAK; // 默认返回橡树
        }
    }
    
    // --- 节点属性 ---
    private TreeType treeType = TreeType.OAK;      // 树木类型
    private int height = 8;                        // 树木的大致高度
    private float branchingDensity = 0.7f;         // 树枝的密集程度
    private float leafiness = 0.8f;               // 叶子的茂密程度
    private int randomSeed = 12345;                // 随机种子
    private String description = "提供快速生成常见树木类型的方式，隐藏L-系统复杂性";
    
    // --- 输入端口 IDs ---
    private static final String INPUT_TREE_TYPE_ID = "input_tree_type";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_BRANCHING_DENSITY_ID = "input_branching_density";
    private static final String INPUT_LEAFINESS_ID = "input_leafiness";
    private static final String INPUT_RANDOM_SEED_ID = "input_random_seed";
    private static final String INPUT_BASE_POSITION_ID = "input_base_position";
    
    // --- 输出端口 IDs ---
    private static final String OUTPUT_PLANT_STRUCTURE_ID = "output_plant_structure";
    private static final String OUTPUT_TREE_INFO_ID = "output_tree_info";
    
    /**
     * 构造一个新的树木预设节点
     */
    public TreePresetNode() {
        super(UUID.randomUUID(), "flora.generators.tree_preset");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_TREE_TYPE_ID, "Tree Type", 
                "树木类型（Oak、Birch、Spruce等）", NodeDataType.TREE_TYPE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", 
                "树木的大致高度", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_BRANCHING_DENSITY_ID, "Branching Density", 
                "树枝的密集程度（0-1）", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_LEAFINESS_ID, "Leafiness", 
                "叶子的茂密程度（0-1）", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_RANDOM_SEED_ID, "Random Seed", 
                "用于生成不同变体的随机种子", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_BASE_POSITION_ID, "Base Position", 
                "树木的底部中心坐标", NodeDataType.COORDINATE, this));
        
        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_PLANT_STRUCTURE_ID, "Plant Structure", 
                "预配置L-系统生成的树木结构", NodeDataType.PLANT_STRUCTURE, this));
        addOutputPort(new BasePort(OUTPUT_TREE_INFO_ID, "Tree Info", 
                "树木的详细信息（类型、参数等）", NodeDataType.STRING, this));
    }
    
    @Override
    public String getDescription() {
        return this.description;
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        String treeTypeStr = getInputValue(INPUT_TREE_TYPE_ID, this.treeType.getId());
        Integer heightValue = getInputValue(INPUT_HEIGHT_ID, this.height);
        Float branchingDensityValue = getInputValue(INPUT_BRANCHING_DENSITY_ID, this.branchingDensity);
        Float leafinessValue = getInputValue(INPUT_LEAFINESS_ID, this.leafiness);
        Integer seedValue = getInputValue(INPUT_RANDOM_SEED_ID, this.randomSeed);
        BlockPos basePositionValue = getInputValue(INPUT_BASE_POSITION_ID, new BlockPos(0, 0, 0));
        
        // 验证输入
        TreeType currentTreeType = TreeType.fromString(treeTypeStr);
        heightValue = Math.max(3, Math.min(50, heightValue != null ? heightValue : 8));
        branchingDensityValue = Math.max(0.0f, Math.min(1.0f, branchingDensityValue != null ? branchingDensityValue : 0.7f));
        leafinessValue = Math.max(0.0f, Math.min(1.0f, leafinessValue != null ? leafinessValue : 0.8f));
        seedValue = seedValue != null ? seedValue : 12345;
        
        if (basePositionValue == null) {
            basePositionValue = new BlockPos(0, 0, 0);
        }
        
        try {
            // 根据树木类型生成对应的L-系统参数
            TreeConfig config = getTreeConfig(currentTreeType, heightValue, branchingDensityValue, leafinessValue);
            
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
            
            // 应用树木特定的方块类型
            applyTreeMaterials(plantStructure, currentTreeType);
            
            // 设置元数据
            plantStructure.setMetadata("tree_type", currentTreeType.getId());
            plantStructure.setMetadata("height", heightValue);
            plantStructure.setMetadata("branching_density", branchingDensityValue);
            plantStructure.setMetadata("leafiness", leafinessValue);
            plantStructure.setMetadata("seed", seedValue);
            plantStructure.setMetadata("generated_string", generatedString);
            
            // 创建树木信息字符串
            String treeInfo = String.format("Tree: %s, Height: %d, Branches: %.1f, Leaves: %.1f, Blocks: %d",
                currentTreeType.getDisplayName(), heightValue, branchingDensityValue, 
                leafinessValue, plantStructure.getTotalBlockCount());
            
            // 设置输出
            outputValues.put(OUTPUT_PLANT_STRUCTURE_ID, plantStructure);
            outputValues.put(OUTPUT_TREE_INFO_ID, treeInfo);
            
        } catch (Exception e) {
            // 出错时输出空的植物结构
            LOGGER.error("Error in Tree Preset generation", e);
            
            PlantStructure emptyPlant = new PlantStructure();
            outputValues.put(OUTPUT_PLANT_STRUCTURE_ID, emptyPlant);
            outputValues.put(OUTPUT_TREE_INFO_ID, "Error generating tree");
        }
    }
    
    /**
     * 树木配置信息
     */
    private static class TreeConfig {
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
     * 根据树木类型获取配置
     */
    private TreeConfig getTreeConfig(TreeType treeType, int height, float branchingDensity, float leafiness) {
        TreeConfig config = new TreeConfig();
        config.rules = new ArrayList<>();
        
        // 计算基础参数
        float baseLength = height / 4.0f;
        float baseAngle = 20.0f + (branchingDensity * 20.0f); // 20-40度
        int iterations = Math.max(2, Math.min(5, height / 3));
        
        switch (treeType) {
            case OAK:
                config.axiom = "F";
                config.rules.add(new LSystemRule("F", "F[+F]F[-F][F]", 0.8f + branchingDensity * 0.2f));
                config.rules.add(new LSystemRule("F", "FF", 0.2f));
                config.iterations = iterations;
                config.segmentLength = baseLength;
                config.segmentWidth = 1.0f;
                config.angle = baseAngle;
                config.lengthDecay = 0.75f;
                config.widthDecay = 0.8f;
                break;
                
            case BIRCH:
                config.axiom = "F";
                config.rules.add(new LSystemRule("F", "F[+F][-F]F", 0.9f));
                config.rules.add(new LSystemRule("F", "FF", 0.1f));
                config.iterations = iterations;
                config.segmentLength = baseLength * 1.2f;
                config.segmentWidth = 0.8f;
                config.angle = baseAngle * 0.8f; // 更直立
                config.lengthDecay = 0.8f;
                config.widthDecay = 0.85f;
                break;
                
            case SPRUCE:
                config.axiom = "F";
                config.rules.add(new LSystemRule("F", "F[&&&+++F][&&&---F]F", 0.8f));
                config.rules.add(new LSystemRule("F", "F[++F][--F]F", 0.2f));
                config.iterations = iterations + 1;
                config.segmentLength = baseLength * 0.8f;
                config.segmentWidth = 1.2f;
                config.angle = baseAngle * 0.6f; // 小角度，锥形
                config.lengthDecay = 0.7f;
                config.widthDecay = 0.75f;
                break;
                
            case JUNGLE:
                config.axiom = "F";
                config.rules.add(new LSystemRule("F", "F[++F[+F]]F[--F[-F]]F", 0.7f));
                config.rules.add(new LSystemRule("F", "F[+++F][---F]F", 0.3f));
                config.iterations = iterations + 1;
                config.segmentLength = baseLength * 1.5f;
                config.segmentWidth = 1.5f;
                config.angle = baseAngle * 1.5f; // 大角度，茂密
                config.lengthDecay = 0.6f;
                config.widthDecay = 0.7f;
                break;
                
            case ACACIA:
                config.axiom = "F";
                config.rules.add(new LSystemRule("F", "F[&++++F][&----F]", 0.6f));
                config.rules.add(new LSystemRule("F", "FF[+++F][---F]", 0.4f));
                config.iterations = iterations;
                config.segmentLength = baseLength;
                config.segmentWidth = 1.0f;
                config.angle = baseAngle * 2.0f; // 很大角度，扁平
                config.lengthDecay = 0.8f;
                config.widthDecay = 0.9f;
                break;
                
            case DARK_OAK:
                config.axiom = "F";
                config.rules.add(new LSystemRule("F", "F[+F[+F]]F[-F[-F]]F", 0.8f));
                config.rules.add(new LSystemRule("F", "F[+F][-F]F", 0.2f));
                config.iterations = iterations;
                config.segmentLength = baseLength * 1.1f;
                config.segmentWidth = 1.3f;
                config.angle = baseAngle * 1.2f;
                config.lengthDecay = 0.7f;
                config.widthDecay = 0.75f;
                break;
                
            case CHERRY:
                config.axiom = "F";
                config.rules.add(new LSystemRule("F", "F[+F]F[-F]F[W]", 0.7f + leafiness * 0.3f));
                config.rules.add(new LSystemRule("F", "F[++F][--F]F", 0.3f));
                config.iterations = iterations;
                config.segmentLength = baseLength;
                config.segmentWidth = 0.9f;
                config.angle = baseAngle;
                config.lengthDecay = 0.8f;
                config.widthDecay = 0.85f;
                break;
                
            default:
                // 使用橡树作为默认
                config = getTreeConfig(TreeType.OAK, height, branchingDensity, leafiness);
                break;
        }
        
        // 根据茂密程度添加叶子
        if (leafiness > 0.5f) {
            for (LSystemRule rule : config.rules) {
                if (rule.getProduction().contains("F") && !rule.getProduction().contains("L")) {
                    // 在规则中添加叶子
                    String newProduction = rule.getProduction() + "[L]";
                    config.rules.add(new LSystemRule(rule.getSymbol(), newProduction, leafiness * 0.3f));
                }
            }
        }
        
        return config;
    }
    
    /**
     * 应用树木特定的材质
     */
    private void applyTreeMaterials(PlantStructure plant, TreeType treeType) {
        String logType, leafType;
        
        switch (treeType) {
            case OAK:
                logType = "minecraft:oak_log";
                leafType = "minecraft:oak_leaves";
                break;
            case BIRCH:
                logType = "minecraft:birch_log";
                leafType = "minecraft:birch_leaves";
                break;
            case SPRUCE:
                logType = "minecraft:spruce_log";
                leafType = "minecraft:spruce_leaves";
                break;
            case JUNGLE:
                logType = "minecraft:jungle_log";
                leafType = "minecraft:jungle_leaves";
                break;
            case ACACIA:
                logType = "minecraft:acacia_log";
                leafType = "minecraft:acacia_leaves";
                break;
            case DARK_OAK:
                logType = "minecraft:dark_oak_log";
                leafType = "minecraft:dark_oak_leaves";
                break;
            case CHERRY:
                logType = "minecraft:cherry_log";
                leafType = "minecraft:cherry_leaves";
                break;
            default:
                logType = "minecraft:oak_log";
                leafType = "minecraft:oak_leaves";
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
            if (treeType == TreeType.CHERRY) {
                block.setBlockType("minecraft:pink_petals");
            } else {
                block.setBlockType("minecraft:poppy");
            }
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
    
    public TreeType getTreeType() {
        return treeType;
    }
    
    public void setTreeType(TreeType treeType) {
        this.treeType = treeType != null ? treeType : TreeType.OAK;
        markDirty();
    }
    
    public void setTreeType(String treeTypeStr) {
        setTreeType(TreeType.fromString(treeTypeStr));
    }
    
    public int getHeight() {
        return height;
    }
    
    public void setHeight(int height) {
        this.height = Math.max(3, Math.min(50, height));
        markDirty();
    }
    
    public float getBranchingDensity() {
        return branchingDensity;
    }
    
    public void setBranchingDensity(float branchingDensity) {
        this.branchingDensity = Math.max(0.0f, Math.min(1.0f, branchingDensity));
        markDirty();
    }
    
    public float getLeafiness() {
        return leafiness;
    }
    
    public void setLeafiness(float leafiness) {
        this.leafiness = Math.max(0.0f, Math.min(1.0f, leafiness));
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
        state.put("treeType", getTreeType().getId());
        state.put("height", getHeight());
        state.put("branchingDensity", getBranchingDensity());
        state.put("leafiness", getLeafiness());
        state.put("randomSeed", getRandomSeed());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("treeType")) {
                Object treeTypeObj = stateMap.get("treeType");
                if (treeTypeObj instanceof String) {
                    setTreeType((String) treeTypeObj);
                }
            }
            
            if (stateMap.containsKey("height")) {
                Object heightObj = stateMap.get("height");
                if (heightObj instanceof Number) {
                    setHeight(((Number) heightObj).intValue());
                }
            }
            
            if (stateMap.containsKey("branchingDensity")) {
                Object densityObj = stateMap.get("branchingDensity");
                if (densityObj instanceof Number) {
                    setBranchingDensity(((Number) densityObj).floatValue());
                }
            }
            
            if (stateMap.containsKey("leafiness")) {
                Object leafinessObj = stateMap.get("leafiness");
                if (leafinessObj instanceof Number) {
                    setLeafiness(((Number) leafinessObj).floatValue());
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
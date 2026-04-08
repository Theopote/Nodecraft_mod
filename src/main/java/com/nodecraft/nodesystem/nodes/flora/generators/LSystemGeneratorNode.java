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
 * L-System Generator 节点: 最基础、最灵活的L-系统实现
 */
@NodeInfo(
    id = "flora.generators.l_system_generator",
    displayName = "L-System Generator",
    description = "Most basic and flexible L-system implementation for experienced users",
    category = "flora.generators"
)
public class LSystemGeneratorNode extends BaseNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSystemGeneratorNode.class);
    
    // --- 节点属性 ---
    private String axiom = "F";                    // 初始字符串
    private int iterations = 3;                    // L-系统迭代次数
    private float angle = 25.0f;                   // 每次旋转的角度
    private float initialSegmentLength = 3.0f;     // 初始枝条长度
    private float lengthDecayFactor = 0.8f;        // 长度衰减因子
    private float initialSegmentWidth = 1.0f;      // 初始树干宽度
    private float widthDecayFactor = 0.8f;         // 宽度衰减因子
    private int seed = 12345;                      // 随机种子
    private String description = "最基础、最灵活的L-系统实现，适合有经验的用户";
    
    // --- 输入端口 IDs ---
    private static final String INPUT_AXIOM_ID = "input_axiom";
    private static final String INPUT_RULES_ID = "input_rules";
    private static final String INPUT_ITERATIONS_ID = "input_iterations";
    private static final String INPUT_ANGLE_ID = "input_angle";
    private static final String INPUT_INITIAL_LENGTH_ID = "input_initial_length";
    private static final String INPUT_LENGTH_DECAY_ID = "input_length_decay";
    private static final String INPUT_INITIAL_WIDTH_ID = "input_initial_width";
    private static final String INPUT_WIDTH_DECAY_ID = "input_width_decay";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_BASE_POSITION_ID = "input_base_position";
    
    // --- 输出端口 IDs ---
    private static final String OUTPUT_PLANT_STRUCTURE_ID = "output_plant_structure";
    private static final String OUTPUT_GENERATED_STRING_ID = "output_generated_string";
    private static final String OUTPUT_BLOCK_COUNT_ID = "output_block_count";
    
    /**
     * 构造一个新的L-系统生成器节点
     */
    public LSystemGeneratorNode() {
        super(UUID.randomUUID(), "flora.generators.l_system_generator");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_AXIOM_ID, "Axiom", 
                "初始字符串（如 'F'）", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_RULES_ID, "Rules", 
                "L-系统规则的列表", NodeDataType.L_SYSTEM_RULE_LIST, this));
        addInputPort(new BasePort(INPUT_ITERATIONS_ID, "Iterations", 
                "L-系统迭代次数（树木的'生长'代数）", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle", 
                "每次旋转的角度（影响分支张开角度）", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_INITIAL_LENGTH_ID, "Initial Segment Length", 
                "初始枝条长度（方块数）", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_LENGTH_DECAY_ID, "Length Decay Factor", 
                "每次分支后长度的衰减因子（如 0.8）", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_INITIAL_WIDTH_ID, "Initial Segment Width", 
                "初始树干宽度（半径，影响方块粗细）", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_WIDTH_DECAY_ID, "Width Decay Factor", 
                "每次分支后宽度的衰减因子", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", 
                "随机种子（用于L-系统中的概率规则）", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_BASE_POSITION_ID, "Base Position", 
                "植物的底部中心坐标", NodeDataType.COORDINATE, this));
        
        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_PLANT_STRUCTURE_ID, "Plant Structure", 
                "生成的植物的三维结构信息", NodeDataType.PLANT_STRUCTURE, this));
        addOutputPort(new BasePort(OUTPUT_GENERATED_STRING_ID, "Generated String", 
                "生成的L-系统字符串", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_COUNT_ID, "Block Count", 
                "生成的方块总数", NodeDataType.INTEGER, this));
    }
    
    @Override
    public String getDescription() {
        return this.description;
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值，如果没有连接则使用默认值
        String axiomValue = getInputValue(INPUT_AXIOM_ID, this.axiom);
        List<?> rulesListObj = getInputValue(INPUT_RULES_ID, new ArrayList<>());
        Integer iterationsValue = getInputValue(INPUT_ITERATIONS_ID, this.iterations);
        Float angleValue = getInputValue(INPUT_ANGLE_ID, this.angle);
        Float initialLengthValue = getInputValue(INPUT_INITIAL_LENGTH_ID, this.initialSegmentLength);
        Float lengthDecayValue = getInputValue(INPUT_LENGTH_DECAY_ID, this.lengthDecayFactor);
        Float initialWidthValue = getInputValue(INPUT_INITIAL_WIDTH_ID, this.initialSegmentWidth);
        Float widthDecayValue = getInputValue(INPUT_WIDTH_DECAY_ID, this.widthDecayFactor);
        Integer seedValue = getInputValue(INPUT_SEED_ID, this.seed);
        BlockPos basePositionValue = getInputValue(INPUT_BASE_POSITION_ID, new BlockPos(0, 0, 0));
        
        // 验证和转换输入
        if (axiomValue == null || axiomValue.trim().isEmpty()) {
            axiomValue = "F";
        }
        
        // 转换规则列表
        List<LSystemRule> rules = new ArrayList<>();
        if (rulesListObj instanceof List) {
            for (Object obj : rulesListObj) {
                if (obj instanceof LSystemRule) {
                    rules.add((LSystemRule) obj);
                }
            }
        }
        
        // 验证数值输入
        iterationsValue = Math.max(0, Math.min(10, iterationsValue != null ? iterationsValue : 3));
        angleValue = angleValue != null ? angleValue : 25.0f;
        initialLengthValue = Math.max(0.1f, initialLengthValue != null ? initialLengthValue : 3.0f);
        lengthDecayValue = Math.max(0.1f, Math.min(1.0f, lengthDecayValue != null ? lengthDecayValue : 0.8f));
        initialWidthValue = Math.max(0.1f, initialWidthValue != null ? initialWidthValue : 1.0f);
        widthDecayValue = Math.max(0.1f, Math.min(1.0f, widthDecayValue != null ? widthDecayValue : 0.8f));
        seedValue = seedValue != null ? seedValue : 12345;
        
        if (basePositionValue == null) {
            basePositionValue = new BlockPos(0, 0, 0);
        }
        
        try {
            // 生成L-系统字符串
            String generatedString = LSystemInterpreter.generateString(
                axiomValue, rules, iterationsValue, seedValue
            );
            
            // 解释L-系统字符串，生成植物结构
            PlantStructure plantStructure = LSystemInterpreter.interpret(
                generatedString,
                basePositionValue,
                initialLengthValue,
                initialWidthValue,
                angleValue,
                lengthDecayValue,
                widthDecayValue
            );
            
            // 设置元数据
            plantStructure.setMetadata("axiom", axiomValue);
            plantStructure.setMetadata("iterations", iterationsValue);
            plantStructure.setMetadata("angle", angleValue);
            plantStructure.setMetadata("seed", seedValue);
            plantStructure.setMetadata("generated_string", generatedString);
            plantStructure.setMetadata("rule_count", rules.size());
            
            // 设置输出
            outputValues.put(OUTPUT_PLANT_STRUCTURE_ID, plantStructure);
            outputValues.put(OUTPUT_GENERATED_STRING_ID, generatedString);
            outputValues.put(OUTPUT_BLOCK_COUNT_ID, plantStructure.getTotalBlockCount());
            
        } catch (Exception e) {
            // 出错时输出空的植物结构
            LOGGER.error("Error in L-System generation", e);
            
            PlantStructure emptyPlant = new PlantStructure();
            outputValues.put(OUTPUT_PLANT_STRUCTURE_ID, emptyPlant);
            outputValues.put(OUTPUT_GENERATED_STRING_ID, "");
            outputValues.put(OUTPUT_BLOCK_COUNT_ID, 0);
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
                // 类型转换失败，返回默认值
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    // --- Getters and Setters ---
    
    public String getAxiom() {
        return axiom;
    }
    
    public void setAxiom(String axiom) {
        this.axiom = axiom != null ? axiom : "F";
        markDirty();
    }
    
    public int getIterations() {
        return iterations;
    }
    
    public void setIterations(int iterations) {
        this.iterations = Math.max(0, Math.min(10, iterations));
        markDirty();
    }
    
    public float getAngle() {
        return angle;
    }
    
    public void setAngle(float angle) {
        this.angle = angle;
        markDirty();
    }
    
    public float getInitialSegmentLength() {
        return initialSegmentLength;
    }
    
    public void setInitialSegmentLength(float initialSegmentLength) {
        this.initialSegmentLength = Math.max(0.1f, initialSegmentLength);
        markDirty();
    }
    
    public float getLengthDecayFactor() {
        return lengthDecayFactor;
    }
    
    public void setLengthDecayFactor(float lengthDecayFactor) {
        this.lengthDecayFactor = Math.max(0.1f, Math.min(1.0f, lengthDecayFactor));
        markDirty();
    }
    
    public float getInitialSegmentWidth() {
        return initialSegmentWidth;
    }
    
    public void setInitialSegmentWidth(float initialSegmentWidth) {
        this.initialSegmentWidth = Math.max(0.1f, initialSegmentWidth);
        markDirty();
    }
    
    public float getWidthDecayFactor() {
        return widthDecayFactor;
    }
    
    public void setWidthDecayFactor(float widthDecayFactor) {
        this.widthDecayFactor = Math.max(0.1f, Math.min(1.0f, widthDecayFactor));
        markDirty();
    }
    
    public int getSeed() {
        return seed;
    }
    
    public void setSeed(int seed) {
        this.seed = seed;
        markDirty();
    }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("axiom", getAxiom());
        state.put("iterations", getIterations());
        state.put("angle", getAngle());
        state.put("initialSegmentLength", getInitialSegmentLength());
        state.put("lengthDecayFactor", getLengthDecayFactor());
        state.put("initialSegmentWidth", getInitialSegmentWidth());
        state.put("widthDecayFactor", getWidthDecayFactor());
        state.put("seed", getSeed());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("axiom")) {
                Object axiomObj = stateMap.get("axiom");
                if (axiomObj instanceof String) {
                    setAxiom((String) axiomObj);
                }
            }
            
            if (stateMap.containsKey("iterations")) {
                Object iterationsObj = stateMap.get("iterations");
                if (iterationsObj instanceof Number) {
                    setIterations(((Number) iterationsObj).intValue());
                }
            }
            
            if (stateMap.containsKey("angle")) {
                Object angleObj = stateMap.get("angle");
                if (angleObj instanceof Number) {
                    setAngle(((Number) angleObj).floatValue());
                }
            }
            
            if (stateMap.containsKey("initialSegmentLength")) {
                Object lengthObj = stateMap.get("initialSegmentLength");
                if (lengthObj instanceof Number) {
                    setInitialSegmentLength(((Number) lengthObj).floatValue());
                }
            }
            
            if (stateMap.containsKey("lengthDecayFactor")) {
                Object decayObj = stateMap.get("lengthDecayFactor");
                if (decayObj instanceof Number) {
                    setLengthDecayFactor(((Number) decayObj).floatValue());
                }
            }
            
            if (stateMap.containsKey("initialSegmentWidth")) {
                Object widthObj = stateMap.get("initialSegmentWidth");
                if (widthObj instanceof Number) {
                    setInitialSegmentWidth(((Number) widthObj).floatValue());
                }
            }
            
            if (stateMap.containsKey("widthDecayFactor")) {
                Object widthDecayObj = stateMap.get("widthDecayFactor");
                if (widthDecayObj instanceof Number) {
                    setWidthDecayFactor(((Number) widthDecayObj).floatValue());
                }
            }
            
            if (stateMap.containsKey("seed")) {
                Object seedObj = stateMap.get("seed");
                if (seedObj instanceof Number) {
                    setSeed(((Number) seedObj).intValue());
                }
            }
        }
    }
} 
package com.nodecraft.nodesystem.nodes.input.context;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.minecraft.PlayerAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 获取玩家当前维度信息的节点
 */
@NodeInfo(
    id = "input.context.dimension_info",
    displayName = "维度信息",
    description = "获取玩家当前所在维度的信息",
    category = "input.context"
)
public class DimensionInfoNode extends BaseNode {
    
    // --- 输出端口 ---
    private static final String OUTPUT_DIMENSION_ID = "output_dimension_id";
    private static final String OUTPUT_IS_OVERWORLD_ID = "output_is_overworld";
    private static final String OUTPUT_IS_NETHER_ID = "output_is_nether";
    private static final String OUTPUT_IS_END_ID = "output_is_end";
    private static final String OUTPUT_HAS_SKYLIGHT_ID = "output_has_skylight";
    private static final String OUTPUT_HAS_CEILING_ID = "output_has_ceiling";
    
    // --- 节点属性 ---
    private String description = "Gets information about the player's current dimension."; // 节点描述
    
    // 常量
    private static final String OVERWORLD_ID = "minecraft:overworld";
    private static final String NETHER_ID = "minecraft:the_nether";
    private static final String END_ID = "minecraft:the_end";
    
    /**
     * 构造一个新的维度信息节点
     */
    public DimensionInfoNode() {
        // 使用新的分类命名 - inputs.minecraft.dimension_info
        super(UUID.randomUUID(), "input.context.dimension_info");
        
        // 创建并添加输出端口
        IPort dimensionIdOutput = new BasePort(OUTPUT_DIMENSION_ID, "Dimension ID", 
                "The dimension identifier", NodeDataType.STRING, this);
        addOutputPort(dimensionIdOutput);
        
        IPort isOverworldOutput = new BasePort(OUTPUT_IS_OVERWORLD_ID, "Is Overworld", 
                "Whether the dimension is the overworld", NodeDataType.BOOLEAN, this);
        addOutputPort(isOverworldOutput);
        
        IPort isNetherOutput = new BasePort(OUTPUT_IS_NETHER_ID, "Is Nether", 
                "Whether the dimension is the nether", NodeDataType.BOOLEAN, this);
        addOutputPort(isNetherOutput);
        
        IPort isEndOutput = new BasePort(OUTPUT_IS_END_ID, "Is End", 
                "Whether the dimension is the end", NodeDataType.BOOLEAN, this);
        addOutputPort(isEndOutput);
        
        IPort hasSkylightOutput = new BasePort(OUTPUT_HAS_SKYLIGHT_ID, "Has Skylight", 
                "Whether the dimension has skylight", NodeDataType.BOOLEAN, this);
        addOutputPort(hasSkylightOutput);
        
        IPort hasCeilingOutput = new BasePort(OUTPUT_HAS_CEILING_ID, "Has Ceiling", 
                "Whether the dimension has a ceiling", NodeDataType.BOOLEAN, this);
        addOutputPort(hasCeilingOutput);
        
        // 设置默认输出值
        resetOutputs();
    }
    
    /**
     * 实现INode接口的getDescription方法
     * @return 节点描述
     */
    @Override
    public String getDescription() {
        return this.description;
    }
    
    /**
     * 节点的计算逻辑
     * @param context 执行上下文
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null) {
            resetOutputs();
            return;
        }
        
        // 获取玩家维度
        PlayerAccessor playerAccessor = context.getPlayerAccessor();
        if (playerAccessor == null) {
            resetOutputs();
            return;
        }
        
        updateOutputs(playerAccessor);
    }
    
    /**
     * 更新输出值
     */
    private void updateOutputs(PlayerAccessor playerAccessor) {
        // 获取维度ID
        String dimensionId = playerAccessor.getPlayerDimension();
        
        // 检查是哪个维度
        boolean isOverworld = OVERWORLD_ID.equals(dimensionId);
        boolean isNether = NETHER_ID.equals(dimensionId);
        boolean isEnd = END_ID.equals(dimensionId);
        
        // 维度特性（在实际实现中，这些可能需要通过其他API获取）
        boolean hasSkylight = isOverworld || isEnd; // 主世界和末地有天空光照
        boolean hasCeiling = isNether; // 下界有天花板
        
        // 更新输出值
        outputValues.put(OUTPUT_DIMENSION_ID, dimensionId);
        outputValues.put(OUTPUT_IS_OVERWORLD_ID, isOverworld);
        outputValues.put(OUTPUT_IS_NETHER_ID, isNether);
        outputValues.put(OUTPUT_IS_END_ID, isEnd);
        outputValues.put(OUTPUT_HAS_SKYLIGHT_ID, hasSkylight);
        outputValues.put(OUTPUT_HAS_CEILING_ID, hasCeiling);
    }
    
    /**
     * 重置输出端口的值为默认值
     */
    private void resetOutputs() {
        outputValues.put(OUTPUT_DIMENSION_ID, OVERWORLD_ID);
        outputValues.put(OUTPUT_IS_OVERWORLD_ID, true);
        outputValues.put(OUTPUT_IS_NETHER_ID, false);
        outputValues.put(OUTPUT_IS_END_ID, false);
        outputValues.put(OUTPUT_HAS_SKYLIGHT_ID, true);
        outputValues.put(OUTPUT_HAS_CEILING_ID, false);
    }
    
    // 此节点没有需要保存的状态
    @Override
    public Object getNodeState() {
        return null;
    }
    
    @Override
    public void setNodeState(Object state) {
        // 无状态需要恢复
    }
} 

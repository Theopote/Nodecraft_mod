package com.nodecraft.nodesystem.nodes.world.read;

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
 * 获取玩家当前位置生物群系的节点
 */
@NodeInfo(
    id = "world.read.biome_at_player",
    displayName = "玩家位置生物群系",
    description = "获取玩家当前位置的生物群系信息",
    category = "world.read",
    order = 4
)
public class BiomeAtPlayerNode extends BaseNode {
    
    // --- 输出端口 ---
    private static final String OUTPUT_BIOME_ID = "output_biome_id";
    private static final String OUTPUT_IS_OCEAN_ID = "output_is_ocean";
    private static final String OUTPUT_IS_SNOWY_ID = "output_is_snowy";
    private static final String OUTPUT_IS_HOT_ID = "output_is_hot";
    private static final String OUTPUT_HAS_PRECIPITATION_ID = "output_has_precipitation";
    
    // --- 节点属性 ---
    private String description = "Gets the biome at the player's current position."; // 节点描述
    
    /**
     * 构造一个新的生物群系节点
     */
    public BiomeAtPlayerNode() {
        super(UUID.randomUUID(), "world.read.biome_at_player");
        
        // 创建并添加输出端口
        IPort biomeIdOutput = new BasePort(OUTPUT_BIOME_ID, "Biome ID", 
                "The identifier of the biome", NodeDataType.STRING, this);
        addOutputPort(biomeIdOutput);
        
        IPort isOceanOutput = new BasePort(OUTPUT_IS_OCEAN_ID, "Is Ocean", 
                "Whether the biome is an ocean", NodeDataType.BOOLEAN, this);
        addOutputPort(isOceanOutput);
        
        IPort isSnowyOutput = new BasePort(OUTPUT_IS_SNOWY_ID, "Is Snowy", 
                "Whether the biome is snowy", NodeDataType.BOOLEAN, this);
        addOutputPort(isSnowyOutput);
        
        IPort isHotOutput = new BasePort(OUTPUT_IS_HOT_ID, "Is Hot", 
                "Whether the biome is hot", NodeDataType.BOOLEAN, this);
        addOutputPort(isHotOutput);
        
        IPort hasPrecipitationOutput = new BasePort(OUTPUT_HAS_PRECIPITATION_ID, "Has Precipitation", 
                "Whether the biome has precipitation", NodeDataType.BOOLEAN, this);
        addOutputPort(hasPrecipitationOutput);
        
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
        
        // 获取玩家生物群系
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
        // 获取生物群系ID
        String biomeId = playerAccessor.getPlayerBiome();
        
        // 这里我们通过分析biomeId字符串来确定生物群系特性
        // 在实际实现中，可能需要通过Minecraft API获取更详细的生物群系信息
        boolean isOcean = biomeId.contains("ocean");
        boolean isSnowy = biomeId.contains("snowy") || biomeId.contains("frozen") || biomeId.contains("ice");
        boolean isHot = biomeId.contains("desert") || biomeId.contains("savanna") || biomeId.contains("badlands") || biomeId.contains("jungle");
        boolean hasPrecipitation = !isHot && !biomeId.contains("desert"); // 大多数生物群系都有降水，除了沙漠等
        
        // 更新输出值
        outputValues.put(OUTPUT_BIOME_ID, biomeId);
        outputValues.put(OUTPUT_IS_OCEAN_ID, isOcean);
        outputValues.put(OUTPUT_IS_SNOWY_ID, isSnowy);
        outputValues.put(OUTPUT_IS_HOT_ID, isHot);
        outputValues.put(OUTPUT_HAS_PRECIPITATION_ID, hasPrecipitation);
    }
    
    /**
     * 重置输出端口的值为默认值
     */
    private void resetOutputs() {
        outputValues.put(OUTPUT_BIOME_ID, "minecraft:plains");
        outputValues.put(OUTPUT_IS_OCEAN_ID, false);
        outputValues.put(OUTPUT_IS_SNOWY_ID, false);
        outputValues.put(OUTPUT_IS_HOT_ID, false);
        outputValues.put(OUTPUT_HAS_PRECIPITATION_ID, true);
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

package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Get Biome 节点: 获取指定坐标的生物群系名称。
 */
@NodeInfo(
    id = "world.read.get_biome",
    displayName = "获取生物群系",
    description = "获取指定坐标的生物群系信息",
    category = "world.read",
    order = 3
)
public class GetBiomeNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "获取指定坐标的生物群系信息";

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_BIOME_ID = "output_biome";
    private static final String OUTPUT_BIOME_NAME_ID = "output_biome_name";
    private static final String OUTPUT_BIOME_TEMP_ID = "output_biome_temperature";
    private static final String OUTPUT_IS_OCEAN_ID = "output_is_ocean";
    private static final String OUTPUT_DOWNFALL_ID = "output_downfall";

    // --- 构造函数 ---
    public GetBiomeNode() {
        super(UUID.randomUUID(), "world.read.get_biome");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", 
                "要查询的坐标", NodeDataType.BLOCK_POS, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_BIOME_ID, "Biome", 
                "生物群系对象", NodeDataType.BIOME, this));
        addOutputPort(new BasePort(OUTPUT_BIOME_NAME_ID, "Biome Name", 
                "生物群系名称", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_BIOME_TEMP_ID, "Temperature", 
                "生物群系温度", NodeDataType.FLOAT, this));
        addOutputPort(new BasePort(OUTPUT_IS_OCEAN_ID, "Is Ocean", 
                "是否为海洋生物群系", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_DOWNFALL_ID, "Downfall", 
                "降水量", NodeDataType.FLOAT, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 默认输出值
        Object biomeObj = null;
        String biomeName = "";
        float temperature = 0.0f;
        boolean isOcean = false;
        float downfall = 0.0f;
        
        // 获取输入坐标
        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        
        // 检查执行上下文和坐标输入是否有效
        if (context != null && context.getWorld() != null && coordinateObj instanceof BlockPos) {
            BlockPos pos = (BlockPos) coordinateObj;
            
            try {
                // 在实际实现中，从世界获取生物群系信息
                // 这里只是示例代码，需要根据Minecraft实际API调整
                // biomeObj = context.getWorld().getBiome(pos);
                
                // 获取生物群系名称
                if (biomeObj != null) {
                    // 假设获取生物群系信息的实现
                    // biomeName = Registry.BIOME.getId(biomeObj).toString();
                    biomeName = "minecraft:plains"; // 模拟名称
                    
                    // 获取生物群系温度
                    // temperature = biomeObj.getTemperature();
                    temperature = 0.8f; // 模拟温度
                    
                    // 检查是否为海洋
                    // isOcean = biomeObj.getCategory() == Biome.Category.OCEAN;
                    isOcean = biomeName.contains("ocean"); // 模拟海洋检查
                    
                    // 获取降水量
                    // downfall = biomeObj.getDownfall();
                    downfall = 0.4f; // 模拟降水量
                }
            } catch (Exception e) {
                // 记录错误但继续执行，使用默认值
                System.err.println("Error getting biome at " + pos + ": " + e.getMessage());
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_BIOME_ID, biomeObj);
        outputValues.put(OUTPUT_BIOME_NAME_ID, biomeName);
        outputValues.put(OUTPUT_BIOME_TEMP_ID, temperature);
        outputValues.put(OUTPUT_IS_OCEAN_ID, isOcean);
        outputValues.put(OUTPUT_DOWNFALL_ID, downfall);
    }
} 

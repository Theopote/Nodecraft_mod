package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Get Light Level 节点: 获取指定坐标的光照等级。
 */
@NodeInfo(
    id = "world.query.get_light_level",
    displayName = "获取光照等级",
    description = "获取指定坐标的光照等级",
    category = "world.query",
    order = 0
)
public class GetLightLevelNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_LIGHT_LEVEL_ID = "output_light_level";
    private static final String OUTPUT_SKY_LIGHT_ID = "output_sky_light";
    private static final String OUTPUT_BLOCK_LIGHT_ID = "output_block_light";
    private static final String OUTPUT_IS_DAY_ID = "output_is_day";
    private static final String OUTPUT_CAN_SEE_SKY_ID = "output_can_see_sky";

    // --- 构造函数 ---
    public GetLightLevelNode() {
        super(UUID.randomUUID(), "world.query.get_light_level");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", 
                "要查询的坐标", NodeDataType.BLOCK_POS, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_LIGHT_LEVEL_ID, "Light Level", 
                "综合光照等级 (0-15)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SKY_LIGHT_ID, "Sky Light", 
                "天空光照等级 (0-15)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_LIGHT_ID, "Block Light", 
                "方块光照等级 (0-15)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_IS_DAY_ID, "Is Day", 
                "是否为白天", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_CAN_SEE_SKY_ID, "Can See Sky", 
                "该位置是否能看到天空", NodeDataType.BOOLEAN, this));
    }

    // --- INode 方法实现 ---
    @Override
    public String getDescription() {
        return "获取指定坐标的光照等级、天空光照、方块光照、是否白天以及是否能看到天空。";
    }

    @Override
    public String getDisplayName() {
        return "Get Light Level";
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 默认输出值
        int lightLevel = 0;
        int skyLight = 0;
        int blockLight = 0;
        boolean isDay = true;
        boolean canSeeSky = false;
        
        // 获取输入坐标
        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        
        // 检查执行上下文和坐标输入是否有效
        if (context != null && context.getWorld() != null && coordinateObj instanceof BlockPos) {
            BlockPos pos = (BlockPos) coordinateObj;
            
            try {
                // 在实际实现中，从世界获取光照信息
                // 这里只是示例代码，需要根据Minecraft实际API调整
                
                // 获取综合光照等级
                // lightLevel = context.getWorld().getLightLevel(pos);
                
                // 获取天空光照
                // skyLight = context.getWorld().getLightFor(LightType.SKY, pos);
                
                // 获取方块光照
                // blockLight = context.getWorld().getLightFor(LightType.BLOCK, pos);
                
                // 判断是否为白天
                // isDay = context.getWorld().isDay();
                
                // 检查是否能看到天空
                // canSeeSky = context.getWorld().canSeeSky(pos);
                
                // 模拟值
                lightLevel = 12;
                skyLight = 15;
                blockLight = 0;
                isDay = true;
                canSeeSky = true;
                
            } catch (Exception e) {
                // 记录错误但继续执行，使用默认值
                System.err.println("Error getting light level at " + pos + ": " + e.getMessage());
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_LIGHT_LEVEL_ID, lightLevel);
        outputValues.put(OUTPUT_SKY_LIGHT_ID, skyLight);
        outputValues.put(OUTPUT_BLOCK_LIGHT_ID, blockLight);
        outputValues.put(OUTPUT_IS_DAY_ID, isDay);
        outputValues.put(OUTPUT_CAN_SEE_SKY_ID, canSeeSky);
    }
} 

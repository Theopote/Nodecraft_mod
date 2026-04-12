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
 * Get Fluid Level 节点: 获取指定坐标的流体等级。
 */
@NodeInfo(
    id = "world.query.get_fluid_level",
    displayName = "获取流体等级",
    description = "获取指定坐标的流体等级",
    category = "world.query",
    order = 1
)
public class GetFluidLevelNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "获取坐标处流体方块的液面高度和流体信息";

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_FLUID_LEVEL_ID = "output_fluid_level";
    private static final String OUTPUT_FLUID_TYPE_ID = "output_fluid_type";
    private static final String OUTPUT_IS_SOURCE_ID = "output_is_source";
    private static final String OUTPUT_IS_FLOWING_ID = "output_is_flowing";
    private static final String OUTPUT_IS_WATER_ID = "output_is_water";
    private static final String OUTPUT_IS_LAVA_ID = "output_is_lava";
    private static final String OUTPUT_HAS_FLUID_ID = "output_has_fluid";

    // --- 构造函数 ---
    public GetFluidLevelNode() {
        super(UUID.randomUUID(), "world.query.get_fluid_level");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", 
                "要查询的坐标", NodeDataType.BLOCK_POS, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_FLUID_LEVEL_ID, "Fluid Level", 
                "流体高度 (0-8，8为满方块)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FLUID_TYPE_ID, "Fluid Type", 
                "流体类型", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_SOURCE_ID, "Is Source", 
                "是否为源头方块", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_FLOWING_ID, "Is Flowing", 
                "是否为流动液体", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_WATER_ID, "Is Water", 
                "是否为水", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_LAVA_ID, "Is Lava", 
                "是否为岩浆", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HAS_FLUID_ID, "Has Fluid", 
                "坐标处是否存在流体", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 默认输出值
        int fluidLevel = 0;
        String fluidType = "";
        boolean isSource = false;
        boolean isFlowing = false;
        boolean isWater = false;
        boolean isLava = false;
        boolean hasFluid = false;
        
        // 获取输入坐标
        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        
        // 检查执行上下文和坐标输入是否有效
        if (context != null && context.getWorld() != null && coordinateObj instanceof BlockPos) {
            BlockPos pos = (BlockPos) coordinateObj;
            
            try {
                // 在实际实现中，从世界获取流体信息
                // 这里只是示例代码，需要根据Minecraft实际API调整
                
                // 获取方块状态
                // BlockState blockState = context.getWorld().getBlockState(pos);
                
                // 检查是否为流体方块
                // FluidState fluidState = blockState.getFluidState();
                // hasFluid = !fluidState.isEmpty();
                
                // 如果有流体，获取流体信息
                if (hasFluid) {
                    // 获取流体类型
                    // fluidType = Registry.FLUID.getId(fluidState.getFluid()).toString();
                    fluidType = "minecraft:water"; // 模拟类型
                    
                    // 获取流体高度（实际使用 fluidState.getLevel() 或类似方法）
                    // 通常水源是8，流动的水是1-7
                    // fluidLevel = fluidState.getLevel();
                    fluidLevel = 8; // 模拟液面高度
                    
                    // 判断各种流体状态
                    isSource = fluidLevel == 8; // 通常8表示源块
                    isFlowing = hasFluid && !isSource;
                    
                    // 判断流体类型
                    // isWater = fluidState.isSource() && fluidState.getFluid() == Fluids.WATER;
                    // isLava = fluidState.isSource() && fluidState.getFluid() == Fluids.LAVA;
                    isWater = fluidType.contains("water");
                    isLava = fluidType.contains("lava");
                }
                
            } catch (Exception e) {
                // 记录错误但继续执行，使用默认值
                System.err.println("Error getting fluid level at " + pos + ": " + e.getMessage());
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_FLUID_LEVEL_ID, fluidLevel);
        outputValues.put(OUTPUT_FLUID_TYPE_ID, fluidType);
        outputValues.put(OUTPUT_IS_SOURCE_ID, isSource);
        outputValues.put(OUTPUT_IS_FLOWING_ID, isFlowing);
        outputValues.put(OUTPUT_IS_WATER_ID, isWater);
        outputValues.put(OUTPUT_IS_LAVA_ID, isLava);
        outputValues.put(OUTPUT_HAS_FLUID_ID, hasFluid);
    }
} 

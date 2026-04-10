package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Get Block 节点: 获取指定坐标的 MinecraftBlock 信息。
 */
@NodeInfo(
    id = "world.read.get_block",
    displayName = "获取方块",
    description = "获取指定坐标的方块信息",
    category = "world.read"
)
public class GetBlockNode extends BaseNode {

    // --- 节点属性 ---
    private String description = "获取指定坐标的方块信息";

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_BLOCK_ID = "output_block";
    private static final String OUTPUT_BLOCK_TYPE_ID = "output_block_type";
    private static final String OUTPUT_IS_AIR_ID = "output_is_air";
    private static final String OUTPUT_IS_SOLID_ID = "output_is_solid";
    private static final String OUTPUT_LIGHT_LEVEL_ID = "output_light_level";

    // --- 构造函数 ---
    public GetBlockNode() {
        super(UUID.randomUUID(), "world.read.get_block");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", 
                "要查询的方块坐标", NodeDataType.BLOCK_POS, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_BLOCK_ID, "Block", 
                "方块对象", NodeDataType.MINECRAFT_BLOCK, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_TYPE_ID, "Block Type", 
                "方块类型ID", NodeDataType.BLOCK_TYPE, this));
        addOutputPort(new BasePort(OUTPUT_IS_AIR_ID, "Is Air", 
                "是否为空气方块", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_SOLID_ID, "Is Solid", 
                "是否为实心方块", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_LIGHT_LEVEL_ID, "Light Level", 
                "方块光照等级", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 默认输出值
        Object blockObj = null;
        String blockType = "";
        boolean isAir = true;
        boolean isSolid = false;
        int lightLevel = 0;
        
        // 获取输入坐标
        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        
        // 检查执行上下文和坐标输入是否有效
        if (context != null && context.getWorld() != null && coordinateObj instanceof BlockPos) {
            BlockPos pos = (BlockPos) coordinateObj;
            
            try {
                // 从世界获取方块状态
                BlockState blockState = context.getWorld().getBlockState(pos);
                blockObj = blockState;
                
                // 获取方块类型ID（如 "minecraft:stone"）
                blockType = Registries.BLOCK.getId(blockState.getBlock()).toString();
                
                // 检查是否为空气
                isAir = blockState.isAir();
                
                // 检查是否为实心方块（有完整碰撞箱）
                isSolid = blockState.isSolidBlock(context.getWorld(), pos);
                
                // 获取光照等级
                lightLevel = context.getWorld().getLightLevel(pos);
            } catch (Exception e) {
                System.err.println("Error getting block at " + pos + ": " + e.getMessage());
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_BLOCK_ID, blockObj);
        outputValues.put(OUTPUT_BLOCK_TYPE_ID, blockType);
        outputValues.put(OUTPUT_IS_AIR_ID, isAir);
        outputValues.put(OUTPUT_IS_SOLID_ID, isSolid);
        outputValues.put(OUTPUT_LIGHT_LEVEL_ID, lightLevel);
    }
} 

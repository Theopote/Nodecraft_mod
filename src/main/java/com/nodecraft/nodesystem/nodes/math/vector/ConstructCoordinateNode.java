package com.nodecraft.nodesystem.nodes.math.vector;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Construct Coordinate Node: 从三个整数构建方块坐标
 */
@NodeInfo(
    id = "math.vector.construct_coordinate",
    displayName = "构建坐标",
    description = "从X、Y、Z分量构建坐标",
    category = "math.vector"
)
public class ConstructCoordinateNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_Z_ID = "input_z";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_COORDINATE_ID = "output_coordinate";

    // --- 构造函数 ---
    public ConstructCoordinateNode() {
        super(UUID.randomUUID(), "math.vector.construct_coordinate");
        
        // 创建并添加输入端口 (接受任何数值类型，但会转换为整数)
        addInputPort(new BasePort(INPUT_X_ID, "X", "X coordinate", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Y coordinate", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Z_ID, "Z", "Z coordinate", NodeDataType.DOUBLE, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_COORDINATE_ID, "Coordinate", "Block position (integer coordinates)", NodeDataType.BLOCK_POS, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Creates a block position from X, Y, Z integer components";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Construct Coordinate";
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值并转换为整数
        int x = getValueAsInt(inputValues.get(INPUT_X_ID), 0);
        int y = getValueAsInt(inputValues.get(INPUT_Y_ID), 0);
        int z = getValueAsInt(inputValues.get(INPUT_Z_ID), 0);
        
        // 创建BlockPos（方块坐标）
        BlockPos coordinate = new BlockPos(x, y, z);
        
        // 设置输出值
        outputValues.put(OUTPUT_COORDINATE_ID, coordinate);
        NodeCraft.LOGGER.info(
                "ConstructCoordinateNode[{}] processed: rawX={}, rawY={}, rawZ={}, outputCoordinate={}",
                getId(),
                inputValues.get(INPUT_X_ID),
                inputValues.get(INPUT_Y_ID),
                inputValues.get(INPUT_Z_ID),
                coordinate
        );
    }
    
    /**
     * 将输入值安全地转换为整数
     * @param value 输入值
     * @param defaultValue 默认值
     * @return 整数值
     */
    private int getValueAsInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            // 将数值舍入为最接近的整数
            return (int) Math.round(((Number) value).doubleValue());
        }
        return defaultValue;
    }
} 

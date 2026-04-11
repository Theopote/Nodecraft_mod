package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Deconstruct Coordinate Node: 将方块坐标分解为X、Y、Z整数值
 */
@NodeInfo(
    id = "reference.points.deconstruct_point",
    displayName = "分解坐标",
    description = "将坐标分解为X、Y、Z分量",
    category = "reference.points"
)
public class DeconstructCoordinateNode extends BaseNode {

    // --- 输入端口 IDs ---
    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";

    // --- 构造函数 ---
    public DeconstructCoordinateNode() {
        super(UUID.randomUUID(), "reference.points.deconstruct_point");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Block position (integer coordinates)", NodeDataType.BLOCK_POS, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "X coordinate (integer)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Y coordinate (integer)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z", "Z coordinate (integer)", NodeDataType.INTEGER, this));
    }

    // 添加 getDescription 方法
    @Override
    public String getDescription() {
        return "Extracts X, Y, Z integer components from a block position";
    }

    // 添加 getDisplayName 方法
    @Override
    public String getDisplayName() {
        return "Deconstruct Coordinate";
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        Object coordinate = inputValues.get(INPUT_COORDINATE_ID);
        
        // 检查输入是否为BlockPos
        if (coordinate instanceof BlockPos) {
            BlockPos pos = (BlockPos) coordinate;
            
            // 设置输出值
            outputValues.put(OUTPUT_X_ID, pos.getX());
            outputValues.put(OUTPUT_Y_ID, pos.getY());
            outputValues.put(OUTPUT_Z_ID, pos.getZ());
        } else {
            // 如果输入无效，输出默认值
            outputValues.put(OUTPUT_X_ID, 0);
            outputValues.put(OUTPUT_Y_ID, 0);
            outputValues.put(OUTPUT_Z_ID, 0);
        }
    }
} 

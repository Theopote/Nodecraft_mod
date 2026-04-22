package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "reference.points.construct_coordinate",
    displayName = "构建坐标",
    description = "通过 X / Y / Z 数值输入构建坐标，并输出 Coordinate / Block Pos / X / Y / Z",
    category = "reference.points",
    order = 1
)
public class ConstructCoordinateNode extends BaseNode {

    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_Z_ID = "input_z";

    private static final String OUTPUT_COORDINATE_ID = "output_coordinate";
    private static final String OUTPUT_BLOCK_POS_ID = "output_block_pos";
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";

    public ConstructCoordinateNode() {
        super(UUID.randomUUID(), "reference.points.construct_coordinate");

        addInputPort(new BasePort(INPUT_X_ID, "X", "X 分量输入", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Y 分量输入", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_Z_ID, "Z", "Z 分量输入", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATE_ID, "Coordinate", "构建后的坐标", NodeDataType.COORDINATE, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_POS_ID, "Block Pos", "构建后的方块坐标", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "X 分量输出", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Y 分量输出", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z", "Z 分量输出", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "通过 X / Y / Z 输入构建坐标，并输出 Coordinate / Block Pos / X / Y / Z。";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        int x = toInt(inputValues.get(INPUT_X_ID));
        int y = toInt(inputValues.get(INPUT_Y_ID));
        int z = toInt(inputValues.get(INPUT_Z_ID));

        BlockPos blockPos = new BlockPos(x, y, z);
        outputValues.put(OUTPUT_COORDINATE_ID, blockPos);
        outputValues.put(OUTPUT_BLOCK_POS_ID, blockPos);
        outputValues.put(OUTPUT_X_ID, x);
        outputValues.put(OUTPUT_Y_ID, y);
        outputValues.put(OUTPUT_Z_ID, z);
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}

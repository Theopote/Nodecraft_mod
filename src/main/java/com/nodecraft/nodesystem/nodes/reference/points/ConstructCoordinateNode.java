package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.construct_coordinate",
    displayName = "Construct Block Position",
    description = "Constructs a block position from X, Y, and Z integer components",
    category = "reference.points",
    order = 1
)
public class ConstructCoordinateNode extends BaseNode {

    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_Z_ID = "input_z";

    private static final String OUTPUT_BLOCK_POS_ID = "output_block_pos";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ConstructCoordinateNode() {
        super(UUID.randomUUID(), "reference.points.construct_coordinate");

        addInputPort(new BasePort(INPUT_X_ID, "X", "X integer coordinate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Y integer coordinate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_Z_ID, "Z", "Z integer coordinate", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_POS_ID, "Block Pos",
            "Constructed block position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether all inputs are valid finite numbers",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Construct Block Position";
    }

    @Override
    public String getDescription() {
        return "Constructs a block position from X, Y, and Z integer components";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Integer x = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_X_ID));
        Integer y = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_Y_ID));
        Integer z = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_Z_ID));

        if (x == null) {
            writeInvalid("X must be exact INTEGER");
            return;
        }
        if (y == null) {
            writeInvalid("Y must be exact INTEGER");
            return;
        }
        if (z == null) {
            writeInvalid("Z must be exact INTEGER");
            return;
        }

        outputValues.put(OUTPUT_BLOCK_POS_ID, new BlockPos(x, y, z));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_BLOCK_POS_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

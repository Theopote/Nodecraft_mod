package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.construct_coordinate",
    displayName = "Construct Coordinate",
    description = "Constructs a block coordinate from X, Y, and Z integer components.",
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
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ConstructCoordinateNode() {
        super(UUID.randomUUID(), "reference.points.construct_coordinate");

        addInputPort(new BasePort(INPUT_X_ID, "X", "X integer coordinate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Y integer coordinate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_Z_ID, "Z", "Z integer coordinate", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATE_ID, "Coordinate", "Constructed coordinate", NodeDataType.COORDINATE, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_POS_ID, "Block Pos", "Constructed block position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "Resolved X coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Resolved Y coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z", "Resolved Z coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether all inputs are valid finite numbers",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Construct Coordinate";
    }

    @Override
    public String getDescription() {
        return "Constructs a block coordinate from X, Y, and Z integer components.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Integer x = toInt(inputValues.get(INPUT_X_ID));
        Integer y = toInt(inputValues.get(INPUT_Y_ID));
        Integer z = toInt(inputValues.get(INPUT_Z_ID));

        if (x == null || y == null || z == null) {
            writeInvalid();
            return;
        }

        BlockPos blockPos = new BlockPos(x, y, z);
        outputValues.put(OUTPUT_COORDINATE_ID, blockPos);
        outputValues.put(OUTPUT_BLOCK_POS_ID, blockPos);
        outputValues.put(OUTPUT_X_ID, x);
        outputValues.put(OUTPUT_Y_ID, y);
        outputValues.put(OUTPUT_Z_ID, z);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_COORDINATE_ID, null);
        outputValues.put(OUTPUT_BLOCK_POS_ID, null);
        outputValues.put(OUTPUT_X_ID, 0);
        outputValues.put(OUTPUT_Y_ID, 0);
        outputValues.put(OUTPUT_Z_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Integer toInt(Object value) {
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            if (Double.isFinite(raw)) {
                return number.intValue();
            }
        }
        return null;
    }
}

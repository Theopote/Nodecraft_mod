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

/**
 * Extracts integer X/Y/Z components from a block position.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.deconstruct_block_position",
    displayName = "Deconstruct Block Position",
    description = "Extracts X, Y, and Z integer components from a block position",
    category = "reference.points",
    order = 4
)
public class DeconstructCoordinateNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructCoordinateNode() {
        super(UUID.randomUUID(), "reference.points.deconstruct_block_position");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Block Pos",
            "Block position to deconstruct into X/Y/Z integers",
            NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "X block coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Y block coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z", "Z block coordinate", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the block position input is valid",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts X, Y, and Z integer components from a block position";
    }

    @Override
    public String getDisplayName() {
        return "Deconstruct Block Position";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPos coordinate = PointUtils.toBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        if (coordinate == null) {
            outputValues.put(OUTPUT_X_ID, 0);
            outputValues.put(OUTPUT_Y_ID, 0);
            outputValues.put(OUTPUT_Z_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_X_ID, coordinate.getX());
        outputValues.put(OUTPUT_Y_ID, coordinate.getY());
        outputValues.put(OUTPUT_Z_ID, coordinate.getZ());
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

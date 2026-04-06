package com.nodecraft.nodesystem.nodes.spatial.construct;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import net.minecraft.util.math.BlockPos;

@NodeInfo(
    id = "spatial.construct.box_corners",
    displayName = "Box by Two Corners",
    description = "Generates an axis-aligned box from two opposite corner points",
    category = "spatial.construct"
)
public class BoxCornersNode extends AbstractBoxGeneratorNode {

    private static final String INPUT_CORNER_A_ID = "input_corner_a";
    private static final String INPUT_CORNER_B_ID = "input_corner_b";

    public BoxCornersNode() {
        super("spatial.construct.box_corners");

        addInputPort(new BasePort(INPUT_CORNER_A_ID, "Corner A", "First corner of the box", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_CORNER_B_ID, "Corner B", "Opposite corner of the box. The result stays axis-aligned.", NodeDataType.BLOCK_POS, this));
    }

    @Override
    public String getDescription() {
        return "Generates an axis-aligned box from two opposite corner points";
    }

    @Override
    public String getDisplayName() {
        return "Box by Two Corners";
    }

    @Override
    protected BoxDefinition resolveBoxDefinition() {
        Object cornerAObj = inputValues.get(INPUT_CORNER_A_ID);
        Object cornerBObj = inputValues.get(INPUT_CORNER_B_ID);

        if (!(cornerAObj instanceof BlockPos cornerA) || !(cornerBObj instanceof BlockPos cornerB)) {
            return null;
        }

        BlockPos minCorner = new BlockPos(
            Math.min(cornerA.getX(), cornerB.getX()),
            Math.min(cornerA.getY(), cornerB.getY()),
            Math.min(cornerA.getZ(), cornerB.getZ())
        );
        BlockPos maxCorner = new BlockPos(
            Math.max(cornerA.getX(), cornerB.getX()),
            Math.max(cornerA.getY(), cornerB.getY()),
            Math.max(cornerA.getZ(), cornerB.getZ())
        );

        return createAxisAlignedDefinition(minCorner, maxCorner);
    }
}

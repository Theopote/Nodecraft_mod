package com.nodecraft.nodesystem.nodes.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import net.minecraft.util.math.BlockPos;

/**
 * Legacy combined box generator kept for compatibility with existing graphs.
 */
@NodeInfo(
    id = "spatial.generators.box_blocks",
    displayName = "Box (Legacy)",
    description = "Legacy combined box generator. Prefer the dedicated center, corners, or corner + size box nodes.",
    category = "spatial.generators"
)
public class BoxBlocksNode extends AbstractBoxGeneratorNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_SIZE_X_ID = "input_size_x";
    private static final String INPUT_SIZE_Y_ID = "input_size_y";
    private static final String INPUT_SIZE_Z_ID = "input_size_z";
    private static final String INPUT_ROT_X_ID = "input_rotation_x";
    private static final String INPUT_ROT_Y_ID = "input_rotation_y";
    private static final String INPUT_ROT_Z_ID = "input_rotation_z";
    private static final String INPUT_CORNER_A_ID = "input_corner_a";
    private static final String INPUT_CORNER_B_ID = "input_corner_b";

    @NodeProperty(displayName = "Use Corner Inputs", category = "Input", order = 20,
        description = "When enabled, Corner A and Corner B take priority over center and size inputs")
    private boolean useCornerInputs = true;

    public BoxBlocksNode() {
        super("spatial.generators.box_blocks");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Center point of the box", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional plane used to orient the box", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_SIZE_X_ID, "Size X", "Width in blocks on the X axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SIZE_Y_ID, "Size Y", "Height in blocks on the Y axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SIZE_Z_ID, "Size Z", "Depth in blocks on the Z axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROT_X_ID, "Rotation X", "Rotation around the X axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Y_ID, "Rotation Y", "Rotation around the Y axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Z_ID, "Rotation Z", "Rotation around the Z axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CORNER_A_ID, "Corner A", "Optional first corner of the box", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_CORNER_B_ID, "Corner B", "Optional second corner of the box", NodeDataType.BLOCK_POS, this));
    }

    @Override
    public String getDescription() {
        return "Legacy combined box generator. Prefer the dedicated center, corners, or corner + size box nodes.";
    }

    @Override
    public String getDisplayName() {
        return "Box (Legacy)";
    }

    @Override
    protected BoxDefinition resolveBoxDefinition() {
        Object cornerAObj = inputValues.get(INPUT_CORNER_A_ID);
        Object cornerBObj = inputValues.get(INPUT_CORNER_B_ID);

        if (useCornerInputs && cornerAObj instanceof BlockPos cornerA && cornerBObj instanceof BlockPos cornerB) {
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

        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        Object sizeXObj = inputValues.get(INPUT_SIZE_X_ID);
        Object sizeYObj = inputValues.get(INPUT_SIZE_Y_ID);
        Object sizeZObj = inputValues.get(INPUT_SIZE_Z_ID);
        Object rotXObj = inputValues.get(INPUT_ROT_X_ID);
        Object rotYObj = inputValues.get(INPUT_ROT_Y_ID);
        Object rotZObj = inputValues.get(INPUT_ROT_Z_ID);

        if (!(centerObj instanceof BlockPos center)
            || !(sizeXObj instanceof Number sizeX)
            || !(sizeYObj instanceof Number sizeY)
            || !(sizeZObj instanceof Number sizeZ)) {
            return null;
        }

        double rotationX = rotXObj instanceof Number number ? number.doubleValue() : 0.0d;
        double rotationY = rotYObj instanceof Number number ? number.doubleValue() : 0.0d;
        double rotationZ = rotZObj instanceof Number number ? number.doubleValue() : 0.0d;

        return createCenterDefinition(
            center,
            sizeX.intValue(),
            sizeY.intValue(),
            sizeZ.intValue(),
            planeObj,
            rotationX,
            rotationY,
            rotationZ
        );
    }
}

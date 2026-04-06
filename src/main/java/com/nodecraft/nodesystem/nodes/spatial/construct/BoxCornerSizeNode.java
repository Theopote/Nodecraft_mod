package com.nodecraft.nodesystem.nodes.spatial.construct;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import net.minecraft.util.math.BlockPos;

@NodeInfo(
    id = "spatial.construct.box_corner_size",
    displayName = "Box by Corner + Size",
    description = "Generates a box from one anchor corner and signed X/Y/Z sizes. Negative values grow in the opposite local axis direction.",
    category = "spatial.construct"
)
public class BoxCornerSizeNode extends AbstractBoxGeneratorNode {

    private static final String INPUT_CORNER_ID = "input_corner";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_SIZE_X_ID = "input_size_x";
    private static final String INPUT_SIZE_Y_ID = "input_size_y";
    private static final String INPUT_SIZE_Z_ID = "input_size_z";
    private static final String INPUT_ROT_X_ID = "input_rotation_x";
    private static final String INPUT_ROT_Y_ID = "input_rotation_y";
    private static final String INPUT_ROT_Z_ID = "input_rotation_z";

    public BoxCornerSizeNode() {
        super("spatial.construct.box_corner_size");

        addInputPort(new BasePort(INPUT_CORNER_ID, "Corner", "Anchor corner of the box", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional reference plane used to orient the local X/Y/Z axes", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_SIZE_X_ID, "Size X", "Signed size along local X. Negative values grow from the corner in the opposite X direction.", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SIZE_Y_ID, "Size Y", "Signed size along local Y. Negative values grow from the corner in the opposite Y direction.", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SIZE_Z_ID, "Size Z", "Signed size along local Z. Negative values grow from the corner in the opposite Z direction.", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROT_X_ID, "Rotation X", "Additional local rotation around the box X axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Y_ID, "Rotation Y", "Additional local rotation around the box Y axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Z_ID, "Rotation Z", "Additional local rotation around the box Z axis in degrees", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDescription() {
        return "Generates a box from one anchor corner and signed X/Y/Z sizes. Negative values grow in the opposite local axis direction.";
    }

    @Override
    public String getDisplayName() {
        return "Box by Corner + Size";
    }

    @Override
    protected BoxDefinition resolveBoxDefinition() {
        Object cornerObj = inputValues.get(INPUT_CORNER_ID);
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        Object sizeXObj = inputValues.get(INPUT_SIZE_X_ID);
        Object sizeYObj = inputValues.get(INPUT_SIZE_Y_ID);
        Object sizeZObj = inputValues.get(INPUT_SIZE_Z_ID);
        Object rotXObj = inputValues.get(INPUT_ROT_X_ID);
        Object rotYObj = inputValues.get(INPUT_ROT_Y_ID);
        Object rotZObj = inputValues.get(INPUT_ROT_Z_ID);

        if (!(cornerObj instanceof BlockPos corner)
            || !(sizeXObj instanceof Number sizeX)
            || !(sizeYObj instanceof Number sizeY)
            || !(sizeZObj instanceof Number sizeZ)) {
            return null;
        }

        double rotationX = rotXObj instanceof Number number ? number.doubleValue() : 0.0d;
        double rotationY = rotYObj instanceof Number number ? number.doubleValue() : 0.0d;
        double rotationZ = rotZObj instanceof Number number ? number.doubleValue() : 0.0d;

        return createCornerAndSizeDefinition(
            corner,
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

package com.nodecraft.nodesystem.nodes.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import net.minecraft.util.math.BlockPos;

@NodeInfo(
    id = "spatial.generators.box_center_size",
    displayName = "Box by Center",
    description = "Generates a box from its center point and X/Y/Z sizes",
    category = "spatial.generators"
)
public class BoxCenterSizeNode extends AbstractBoxGeneratorNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_SIZE_X_ID = "input_size_x";
    private static final String INPUT_SIZE_Y_ID = "input_size_y";
    private static final String INPUT_SIZE_Z_ID = "input_size_z";
    private static final String INPUT_ROT_X_ID = "input_rotation_x";
    private static final String INPUT_ROT_Y_ID = "input_rotation_y";
    private static final String INPUT_ROT_Z_ID = "input_rotation_z";

    public BoxCenterSizeNode() {
        super("spatial.generators.box_center_size");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Center point of the box", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional plane used to orient the box", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_SIZE_X_ID, "Size X", "Width in blocks on the X axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SIZE_Y_ID, "Size Y", "Height in blocks on the Y axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SIZE_Z_ID, "Size Z", "Depth in blocks on the Z axis", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROT_X_ID, "Rotation X", "Rotation around the X axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Y_ID, "Rotation Y", "Rotation around the Y axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Z_ID, "Rotation Z", "Rotation around the Z axis in degrees", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDescription() {
        return "Generates a box from its center point and X/Y/Z sizes";
    }

    @Override
    public String getDisplayName() {
        return "Box by Center";
    }

    @Override
    protected BoxDefinition resolveBoxDefinition() {
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

package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

@NodeInfo(
    id = "geometry.primitives.box_from_corner_size",
    displayName = "Box by Corner + Size",
    description = "Generates a box from one anchor corner and signed X/Y/Z sizes. Negative values grow in the opposite local axis direction.",
    category = "geometry.primitives",
    order = 1
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
        super("geometry.primitives.box_from_corner_size");

        addInputPort(new BasePort(INPUT_CORNER_ID, "Corner", "Anchor corner of the box", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional reference plane used to orient the local X/Y/Z axes", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_SIZE_X_ID, "Size X", "Signed size along local X. Negative values grow from the corner in the opposite X direction. 0 disables box generation.", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SIZE_Y_ID, "Size Y", "Signed size along local Y. Negative values grow from the corner in the opposite Y direction. 0 disables box generation.", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SIZE_Z_ID, "Size Z", "Signed size along local Z. Negative values grow from the corner in the opposite Z direction. 0 disables box generation.", NodeDataType.INTEGER, this));
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

        Vector3d cornerVector = resolveVectorInput(cornerObj);
        Integer sizeX = resolveInt(sizeXObj);
        Integer sizeY = resolveInt(sizeYObj);
        Integer sizeZ = resolveInt(sizeZObj);
        if (cornerVector == null || sizeX == null || sizeY == null || sizeZ == null) {
            return null;
        }
        BlockPos corner = BlockPos.ofFloored(cornerVector.x, cornerVector.y, cornerVector.z);

        double rotationX = resolveFiniteDouble(rotXObj, 0.0d);
        double rotationY = resolveFiniteDouble(rotYObj, 0.0d);
        double rotationZ = resolveFiniteDouble(rotZObj, 0.0d);

        return createCornerAndSizeDefinition(
            corner,
            sizeX,
            sizeY,
            sizeZ,
            planeObj,
            rotationX,
            rotationY,
            rotationZ
        );
    }

}

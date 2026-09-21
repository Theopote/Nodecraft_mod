package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
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

    @NodeProperty(displayName = "Corner X", category = "Corner", order = 1)
    private double cornerX = 0.0d;
    @NodeProperty(displayName = "Corner Y", category = "Corner", order = 2)
    private double cornerY = 0.0d;
    @NodeProperty(displayName = "Corner Z", category = "Corner", order = 3)
    private double cornerZ = 0.0d;

    @NodeProperty(displayName = "Size X", category = "Size", order = 10)
    private double sizeX = 5.0d;
    @NodeProperty(displayName = "Size Y", category = "Size", order = 11)
    private double sizeY = 5.0d;
    @NodeProperty(displayName = "Size Z", category = "Size", order = 12)
    private double sizeZ = 5.0d;

    public BoxCornerSizeNode() {
        super("geometry.primitives.box_from_corner_size");

        addInputPort(new BasePort(INPUT_CORNER_ID, "Corner", "Anchor corner of the box", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional reference plane used to orient the local X/Y/Z axes", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_SIZE_X_ID, "Size X", "Signed size along local X. Negative values grow from the corner in the opposite X direction. 0 disables box generation.", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SIZE_Y_ID, "Size Y", "Signed size along local Y. Negative values grow from the corner in the opposite Y direction. 0 disables box generation.", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SIZE_Z_ID, "Size Z", "Signed size along local Z. Negative values grow from the corner in the opposite Z direction. 0 disables box generation.", NodeDataType.DOUBLE, this));
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
        if (cornerVector == null) {
            cornerVector = new Vector3d(cornerX, cornerY, cornerZ);
        }

        double resolvedSizeX = resolveFiniteDouble(sizeXObj, sizeX);
        double resolvedSizeY = resolveFiniteDouble(sizeYObj, sizeY);
        double resolvedSizeZ = resolveFiniteDouble(sizeZObj, sizeZ);

        double rotationX = resolveFiniteDouble(rotXObj, 0.0d);
        double rotationY = resolveFiniteDouble(rotYObj, 0.0d);
        double rotationZ = resolveFiniteDouble(rotZObj, 0.0d);

        return createContinuousCornerAndSizeDefinition(
            cornerVector,
            resolvedSizeX,
            resolvedSizeY,
            resolvedSizeZ,
            planeObj,
            rotationX,
            rotationY,
            rotationZ
        );
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        Object parent = super.getNodeState();
        if (parent instanceof Map<?, ?> parentMap) {
            for (Map.Entry<?, ?> entry : parentMap.entrySet()) {
                if (entry.getKey() != null) {
                    state.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        state.put("cornerX", cornerX);
        state.put("cornerY", cornerY);
        state.put("cornerZ", cornerZ);
        state.put("sizeX", sizeX);
        state.put("sizeY", sizeY);
        state.put("sizeZ", sizeZ);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        super.setNodeState(state);
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("cornerX") instanceof Number n) cornerX = n.doubleValue();
        if (map.get("cornerY") instanceof Number n) cornerY = n.doubleValue();
        if (map.get("cornerZ") instanceof Number n) cornerZ = n.doubleValue();
        if (map.get("sizeX") instanceof Number n) sizeX = n.doubleValue();
        if (map.get("sizeY") instanceof Number n) sizeY = n.doubleValue();
        if (map.get("sizeZ") instanceof Number n) sizeZ = n.doubleValue();
    }

}

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
    id = "geometry.primitives.box",
    displayName = "Box by Center + Size",
    description = "Constructs continuous box geometry from a center point and X/Y/Z sizes. Blocks/Region remain legacy convenience outputs.",
    category = "geometry.primitives",
    order = 0
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

    @NodeProperty(displayName = "Center X", category = "Center", order = 1)
    private double centerX = 0.0d;
    @NodeProperty(displayName = "Center Y", category = "Center", order = 2)
    private double centerY = 0.0d;
    @NodeProperty(displayName = "Center Z", category = "Center", order = 3)
    private double centerZ = 0.0d;

    @NodeProperty(displayName = "Size X", category = "Size", order = 10)
    private double sizeX = 5.0d;
    @NodeProperty(displayName = "Size Y", category = "Size", order = 11)
    private double sizeY = 5.0d;
    @NodeProperty(displayName = "Size Z", category = "Size", order = 12)
    private double sizeZ = 5.0d;

    public BoxCenterSizeNode() {
        super("geometry.primitives.box");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Center point of the box", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional reference plane used to orient the local X/Y/Z axes", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_SIZE_X_ID, "Size X", "Box size along local X (continuous). 0 disables generation.", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SIZE_Y_ID, "Size Y", "Box size along local Y (continuous). 0 disables generation.", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SIZE_Z_ID, "Size Z", "Box size along local Z (continuous). 0 disables generation.", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_X_ID, "Rotation X", "Additional local rotation around the box X axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Y_ID, "Rotation Y", "Additional local rotation around the box Y axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Z_ID, "Rotation Z", "Additional local rotation around the box Z axis in degrees", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDescription() {
        return "Constructs continuous box geometry from a center point and X/Y/Z sizes";
    }

    @Override
    public String getDisplayName() {
        return "Box by Center + Size";
    }

    @Override
    protected BoxDefinition resolveBoxDefinition() {
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object planeObj = inputValues.get(INPUT_PLANE_ID);

        Vector3d centerVector = resolveVectorInput(centerObj);
        if (centerVector == null) {
            centerVector = new Vector3d(centerX, centerY, centerZ);
        }

        double resolvedSizeX = resolveFiniteDouble(inputValues.get(INPUT_SIZE_X_ID), sizeX);
        double resolvedSizeY = resolveFiniteDouble(inputValues.get(INPUT_SIZE_Y_ID), sizeY);
        double resolvedSizeZ = resolveFiniteDouble(inputValues.get(INPUT_SIZE_Z_ID), sizeZ);

        double rotationX = resolveFiniteDouble(inputValues.get(INPUT_ROT_X_ID), 0.0d);
        double rotationY = resolveFiniteDouble(inputValues.get(INPUT_ROT_Y_ID), 0.0d);
        double rotationZ = resolveFiniteDouble(inputValues.get(INPUT_ROT_Z_ID), 0.0d);

        return createContinuousCenterDefinition(
            centerVector,
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
        state.put("centerX", centerX);
        state.put("centerY", centerY);
        state.put("centerZ", centerZ);
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
        if (map.get("centerX") instanceof Number n) centerX = n.doubleValue();
        if (map.get("centerY") instanceof Number n) centerY = n.doubleValue();
        if (map.get("centerZ") instanceof Number n) centerZ = n.doubleValue();
        if (map.get("sizeX") instanceof Number n) sizeX = n.doubleValue();
        if (map.get("sizeY") instanceof Number n) sizeY = n.doubleValue();
        if (map.get("sizeZ") instanceof Number n) sizeZ = n.doubleValue();
    }
}

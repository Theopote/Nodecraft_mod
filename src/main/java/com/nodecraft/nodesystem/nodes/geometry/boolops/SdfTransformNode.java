package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.TransformedSdfData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "geometry.boolean.sdf_transform",
    displayName = "SDF Transform",
    description = "Applies translation, rotation, and uniform scale to an input SDF",
    category = "geometry.boolean",
    order = 21
)
public class SdfTransformNode extends BaseNode {

    @NodeProperty(displayName = "Translation X", category = "Transform", order = 1)
    private double translationX = 0.0d;
    @NodeProperty(displayName = "Translation Y", category = "Transform", order = 2)
    private double translationY = 0.0d;
    @NodeProperty(displayName = "Translation Z", category = "Transform", order = 3)
    private double translationZ = 0.0d;

    @NodeProperty(displayName = "Rotation X", category = "Transform", order = 4)
    private double rotationX = 0.0d;
    @NodeProperty(displayName = "Rotation Y", category = "Transform", order = 5)
    private double rotationY = 0.0d;
    @NodeProperty(displayName = "Rotation Z", category = "Transform", order = 6)
    private double rotationZ = 0.0d;

    @NodeProperty(displayName = "Scale", category = "Transform", order = 7)
    private double scale = 1.0d;

    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_TRANSLATION_ID = "input_translation";
    private static final String INPUT_ROT_X_ID = "input_rotation_x";
    private static final String INPUT_ROT_Y_ID = "input_rotation_y";
    private static final String INPUT_ROT_Z_ID = "input_rotation_z";
    private static final String INPUT_SCALE_ID = "input_scale";

    private static final String OUTPUT_SDF_ID = "output_sdf";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SdfTransformNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_transform");

        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Source signed distance field", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_TRANSLATION_ID, "Translation", "Optional translation vector override", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ROT_X_ID, "Rotation X", "Rotation around X axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Y_ID, "Rotation Y", "Rotation around Y axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Z_ID, "Rotation Z", "Rotation around Z axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SCALE_ID, "Scale", "Uniform scale factor", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF", "Transformed SDF", NodeDataType.SDF, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when transform succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Applies translation, rotation, and uniform scale to an input SDF";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            outputValues.put(OUTPUT_SDF_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d translation = inputValues.get(INPUT_TRANSLATION_ID) instanceof Vector3d t
            ? new Vector3d(t)
            : new Vector3d(translationX, translationY, translationZ);
        double rx = getInputDouble(INPUT_ROT_X_ID, rotationX);
        double ry = getInputDouble(INPUT_ROT_Y_ID, rotationY);
        double rz = getInputDouble(INPUT_ROT_Z_ID, rotationZ);
        double s = getInputDouble(INPUT_SCALE_ID, scale);

        SignedDistanceFieldData transformed = new TransformedSdfData(sdf, translation, rx, ry, rz, s);
        outputValues.put(OUTPUT_SDF_ID, transformed);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("translationX", translationX);
        state.put("translationY", translationY);
        state.put("translationZ", translationZ);
        state.put("rotationX", rotationX);
        state.put("rotationY", rotationY);
        state.put("rotationZ", rotationZ);
        state.put("scale", scale);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("translationX") instanceof Number value) translationX = value.doubleValue();
        if (map.get("translationY") instanceof Number value) translationY = value.doubleValue();
        if (map.get("translationZ") instanceof Number value) translationZ = value.doubleValue();
        if (map.get("rotationX") instanceof Number value) rotationX = value.doubleValue();
        if (map.get("rotationY") instanceof Number value) rotationY = value.doubleValue();
        if (map.get("rotationZ") instanceof Number value) rotationZ = value.doubleValue();
        if (map.get("scale") instanceof Number value) scale = value.doubleValue();
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}

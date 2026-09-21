package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.orientation.rotate_vector",
    displayName = "Rotate Vector",
    description = "Rotates a vector around an axis by an angle in degrees",
    category = "transform.orientation",
    order = 1
)
public class RotateVectorNode extends BaseNode {

    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_ANGLE_ID = "input_angle";

    private static final String OUTPUT_ROTATED_VECTOR_ID = "output_rotated_vector";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Default Axis X", category = "Rotation", order = 1)
    private double defaultAxisX = 0.0d;
    @NodeProperty(displayName = "Default Axis Y", category = "Rotation", order = 2)
    private double defaultAxisY = 1.0d;
    @NodeProperty(displayName = "Default Axis Z", category = "Rotation", order = 3)
    private double defaultAxisZ = 0.0d;
    @NodeProperty(displayName = "Default Angle", category = "Rotation", order = 4)
    private double defaultAngle = 90.0d;

    public RotateVectorNode() {
        super(UUID.randomUUID(), "transform.orientation.rotate_vector");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Vector to rotate", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Axis of rotation (will be normalized)", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle", "Angle of rotation in degrees", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ROTATED_VECTOR_ID, "Rotated Vector", "Resulting rotated vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the vector rotation succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Rotates a vector around an axis by an angle in degrees";
    }

    @Override
    public String getDisplayName() {
        return "Rotate Vector";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d vector = SpatialValueResolver.resolveVector(inputValues.get(INPUT_VECTOR_ID));
        Vector3d axis = SpatialValueResolver.resolveVector(inputValues.get(INPUT_AXIS_ID));
        if (axis == null) {
            axis = new Vector3d(defaultAxisX, defaultAxisY, defaultAxisZ);
        }
        double angleDeg = getInputDouble(INPUT_ANGLE_ID, defaultAngle);

        if (vector == null
            || !isFinite(vector)
            || !isFinite(axis)
            || axis.lengthSquared() <= 1.0e-12d
            || !Double.isFinite(angleDeg)) {
            writeInvalid();
            return;
        }

        axis.normalize();
        double angleRad = Math.toRadians(angleDeg);
        Quaterniond rotation = new Quaterniond(new AxisAngle4d(angleRad, axis.x, axis.y, axis.z));
        Vector3d result = rotation.transform(new Vector3d(vector));

        outputValues.put(OUTPUT_ROTATED_VECTOR_ID, result);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("defaultAxisX", defaultAxisX);
        state.put("defaultAxisY", defaultAxisY);
        state.put("defaultAxisZ", defaultAxisZ);
        state.put("defaultAngle", defaultAngle);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultAxisX") instanceof Number value) defaultAxisX = value.doubleValue();
        if (map.get("defaultAxisY") instanceof Number value) defaultAxisY = value.doubleValue();
        if (map.get("defaultAxisZ") instanceof Number value) defaultAxisZ = value.doubleValue();
        if (map.get("defaultAngle") instanceof Number value) defaultAngle = value.doubleValue();
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_ROTATED_VECTOR_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

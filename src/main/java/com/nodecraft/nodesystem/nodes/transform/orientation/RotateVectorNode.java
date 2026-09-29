package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.orientation.rotate_vector",
    displayName = "Rotate Vector",
    description = "Rotates a vector around an axis by an angle in degrees",
    category = "transform.orientation",
    order = 1
)
public class RotateVectorNode extends AbstractOrientationNode {

    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_ANGLE_ID = "input_angle";

    private static final String OUTPUT_ROTATED_VECTOR_ID = "output_rotated_vector";

    @NodeProperty(displayName = "Default Axis X", category = "Rotation", order = 1)
    private double defaultAxisX = 0.0d;
    @NodeProperty(displayName = "Default Axis Y", category = "Rotation", order = 2)
    private double defaultAxisY = 1.0d;
    @NodeProperty(displayName = "Default Axis Z", category = "Rotation", order = 3)
    private double defaultAxisZ = 0.0d;
    @NodeProperty(displayName = "Default Angle", category = "Rotation", order = 4)
    private double defaultAngle = 90.0d;

    public RotateVectorNode() {
        super("transform.orientation.rotate_vector");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Vector to rotate", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Axis of rotation (will be normalized)", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle", "Angle of rotation in degrees", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ROTATED_VECTOR_ID, "Rotated Vector", "Resulting rotated vector", NodeDataType.VECTOR, this));
        addValidAndErrorOutputs();
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
        Vector3d vector = VectorUtils.toVector(inputValues.get(INPUT_VECTOR_ID));
        if (!VectorUtils.isFinite(vector)) {
            writeInvalid("Vector is missing or invalid");
            return;
        }

        Vector3d defaultAxis = new Vector3d(defaultAxisX, defaultAxisY, defaultAxisZ);
        Vector3d axis = OptionalPortDrive.resolveOptionalVector(this, INPUT_AXIS_ID, defaultAxis);
        if (axis == null) {
            writeInvalid("Axis is missing or invalid");
            return;
        }
        if (!VectorUtils.isNonZero(axis)) {
            writeInvalid("Axis is zero-length");
            return;
        }

        Double angleDegrees = OptionalPortDrive.resolveOptionalDouble(this, INPUT_ANGLE_ID, defaultAngle);
        if (angleDegrees == null || !Double.isFinite(angleDegrees)) {
            writeInvalid("Angle must be finite");
            return;
        }

        axis = new Vector3d(axis).normalize();
        Quaterniond rotation = new Quaterniond(new AxisAngle4d(Math.toRadians(angleDegrees), axis.x, axis.y, axis.z));
        Vector3d result = rotation.transform(new Vector3d(vector));

        outputValues.put(OUTPUT_ROTATED_VECTOR_ID, VectorUtils.toVectorPort(result));
        markSuccess();
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

    private void writeInvalid(String error) {
        putNullOutputs(OUTPUT_ROTATED_VECTOR_ID);
        markInvalid(error);
    }
}

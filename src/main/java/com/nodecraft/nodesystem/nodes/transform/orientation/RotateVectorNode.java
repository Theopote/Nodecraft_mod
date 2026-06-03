package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "transform.orientation.rotate_vector",
    displayName = "Rotate Vector",
    description = "Rotates a vector around an axis by an angle in radians",
    category = "transform.orientation",
    order = 1
)
public class RotateVectorNode extends BaseNode {

    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_ANGLE_ID = "input_angle_rad";

    private static final String OUTPUT_ROTATED_VECTOR_ID = "output_rotated_vector";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RotateVectorNode() {
        super(UUID.randomUUID(), "transform.orientation.rotate_vector");

        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Vector to rotate", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Axis of rotation (will be normalized)", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle (rad)", "Angle of rotation in radians", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ROTATED_VECTOR_ID, "Rotated Vector", "Resulting rotated vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the vector rotation succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Rotates a vector around an axis by an angle in radians";
    }

    @Override
    public String getDisplayName() {
        return "Rotate Vector";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d vector = OrientationUtils.resolveVector(inputValues.get(INPUT_VECTOR_ID));
        Vector3d axis = OrientationUtils.resolveVector(inputValues.get(INPUT_AXIS_ID));
        Object angleObj = inputValues.get(INPUT_ANGLE_ID);

        if (!OrientationUtils.isFinite(vector)
            || !OrientationUtils.isUsableDirection(axis)
            || !(angleObj instanceof Number angleNumber)) {
            writeInvalid();
            return;
        }

        double angleRad = angleNumber.doubleValue();
        if (!Double.isFinite(angleRad)) {
            writeInvalid();
            return;
        }

        axis.normalize();
        Quaterniond rotation = new Quaterniond(new AxisAngle4d(angleRad, axis.x, axis.y, axis.z));
        Vector3d result = rotation.transform(new Vector3d(vector));

        outputValues.put(OUTPUT_ROTATED_VECTOR_ID, result);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_ROTATED_VECTOR_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

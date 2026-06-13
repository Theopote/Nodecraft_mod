package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "transform.basic_transforms.rotate_coordinates",
    displayName = "Rotate Coordinates",
    description = "Rotates a list of block coordinates around a point and axis",
    category = "transform.basic_transforms",
    order = 2
)
public class RotateCoordinatesNode extends BaseNode {

    public enum RotationAxis {
        X_AXIS, Y_AXIS, Z_AXIS, CUSTOM
    }

    @NodeProperty(displayName = "Default Axis", category = "Rotation", order = 1)
    private RotationAxis rotationAxis = RotationAxis.Y_AXIS;

    @NodeProperty(displayName = "Default Angle", category = "Rotation", order = 2)
    private double defaultAngle = 0.0d;

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_ANGLE_ID = "input_angle";

    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";
    private static final String OUTPUT_EFFECTIVE_AXIS_ID = "output_effective_axis";
    private static final String OUTPUT_EFFECTIVE_ANGLE_ID = "output_effective_angle";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RotateCoordinatesNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.rotate_coordinates");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "The coordinates to rotate", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Rotation center point", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Rotation axis vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle", "Rotation angle in degrees", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", "Rotated coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_AXIS_ID, "Effective Axis", "Rotation axis actually used", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_ANGLE_ID, "Effective Angle", "Rotation angle actually used in degrees", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output coordinates", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the coordinate rotation succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Rotates a list of coordinates around a point and axis";
    }

    @Override
    public String getDisplayName() {
        return "Rotate Coordinates";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object centerObj = inputValues.get(INPUT_CENTER_ID);
        Object axisObj = inputValues.get(INPUT_AXIS_ID);
        Object angleObj = inputValues.get(INPUT_ANGLE_ID);

        BlockPosList result = new BlockPosList();
        if (!(coordinatesObj instanceof BlockPosList coordinates)) {
            writeResult(result, false);
            return;
        }

        BlockPos centerPos = centerObj instanceof BlockPos pos ? pos : BlockPos.ORIGIN;
        Vector3d axis = axisObj instanceof Vector3d axisInput ? new Vector3d(axisInput) : axisFromProperty();
        double angleDegrees = angleObj instanceof Number angleNumber ? angleNumber.doubleValue() : defaultAngle;
        if (!isFinite(axis) || axis.lengthSquared() <= 1.0e-12d || !Double.isFinite(angleDegrees)) {
            writeResult(result, false);
            return;
        }

        axis.normalize();
        Quaterniond rotation = new Quaterniond(new AxisAngle4d(Math.toRadians(angleDegrees), axis.x, axis.y, axis.z));
        Vector3d center = new Vector3d(centerPos.getX(), centerPos.getY(), centerPos.getZ());

        for (BlockPos pos : coordinates) {
            Vector3d transformed = new Vector3d(pos.getX(), pos.getY(), pos.getZ())
                .sub(center)
                .rotate(rotation)
                .add(center);
            result.add(new BlockPos(
                (int) Math.round(transformed.x),
                (int) Math.round(transformed.y),
                (int) Math.round(transformed.z)
            ));
        }

        writeResult(result, true, axis, angleDegrees);
    }

    private void writeResult(BlockPosList result, boolean valid) {
        writeResult(result, valid, new Vector3d(), 0.0d);
    }

    private void writeResult(BlockPosList result, boolean valid, Vector3d effectiveAxis, double effectiveAngle) {
        outputValues.put(OUTPUT_COORDINATES_ID, result);
        outputValues.put(OUTPUT_EFFECTIVE_AXIS_ID, effectiveAxis);
        outputValues.put(OUTPUT_EFFECTIVE_ANGLE_ID, effectiveAngle);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private Vector3d axisFromProperty() {
        RotationAxis axis = rotationAxis == null ? RotationAxis.Y_AXIS : rotationAxis;
        return switch (axis) {
            case X_AXIS -> new Vector3d(1.0d, 0.0d, 0.0d);
            case Z_AXIS -> new Vector3d(0.0d, 0.0d, 1.0d);
            case Y_AXIS, CUSTOM -> new Vector3d(0.0d, 1.0d, 0.0d);
        };
    }

    private boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    public RotationAxis getRotationAxis() {
        return rotationAxis;
    }

    public void setRotationAxis(RotationAxis axis) {
        if (axis != null && this.rotationAxis != axis) {
            this.rotationAxis = axis;
            markDirty();
        }
    }

    public double getDefaultAngle() {
        return defaultAngle;
    }

    public void setDefaultAngle(double defaultAngle) {
        if (Double.compare(this.defaultAngle, defaultAngle) != 0) {
            this.defaultAngle = defaultAngle;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("rotationAxis", rotationAxis.name());
        state.put("defaultAngle", defaultAngle);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }
        if (stateMap.get("rotationAxis") instanceof String axisName) {
            try {
                setRotationAxis(RotationAxis.valueOf(axisName));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (stateMap.get("defaultAngle") instanceof Number value) {
            setDefaultAngle(value.doubleValue());
        }
    }
}

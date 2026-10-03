package com.nodecraft.nodesystem.nodes.transform.placement;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PlacementBlockUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.placement.rotate_block_positions",
    displayName = "Rotate Block Positions",
    description = "Rotates a list of block positions around a point and axis",
    category = "transform.placement",
    order = 5
)
public class RotateBlockPositionsNode extends AbstractPlacementNode {

    public enum RotationAxis {
        X_AXIS, Y_AXIS, Z_AXIS
    }

    @NodeProperty(displayName = "Default Axis", category = "Rotation", order = 1)
    private RotationAxis rotationAxis = RotationAxis.Y_AXIS;

    @NodeProperty(displayName = "Default Angle", category = "Rotation", order = 2)
    private double defaultAngle = 0.0d;

    private static final String INPUT_BLOCK_POSITIONS_ID = "input_block_positions";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_ANGLE_ID = "input_angle";

    private static final String OUTPUT_BLOCK_POSITIONS_ID = "output_block_positions";
    private static final String OUTPUT_EFFECTIVE_AXIS_ID = "output_effective_axis";
    private static final String OUTPUT_EFFECTIVE_ANGLE_ID = "output_effective_angle";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public RotateBlockPositionsNode() {
        super("transform.placement.rotate_block_positions");

        addInputPort(new BasePort(INPUT_BLOCK_POSITIONS_ID, "Block Positions", "The block positions to rotate", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Rotation center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Rotation axis vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle", "Rotation angle in degrees", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_POSITIONS_ID, "Block Positions", "Rotated block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_AXIS_ID, "Effective Axis", "Rotation axis actually used", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_ANGLE_ID, "Effective Angle", "Rotation angle actually used in degrees", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output block positions", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Rotates a list of block positions around a point and axis";
    }

    @Override
    public String getDisplayName() {
        return "Rotate Block Positions";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinatesObj = inputValues.get(INPUT_BLOCK_POSITIONS_ID);
        if (!(coordinatesObj instanceof BlockPosList coordinates)) {
            writeInvalid("Missing block position list", null, Double.NaN);
            return;
        }

        String listError = PlacementBlockUtils.validateBlockListSize(coordinates);
        if (listError != null) {
            writeInvalid(listError, null, Double.NaN);
            return;
        }

        Vector3d center = OptionalPortDrive.resolveOptionalPoint(this, INPUT_CENTER_ID, new Vector3d());
        Vector3d axis = OptionalPortDrive.resolveOptionalVector(this, INPUT_AXIS_ID, axisFromProperty());
        Double angleDegrees = OptionalPortDrive.resolveOptionalDouble(this, INPUT_ANGLE_ID, defaultAngle);
        if (center == null || axis == null || angleDegrees == null) {
            writeInvalid("Rotation center, axis, or angle invalid", null, Double.NaN);
            return;
        }

        Vector3d unitAxis = VectorUtils.safeNormalize(axis);
        if (unitAxis == null) {
            writeInvalid("Rotation axis must be a finite non-zero VECTOR", null, Double.NaN);
            return;
        }

        double angleRad = Math.toRadians(angleDegrees);
        if (!Double.isFinite(angleRad)) {
            writeInvalid("Rotation angle too large", null, Double.NaN);
            return;
        }

        Quaterniond rotation = new Quaterniond(new AxisAngle4d(angleRad, unitAxis.x, unitAxis.y, unitAxis.z));

        BlockPosList result = new BlockPosList();
        for (BlockPos pos : coordinates) {
            Vector3d transformed = BlockSpace.cellCenter(pos)
                .sub(center)
                .rotate(rotation)
                .add(center);
            BlockPos snapped = PlacementBlockUtils.trySnapCellCenter(transformed);
            if (snapped == null) {
                writeInvalid("Non-finite rotation result", null, Double.NaN);
                return;
            }
            result.add(snapped);
        }

        outputValues.put(OUTPUT_BLOCK_POSITIONS_ID, result);
        outputValues.put(OUTPUT_EFFECTIVE_AXIS_ID, VectorUtils.toVectorPort(unitAxis));
        outputValues.put(OUTPUT_EFFECTIVE_ANGLE_ID, angleDegrees);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
        markSuccess();
    }

    private void writeInvalid(String error, @Nullable Vector3d effectiveAxis, double effectiveAngle) {
        putEmptyBlockListOutputs(OUTPUT_BLOCK_POSITIONS_ID);
        outputValues.put(OUTPUT_EFFECTIVE_AXIS_ID, VectorUtils.toVectorPort(effectiveAxis));
        outputValues.put(OUTPUT_EFFECTIVE_ANGLE_ID, effectiveAngle);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }

    private Vector3d axisFromProperty() {
        RotationAxis axis = rotationAxis == null ? RotationAxis.Y_AXIS : rotationAxis;
        return switch (axis) {
            case X_AXIS -> new Vector3d(1.0d, 0.0d, 0.0d);
            case Z_AXIS -> new Vector3d(0.0d, 0.0d, 1.0d);
            case Y_AXIS -> new Vector3d(0.0d, 1.0d, 0.0d);
        };
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
        if (!Double.isFinite(defaultAngle)) {
            return;
        }
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

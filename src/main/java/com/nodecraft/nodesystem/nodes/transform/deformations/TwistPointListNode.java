package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.deformations.twist",
    displayName = "Twist Point List",
    description = "Twists a point list around an axis by distributing rotation along a specified axial length",
    category = "transform.deformations",
    order = 0
)
public class TwistPointListNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    public enum ClampMode {
        CLAMP,
        REPEAT,
        UNBOUNDED
    }

    @NodeProperty(displayName = "Angle Degrees", category = "Twist", order = 1)
    private double angleDegrees = 180.0d;

    @NodeProperty(displayName = "Twist Length", category = "Twist", order = 2)
    private double twistLength = 10.0d;

    @NodeProperty(displayName = "Clamp Mode", category = "Twist", order = 3)
    private ClampMode clampMode = ClampMode.CLAMP;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_AXIS_ORIGIN_ID = "input_axis_origin";
    private static final String INPUT_AXIS_DIRECTION_ID = "input_axis_direction";
    private static final String INPUT_ANGLE_DEGREES_ID = "input_angle_degrees";
    private static final String INPUT_TWIST_LENGTH_ID = "input_twist_length";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TwistPointListNode() {
        super(UUID.randomUUID(), "transform.deformations.twist");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Point list to twist", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_AXIS_ORIGIN_ID, "Axis Origin", "Origin point of the twist axis", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_DIRECTION_ID, "Axis Direction", "Direction vector of the twist axis", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ANGLE_DEGREES_ID, "Angle Degrees", "Optional total twist angle override in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TWIST_LENGTH_ID, "Twist Length", "Optional axial length over which the twist angle is distributed", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Twisted point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of points in the twisted output", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the inputs were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Twists a point list around an axis by distributing rotation along a specified axial length";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pointsInput = SpatialValueResolver.resolvePointList(inputValues.get(INPUT_POINTS_ID));
        Vector3d axisOrigin = SpatialValueResolver.resolveVector3d(inputValues.get(INPUT_AXIS_ORIGIN_ID));
        Object axisDirectionObj = inputValues.get(INPUT_AXIS_DIRECTION_ID);

        if (pointsInput.isEmpty() || !DeformationUtils.isFinite(axisOrigin) || !(axisDirectionObj instanceof Vector3d axisDirection)) {
            writeEmptyOutputs();
            return;
        }

        Vector3d axis = new Vector3d(axisDirection);
        if (!DeformationUtils.isUsableDirection(axis)) {
            writeEmptyOutputs();
            return;
        }
        axis.normalize();

        double resolvedAngleDegrees = resolveDouble(inputValues.get(INPUT_ANGLE_DEGREES_ID), angleDegrees);
        double resolvedTwistLength = Math.max(EPSILON, resolveDouble(inputValues.get(INPUT_TWIST_LENGTH_ID), twistLength));
        if (!Double.isFinite(resolvedAngleDegrees) || !Double.isFinite(resolvedTwistLength)) {
            writeEmptyOutputs();
            return;
        }
        double totalAngleRadians = Math.toRadians(resolvedAngleDegrees);

        List<Vector3d> twistedPoints = new ArrayList<>(pointsInput.size());
        for (Vector3d point : pointsInput) {
            Vector3d offset = new Vector3d(point).sub(axisOrigin);
            double axialDistance = offset.dot(axis);
            double normalizedDistance = axialDistance / resolvedTwistLength;
            double twistFactor = applyClampMode(normalizedDistance);
            double angle = totalAngleRadians * twistFactor;

            Vector3d axialComponent = new Vector3d(axis).mul(axialDistance);
            Vector3d radialComponent = new Vector3d(offset).sub(axialComponent);
            Vector3d rotatedRadial = DeformationUtils.rotateAroundAxis(radialComponent, axis, angle);
            twistedPoints.add(new Vector3d(axisOrigin).add(axialComponent).add(rotatedRadial));
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(twistedPoints));
        outputValues.put(OUTPUT_COUNT_ID, twistedPoints.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("angleDegrees", angleDegrees);
        state.put("twistLength", twistLength);
        state.put("clampMode", clampMode.name());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("angleDegrees") instanceof Number value) {
            setAngleDegrees(value.doubleValue());
        }
        if (map.get("twistLength") instanceof Number value) {
            setTwistLength(value.doubleValue());
        }
        if (map.get("clampMode") instanceof String value) {
            setClampModeString(value);
        }
    }

    public double getAngleDegrees() {
        return angleDegrees;
    }

    public void setAngleDegrees(double angleDegrees) {
        this.angleDegrees = DeformationUtils.resolveFiniteDouble(angleDegrees, this.angleDegrees);
        markDirty();
    }

    public double getTwistLength() {
        return twistLength;
    }

    public void setTwistLength(double twistLength) {
        this.twistLength = Math.max(EPSILON, DeformationUtils.resolveFiniteDouble(twistLength, this.twistLength));
        markDirty();
    }

    public ClampMode getClampMode() {
        return clampMode;
    }

    public void setClampMode(ClampMode clampMode) {
        this.clampMode = clampMode == null ? ClampMode.CLAMP : clampMode;
        markDirty();
    }

    public void setClampModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            setClampMode(ClampMode.CLAMP);
            return;
        }
        try {
            setClampMode(ClampMode.valueOf(mode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setClampMode(ClampMode.CLAMP);
        }
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private double resolveDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return DeformationUtils.resolveFiniteDouble(number, fallback);
        }
        return fallback;
    }

    private double applyClampMode(double normalizedDistance) {
        return switch (clampMode) {
            case CLAMP -> Math.max(0.0d, Math.min(1.0d, normalizedDistance));
            case REPEAT -> normalizedDistance - Math.floor(normalizedDistance);
            case UNBOUNDED -> normalizedDistance;
        };
    }

}

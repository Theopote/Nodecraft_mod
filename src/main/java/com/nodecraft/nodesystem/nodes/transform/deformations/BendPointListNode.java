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
    id = "transform.deformations.bend",
    displayName = "Bend Point List",
    description = "Bends a point list into an arc along an axis over a configurable bend length",
    category = "transform.deformations",
    order = 1
)
public class BendPointListNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    public enum ClampMode {CLAMP, REPEAT, UNBOUNDED}
    public enum BendPlaneMode {AUTO, XY, XZ, YZ, CUSTOM}

    @NodeProperty(displayName = "Bend Degrees", category = "Bend", order = 1)
    private double bendDegrees = 45.0d;

    @NodeProperty(displayName = "Bend Length", category = "Bend", order = 2)
    private double bendLength = 10.0d;

    @NodeProperty(displayName = "Clamp Mode", category = "Bend", order = 3)
    private ClampMode clampMode = ClampMode.CLAMP;

    @NodeProperty(displayName = "Bend Plane", category = "Bend", order = 4)
    private BendPlaneMode bendPlaneMode = BendPlaneMode.AUTO;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_AXIS_ORIGIN_ID = "input_axis_origin";
    private static final String INPUT_AXIS_DIRECTION_ID = "input_axis_direction";
    private static final String INPUT_BEND_NORMAL_ID = "input_bend_normal";
    private static final String INPUT_BEND_DEGREES_ID = "input_bend_degrees";
    private static final String INPUT_BEND_LENGTH_ID = "input_bend_length";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public BendPointListNode() {
        super(UUID.randomUUID(), "transform.deformations.bend");
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Point list to bend", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_AXIS_ORIGIN_ID, "Axis Origin", "Origin point of the bend axis", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_DIRECTION_ID, "Axis Direction", "Direction vector of the bend axis", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_BEND_NORMAL_ID, "Bend Normal", "Direction the bend curves toward", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_BEND_DEGREES_ID, "Bend Degrees", "Optional total bend angle override in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BEND_LENGTH_ID, "Bend Length", "Optional length over which the bend is distributed", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Bent point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of points in the bent output", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the inputs were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Bends a point list into an arc along an axis over a configurable bend length";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pointsInput = SpatialValueResolver.resolvePointList(inputValues.get(INPUT_POINTS_ID));
        Vector3d axisOrigin = SpatialValueResolver.resolveVector3d(inputValues.get(INPUT_AXIS_ORIGIN_ID));
        if (pointsInput.isEmpty() || !DeformationUtils.isFinite(axisOrigin) || !(inputValues.get(INPUT_AXIS_DIRECTION_ID) instanceof Vector3d axisDirection)) {
            writeEmptyOutputs();
            return;
        }

        Vector3d axis = new Vector3d(axisDirection);
        if (!DeformationUtils.isUsableDirection(axis)) {
            writeEmptyOutputs();
            return;
        }
        axis.normalize();

        Vector3d normal = resolveBendNormal(axis);
        normal.sub(new Vector3d(axis).mul(normal.dot(axis)));
        if (normal.lengthSquared() <= EPSILON) {
            normal = defaultNormal(axis);
        }
        if (!DeformationUtils.isUsableDirection(normal)) {
            writeEmptyOutputs();
            return;
        }
        normal.normalize();
        Vector3d binormal = new Vector3d(axis).cross(normal);
        if (!DeformationUtils.isUsableDirection(binormal)) {
            writeEmptyOutputs();
            return;
        }
        binormal.normalize();

        double resolvedBendDegrees = resolveDouble(inputValues.get(INPUT_BEND_DEGREES_ID), bendDegrees);
        double resolvedBendLength = Math.max(EPSILON, resolveDouble(inputValues.get(INPUT_BEND_LENGTH_ID), bendLength));
        if (!Double.isFinite(resolvedBendDegrees) || !Double.isFinite(resolvedBendLength)) {
            writeEmptyOutputs();
            return;
        }
        double totalAngleRadians = Math.toRadians(resolvedBendDegrees);
        double curvature = Math.abs(totalAngleRadians) <= EPSILON ? 0.0d : totalAngleRadians / resolvedBendLength;
        double radius = Math.abs(curvature) <= EPSILON ? 0.0d : 1.0d / curvature;

        List<Vector3d> bentPoints = new ArrayList<>(pointsInput.size());
        for (Vector3d point : pointsInput) {
            Vector3d offset = new Vector3d(point).sub(axisOrigin);
            double axialDistance = offset.dot(axis);
            double normalizedDistance = axialDistance / resolvedBendLength;
            double bendFactor = applyClampMode(normalizedDistance);
            double theta = totalAngleRadians * bendFactor;

            Vector3d axialComponent = new Vector3d(axis).mul(axialDistance);
            Vector3d radialComponent = new Vector3d(offset).sub(axialComponent);

            Vector3d centerline;
            if (Math.abs(totalAngleRadians) <= EPSILON) {
                centerline = new Vector3d(axisOrigin).add(axialComponent);
            } else {
                centerline = new Vector3d(axisOrigin)
                    .add(new Vector3d(normal).mul(radius * (1.0d - Math.cos(theta))))
                    .add(new Vector3d(axis).mul(radius * Math.sin(theta)));
            }

            Vector3d rotatedRadial = DeformationUtils.rotateAroundAxis(radialComponent, binormal, theta);
            bentPoints.add(centerline.add(rotatedRadial));
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(bentPoints));
        outputValues.put(OUTPUT_COUNT_ID, bentPoints.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("bendDegrees", bendDegrees);
        state.put("bendLength", bendLength);
        state.put("clampMode", clampMode.name());
        state.put("bendPlaneMode", bendPlaneMode.name());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("bendDegrees") instanceof Number value) {
            bendDegrees = DeformationUtils.finiteOrCurrent(value, bendDegrees);
        }
        if (map.get("bendLength") instanceof Number value) {
            bendLength = Math.max(EPSILON, DeformationUtils.finiteOrCurrent(value, bendLength));
        }
        if (map.get("clampMode") instanceof String value) {
            try {
                clampMode = ClampMode.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                clampMode = ClampMode.CLAMP;
            }
        }
        if (map.get("bendPlaneMode") instanceof String value) {
            try {
                bendPlaneMode = BendPlaneMode.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                bendPlaneMode = BendPlaneMode.AUTO;
            }
        }
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private double resolveDouble(Object value, double fallback) {
        return DeformationUtils.resolveFiniteDouble(value, fallback);
    }

    private double applyClampMode(double normalizedDistance) {
        return switch (clampMode) {
            case CLAMP -> Math.max(0.0d, Math.min(1.0d, normalizedDistance));
            case REPEAT -> normalizedDistance - Math.floor(normalizedDistance);
            case UNBOUNDED -> normalizedDistance;
        };
    }

    private Vector3d defaultNormal(Vector3d axis) {
        Vector3d fallback = Math.abs(axis.y) < 0.9d ? new Vector3d(0.0d, 1.0d, 0.0d) : new Vector3d(1.0d, 0.0d, 0.0d);
        fallback.sub(new Vector3d(axis).mul(fallback.dot(axis)));
        return fallback.normalize();
    }

    private Vector3d resolveBendNormal(Vector3d axis) {
        if (bendPlaneMode == BendPlaneMode.CUSTOM && inputValues.get(INPUT_BEND_NORMAL_ID) instanceof Vector3d customNormal) {
            return new Vector3d(customNormal);
        }
        return switch (bendPlaneMode) {
            case XY -> new Vector3d(0.0d, 0.0d, 1.0d);
            case XZ -> new Vector3d(0.0d, 1.0d, 0.0d);
            case YZ -> new Vector3d(1.0d, 0.0d, 0.0d);
            case CUSTOM, AUTO -> inputValues.get(INPUT_BEND_NORMAL_ID) instanceof Vector3d custom
                ? new Vector3d(custom)
                : defaultNormal(axis);
        };
    }

}

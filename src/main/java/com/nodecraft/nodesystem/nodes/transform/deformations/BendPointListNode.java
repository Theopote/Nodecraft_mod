package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialTolerance;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
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
        List<Vector3d> pointsInput = PointUtils.resolveStrictPointList(inputValues.get(INPUT_POINTS_ID));
        Vector3d axisOrigin = OptionalPortDrive.resolveOptionalPoint(this, INPUT_AXIS_ORIGIN_ID, new Vector3d());
        Vector3d axisDirection = SpatialValueResolver.resolveVector(inputValues.get(INPUT_AXIS_DIRECTION_ID));
        Double resolvedBendDegrees = OptionalPortDrive.resolveOptionalDouble(this, INPUT_BEND_DEGREES_ID, bendDegrees);
        Double resolvedBendLength = OptionalPortDrive.resolveOptionalDouble(this, INPUT_BEND_LENGTH_ID, bendLength);

        if (pointsInput == null
                || axisOrigin == null
                || !VectorUtils.isNonZero(axisDirection)
                || resolvedBendDegrees == null
                || resolvedBendLength == null
                || resolvedBendLength <= 0.0d) {
            writeInvalid();
            return;
        }

        Vector3d axis = new Vector3d(axisDirection).normalize();
        Vector3d normal = resolveBendNormal(axis);
        if (normal == null) {
            writeInvalid();
            return;
        }
        normal.sub(new Vector3d(axis).mul(normal.dot(axis)));
        if (!VectorUtils.isNonZero(normal)) {
            if (bendPlaneMode == BendPlaneMode.CUSTOM) {
                writeInvalid();
                return;
            }
            normal = defaultNormal(axis);
        }
        if (!VectorUtils.isNonZero(normal)) {
            writeInvalid();
            return;
        }
        normal.normalize();
        Vector3d binormal = new Vector3d(axis).cross(normal);
        if (!VectorUtils.isNonZero(binormal)) {
            writeInvalid();
            return;
        }
        binormal.normalize();

        double totalAngleRadians = Math.toRadians(resolvedBendDegrees);
        double curvature = Math.abs(totalAngleRadians) <= SpatialTolerance.EPS ? 0.0d : totalAngleRadians / resolvedBendLength;
        double radius = Math.abs(curvature) <= SpatialTolerance.EPS ? 0.0d : 1.0d / curvature;

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
            if (Math.abs(totalAngleRadians) <= SpatialTolerance.EPS) {
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
        if (map.get("bendDegrees") instanceof Number value && Double.isFinite(value.doubleValue())) {
            bendDegrees = value.doubleValue();
        }
        if (map.get("bendLength") instanceof Number value && Double.isFinite(value.doubleValue()) && value.doubleValue() > 0.0d) {
            bendLength = value.doubleValue();
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

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
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

    private @Nullable Vector3d resolveBendNormal(Vector3d axis) {
        BendPlaneMode mode = bendPlaneMode == null ? BendPlaneMode.AUTO : bendPlaneMode;
        return switch (mode) {
            case XY -> new Vector3d(0.0d, 0.0d, 1.0d);
            case XZ -> new Vector3d(0.0d, 1.0d, 0.0d);
            case YZ -> new Vector3d(1.0d, 0.0d, 0.0d);
            case CUSTOM -> {
                Vector3d custom = OptionalPortDrive.resolveOptionalVector(this, INPUT_BEND_NORMAL_ID, null);
                if (!VectorUtils.isNonZero(custom)) {
                    yield null;
                }
                yield new Vector3d(custom);
            }
            case AUTO -> {
                if (OptionalPortDrive.isConnected(this, INPUT_BEND_NORMAL_ID)) {
                    Vector3d custom = OptionalPortDrive.resolveOptionalVector(this, INPUT_BEND_NORMAL_ID, null);
                    if (!VectorUtils.isNonZero(custom)) {
                        yield null;
                    }
                    yield new Vector3d(custom);
                }
                yield defaultNormal(axis);
            }
        };
    }
}

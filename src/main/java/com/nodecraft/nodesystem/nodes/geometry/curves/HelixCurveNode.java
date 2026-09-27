package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.helix",
    displayName = "Helix Curve",
    description = "Builds a sampled helix from center, axis, radius, pitch, turns, and segment count.",
    category = "geometry.curves",
    order = 10
)
public class HelixCurveNode extends AbstractCurveNode {

    @NodeProperty(displayName = "Center X", category = "Center", order = 1,
        description = "Default center X when Center port is unconnected")
    private double centerX = 0.0d;

    @NodeProperty(displayName = "Center Y", category = "Center", order = 2,
        description = "Default center Y when Center port is unconnected")
    private double centerY = 0.0d;

    @NodeProperty(displayName = "Center Z", category = "Center", order = 3,
        description = "Default center Z when Center port is unconnected")
    private double centerZ = 0.0d;

    @NodeProperty(displayName = "Axis X", category = "Axis", order = 4,
        description = "Default axis X when Axis port is unconnected")
    private double axisX = 0.0d;

    @NodeProperty(displayName = "Axis Y", category = "Axis", order = 5,
        description = "Default axis Y when Axis port is unconnected")
    private double axisY = 1.0d;

    @NodeProperty(displayName = "Axis Z", category = "Axis", order = 6,
        description = "Default axis Z when Axis port is unconnected")
    private double axisZ = 0.0d;

    @NodeProperty(displayName = "Default Radius", category = "Helix", order = 7)
    private double defaultRadius = 4.0d;

    @NodeProperty(displayName = "Default Pitch", category = "Helix", order = 8,
        description = "Vertical advance per turn")
    private double defaultPitch = 2.0d;

    @NodeProperty(displayName = "Default Turns", category = "Helix", order = 9)
    private double defaultTurns = 3.0d;

    @NodeProperty(displayName = "Default Segments Per Turn", category = "Helix", order = 10)
    private int defaultSegmentsPerTurn = 24;

    @NodeProperty(displayName = "Default Start Angle", category = "Helix", order = 11,
        description = "Initial angle in degrees")
    private double defaultStartAngle = 0.0d;

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_PITCH_ID = "input_pitch";
    private static final String INPUT_TURNS_ID = "input_turns";
    private static final String INPUT_SEGMENTS_PER_TURN_ID = "input_segments_per_turn";
    private static final String INPUT_START_ANGLE_ID = "input_start_angle";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_LENGTH_ID = "output_length";

    public HelixCurveNode() {
        super(UUID.randomUUID(), "geometry.curves.helix");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Helix base center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Helix axis direction", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Helix radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PITCH_ID, "Pitch", "Vertical advance per turn", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TURNS_ID, "Turns", "Number of turns", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_PER_TURN_ID, "Segments Per Turn", "Sampling density per turn", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "Initial angle in degrees", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Primary helix path output", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Helix sample points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Approximate polyline length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when helix inputs are valid", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = PlaneProjectionUtils.resolvePointOrDefault(
            inputValues.get(INPUT_CENTER_ID), centerX, centerY, centerZ);
        Vector3d axisIn = resolveInputVector(inputValues.get(INPUT_AXIS_ID));
        if (axisIn == null) {
            axisIn = new Vector3d(axisX, axisY, axisZ);
        }

        Vector3d axis = new Vector3d(axisIn);
        if (axis.lengthSquared() <= 1.0e-12d) {
            invalidate("Axis must be a non-zero vector");
            return;
        }
        axis.normalize();

        Double radius = resolvePositiveDouble(INPUT_RADIUS_ID, defaultRadius);
        Double pitch = resolveFiniteDouble(INPUT_PITCH_ID, defaultPitch);
        Double turns = resolvePositiveDouble(INPUT_TURNS_ID, defaultTurns);
        Integer segmentsPerTurn = resolveBoundedInteger(
            INPUT_SEGMENTS_PER_TURN_ID,
            defaultSegmentsPerTurn,
            6,
            GenerationLimits.MAX_CURVE_SAMPLES
        );
        Double startAngleDegrees = resolveFiniteDouble(INPUT_START_ANGLE_ID, defaultStartAngle);

        if (radius == null) {
            invalidate("Radius must be a finite value greater than 0");
            return;
        }
        if (pitch == null) {
            invalidate("Pitch must be finite");
            return;
        }
        if (turns == null) {
            invalidate("Turns must be a finite value greater than 0");
            return;
        }
        if (segmentsPerTurn == null) {
            invalidate("Segments Per Turn must be an integer from 6 to " + GenerationLimits.MAX_CURVE_SAMPLES);
            return;
        }
        if (startAngleDegrees == null) {
            invalidate("Start Angle must be finite");
            return;
        }

        int totalSegments = Math.max(2, (int) Math.ceil(turns * segmentsPerTurn));
        if (totalSegments + 1 > GenerationLimits.MAX_CURVE_SAMPLES) {
            invalidate("Sample count exceeds maximum (" + GenerationLimits.MAX_CURVE_SAMPLES + ")");
            return;
        }

        double startAngle = Math.toRadians(startAngleDegrees);
        Vector3d basisU = fallbackAxis(axis);
        Vector3d basisV = new Vector3d(axis).cross(basisU).normalize();
        basisU = new Vector3d(basisV).cross(axis).normalize();

        List<Vec3d> pts = new ArrayList<>(totalSegments + 1);
        for (int i = 0; i <= totalSegments; i++) {
            double t = i / (double) totalSegments;
            double angle = startAngle + t * turns * Math.PI * 2.0d;
            double along = t * turns * pitch;
            Vector3d p = new Vector3d(center)
                .add(new Vector3d(axis).mul(along))
                .add(new Vector3d(basisU).mul(Math.cos(angle) * radius))
                .add(new Vector3d(basisV).mul(Math.sin(angle) * radius));
            pts.add(new Vec3d(p.x, p.y, p.z));
        }

        double length = 0.0d;
        for (int i = 1; i < pts.size(); i++) {
            length += pts.get(i - 1).distanceTo(pts.get(i));
        }

        PolylineData polyline = new PolylineData(pts);
        List<Vector3d> pointVectors = new ArrayList<>(pts.size());
        for (Vec3d point : pts) {
            pointVectors.add(new Vector3d(point.x, point.y, point.z));
        }

        outputValues.put(OUTPUT_PATH_ID, PathData.fromPolyline(polyline));
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(pointVectors));
        outputValues.put(OUTPUT_LENGTH_ID, length);
        markSuccess();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("centerX", centerX);
        state.put("centerY", centerY);
        state.put("centerZ", centerZ);
        state.put("axisX", axisX);
        state.put("axisY", axisY);
        state.put("axisZ", axisZ);
        state.put("defaultRadius", defaultRadius);
        state.put("defaultPitch", defaultPitch);
        state.put("defaultTurns", defaultTurns);
        state.put("defaultSegmentsPerTurn", defaultSegmentsPerTurn);
        state.put("defaultStartAngle", defaultStartAngle);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("centerX") instanceof Number n) {
            centerX = n.doubleValue();
        }
        if (map.get("centerY") instanceof Number n) {
            centerY = n.doubleValue();
        }
        if (map.get("centerZ") instanceof Number n) {
            centerZ = n.doubleValue();
        }
        if (map.get("axisX") instanceof Number n) {
            axisX = n.doubleValue();
        }
        if (map.get("axisY") instanceof Number n) {
            axisY = n.doubleValue();
        }
        if (map.get("axisZ") instanceof Number n) {
            axisZ = n.doubleValue();
        }
        if (map.get("defaultRadius") instanceof Number n) {
            defaultRadius = n.doubleValue();
        }
        if (map.get("defaultPitch") instanceof Number n) {
            defaultPitch = n.doubleValue();
        }
        if (map.get("defaultTurns") instanceof Number n) {
            defaultTurns = n.doubleValue();
        }
        if (map.get("defaultSegmentsPerTurn") instanceof Number n) {
            defaultSegmentsPerTurn = n.intValue();
        }
        if (map.get("defaultStartAngle") instanceof Number n) {
            defaultStartAngle = n.doubleValue();
        }
    }

    private void invalidate(String message) {
        putNullOutputs(OUTPUT_PATH_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putDoubleOutputs(0.0d, OUTPUT_LENGTH_ID);
        markInvalid(message);
    }

    private Vector3d fallbackAxis(Vector3d axis) {
        Vector3d reference = Math.abs(axis.y) < 0.99d ? new Vector3d(0.0d, 1.0d, 0.0d) : new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d u = reference.sub(new Vector3d(axis).mul(reference.dot(axis)));
        if (u.lengthSquared() <= 1.0e-12d) {
            reference = new Vector3d(0.0d, 0.0d, 1.0d);
            u = reference.sub(new Vector3d(axis).mul(reference.dot(axis)));
        }
        return u.normalize();
    }
}

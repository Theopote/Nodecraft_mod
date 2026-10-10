package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.CurveSampleFence;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.CurveInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
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
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Helix base center point", NodeDataType.POINT, this, false, false));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Helix axis direction", NodeDataType.VECTOR, this, false, false));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Helix radius", NodeDataType.DOUBLE, this, false, false));
        addInputPort(new BasePort(INPUT_PITCH_ID, "Pitch", "Vertical advance per turn", NodeDataType.DOUBLE, this, false, false));
        addInputPort(new BasePort(INPUT_TURNS_ID, "Turns", "Number of turns", NodeDataType.DOUBLE, this, false, false));
        addInputPort(new BasePort(INPUT_SEGMENTS_PER_TURN_ID, "Segments Per Turn", "Sampling density per turn", NodeDataType.INTEGER, this, false, false));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "Initial angle in degrees", NodeDataType.DOUBLE, this, false, false));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Primary helix path output", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Helix sample points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Approximate polyline length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when helix inputs are valid", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center;
        if (CurveInputUtils.isConnected(this, INPUT_CENTER_ID)) {
            center = CurveInputUtils.requireConnectedPointData(this, INPUT_CENTER_ID);
            if (center == null) {
                invalidate("Center is connected but invalid (must be finite PointData)");
                return;
            }
        } else {
            center = new Vector3d(centerX, centerY, centerZ);
            if (!VectorUtils.isFinite(center)) {
                invalidate("Default center is non-finite");
                return;
            }
        }

        Vector3d axisIn;
        if (CurveInputUtils.isConnected(this, INPUT_AXIS_ID)) {
            axisIn = CurveInputUtils.requireConnectedVectorData(this, INPUT_AXIS_ID);
            if (axisIn == null) {
                invalidate("Axis is connected but invalid (must be finite VectorData)");
                return;
            }
        } else {
            axisIn = new Vector3d(axisX, axisY, axisZ);
        }

        Vector3d axis = VectorUtils.safeNormalize(axisIn);
        if (axis == null) {
            invalidate("Axis must be a non-zero finite vector");
            return;
        }

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

        double product = turns * (double) segmentsPerTurn;
        if (!Double.isFinite(product)) {
            invalidate("Helix sample product is non-finite");
            return;
        }
        double ceilSegments = Math.ceil(product);
        if (!Double.isFinite(ceilSegments) || ceilSegments < 2.0d
            || ceilSegments > (double) (GenerationLimits.MAX_CURVE_SAMPLES - 1)) {
            invalidate("Sample count exceeds maximum (" + GenerationLimits.MAX_CURVE_SAMPLES + ")");
            return;
        }
        int totalSegments = (int) ceilSegments;
        long sampleCount = (long) totalSegments + 1L;
        if (sampleCount > GenerationLimits.MAX_CURVE_SAMPLES) {
            invalidate("Sample count exceeds maximum (" + GenerationLimits.MAX_CURVE_SAMPLES + ")");
            return;
        }

        double startAngle = Math.toRadians(startAngleDegrees);
        if (!Double.isFinite(startAngle)) {
            invalidate("Start Angle in radians is non-finite");
            return;
        }
        var basis = PlaneProjectionUtils.createBasisFromNormal(axis);
        if (basis == null) {
            invalidate("Helix frame could not be constructed from axis");
            return;
        }

        List<Vec3d> pts = new ArrayList<>((int) sampleCount);
        for (int i = 0; i <= totalSegments; i++) {
            double t = i / (double) totalSegments;
            double angle = startAngle + t * turns * Math.PI * 2.0d;
            double along = t * turns * pitch;
            if (!Double.isFinite(angle) || !Double.isFinite(along)) {
                invalidate("Helix sample parameter is non-finite");
                return;
            }
            Vector3d alongAxis = VectorUtils.safeScale(axis, along);
            Vector3d radialU = VectorUtils.safeScale(basis.xAxis(), Math.cos(angle) * radius);
            Vector3d radialV = VectorUtils.safeScale(basis.yAxis(), Math.sin(angle) * radius);
            Vector3d point = VectorUtils.safeAdd(center, VectorUtils.safeAdd(alongAxis, VectorUtils.safeAdd(radialU, radialV)));
            if (point == null || !VectorUtils.isFinite(point)) {
                invalidate("Helix sample point is non-finite");
                return;
            }
            pts.add(new Vec3d(point.x, point.y, point.z));
        }

        CurveSampleFence.Result fenced = CurveSampleFence.validate(pts);
        if (fenced == null) {
            invalidate("Helix samples are non-finite or over budget");
            return;
        }

        outputValues.put(OUTPUT_PATH_ID, fenced.path());
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(fenced.vectors()));
        outputValues.put(OUTPUT_LENGTH_ID, fenced.length());
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
        putDoubleOutputs(Double.NaN, OUTPUT_LENGTH_ID);
        markInvalid(message);
    }
}

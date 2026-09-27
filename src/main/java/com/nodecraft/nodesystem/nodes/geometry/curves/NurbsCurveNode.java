package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.nodes.geometry.curves.util.CurveMathUtils;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.CurveInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.nurbs",
    displayName = "NURBS Curve",
    description = "Builds a sampled clamped uniform NURBS curve from control points and optional per-point weights",
    category = "geometry.curves",
    order = 8
)
public class NurbsCurveNode extends AbstractCurveNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(displayName = "Default Degree", category = "NURBS", order = 1)
    private int defaultDegree = 3;

    @NodeProperty(displayName = "Default Resolution Per Span", category = "NURBS", order = 2)
    private int defaultResolutionPerSpan = 12;

    @NodeProperty(displayName = "Default Weight", category = "NURBS", order = 3)
    private double defaultWeight = 1.0d;

    private static final String INPUT_CONTROL_POINTS_ID = "input_control_points";
    private static final String INPUT_WEIGHTS_ID = "input_weights";
    private static final String INPUT_DEGREE_ID = "input_degree";
    private static final String INPUT_RESOLUTION_ID = "input_resolution";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_CONTROL_PATH_ID = "output_control_path";
    private static final String OUTPUT_CONTROL_COUNT_ID = "output_control_count";
    private static final String OUTPUT_DEGREE_ID = "output_degree";
    private static final String OUTPUT_LENGTH_ID = "output_length";

    public NurbsCurveNode() {
        super(UUID.randomUUID(), "geometry.curves.nurbs");

        addInputPort(new BasePort(INPUT_CONTROL_POINTS_ID, "Control Points", "Ordered NURBS control points", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_WEIGHTS_ID, "Weights", "Optional weight list aligned with control points (unconnected uses Default Weight)", NodeDataType.DOUBLE_LIST, this));
        addInputPort(new BasePort(INPUT_DEGREE_ID, "Degree", "Curve degree (1..5, must be less than control point count)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_RESOLUTION_ID, "Resolution / Span", "Samples generated per knot span", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Primary NURBS path output", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled points along the NURBS curve", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_PATH_ID, "Control Path", "Path through the control points", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_COUNT_ID, "Control Count", "Number of valid control points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_DEGREE_ID, "Degree", "Degree used for evaluation", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Sampled path length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when valid control points are sufficient", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> resolved = SpatialValueResolver.resolvePointList(inputValues.get(INPUT_CONTROL_POINTS_ID));
        List<Vec3d> controlPoints = new ArrayList<>(resolved.size());
        for (Vector3d point : resolved) {
            controlPoints.add(new Vec3d(point.x, point.y, point.z));
        }

        if (controlPoints.size() < 2) {
            invalidate("At least 2 control points are required", controlPoints.size(), 0);
            return;
        }

        List<Double> weights = CurveInputUtils.resolveOptionalNurbsWeights(
            this, INPUT_WEIGHTS_ID, controlPoints.size(), defaultWeight);
        if (weights == null) {
            invalidate("Weights must be a DOUBLE_LIST aligned to control points with finite values > 0", controlPoints.size(), 0);
            return;
        }

        Integer degree = resolveBoundedInteger(INPUT_DEGREE_ID, defaultDegree, 1, 5);
        if (degree == null) {
            invalidate("Degree must be an integer from 1 to 5", controlPoints.size(), 0);
            return;
        }
        if (degree >= controlPoints.size()) {
            invalidate("Degree must be less than control point count", controlPoints.size(), degree);
            return;
        }

        Integer resolutionPerSpan = resolveBoundedInteger(
            INPUT_RESOLUTION_ID,
            defaultResolutionPerSpan,
            2,
            GenerationLimits.MAX_CURVE_SAMPLES
        );
        if (resolutionPerSpan == null) {
            invalidate("Resolution / Span must be an integer from 2 to " + GenerationLimits.MAX_CURVE_SAMPLES, controlPoints.size(), degree);
            return;
        }

        int n = controlPoints.size() - 1;
        int spanCount = Math.max(1, n - degree + 1);
        int totalSamples = spanCount * resolutionPerSpan + 1;
        if (totalSamples > GenerationLimits.MAX_CURVE_SAMPLES) {
            invalidate("Sample count exceeds maximum (" + GenerationLimits.MAX_CURVE_SAMPLES + ")", controlPoints.size(), degree);
            return;
        }

        int knotCount = n + degree + 2;
        double[] knots = CurveMathUtils.buildClampedUniformKnots(knotCount, degree, n);

        double uStart = knots[degree];
        double uEnd = knots[n + 1];
        List<Vec3d> sampled = new ArrayList<>(totalSamples);

        for (int i = 0; i < totalSamples; i++) {
            double t = totalSamples == 1 ? 0.0d : (double) i / (double) (totalSamples - 1);
            double u = uStart + (uEnd - uStart) * t;
            sampled.add(CurveMathUtils.evaluateNurbs(controlPoints, weights, knots, degree, u, n, EPSILON));
        }

        PolylineData polyline = new PolylineData(sampled);
        List<Vector3d> sampledVectors = new ArrayList<>(sampled.size());
        for (Vec3d sample : sampled) {
            sampledVectors.add(new Vector3d(sample.x, sample.y, sample.z));
        }

        outputValues.put(OUTPUT_PATH_ID, PathData.fromPolyline(polyline));
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(sampledVectors));
        outputValues.put(OUTPUT_CONTROL_PATH_ID, PathData.fromPolyline(new PolylineData(controlPoints)));
        outputValues.put(OUTPUT_CONTROL_COUNT_ID, controlPoints.size());
        outputValues.put(OUTPUT_DEGREE_ID, degree);
        outputValues.put(OUTPUT_LENGTH_ID, polyline.getLength());
        markSuccess();
    }

    public int getDefaultDegree() {
        return defaultDegree;
    }

    public void setDefaultDegree(int defaultDegree) {
        if (defaultDegree >= 1 && defaultDegree <= 5 && this.defaultDegree != defaultDegree) {
            this.defaultDegree = defaultDegree;
            markDirty();
        }
    }

    public int getDefaultResolutionPerSpan() {
        return defaultResolutionPerSpan;
    }

    public void setDefaultResolutionPerSpan(int defaultResolutionPerSpan) {
        if (defaultResolutionPerSpan >= 2
                && defaultResolutionPerSpan <= GenerationLimits.MAX_CURVE_SAMPLES
                && this.defaultResolutionPerSpan != defaultResolutionPerSpan) {
            this.defaultResolutionPerSpan = defaultResolutionPerSpan;
            markDirty();
        }
    }

    public double getDefaultWeight() {
        return defaultWeight;
    }

    public void setDefaultWeight(double defaultWeight) {
        if (Double.isFinite(defaultWeight) && defaultWeight > 0.0d
                && Double.compare(this.defaultWeight, defaultWeight) != 0) {
            this.defaultWeight = defaultWeight;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("defaultDegree", defaultDegree);
            put("defaultResolutionPerSpan", defaultResolutionPerSpan);
            put("defaultWeight", defaultWeight);
        }};
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultDegree") instanceof Number value) {
            setDefaultDegree(value.intValue());
        }
        if (map.get("defaultResolutionPerSpan") instanceof Number value) {
            setDefaultResolutionPerSpan(value.intValue());
        }
        if (map.get("defaultWeight") instanceof Number value) {
            setDefaultWeight(value.doubleValue());
        }
    }

    private void invalidate(String message, int controlCount, int degree) {
        putNullOutputs(OUTPUT_PATH_ID, OUTPUT_CONTROL_PATH_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putIntOutputs(controlCount, OUTPUT_CONTROL_COUNT_ID);
        putIntOutputs(degree, OUTPUT_DEGREE_ID);
        putDoubleOutputs(0.0d, OUTPUT_LENGTH_ID);
        markInvalid(message);
    }
}

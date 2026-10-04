package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.nodes.geometry.curves.util.CurveMathUtils;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.CurveSampleFence;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
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
    id = "geometry.curves.b_spline",
    displayName = "B-Spline",
    description = "Builds a sampled clamped uniform B-spline from an ordered control point list",
    category = "geometry.curves",
    order = 7
)
public class BSplineNode extends AbstractCurveNode {

    @NodeProperty(displayName = "Default Degree", category = "B-Spline", order = 1)
    private int defaultDegree = 3;

    @NodeProperty(displayName = "Default Resolution Per Span", category = "B-Spline", order = 2)
    private int defaultResolutionPerSpan = 12;

    private static final String INPUT_CONTROL_POINTS_ID = "input_control_points";
    private static final String INPUT_DEGREE_ID = "input_degree";
    private static final String INPUT_RESOLUTION_ID = "input_resolution";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_CONTROL_PATH_ID = "output_control_path";
    private static final String OUTPUT_CONTROL_COUNT_ID = "output_control_count";
    private static final String OUTPUT_DEGREE_ID = "output_degree";
    private static final String OUTPUT_LENGTH_ID = "output_length";

    public BSplineNode() {
        super(UUID.randomUUID(), "geometry.curves.b_spline");

        addInputPort(new BasePort(INPUT_CONTROL_POINTS_ID, "Control Points", "Ordered B-spline control points", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_DEGREE_ID, "Degree", "Spline degree (1..5, must be less than control point count)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_RESOLUTION_ID, "Resolution / Span", "Samples generated per knot span", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Primary B-spline path output", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled points along the B-spline", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_PATH_ID, "Control Path", "Path through the control points", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_COUNT_ID, "Control Count", "Number of valid control points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_DEGREE_ID, "Degree", "Degree used for evaluation", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Sampled path length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when valid control points are sufficient", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> resolved = CurveInputUtils.resolveStrictPointListBounded(
            inputValues.get(INPUT_CONTROL_POINTS_ID),
            GenerationLimits.MAX_CURVE_CONTROL_POINTS
        );
        if (resolved == null) {
            invalidate("Control points are missing, mixed, non-finite, empty, or exceed the control-point budget", 0, 0);
            return;
        }
        List<Vec3d> controlPoints = toVec3d(resolved);

        if (controlPoints.size() < 2) {
            invalidate("At least 2 control points are required", controlPoints.size(), 0);
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
        long estimated = (long) spanCount * resolutionPerSpan + 1L;
        if (estimated > GenerationLimits.MAX_CURVE_SAMPLES) {
            invalidate("Sample count exceeds maximum (" + GenerationLimits.MAX_CURVE_SAMPLES + ")", controlPoints.size(), degree);
            return;
        }
        if (!CurveInputUtils.isWithinEvaluationWork(controlPoints.size(), estimated)) {
            invalidate("B-spline evaluation work exceeds limit (" + GenerationLimits.MAX_CURVE_EVALUATION_WORK + ")",
                controlPoints.size(), degree);
            return;
        }
        int totalSamples = (int) estimated;

        int knotCount = n + degree + 2;
        double[] knots = CurveMathUtils.buildClampedUniformKnots(knotCount, degree, n);

        double uStart = knots[degree];
        double uEnd = knots[n + 1];
        List<Vec3d> sampled = new ArrayList<>(totalSamples);

        for (int i = 0; i < totalSamples; i++) {
            double t = totalSamples == 1 ? 0.0d : (double) i / (double) (totalSamples - 1);
            double u = uStart + (uEnd - uStart) * t;
            sampled.add(CurveMathUtils.evaluateBSpline(controlPoints, knots, degree, u, n));
        }

        CurveSampleFence.Result fenced = CurveSampleFence.validate(sampled);
        if (fenced == null) {
            invalidate("B-spline samples are non-finite or over budget", controlPoints.size(), degree);
            return;
        }
        PathData controlPath = PathUtils.toPathData(resolved);
        if (controlPath == null) {
            invalidate("Control path could not be constructed", controlPoints.size(), degree);
            return;
        }

        outputValues.put(OUTPUT_PATH_ID, fenced.path());
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(fenced.vectors()));
        outputValues.put(OUTPUT_CONTROL_PATH_ID, controlPath);
        outputValues.put(OUTPUT_CONTROL_COUNT_ID, controlPoints.size());
        outputValues.put(OUTPUT_DEGREE_ID, degree);
        outputValues.put(OUTPUT_LENGTH_ID, fenced.length());
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

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("defaultDegree", defaultDegree);
            put("defaultResolutionPerSpan", defaultResolutionPerSpan);
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
    }

    private void invalidate(String message, int controlCount, int degree) {
        putNullOutputs(OUTPUT_PATH_ID, OUTPUT_CONTROL_PATH_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putIntOutputs(controlCount, OUTPUT_CONTROL_COUNT_ID);
        putIntOutputs(degree, OUTPUT_DEGREE_ID);
        putDoubleOutputs(Double.NaN, OUTPUT_LENGTH_ID);
        markInvalid(message);
    }

    private static List<Vec3d> toVec3d(List<Vector3d> points) {
        List<Vec3d> out = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            out.add(new Vec3d(point.x, point.y, point.z));
        }
        return out;
    }
}

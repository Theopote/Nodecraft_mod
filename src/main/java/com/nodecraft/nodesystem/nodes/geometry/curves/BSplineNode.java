package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.nodes.geometry.curves.util.CurveMathUtils;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
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
    order = 11
)
public class BSplineNode extends AbstractCurveNode {

    @NodeProperty(displayName = "Default Degree", category = "B-Spline", order = 1)
    private int defaultDegree = 3;

    @NodeProperty(displayName = "Default Resolution Per Span", category = "B-Spline", order = 2)
    private int defaultResolutionPerSpan = 12;

    private static final String INPUT_CONTROL_POINTS_ID = "input_control_points";
    private static final String INPUT_DEGREE_ID = "input_degree";
    private static final String INPUT_RESOLUTION_ID = "input_resolution";

    private static final String OUTPUT_CURVE_ID = "output_curve";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_CONTROL_POLYGON_ID = "output_control_polygon";
    private static final String OUTPUT_CONTROL_COUNT_ID = "output_control_count";
    private static final String OUTPUT_EFFECTIVE_DEGREE_ID = "output_effective_degree";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public BSplineNode() {
        super(UUID.randomUUID(), "geometry.curves.b_spline");

        addInputPort(new BasePort(INPUT_CONTROL_POINTS_ID, "Control Points", "Ordered B-spline control points", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_DEGREE_ID, "Degree", "Spline degree (1..5, clamped by control-point count)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_RESOLUTION_ID, "Resolution / Span", "Samples generated per knot span", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_CURVE_ID, "Curve", "Sampled B-spline curve representation", NodeDataType.CURVE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Sampled polyline approximation", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled points along the B-spline", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_POLYGON_ID, "Control Polygon", "Polyline through the control points", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_COUNT_ID, "Control Count", "Number of valid control points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_DEGREE_ID, "Effective Degree", "Degree used after safety clamping", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Sampled polyline length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when valid control points are sufficient", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> resolved = SpatialValueResolver.resolvePointList(inputValues.get(INPUT_CONTROL_POINTS_ID));
        List<Vec3d> controlPoints = new ArrayList<>(resolved.size());
        for (Vector3d point : resolved) {
            controlPoints.add(new Vec3d(point.x, point.y, point.z));
        }

        int requestedDegree = Math.max(1, getInputInt(INPUT_DEGREE_ID, defaultDegree));
        int requestedResolution = getInputInt(INPUT_RESOLUTION_ID, defaultResolutionPerSpan);

        if (controlPoints.size() < 2) {
            writeInvalid();
            outputValues.put(OUTPUT_CONTROL_COUNT_ID, controlPoints.size());
            outputValues.put(OUTPUT_EFFECTIVE_DEGREE_ID, 0);
            return;
        }

        int effectiveDegree = Math.min(Math.min(5, requestedDegree), controlPoints.size() - 1);
        int n = controlPoints.size() - 1;
        int knotCount = n + effectiveDegree + 2;
        double[] knots = CurveMathUtils.buildClampedUniformKnots(knotCount, effectiveDegree, n);

        int spanCount = Math.max(1, n - effectiveDegree + 1);
        int resolutionPerSpan = GenerationLimits.clampResolutionPerUnit(2, requestedResolution, spanCount);
        int totalSamples = spanCount * resolutionPerSpan + 1;
        double uStart = knots[effectiveDegree];
        double uEnd = knots[n + 1];
        List<Vec3d> sampled = new ArrayList<>(totalSamples);

        for (int i = 0; i < totalSamples; i++) {
            double t = totalSamples == 1 ? 0.0d : (double) i / (double) (totalSamples - 1);
            double u = uStart + (uEnd - uStart) * t;
            sampled.add(CurveMathUtils.evaluateBSpline(controlPoints, knots, effectiveDegree, u, n));
        }

        Curve curve = new Curve(Curve.CurveType.LINEAR, 2);
        for (Vec3d sample : sampled) {
            curve.addControlPoint(sample);
        }

        PolylineData polyline = new PolylineData(sampled);
        PolylineData controlPolygon = new PolylineData(controlPoints);
        List<Vector3d> sampledVectors = new ArrayList<>(sampled.size());
        for (Vec3d sample : sampled) {
            sampledVectors.add(new Vector3d(sample.x, sample.y, sample.z));
        }

        outputValues.put(OUTPUT_CURVE_ID, curve);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(sampledVectors));
        outputValues.put(OUTPUT_CONTROL_POLYGON_ID, controlPolygon);
        outputValues.put(OUTPUT_CONTROL_COUNT_ID, controlPoints.size());
        outputValues.put(OUTPUT_EFFECTIVE_DEGREE_ID, effectiveDegree);
        outputValues.put(OUTPUT_LENGTH_ID, polyline.getLength());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public int getDefaultDegree() {
        return defaultDegree;
    }

    public void setDefaultDegree(int defaultDegree) {
        int resolved = Math.max(1, defaultDegree);
        if (this.defaultDegree != resolved) {
            this.defaultDegree = resolved;
            markDirty();
        }
    }

    public int getDefaultResolutionPerSpan() {
        return defaultResolutionPerSpan;
    }

    public void setDefaultResolutionPerSpan(int defaultResolutionPerSpan) {
        int resolved = GenerationLimits.clampSegments(2, defaultResolutionPerSpan);
        if (this.defaultResolutionPerSpan != resolved) {
            this.defaultResolutionPerSpan = resolved;
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

    private void writeInvalid() {
        outputValues.put(OUTPUT_CURVE_ID, null);
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_CONTROL_POLYGON_ID, null);
        outputValues.put(OUTPUT_CONTROL_COUNT_ID, 0);
        outputValues.put(OUTPUT_EFFECTIVE_DEGREE_ID, 0);
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}

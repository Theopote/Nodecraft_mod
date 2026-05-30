
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.nurbs",
    displayName = "NURBS Curve",
    description = "Builds a sampled clamped uniform NURBS curve from control points and optional per-point weights",
    category = "geometry.curves",
    order = 12
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

    private static final String OUTPUT_CURVE_ID = "output_curve";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_CONTROL_POLYGON_ID = "output_control_polygon";
    private static final String OUTPUT_CONTROL_COUNT_ID = "output_control_count";
    private static final String OUTPUT_EFFECTIVE_DEGREE_ID = "output_effective_degree";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public NurbsCurveNode() {
        super(UUID.randomUUID(), "geometry.curves.nurbs");

        addInputPort(new BasePort(INPUT_CONTROL_POINTS_ID, "Control Points", "Ordered NURBS control points", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_WEIGHTS_ID, "Weights", "Optional weight list aligned with control points (missing values fallback to Default Weight)", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_DEGREE_ID, "Degree", "Curve degree (1..5, clamped by control-point count)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_RESOLUTION_ID, "Resolution / Span", "Samples generated per knot span", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_CURVE_ID, "Curve", "Sampled NURBS curve representation", NodeDataType.CURVE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Sampled polyline approximation", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled points along the NURBS curve", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_POLYGON_ID, "Control Polygon", "Polyline through the control points", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_COUNT_ID, "Control Count", "Number of valid control points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_DEGREE_ID, "Effective Degree", "Degree used after safety clamping", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Sampled polyline length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when valid control points are sufficient", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object controlPointsObj = inputValues.get(INPUT_CONTROL_POINTS_ID);
        if (!(controlPointsObj instanceof Collection<?> collection)) {
            writeEmptyOutputs();
            return;
        }

        List<Vec3d> controlPoints = new ArrayList<>();
        for (Object entry : collection) {
            Vec3d point = PlaneProjectionUtils.resolveVec3dPoint(entry);
            if (point != null) {
                controlPoints.add(point);
            }
        }

        if (controlPoints.size() < 2) {
            writeEmptyOutputs();
            outputValues.put(OUTPUT_CONTROL_COUNT_ID, controlPoints.size());
            outputValues.put(OUTPUT_EFFECTIVE_DEGREE_ID, 0);
            return;
        }

        List<Double> weights = resolveWeights(inputValues.get(INPUT_WEIGHTS_ID), controlPoints.size());
        int requestedDegree = Math.max(1, getInputInt(INPUT_DEGREE_ID, defaultDegree));
        int resolutionPerSpan = Math.max(2, getInputInt(INPUT_RESOLUTION_ID, defaultResolutionPerSpan));

        int effectiveDegree = Math.min(Math.min(5, requestedDegree), controlPoints.size() - 1);
        int n = controlPoints.size() - 1;
        int knotCount = n + effectiveDegree + 2;
        double[] knots = CurveMathUtils.buildClampedUniformKnots(knotCount, effectiveDegree, n);

        int spanCount = Math.max(1, n - effectiveDegree + 1);
        int totalSamples = spanCount * resolutionPerSpan + 1;
        double uStart = knots[effectiveDegree];
        double uEnd = knots[n + 1];
        List<Vec3d> sampled = new ArrayList<>(totalSamples);

        for (int i = 0; i < totalSamples; i++) {
            double t = totalSamples == 1 ? 0.0d : (double) i / (double) (totalSamples - 1);
            double u = uStart + (uEnd - uStart) * t;
            sampled.add(CurveMathUtils.evaluateNurbs(controlPoints, weights, knots, effectiveDegree, u, n, EPSILON));
        }

        Curve curve = new Curve(Curve.CurveType.LINEAR, 2);
        for (Vec3d sample : sampled) {
            curve.addControlPoint(sample);
        }

        PolylineData polyline = new PolylineData(sampled);
        PolylineData controlPolygon = new PolylineData(controlPoints);
        List<PointData> points = new ArrayList<>(sampled.size());
        for (Vec3d sample : sampled) {
            points.add(new PointData(sample.x, sample.y, sample.z));
        }

        outputValues.put(OUTPUT_CURVE_ID, curve);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
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
        int resolved = Math.max(2, defaultResolutionPerSpan);
        if (this.defaultResolutionPerSpan != resolved) {
            this.defaultResolutionPerSpan = resolved;
            markDirty();
        }
    }

    public double getDefaultWeight() {
        return defaultWeight;
    }

    public void setDefaultWeight(double defaultWeight) {
        double resolved = Math.max(EPSILON, defaultWeight);
        if (Double.compare(this.defaultWeight, resolved) != 0) {
            this.defaultWeight = resolved;
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

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_CURVE_ID, null);
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_CONTROL_POLYGON_ID, null);
        outputValues.put(OUTPUT_CONTROL_COUNT_ID, 0);
        outputValues.put(OUTPUT_EFFECTIVE_DEGREE_ID, 0);
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Double> resolveWeights(Object value, int count) {
        List<Double> weights = new ArrayList<>(count);
        if (value instanceof Collection<?> collection) {
            for (Object entry : collection) {
                if (weights.size() >= count) {
                    break;
                }
                if (entry instanceof Number number) {
                    weights.add(Math.max(EPSILON, number.doubleValue()));
                } else {
                    weights.add(Math.max(EPSILON, defaultWeight));
                }
            }
        }
        while (weights.size() < count) {
            weights.add(Math.max(EPSILON, defaultWeight));
        }
        return weights;
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}

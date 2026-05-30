
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
    id = "geometry.curves.interpolate_spline",
    displayName = "Interpolate Spline",
    description = "Builds a Catmull-Rom interpolation spline that passes through all resolved input points",
    category = "geometry.curves",
    order = 10
)
public class InterpolateSplineNode extends AbstractCurveNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(displayName = "Default Resolution Per Segment", category = "Spline", order = 1)
    private int defaultResolutionPerSegment = 12;

    @NodeProperty(displayName = "Default Alpha", category = "Spline", order = 2)
    private double defaultAlpha = 0.5d;

    @NodeProperty(displayName = "Closed", category = "Spline", order = 3)
    private boolean closed = false;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_RESOLUTION_ID = "input_resolution";
    private static final String INPUT_ALPHA_ID = "input_alpha";

    private static final String OUTPUT_CURVE_ID = "output_curve";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_CONTROL_POLYGON_ID = "output_control_polygon";
    private static final String OUTPUT_CONTROL_COUNT_ID = "output_control_count";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public InterpolateSplineNode() {
        super(UUID.randomUUID(), "geometry.curves.interpolate_spline");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Ordered interpolation points (curve passes through each point)", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_RESOLUTION_ID, "Resolution / Segment", "Samples generated per segment", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ALPHA_ID, "Alpha", "Parameterization alpha: 0.0 uniform, 0.5 centripetal, 1.0 chordal", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_CURVE_ID, "Curve", "Sampled curve representation", NodeDataType.CURVE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Sampled polyline approximation", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled points on the interpolation spline", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_POLYGON_ID, "Control Polygon", "Polyline through interpolation input points", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_COUNT_ID, "Control Count", "Number of valid interpolation points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Sampled spline length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when at least 2 points were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        if (!(pointsObj instanceof Collection<?> collection)) {
            writeEmptyOutputs();
            return;
        }

        List<Vec3d> points = new ArrayList<>();
        for (Object entry : collection) {
            Vec3d point = PlaneProjectionUtils.resolveVec3dPoint(entry);
            if (point != null) {
                points.add(point);
            }
        }

        int resolutionPerSegment = Math.max(2, getInputInt(INPUT_RESOLUTION_ID, defaultResolutionPerSegment));
        double alpha = clamp(getInputDouble(INPUT_ALPHA_ID, defaultAlpha), 0.0d, 1.0d);

        if (points.size() < 2) {
            writeEmptyOutputs();
            outputValues.put(OUTPUT_CONTROL_COUNT_ID, points.size());
            return;
        }

        List<Vec3d> sampled = sampleCatmullRom(points, resolutionPerSegment, alpha, closed);
        if (sampled.size() < 2) {
            writeEmptyOutputs();
            outputValues.put(OUTPUT_CONTROL_COUNT_ID, points.size());
            return;
        }

        Curve curve = new Curve(Curve.CurveType.LINEAR, 2);
        for (Vec3d sample : sampled) {
            curve.addControlPoint(sample);
        }

        PolylineData polyline = new PolylineData(sampled);
        PolylineData controlPolygon = new PolylineData(points);
        List<PointData> pointData = new ArrayList<>(sampled.size());
        for (Vec3d sample : sampled) {
            pointData.add(new PointData(sample.x, sample.y, sample.z));
        }

        outputValues.put(OUTPUT_CURVE_ID, curve);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(pointData));
        outputValues.put(OUTPUT_CONTROL_POLYGON_ID, controlPolygon);
        outputValues.put(OUTPUT_CONTROL_COUNT_ID, points.size());
        outputValues.put(OUTPUT_LENGTH_ID, polyline.getLength());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public int getDefaultResolutionPerSegment() {
        return defaultResolutionPerSegment;
    }

    public void setDefaultResolutionPerSegment(int defaultResolutionPerSegment) {
        int resolved = Math.max(2, defaultResolutionPerSegment);
        if (this.defaultResolutionPerSegment != resolved) {
            this.defaultResolutionPerSegment = resolved;
            markDirty();
        }
    }

    public double getDefaultAlpha() {
        return defaultAlpha;
    }

    public void setDefaultAlpha(double defaultAlpha) {
        double resolved = clamp(defaultAlpha, 0.0d, 1.0d);
        if (Double.compare(this.defaultAlpha, resolved) != 0) {
            this.defaultAlpha = resolved;
            markDirty();
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        if (this.closed != closed) {
            this.closed = closed;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("defaultResolutionPerSegment", defaultResolutionPerSegment);
            put("defaultAlpha", defaultAlpha);
            put("closed", closed);
        }};
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultResolutionPerSegment") instanceof Number value) {
            setDefaultResolutionPerSegment(value.intValue());
        }
        if (map.get("defaultAlpha") instanceof Number value) {
            setDefaultAlpha(value.doubleValue());
        }
        if (map.get("closed") instanceof Boolean value) {
            setClosed(value);
        }
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_CURVE_ID, null);
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_CONTROL_POLYGON_ID, null);
        outputValues.put(OUTPUT_CONTROL_COUNT_ID, 0);
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Vec3d> sampleCatmullRom(List<Vec3d> points, int samplesPerSegment, double alpha, boolean closedPath) {
        List<Vec3d> sampled = new ArrayList<>();
        int count = points.size();
        int segmentCount = closedPath ? count : count - 1;

        for (int i = 0; i < segmentCount; i++) {
            Vec3d p0 = points.get(closedPath ? floorMod(i - 1, count) : Math.max(0, i - 1));
            Vec3d p1 = points.get(i);
            Vec3d p2 = points.get((i + 1) % count);
            Vec3d p3 = points.get(closedPath ? floorMod(i + 2, count) : Math.min(count - 1, i + 2));

            double t0 = 0.0d;
            double t1 = t0 + tj(t0, p0, p1, alpha);
            double t2 = t1 + tj(t1, p1, p2, alpha);
            double t3 = t2 + tj(t2, p2, p3, alpha);

            if (Math.abs(t1 - t0) <= EPSILON || Math.abs(t2 - t1) <= EPSILON || Math.abs(t3 - t2) <= EPSILON) {
                appendLinearFallback(sampled, p1, p2, samplesPerSegment, i == 0);
                continue;
            }

            for (int j = 0; j <= samplesPerSegment; j++) {
                if (i > 0 && j == 0) {
                    continue;
                }
                double u = (double) j / (double) samplesPerSegment;
                double t = t1 + (t2 - t1) * u;
                sampled.add(interpolateCatmullRomPoint(p0, p1, p2, p3, t0, t1, t2, t3, t));
            }
        }

        return sampled;
    }

    private void appendLinearFallback(List<Vec3d> sampled, Vec3d p1, Vec3d p2, int samplesPerSegment, boolean includeStart) {
        for (int j = 0; j <= samplesPerSegment; j++) {
            if (!includeStart && j == 0) {
                continue;
            }
            double t = (double) j / (double) samplesPerSegment;
            sampled.add(lerp(p1, p2, t));
        }
    }

    private Vec3d interpolateCatmullRomPoint(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3,
                                             double t0, double t1, double t2, double t3, double t) {
        Vec3d a1 = blend(p0, p1, t0, t1, t);
        Vec3d a2 = blend(p1, p2, t1, t2, t);
        Vec3d a3 = blend(p2, p3, t2, t3, t);

        Vec3d b1 = blend(a1, a2, t0, t2, t);
        Vec3d b2 = blend(a2, a3, t1, t3, t);

        return blend(b1, b2, t1, t2, t);
    }

    private Vec3d blend(Vec3d a, Vec3d b, double ta, double tb, double t) {
        if (Math.abs(tb - ta) <= EPSILON) {
            return new Vec3d(a.x, a.y, a.z);
        }
        double w1 = (tb - t) / (tb - ta);
        double w2 = (t - ta) / (tb - ta);
        return new Vec3d(
            a.x * w1 + b.x * w2,
            a.y * w1 + b.y * w2,
            a.z * w1 + b.z * w2
        );
    }

    private double tj(double ti, Vec3d pi, Vec3d pj, double alpha) {
        double dx = pj.x - pi.x;
        double dy = pj.y - pi.y;
        double dz = pj.z - pi.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return ti + Math.pow(distance, alpha);
    }

    private Vec3d lerp(Vec3d start, Vec3d end, double t) {
        return new Vec3d(
            start.x + (end.x - start.x) * t,
            start.y + (end.y - start.y) * t,
            start.z + (end.z - start.z) * t
        );
    }

    private int floorMod(int value, int modulus) {
        int result = value % modulus;
        return result < 0 ? result + modulus : result;
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

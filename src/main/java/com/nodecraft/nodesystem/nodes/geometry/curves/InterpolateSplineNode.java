package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.CurveSampleFence;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.CurveInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.interpolate_spline",
    displayName = "Interpolate Spline",
    description = "Builds a Catmull-Rom interpolation spline that passes through all resolved input points",
    category = "geometry.curves",
    order = 6
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

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_CONTROL_PATH_ID = "output_control_path";
    private static final String OUTPUT_CONTROL_COUNT_ID = "output_control_count";
    private static final String OUTPUT_LENGTH_ID = "output_length";

    public InterpolateSplineNode() {
        super(UUID.randomUUID(), "geometry.curves.interpolate_spline");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Ordered interpolation points (curve passes through each point)", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_RESOLUTION_ID, "Resolution / Segment", "Samples generated per segment", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ALPHA_ID, "Alpha", "Parameterization alpha: 0.0 uniform, 0.5 centripetal, 1.0 chordal", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Primary interpolation path output", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled points on the interpolation spline", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_PATH_ID, "Control Path", "Path through interpolation input points", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_COUNT_ID, "Control Count", "Number of valid interpolation points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Sampled spline length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when at least 2 points were resolved", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> resolved = CurveInputUtils.resolveStrictPointListBounded(
            inputValues.get(INPUT_POINTS_ID),
            GenerationLimits.MAX_CURVE_CONTROL_POINTS
        );
        if (resolved == null) {
            invalidate("Point list is missing, mixed, non-finite, empty, or exceeds the control-point budget", 0);
            return;
        }
        List<Vec3d> points = toVec3d(resolved);

        Double alpha = resolveFiniteDouble(INPUT_ALPHA_ID, defaultAlpha);
        if (alpha == null || alpha < 0.0d || alpha > 1.0d) {
            invalidate("Alpha must be finite in [0, 1]", points.size());
            return;
        }

        if (points.size() < 2) {
            invalidate("At least 2 points are required", points.size());
            return;
        }

        Integer resolutionPerSegment = resolveBoundedInteger(
            INPUT_RESOLUTION_ID,
            defaultResolutionPerSegment,
            2,
            GenerationLimits.MAX_CURVE_SAMPLES
        );
        if (resolutionPerSegment == null) {
            invalidate("Resolution / Segment must be an integer from 2 to " + GenerationLimits.MAX_CURVE_SAMPLES, points.size());
            return;
        }

        int segmentCount = closed ? points.size() : points.size() - 1;
        long estimatedSamples = (long) segmentCount * resolutionPerSegment + 1L;
        if (estimatedSamples > GenerationLimits.MAX_CURVE_SAMPLES) {
            invalidate("Sample count exceeds maximum (" + GenerationLimits.MAX_CURVE_SAMPLES + ")", points.size());
            return;
        }
        if (!CurveInputUtils.isWithinEvaluationWork(points.size(), estimatedSamples)) {
            invalidate("Spline evaluation work exceeds limit (" + GenerationLimits.MAX_CURVE_EVALUATION_WORK + ")",
                points.size());
            return;
        }

        List<Vec3d> sampled = sampleCatmullRom(points, resolutionPerSegment, alpha, closed);
        CurveSampleFence.Result fenced = CurveSampleFence.validate(sampled);
        if (fenced == null) {
            invalidate("Interpolation samples are non-finite or the parameter sequence overflowed", points.size());
            return;
        }
        PathData controlPath = PathUtils.toPathData(resolved);
        if (controlPath == null) {
            invalidate("Control path could not be constructed", points.size());
            return;
        }

        outputValues.put(OUTPUT_PATH_ID, fenced.path());
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(fenced.vectors()));
        outputValues.put(OUTPUT_CONTROL_PATH_ID, controlPath);
        outputValues.put(OUTPUT_CONTROL_COUNT_ID, points.size());
        outputValues.put(OUTPUT_LENGTH_ID, fenced.length());
        markSuccess();
    }

    public int getDefaultResolutionPerSegment() {
        return defaultResolutionPerSegment;
    }

    public void setDefaultResolutionPerSegment(int defaultResolutionPerSegment) {
        if (defaultResolutionPerSegment >= 2
                && defaultResolutionPerSegment <= GenerationLimits.MAX_CURVE_SAMPLES
                && this.defaultResolutionPerSegment != defaultResolutionPerSegment) {
            this.defaultResolutionPerSegment = defaultResolutionPerSegment;
            markDirty();
        }
    }

    public double getDefaultAlpha() {
        return defaultAlpha;
    }

    public void setDefaultAlpha(double defaultAlpha) {
        if (Double.isFinite(defaultAlpha)
                && defaultAlpha >= 0.0d
                && defaultAlpha <= 1.0d
                && Double.compare(this.defaultAlpha, defaultAlpha) != 0) {
            this.defaultAlpha = defaultAlpha;
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

    private void invalidate(String message, int controlCount) {
        putNullOutputs(OUTPUT_PATH_ID, OUTPUT_CONTROL_PATH_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putIntOutputs(controlCount, OUTPUT_CONTROL_COUNT_ID);
        putDoubleOutputs(Double.NaN, OUTPUT_LENGTH_ID);
        markInvalid(message);
    }

    private @Nullable List<Vec3d> sampleCatmullRom(List<Vec3d> points, int samplesPerSegment, double alpha, boolean closedPath) {
        List<Vec3d> sampled = new ArrayList<>();
        int count = points.size();
        int segmentCount = closedPath ? count : count - 1;

        for (int i = 0; i < segmentCount; i++) {
            Vec3d p0 = points.get(closedPath ? floorMod(i - 1, count) : Math.max(0, i - 1));
            Vec3d p1 = points.get(i);
            Vec3d p2 = points.get((i + 1) % count);
            Vec3d p3 = points.get(closedPath ? floorMod(i + 2, count) : Math.min(count - 1, i + 2));

            Double t1 = nextKnot(0.0d, p0, p1, alpha);
            Double t2 = t1 == null ? null : nextKnot(t1, p1, p2, alpha);
            Double t3 = t2 == null ? null : nextKnot(t2, p2, p3, alpha);
            if (t1 == null || t2 == null || t3 == null) {
                return null;
            }
            double t0 = 0.0d;

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
                if (!Double.isFinite(t)) {
                    return null;
                }
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

    private static @Nullable Double nextKnot(double ti, Vec3d pi, Vec3d pj, double alpha) {
        double distance = VectorUtils.safeDistance(toVector(pi), toVector(pj));
        if (!Double.isFinite(distance)) {
            return null;
        }
        double increment = Math.pow(distance, alpha);
        if (!Double.isFinite(increment)) {
            return null;
        }
        double t = ti + increment;
        return Double.isFinite(t) ? t : null;
    }

    private static Vector3d toVector(Vec3d point) {
        return new Vector3d(point.x, point.y, point.z);
    }

    private static List<Vec3d> toVec3d(List<Vector3d> points) {
        List<Vec3d> out = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            out.add(new Vec3d(point.x, point.y, point.z));
        }
        return out;
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
}

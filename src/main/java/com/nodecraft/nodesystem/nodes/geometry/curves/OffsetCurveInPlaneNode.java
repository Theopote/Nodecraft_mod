package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.offset_curve_plane",
    displayName = "Offset Curve In Plane",
    description = "Offsets a curve, polyline, or line in a work plane by signed distance.",
    category = "geometry.curves",
    order = 11
)
public class OffsetCurveInPlaneNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Miter Limit", category = "Offset", order = 1,
        description = "Maximum miter extension factor relative to |offset| before bevel fallback")
    private double miterLimit = 4.0d;

    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_OFFSET_ID = "input_offset";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_COUNT_ID = "input_count";

    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public OffsetCurveInPlaneNode() {
        super(UUID.randomUUID(), "geometry.curves.offset_curve_plane");

        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve", "Curve to offset", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline", "Fallback polyline to offset", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line", "Fallback line to offset", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Work plane containing the curve", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Signed offset distance in the plane", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing", "Optional rebuild spacing before offset", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Optional rebuild sample count; overrides spacing", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Offset sampled polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Offset points as Vector3d list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Source path length used for offset", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the offset succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!(inputValues.get(INPUT_PLANE_ID) instanceof PlaneData plane)
            || !(inputValues.get(INPUT_OFFSET_ID) instanceof Number offsetNumber)) {
            writeInvalid();
            return;
        }

        double offset = offsetNumber.doubleValue();
        if (!Double.isFinite(offset) || Math.abs(offset) < EPS) {
            writeInvalid();
            return;
        }

        List<Vector3d> verts = resolveVertices();
        SampledPath sampled = samplePath(verts);
        if (sampled == null || sampled.points().size() < 2) {
            writeInvalid();
            return;
        }

        List<Vector3d> unique = sampled.closed()
            ? sampled.points().subList(0, sampled.points().size() - 1)
            : sampled.points();
        if (unique.size() < 2) {
            writeInvalid();
            return;
        }

        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        List<Vector2d> pts2d = new ArrayList<>(unique.size());
        for (Vector3d point : unique) {
            pts2d.add(axes.to2d(plane.projectPoint(point)));
        }

        List<Vector2d> offset2d = offsetPolyline2d(pts2d, sampled.closed(), offset, miterLimit);
        if (offset2d == null || offset2d.size() < 2) {
            writeInvalid();
            return;
        }

        List<Vector3d> offsetPoints = new ArrayList<>(offset2d.size());
        for (Vector2d point : offset2d) {
            offsetPoints.add(axes.from2d(point));
        }

        List<Vec3d> polyPoints = PathUtils.toVec3dList(offsetPoints, sampled.closed());
        PolylineData polyline = PathUtils.createPolylineOrNull(polyPoints);
        if (polyline == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(offsetPoints));
        outputValues.put(OUTPUT_LENGTH_ID, sampled.length());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private @Nullable SampledPath samplePath(@Nullable List<Vector3d> verts) {
        if (verts == null || verts.size() < 2) {
            return null;
        }
        boolean closed = PathUtils.isClosed(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        if (unique.size() < 2) {
            return null;
        }

        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null) {
            return null;
        }
        double total = cumulative[cumulative.length - 1];
        if (total <= EPS) {
            return null;
        }

        int count = inputValues.get(INPUT_COUNT_ID) instanceof Number number ? number.intValue() : -1;
        double spacing = inputValues.get(INPUT_SPACING_ID) instanceof Number number ? number.doubleValue() : 0.0d;
        if (count < 2 && spacing <= EPS) {
            return new SampledPath(List.copyOf(verts), closed, total);
        }

        List<Double> distances = buildSampleDistances(total, count, spacing);
        if (distances.isEmpty()) {
            return null;
        }

        List<Vector3d> samples = new ArrayList<>(distances.size() + (closed ? 1 : 0));
        for (double distance : distances) {
            samples.add(PathUtils.sampleAtDistance(unique, closed, cumulative, distance));
        }
        if (closed && !samples.isEmpty()) {
            samples.add(new Vector3d(samples.getFirst()));
        }
        return new SampledPath(samples, closed, total);
    }

    private List<Double> buildSampleDistances(double total, int count, double spacing) {
        List<Double> distances = new ArrayList<>();
        if (count >= 2) {
            count = GenerationLimits.clampPositiveCount(count);
            for (int i = 0; i < count; i++) {
                distances.add(total * i / (double) (count - 1));
            }
        } else if (spacing > EPS) {
            for (double distance = 0.0d; distance <= total + EPS; distance += spacing) {
                distances.add(Math.min(distance, total));
            }
            if (!distances.isEmpty() && distances.getLast() < total - EPS) {
                distances.add(total);
            }
        }
        return distances;
    }

    private static @Nullable List<Vector2d> offsetPolyline2d(List<Vector2d> pts,
                                                             boolean closed,
                                                             double offset,
                                                             double miterLimit) {
        int n = pts.size();
        if (n < 2) {
            return null;
        }
        int segCount = closed ? n : n - 1;
        Vector2d[] left = new Vector2d[segCount];
        for (int i = 0; i < segCount; i++) {
            Vector2d a = pts.get(i);
            Vector2d b = pts.get((i + 1) % n);
            Vector2d d = new Vector2d(b).sub(a);
            double len = d.length();
            if (len < EPS) {
                return null;
            }
            d.mul(1.0d / len);
            left[i] = new Vector2d(-d.y, d.x).mul(offset);
        }

        List<Vector2d> out = new ArrayList<>(n);
        if (!closed) {
            out.add(new Vector2d(pts.getFirst()).add(left[0]));
            for (int i = 1; i < n - 1; i++) {
                Vector2d corner = MiterJoinCalculator.intersectOrBevel(
                    pts.get(i - 1), pts.get(i), left[i - 1],
                    pts.get(i), pts.get(i + 1), left[i],
                    pts.get(i), miterLimit, offset);
                if (corner == null) {
                    return null;
                }
                out.add(corner);
            }
            out.add(new Vector2d(pts.get(n - 1)).add(left[n - 2]));
            return out;
        }

        for (int i = 0; i < n; i++) {
            int prev = (i - 1 + n) % n;
            int next = (i + 1) % n;
            Vector2d corner = MiterJoinCalculator.intersectOrBevel(
                pts.get(prev), pts.get(i), left[prev],
                pts.get(i), pts.get(next), left[i],
                pts.get(i), miterLimit, offset);
            if (corner == null) {
                return null;
            }
            out.add(corner);
        }
        return out;
    }

    private @Nullable List<Vector3d> resolveVertices() {
        return PathUtils.resolveVertices(
            inputValues.get(INPUT_CURVE_ID),
            inputValues.get(INPUT_POLYLINE_ID),
            inputValues.get(INPUT_LINE_ID)
        );
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    public double getMiterLimit() {
        return miterLimit;
    }

    public void setMiterLimit(double miterLimit) {
        double resolved = Math.max(0.0d, miterLimit);
        if (Double.compare(this.miterLimit, resolved) != 0) {
            this.miterLimit = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of("miterLimit", miterLimit);
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map<?, ?> map && map.get("miterLimit") instanceof Number number) {
            setMiterLimit(number.doubleValue());
        }
    }

    private record SampledPath(List<Vector3d> points, boolean closed, double length) {
    }
}

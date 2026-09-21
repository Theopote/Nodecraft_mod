package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.InPlanePathOffset;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.offset_curve_plane",
    displayName = "Offset Path In Plane",
    description = "Offsets a path (line, polyline, or curve) in a work plane by signed distance, with optional resampling.",
    category = "geometry.curves",
    order = 11
)
public class OffsetCurveInPlaneNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Miter Limit", category = "Offset", order = 1,
        description = "Maximum miter extension factor relative to |offset| before bevel fallback")
    private double miterLimit = 4.0d;

    private static final String INPUT_PATH_ID = "input_path";
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

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to offset (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Work plane containing the curve", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Signed offset distance in the plane", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing", "Optional rebuild spacing before offset", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Optional rebuild sample count; overrides spacing", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Offset sampled polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Offset points as point list", NodeDataType.POINT_LIST, this));
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

        InPlanePathOffset.Result result = InPlanePathOffset.offset(sampled.points(), plane, offset, miterLimit);
        if (result == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_POLYLINE_ID, result.polyline());
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(result.points()));
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

    private @Nullable List<Vector3d> resolveVertices() {
        return resolvePathVertices(INPUT_PATH_ID);
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

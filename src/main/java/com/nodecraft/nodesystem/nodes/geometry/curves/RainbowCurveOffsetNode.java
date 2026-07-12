package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.rainbow_curve_offset",
    displayName = "Rainbow Curve Offset",
    description = "Generates multiple parallel offset polylines around a space curve using path frames.",
    category = "geometry.curves",
    order = 12
)
public class RainbowCurveOffsetNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SEPARATION_ID = "input_separation";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_SAMPLE_COUNT_ID = "input_sample_count";
    private static final String INPUT_UP_VECTOR_ID = "input_up_vector";

    private static final String OUTPUT_POLYLINES_ID = "output_polylines";
    private static final String OUTPUT_CENTER_POLYLINE_ID = "output_center_polyline";
    private static final String OUTPUT_OFFSETS_ID = "output_offsets";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RainbowCurveOffsetNode() {
        super(UUID.randomUUID(), "geometry.curves.rainbow_curve_offset");

        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve", "Space curve to offset", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline", "Fallback polyline to offset", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line", "Fallback line to offset", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of offset curves to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEPARATION_ID, "Separation", "Distance between adjacent offset curves", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing", "Optional path sample spacing", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SAMPLE_COUNT_ID, "Sample Count", "Optional path sample count; overrides spacing", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_UP_VECTOR_ID, "Up Vector", "Reference up vector for stable frame normals", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POLYLINES_ID, "Polylines", "Generated offset polylines", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_POLYLINE_ID, "Center Polyline", "Sampled source centerline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_OFFSETS_ID, "Offsets", "Offset distances used for each output curve", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of generated offset curves", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Source path length used for sampling", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when offset curves were generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolveVertices();
        SampledPath sampled = samplePath(verts);
        if (sampled == null || sampled.points().size() < 2) {
            writeInvalid();
            return;
        }

        int count = inputValues.get(INPUT_COUNT_ID) instanceof Number number ? number.intValue() : 5;
        count = GenerationLimits.clampPositiveCount(count);
        double separation = inputValues.get(INPUT_SEPARATION_ID) instanceof Number number ? number.doubleValue() : 1.0d;
        if (!Double.isFinite(separation) || Math.abs(separation) <= EPS) {
            writeInvalid();
            return;
        }

        List<Vector3d> centerPoints = sampled.closed()
            ? sampled.points().subList(0, sampled.points().size() - 1)
            : sampled.points();
        Vector3d up = resolveUpVector(inputValues.get(INPUT_UP_VECTOR_ID));
        List<Vector3d> normals = buildNormals(centerPoints, sampled.closed(), up);
        if (normals.size() != centerPoints.size()) {
            writeInvalid();
            return;
        }

        List<PolylineData> polylines = new ArrayList<>(count);
        List<Double> offsets = new ArrayList<>(count);
        double centerIndex = (count - 1) * 0.5d;
        for (int rail = 0; rail < count; rail++) {
            double offset = (rail - centerIndex) * separation;
            offsets.add(offset);

            List<Vector3d> points = new ArrayList<>(centerPoints.size());
            for (int i = 0; i < centerPoints.size(); i++) {
                points.add(new Vector3d(centerPoints.get(i)).fma(offset, normals.get(i)));
            }

            PolylineData polyline = PathUtils.createPolylineOrNull(PathUtils.toVec3dList(points, sampled.closed()));
            if (polyline != null) {
                polylines.add(polyline);
            }
        }

        PolylineData centerPolyline = PathUtils.createPolylineOrNull(PathUtils.toVec3dList(centerPoints, sampled.closed()));
        if (polylines.isEmpty() || centerPolyline == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_POLYLINES_ID, List.copyOf(polylines));
        outputValues.put(OUTPUT_CENTER_POLYLINE_ID, centerPolyline);
        outputValues.put(OUTPUT_OFFSETS_ID, List.copyOf(offsets));
        outputValues.put(OUTPUT_COUNT_ID, polylines.size());
        outputValues.put(OUTPUT_LENGTH_ID, sampled.length());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private List<Vector3d> buildNormals(List<Vector3d> points, boolean closed, Vector3d up) {
        List<Vector3d> normals = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            Vector3d prev = points.get(i == 0 ? (closed ? points.size() - 1 : 0) : i - 1);
            Vector3d next = points.get(i == points.size() - 1 ? (closed ? 0 : points.size() - 1) : i + 1);
            Vector3d tangent = new Vector3d(next).sub(prev);
            if (tangent.lengthSquared() <= EPS) {
                return List.of();
            }
            tangent.normalize();

            Vector3d binormal = new Vector3d(tangent).cross(up);
            if (binormal.lengthSquared() <= EPS) {
                Vector3d fallbackUp = Math.abs(tangent.y) < 0.9d
                    ? new Vector3d(0.0d, 1.0d, 0.0d)
                    : new Vector3d(1.0d, 0.0d, 0.0d);
                binormal = new Vector3d(tangent).cross(fallbackUp);
            }
            if (binormal.lengthSquared() <= EPS) {
                return List.of();
            }
            binormal.normalize();

            Vector3d normal = new Vector3d(binormal).cross(tangent);
            if (normal.lengthSquared() <= EPS) {
                return List.of();
            }
            normals.add(normal.normalize());
        }
        return normals;
    }

    private @Nullable SampledPath samplePath(@Nullable List<Vector3d> verts) {
        if (verts == null || verts.size() < 2) {
            return null;
        }
        boolean closed = PathUtils.isClosed(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null) {
            return null;
        }
        double total = cumulative[cumulative.length - 1];
        if (total <= EPS) {
            return null;
        }

        int sampleCount = inputValues.get(INPUT_SAMPLE_COUNT_ID) instanceof Number number ? number.intValue() : -1;
        double spacing = inputValues.get(INPUT_SPACING_ID) instanceof Number number ? number.doubleValue() : 0.0d;
        if (sampleCount < 2 && spacing <= EPS) {
            return new SampledPath(List.copyOf(verts), closed, total);
        }

        List<Double> distances = new ArrayList<>();
        if (sampleCount >= 2) {
            for (int i = 0; i < sampleCount; i++) {
                distances.add(total * i / (double) (sampleCount - 1));
            }
        } else {
            for (double distance = 0.0d; distance <= total + EPS; distance += spacing) {
                distances.add(Math.min(distance, total));
            }
            if (!distances.isEmpty() && distances.getLast() < total - EPS) {
                distances.add(total);
            }
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

    private Vector3d resolveUpVector(Object value) {
        if (value instanceof Vector3d vector && vector.lengthSquared() > EPS) {
            return new Vector3d(vector).normalize();
        }
        return new Vector3d(0.0d, 1.0d, 0.0d);
    }

    private @Nullable List<Vector3d> resolveVertices() {
        return PathUtils.resolveVertices(
            inputValues.get(INPUT_CURVE_ID),
            inputValues.get(INPUT_POLYLINE_ID),
            inputValues.get(INPUT_LINE_ID)
        );
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POLYLINES_ID, List.of());
        outputValues.put(OUTPUT_CENTER_POLYLINE_ID, null);
        outputValues.put(OUTPUT_OFFSETS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private record SampledPath(List<Vector3d> points, boolean closed, double length) {
    }
}

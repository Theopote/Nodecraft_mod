package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.CurveInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.rainbow_curve_offset",
    displayName = "Rainbow Curve Offset",
    description = "Generates multiple parallel offset paths around a space curve using path frames.",
    category = "geometry.curves",
    order = 24
)
public class RainbowCurveOffsetNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;
    private static final Vector3d DEFAULT_UP = new Vector3d(0.0d, 1.0d, 0.0d);

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SEPARATION_ID = "input_separation";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_SAMPLE_COUNT_ID = "input_sample_count";
    private static final String INPUT_UP_VECTOR_ID = "input_up_vector";

    private static final String OUTPUT_PATHS_ID = "output_paths";
    private static final String OUTPUT_CENTER_PATH_ID = "output_center_path";
    private static final String OUTPUT_OFFSETS_ID = "output_offsets";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_LENGTH_ID = "output_length";

    public RainbowCurveOffsetNode() {
        super(UUID.randomUUID(), "geometry.curves.rainbow_curve_offset");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to offset (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of offset paths to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEPARATION_ID, "Separation", "Distance between adjacent offset paths", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing", "Optional path sample spacing", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SAMPLE_COUNT_ID, "Sample Count", "Optional path sample count; overrides spacing", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_UP_VECTOR_ID, "Up Vector", "Reference up vector for stable frame normals", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_PATHS_ID, "Paths", "Generated offset paths", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_PATH_ID, "Center Path", "Sampled source centerline", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_OFFSETS_ID, "Offsets", "Offset distances used for each output path", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of generated offset paths", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Source path length used for sampling", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when offset paths were generated", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        SampledPath sampled = samplePath(verts);
        if (sampled == null || sampled.points().size() < 2) {
            invalidate("Path is missing or invalid");
            return;
        }

        Integer count = resolveBoundedInteger(INPUT_COUNT_ID, 5, 1, GenerationLimits.MAX_CURVE_OUTPUT_PATHS);
        if (count == null) {
            invalidate("Count must be an integer from 1 to " + GenerationLimits.MAX_CURVE_OUTPUT_PATHS);
            return;
        }

        Double separation = resolveFiniteDouble(INPUT_SEPARATION_ID, 1.0d);
        if (separation == null || Math.abs(separation) <= EPS) {
            invalidate("Separation is connected but invalid (must be finite and non-zero)");
            return;
        }

        int samplesPerPath = sampled.points().size();
        if (!GenerationLimits.isWithinCurveWorkload(count, samplesPerPath)) {
            invalidate("Rainbow workload exceeds maximum (paths × samples <= "
                + GenerationLimits.MAX_CURVE_TOTAL_SAMPLES + ")");
            return;
        }

        Vector3d up = OptionalPortDrive.isConnected(this, INPUT_UP_VECTOR_ID)
            ? CurveInputUtils.resolveOptionalNonZeroVector(this, INPUT_UP_VECTOR_ID, null)
            : new Vector3d(DEFAULT_UP);
        if (up == null) {
            invalidate("Up Vector is connected but invalid or zero");
            return;
        }

        List<Vector3d> centerPoints = sampled.closed()
            ? sampled.points().subList(0, sampled.points().size() - 1)
            : sampled.points();
        List<Vector3d> normals = buildNormals(centerPoints, sampled.closed(), up);
        if (normals.size() != centerPoints.size()) {
            invalidate("Unable to build stable frame normals along the path");
            return;
        }

        List<PathData> paths = new ArrayList<>(count);
        List<Double> offsets = new ArrayList<>(count);
        double centerIndex = (count - 1) * 0.5d;
        for (int rail = 0; rail < count; rail++) {
            double offset = (rail - centerIndex) * separation;
            offsets.add(offset);

            List<Vector3d> points = new ArrayList<>(centerPoints.size());
            for (int i = 0; i < centerPoints.size(); i++) {
                points.add(new Vector3d(centerPoints.get(i)).fma(offset, normals.get(i)));
            }
            PathData path = PathUtils.toPathData(sampled.closed() ? appendClosingVertex(points) : points);
            if (path != null) {
                paths.add(path);
            }
        }

        PathData centerPath = PathUtils.toPathData(sampled.points());
        if (paths.isEmpty() || centerPath == null) {
            invalidate("Unable to generate offset paths");
            return;
        }

        outputValues.put(OUTPUT_PATHS_ID, List.copyOf(paths));
        outputValues.put(OUTPUT_CENTER_PATH_ID, centerPath);
        outputValues.put(OUTPUT_OFFSETS_ID, List.copyOf(offsets));
        outputValues.put(OUTPUT_COUNT_ID, paths.size());
        outputValues.put(OUTPUT_LENGTH_ID, sampled.length());
        markSuccess();
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_CENTER_PATH_ID, OUTPUT_LENGTH_ID);
        putEmptyListOutputs(OUTPUT_PATHS_ID, OUTPUT_OFFSETS_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
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

        Integer sampleCount = OptionalPortDrive.isConnected(this, INPUT_SAMPLE_COUNT_ID)
            ? resolveBoundedInteger(INPUT_SAMPLE_COUNT_ID, 0, 2, GenerationLimits.MAX_CURVE_SAMPLES)
            : null;
        Double spacing = OptionalPortDrive.isConnected(this, INPUT_SPACING_ID)
            ? resolvePositiveDouble(INPUT_SPACING_ID, 0.0d)
            : null;

        if (OptionalPortDrive.isConnected(this, INPUT_SAMPLE_COUNT_ID) && sampleCount == null) {
            return null;
        }
        if (OptionalPortDrive.isConnected(this, INPUT_SPACING_ID) && spacing == null) {
            return null;
        }

        if (sampleCount == null && spacing == null) {
            return new SampledPath(List.copyOf(verts), closed, total);
        }

        List<Double> distances = new ArrayList<>();
        if (sampleCount != null) {
            if (sampleCount > GenerationLimits.MAX_CURVE_SAMPLES) {
                return null;
            }
            for (int i = 0; i < sampleCount; i++) {
                distances.add(total * i / (double) (sampleCount - 1));
            }
        } else if (spacing != null) {
            for (double distance = 0.0d; distance <= total + EPS; distance += spacing) {
                distances.add(Math.min(distance, total));
            }
            if (!distances.isEmpty() && distances.getLast() < total - EPS) {
                distances.add(total);
            }
            if (distances.size() > GenerationLimits.MAX_CURVE_SAMPLES) {
                return null;
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

    private static List<Vector3d> appendClosingVertex(List<Vector3d> row) {
        List<Vector3d> copy = new ArrayList<>(row.size() + 1);
        copy.addAll(row);
        if (!copy.isEmpty()) {
            copy.add(new Vector3d(copy.getFirst()));
        }
        return copy;
    }

    private record SampledPath(List<Vector3d> points, boolean closed, double length) {
    }
}

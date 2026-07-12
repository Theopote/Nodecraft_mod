package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rebuilds a path into near-uniform arc-length samples from Curve/Polyline/Line inputs.
 */
@NodeInfo(
    id = "geometry.curves.rebuild_curve_length",
    displayName = "Curve Rebuild By Length",
    description = "Rebuilds a curve/path to uniform arc-length samples using spacing, or using a total point count (count wins when both are provided)",
    category = "geometry.curves",
    order = 13
)
public class CurveRebuildByLengthNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_COUNT_ID = "input_count";

    private static final String OUTPUT_CURVE_ID = "output_curve";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public CurveRebuildByLengthNode() {
        super(UUID.randomUUID(), "geometry.curves.rebuild_curve_length");

        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve",
            "Curve to rebuild by arc length", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline",
            "Fallback polyline to rebuild when no curve is connected", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line",
            "Fallback line segment when no curve/polyline is connected", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing",
            "Target distance between samples along the path (> 0 when used)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count",
            "Target number of samples along the path (>= 2). When set, overrides spacing", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_CURVE_ID, "Curve",
            "Rebuilt sampled curve as a linear control path", NodeDataType.CURVE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline",
            "Rebuilt polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points",
            "Rebuilt points as a list of Vector3d positions", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length",
            "Total input path length used for rebuilding", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when rebuilding succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolveVertices();
        if (verts == null || verts.size() < 2) {
            writeInvalid();
            return;
        }

        boolean closed = PathUtils.isClosed(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        if (unique.size() < 2) {
            writeInvalid();
            return;
        }

        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null) {
            writeInvalid();
            return;
        }
        double total = cumulative[cumulative.length - 1];
        if (total <= EPS) {
            writeInvalid();
            return;
        }

        Object spacingObj = inputValues.get(INPUT_SPACING_ID);
        Object countObj = inputValues.get(INPUT_COUNT_ID);
        int count = countObj instanceof Number n ? n.intValue() : -1;
        if (count >= 2) {
            count = GenerationLimits.clampPositiveCount(count);
        }
        double spacing = spacingObj instanceof Number s ? s.doubleValue() : 0.0d;

        List<Double> sampleDistances = new ArrayList<>();
        if (count >= 2) {
            for (int i = 0; i < count; i++) {
                sampleDistances.add(total * i / (double) (count - 1));
            }
        } else if (spacing > EPS) {
            for (double d = 0.0d; d <= total + EPS; d += spacing) {
                sampleDistances.add(Math.min(d, total));
            }
            if (sampleDistances.get(sampleDistances.size() - 1) < total - EPS) {
                sampleDistances.add(total);
            }
        } else {
            writeInvalid();
            return;
        }

        List<Vector3d> samples = new ArrayList<>(sampleDistances.size());
        for (double d : sampleDistances) {
            samples.add(PathUtils.sampleAtDistance(unique, closed, cumulative, d));
        }

        if (closed && samples.size() >= 2) {
            while (samples.size() >= 2 && samples.get(0).distance(samples.get(samples.size() - 1)) < 1.0e-6d) {
                samples.remove(samples.size() - 1);
            }
        }

        List<Vec3d> rebuilt = PathUtils.toVec3dList(samples, closed);
        PolylineData polyline = PathUtils.createPolylineOrNull(rebuilt);
        if (polyline == null) {
            writeInvalid();
            return;
        }

        Curve curve = new Curve(Curve.CurveType.LINEAR, 2);
        for (Vec3d point : rebuilt) {
            curve.addControlPoint(point);
        }
        outputValues.put(OUTPUT_CURVE_ID, curve);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(samples));
        outputValues.put(OUTPUT_LENGTH_ID, total);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_CURVE_ID, null);
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Vector3d> resolveVertices() {
        return PathUtils.resolveVertices(
            inputValues.get(INPUT_CURVE_ID),
            inputValues.get(INPUT_POLYLINE_ID),
            inputValues.get(INPUT_LINE_ID)
        );
    }
}

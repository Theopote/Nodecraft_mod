package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
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

/**
 * Resamples a polyline at uniform arc-length spacing or into a fixed number of evenly spaced samples.
 */
@NodeInfo(
    id = "geometry.curves.resample_polyline_length",
    displayName = "Resample Polyline By Length",
    description = "Resamples a polyline along its arc length using spacing, or using a total point count (count wins when both are provided)",
    category = "geometry.curves",
    order = 12
)
public class ResamplePolylineByLengthNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_COUNT_ID = "input_count";

    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ResamplePolylineByLengthNode() {
        super(UUID.randomUUID(), "geometry.curves.resample_polyline_length");

        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline",
            "Polyline to resample (open or closed)",
            NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line",
            "Optional 2-point line when no polyline is connected",
            NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing",
            "Target distance between samples along the path (must be > 0 when used)",
            NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count",
            "Target number of samples along the path (>= 2). When set, overrides spacing",
            NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline",
            "Resampled polyline (closed when the input polyline is closed)",
            NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points",
            "Resampled points as a list of Vector3d positions",
            NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length",
            "Total path length used for sampling",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when resampling succeeded",
            NodeDataType.BOOLEAN, this));
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

        List<Double> sampleDistances;
        if (count >= 2) {
            sampleDistances = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                sampleDistances.add(total * i / (double) (count - 1));
            }
        } else if (spacing > EPS) {
            sampleDistances = new ArrayList<>();
            int maxInstances = GenerationLimits.clampSpacingInstanceCount(total, spacing);
            int emitted = 0;
            for (double d = 0.0d; d <= total + EPS && emitted < maxInstances; d += spacing) {
                sampleDistances.add(Math.min(d, total));
                emitted++;
            }
            if (emitted < maxInstances
                && (sampleDistances.isEmpty() || sampleDistances.getLast() < total - EPS)) {
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
            while (samples.size() >= 2
                && samples.getFirst().distance(samples.getLast()) < 1.0e-6d) {
                samples.removeLast();
            }
        }

        List<Vec3d> polyPts = PathUtils.toVec3dList(samples, closed);
        PolylineData polyline = PathUtils.createPolylineOrNull(polyPts);
        if (polyline == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, samples);
        outputValues.put(OUTPUT_LENGTH_ID, total);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Vector3d> resolveVertices() {
        return PathUtils.resolveVertices(
            null,
            inputValues.get(INPUT_POLYLINE_ID),
            inputValues.get(INPUT_LINE_ID)
        );
    }
}

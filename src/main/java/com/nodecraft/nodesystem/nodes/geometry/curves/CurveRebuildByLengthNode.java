package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.PathSamplingUtils;
import com.nodecraft.nodesystem.util.SamplingMode;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.rebuild_curve_length",
    displayName = "Curve Rebuild By Length",
    description = "Rebuilds a curve/path using explicit sampling mode (Count or Spacing).",
    category = "geometry.curves",
    order = 13
)
public class CurveRebuildByLengthNode extends AbstractCurveNode {

    @NodeProperty(displayName = "Sampling Mode", category = "Rebuild", order = 1)
    private SamplingMode samplingMode = SamplingMode.SPACING;

    @NodeProperty(displayName = "Default Spacing", category = "Rebuild", order = 2,
        description = "Target distance between samples when Mode=Spacing")
    private double defaultSpacing = 1.0d;

    @NodeProperty(displayName = "Default Count", category = "Rebuild", order = 3)
    private int defaultCount = 10;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_MODE_ID = "input_mode";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_COUNT_ID = "input_count";

    private static final String OUTPUT_CURVE_ID = "output_curve";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public CurveRebuildByLengthNode() {
        super(UUID.randomUUID(), "geometry.curves.rebuild_curve_length");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to rebuild by arc length (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_MODE_ID, "Mode",
            "Sampling mode: Count or Spacing", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing",
            "Target distance between samples when Mode=Spacing", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count",
            "Target sample count when Mode=Count (>= 2)", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_CURVE_ID, "Curve",
            "Rebuilt sampled curve as a linear control path", NodeDataType.CURVE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline",
            "Rebuilt polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points",
            "Rebuilt points as point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length",
            "Total input path length used for rebuilding", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when rebuilding succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        if (verts == null || verts.size() < 2) {
            writeInvalid();
            return;
        }

        SamplingMode mode = SamplingMode.fromObject(inputValues.get(INPUT_MODE_ID), samplingMode);
        int count = inputValues.get(INPUT_COUNT_ID) instanceof Number n ? n.intValue() : defaultCount;
        double spacing = inputValues.get(INPUT_SPACING_ID) instanceof Number n ? n.doubleValue() : defaultSpacing;

        PathSamplingUtils.PathSampleResult sample = PathSamplingUtils.sample(verts, mode, count, spacing);
        if (!sample.valid() || sample.points().isEmpty()) {
            writeInvalid();
            return;
        }

        List<Vector3d> samples = sample.points();
        List<Vec3d> polyPts = new ArrayList<>(samples.size());
        for (Vector3d point : samples) {
            polyPts.add(new Vec3d(point.x, point.y, point.z));
        }

        Curve curve = buildLinearCurve(polyPts);
        PolylineData polyline = PathUtils.createPolylineOrNull(polyPts);
        if (polyline == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_CURVE_ID, curve);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(samples));
        outputValues.put(OUTPUT_LENGTH_ID, sample.totalLength());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_CURVE_ID, null);
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

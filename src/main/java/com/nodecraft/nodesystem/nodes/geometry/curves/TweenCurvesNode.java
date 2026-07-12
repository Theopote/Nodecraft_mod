package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Creates intermediate sampled curves between two compatible paths.
 */
@NodeInfo(
    id = "geometry.curves.tween_curves",
    displayName = "Tween Curves",
    description = "Creates evenly spaced intermediate curves between two curve, polyline, or line inputs",
    category = "geometry.curves",
    order = 23
)
public class TweenCurvesNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Default Count", category = "Tween", order = 1,
        description = "Number of intermediate curves generated when Count input is not connected")
    private int defaultCount = 1;

    @NodeProperty(displayName = "Default Samples", category = "Tween", order = 2,
        description = "Number of sample points used on each tween curve")
    private int defaultSamples = 32;

    @NodeProperty(displayName = "Reverse A", category = "Tween", order = 3)
    private boolean reverseA = false;

    @NodeProperty(displayName = "Reverse B", category = "Tween", order = 4)
    private boolean reverseB = false;

    @NodeProperty(displayName = "Include Inputs", category = "Tween", order = 5,
        description = "When enabled, Curve A and Curve B are included at the start and end of the output lists")
    private boolean includeInputs = false;

    private static final String INPUT_CURVE_A_ID = "input_curve_a";
    private static final String INPUT_POLYLINE_A_ID = "input_polyline_a";
    private static final String INPUT_LINE_A_ID = "input_line_a";
    private static final String INPUT_CURVE_B_ID = "input_curve_b";
    private static final String INPUT_POLYLINE_B_ID = "input_polyline_b";
    private static final String INPUT_LINE_B_ID = "input_line_b";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SAMPLES_ID = "input_samples";

    private static final String OUTPUT_CURVES_ID = "output_curves";
    private static final String OUTPUT_CURVES_TREE_ID = "output_curves_tree";
    private static final String OUTPUT_POLYLINES_ID = "output_polylines";
    private static final String OUTPUT_POLYLINES_TREE_ID = "output_polylines_tree";
    private static final String OUTPUT_POINT_ROWS_ID = "output_point_rows";
    private static final String OUTPUT_POINT_ROWS_TREE_ID = "output_point_rows_tree";
    private static final String OUTPUT_FIRST_POLYLINE_ID = "output_first_polyline";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TweenCurvesNode() {
        super(UUID.randomUUID(), "geometry.curves.tween_curves");

        addInputPort(new BasePort(INPUT_CURVE_A_ID, "Curve A",
            "First curve to tween from", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_A_ID, "Polyline A",
            "Fallback first polyline", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_A_ID, "Line A",
            "Fallback first line", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_CURVE_B_ID, "Curve B",
            "Second curve to tween to", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_B_ID, "Polyline B",
            "Fallback second polyline", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_B_ID, "Line B",
            "Fallback second line", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count",
            "Number of intermediate curves to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SAMPLES_ID, "Samples",
            "Sample point count per tween curve", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_CURVES_ID, "Curves",
            "Generated tween curves as Curve list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_CURVES_TREE_ID, "Curves Tree",
            "Generated tween curves keyed by tween index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINES_ID, "Polylines",
            "Generated tween curves as Polyline list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINES_TREE_ID, "Polyline Tree",
            "Generated tween polylines keyed by tween index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_POINT_ROWS_ID, "Point Rows",
            "Generated tween points; each row is one tween curve", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_POINT_ROWS_TREE_ID, "Point Row Tree",
            "Tween curve point rows keyed by tween index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_FIRST_POLYLINE_ID, "First Polyline",
            "First generated tween polyline for single-curve workflows", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Generated curve count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when tween curves were generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Creates evenly spaced intermediate curves between two curve, polyline, or line inputs";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pathA = resolvePath(INPUT_CURVE_A_ID, INPUT_POLYLINE_A_ID, INPUT_LINE_A_ID, reverseA);
        List<Vector3d> pathB = resolvePath(INPUT_CURVE_B_ID, INPUT_POLYLINE_B_ID, INPUT_LINE_B_ID, reverseB);
        int count = GenerationLimits.clampPositiveCount(Math.max(0, readIntInput(INPUT_COUNT_ID, defaultCount)));
        int samples = Math.max(2, readIntInput(INPUT_SAMPLES_ID, defaultSamples));
        if (pathA == null || pathB == null || count < 1) {
            writeInvalid();
            return;
        }

        ResampledPath sampledA = resample(pathA, samples);
        ResampledPath sampledB = resample(pathB, samples);
        if (sampledA == null || sampledB == null || sampledA.closed != sampledB.closed) {
            writeInvalid();
            return;
        }

        List<Curve> curves = new ArrayList<>(count + (includeInputs ? 2 : 0));
        List<PolylineData> polylines = new ArrayList<>(count + (includeInputs ? 2 : 0));
        List<List<Vector3d>> pointRows = new ArrayList<>(count + (includeInputs ? 2 : 0));

        if (includeInputs) {
            appendTweenRow(sampledA.points, sampledA.closed, curves, polylines, pointRows);
        }
        for (int i = 1; i <= count; i++) {
            double t = i / (double) (count + 1);
            List<Vector3d> row = interpolateRows(sampledA.points, sampledB.points, t);
            appendTweenRow(row, sampledA.closed, curves, polylines, pointRows);
        }
        if (includeInputs) {
            appendTweenRow(sampledB.points, sampledB.closed, curves, polylines, pointRows);
        }

        if (polylines.isEmpty()) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_CURVES_ID, List.copyOf(curves));
        outputValues.put(OUTPUT_CURVES_TREE_ID, buildValueTree(curves));
        outputValues.put(OUTPUT_POLYLINES_ID, List.copyOf(polylines));
        outputValues.put(OUTPUT_POLYLINES_TREE_ID, buildValueTree(polylines));
        outputValues.put(OUTPUT_POINT_ROWS_ID, List.copyOf(pointRows));
        outputValues.put(OUTPUT_POINT_ROWS_TREE_ID, buildPointRowTree(pointRows));
        outputValues.put(OUTPUT_FIRST_POLYLINE_ID, polylines.getFirst());
        outputValues.put(OUTPUT_COUNT_ID, polylines.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public int getDefaultCount() {
        return defaultCount;
    }

    public void setDefaultCount(int defaultCount) {
        int resolved = GenerationLimits.clampPositiveCount(Math.max(0, defaultCount));
        if (this.defaultCount != resolved) {
            this.defaultCount = resolved;
            markDirty();
        }
    }

    public int getDefaultSamples() {
        return defaultSamples;
    }

    public void setDefaultSamples(int defaultSamples) {
        int resolved = Math.max(2, defaultSamples);
        if (this.defaultSamples != resolved) {
            this.defaultSamples = resolved;
            markDirty();
        }
    }

    public boolean isReverseA() {
        return reverseA;
    }

    public void setReverseA(boolean reverseA) {
        if (this.reverseA != reverseA) {
            this.reverseA = reverseA;
            markDirty();
        }
    }

    public boolean isReverseB() {
        return reverseB;
    }

    public void setReverseB(boolean reverseB) {
        if (this.reverseB != reverseB) {
            this.reverseB = reverseB;
            markDirty();
        }
    }

    public boolean isIncludeInputs() {
        return includeInputs;
    }

    public void setIncludeInputs(boolean includeInputs) {
        if (this.includeInputs != includeInputs) {
            this.includeInputs = includeInputs;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of(
            "defaultCount", defaultCount,
            "defaultSamples", defaultSamples,
            "reverseA", reverseA,
            "reverseB", reverseB,
            "includeInputs", includeInputs
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultCount") instanceof Number value) {
            setDefaultCount(value.intValue());
        }
        if (map.get("defaultSamples") instanceof Number value) {
            setDefaultSamples(value.intValue());
        }
        if (map.get("reverseA") instanceof Boolean value) {
            setReverseA(value);
        }
        if (map.get("reverseB") instanceof Boolean value) {
            setReverseB(value);
        }
        if (map.get("includeInputs") instanceof Boolean value) {
            setIncludeInputs(value);
        }
    }

    private @Nullable List<Vector3d> resolvePath(String curveId, String polylineId, String lineId, boolean reverse) {
        List<Vector3d> points = PathUtils.resolveVertices(
            inputValues.get(curveId),
            inputValues.get(polylineId),
            inputValues.get(lineId)
        );
        if (points == null || points.size() < 2) {
            return null;
        }
        List<Vector3d> copy = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            copy.add(new Vector3d(point));
        }
        if (reverse) {
            Collections.reverse(copy);
        }
        return copy;
    }

    private @Nullable ResampledPath resample(List<Vector3d> path, int samples) {
        boolean closed = PathUtils.isClosed(path);
        List<Vector3d> unique = closed ? path.subList(0, path.size() - 1) : path;
        if (unique.size() < 2) {
            return null;
        }
        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null || cumulative[cumulative.length - 1] <= EPS) {
            return null;
        }

        int sampleCount = closed ? samples : Math.max(2, samples);
        List<Vector3d> out = new ArrayList<>(sampleCount);
        double total = cumulative[cumulative.length - 1];
        for (int i = 0; i < sampleCount; i++) {
            double t = closed
                ? i / (double) sampleCount
                : i / (double) (sampleCount - 1);
            out.add(PathUtils.sampleAtDistance(unique, closed, cumulative, total * t));
        }
        return new ResampledPath(List.copyOf(out), closed);
    }

    private List<Vector3d> interpolateRows(List<Vector3d> a, List<Vector3d> b, double t) {
        List<Vector3d> out = new ArrayList<>(a.size());
        for (int i = 0; i < a.size(); i++) {
            out.add(new Vector3d(a.get(i)).lerp(b.get(i), t));
        }
        return out;
    }

    private void appendTweenRow(List<Vector3d> row,
                                boolean closed,
                                List<Curve> curves,
                                List<PolylineData> polylines,
                                List<List<Vector3d>> pointRows) {
        List<Vec3d> vecs = PathUtils.toVec3dList(row, closed);
        PolylineData polyline = PathUtils.createPolylineOrNull(vecs);
        if (polyline == null) {
            return;
        }
        curves.add(buildLinearCurve(vecs));
        polylines.add(polyline);
        pointRows.add(List.copyOf(row));
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_CURVES_ID, List.of());
        outputValues.put(OUTPUT_CURVES_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_POLYLINES_ID, List.of());
        outputValues.put(OUTPUT_POLYLINES_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_POINT_ROWS_ID, List.of());
        outputValues.put(OUTPUT_POINT_ROWS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_FIRST_POLYLINE_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private DataTreeData buildValueTree(List<?> values) {
        List<DataTreeData.Branch> branches = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            branches.add(new DataTreeData.Branch(List.of(i), List.of(values.get(i))));
        }
        return new DataTreeData(branches);
    }

    private DataTreeData buildPointRowTree(List<List<Vector3d>> pointRows) {
        List<DataTreeData.Branch> branches = new ArrayList<>(pointRows.size());
        for (int i = 0; i < pointRows.size(); i++) {
            branches.add(new DataTreeData.Branch(List.of(i), new ArrayList<>(pointRows.get(i))));
        }
        return new DataTreeData(branches);
    }

    private record ResampledPath(List<Vector3d> points, boolean closed) {
    }
}

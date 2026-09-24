package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.tween_curves",
    displayName = "Tween Paths",
    description = "Creates evenly spaced intermediate paths between two path inputs.",
    category = "geometry.curves",
    order = 23
)
public class TweenCurvesNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Default Count", category = "Tween", order = 1,
        description = "Number of intermediate paths when Count input is not connected")
    private int defaultCount = 1;

    @NodeProperty(displayName = "Default Samples", category = "Tween", order = 2,
        description = "Number of sample points used on each tween path")
    private int defaultSamples = 32;

    @NodeProperty(displayName = "Reverse A", category = "Tween", order = 3)
    private boolean reverseA = false;

    @NodeProperty(displayName = "Reverse B", category = "Tween", order = 4)
    private boolean reverseB = false;

    @NodeProperty(displayName = "Include Inputs", category = "Tween", order = 5,
        description = "When enabled, Path A and Path B are included at the start and end of the output list")
    private boolean includeInputs = false;

    private static final String INPUT_PATH_A_ID = "input_path_a";
    private static final String INPUT_PATH_B_ID = "input_path_b";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SAMPLES_ID = "input_samples";

    private static final String OUTPUT_PATHS_ID = "output_paths";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TweenCurvesNode() {
        super(UUID.randomUUID(), "geometry.curves.tween_curves");

        addInputPort(new BasePort(INPUT_PATH_A_ID, "Path A",
            "First path to tween from (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PATH_B_ID, "Path B",
            "Second path to tween to (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count",
            "Number of intermediate paths to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SAMPLES_ID, "Samples",
            "Sample point count per tween path", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PATHS_ID, "Paths",
            "Generated tween paths", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of generated paths", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when tween paths were generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Creates evenly spaced intermediate paths between two path inputs.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pathA = resolvePathVertices(INPUT_PATH_A_ID, reverseA);
        List<Vector3d> pathB = resolvePathVertices(INPUT_PATH_B_ID, reverseB);
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

        List<PathData> paths = new ArrayList<>(count + (includeInputs ? 2 : 0));

        if (includeInputs) {
            appendTweenPath(sampledA.points, sampledA.closed, paths);
        }
        for (int i = 1; i <= count; i++) {
            double t = i / (double) (count + 1);
            List<Vector3d> row = interpolateRows(sampledA.points, sampledB.points, t);
            appendTweenPath(row, sampledA.closed, paths);
        }
        if (includeInputs) {
            appendTweenPath(sampledB.points, sampledB.closed, paths);
        }

        if (paths.isEmpty()) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_PATHS_ID, List.copyOf(paths));
        outputValues.put(OUTPUT_COUNT_ID, paths.size());
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

    private void appendTweenPath(List<Vector3d> row, boolean closed, List<PathData> paths) {
        PathData path = PathUtils.toPathData(closed && !row.isEmpty()
            ? appendClosingVertex(row) : row);
        if (path != null) {
            paths.add(path);
        }
    }

    private static List<Vector3d> appendClosingVertex(List<Vector3d> row) {
        List<Vector3d> copy = new ArrayList<>(row.size() + 1);
        copy.addAll(row);
        if (!copy.isEmpty()) {
            copy.add(new Vector3d(copy.getFirst()));
        }
        return copy;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PATHS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private record ResampledPath(List<Vector3d> points, boolean closed) {
    }
}

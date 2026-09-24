package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.PathFrameUtils;
import com.nodecraft.nodesystem.util.PathSamplingUtils;
import com.nodecraft.nodesystem.util.SamplingMode;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.linear.path_instances",
    displayName = "Path Frames",
    description = "Generates parallel-transport frames along a path using explicit sampling mode.",
    category = "pattern.linear",
    order = 4
)
public class PathInstancesNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(displayName = "Sampling Mode", category = "Sampling", order = 1,
        description = "Original = path vertices; Count = uniform samples; Spacing = arc-length spacing")
    private SamplingMode samplingMode = SamplingMode.ORIGINAL;

    @NodeProperty(displayName = "Default Spacing", category = "Sampling", order = 2)
    private double defaultSpacing = 1.0d;

    @NodeProperty(displayName = "Default Count", category = "Sampling", order = 3)
    private int defaultCount = 10;

    @NodeProperty(displayName = "Deduplicate Near Duplicates", category = "Instances", order = 10,
        description = "When true, skips consecutive samples closer than Epsilon.")
    private boolean deduplicateNearDuplicates = false;

    @NodeProperty(displayName = "Deduplicate Epsilon", category = "Instances", order = 11)
    private double deduplicateEpsilon = 1.0e-6d;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_MODE_ID = "input_mode";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_UP_VECTOR_ID = "input_up_vector";

    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_TANGENTS_ID = "output_tangents";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PathInstancesNode() {
        super(UUID.randomUUID(), "pattern.linear.path_instances");
        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to sample (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_MODE_ID, "Mode",
            "Sampling mode: Original, Count, or Spacing", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count",
            "Sample count when Mode=Count (>= 2)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing",
            "Arc-length spacing when Mode=Spacing (> 0)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_UP_VECTOR_ID, "Up Vector", "Reference up vector for frame construction", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Parallel-transport frames along the path", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Frame origin points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TANGENTS_ID, "Tangents", "Path direction at each frame", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of generated frames", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Total path length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame generation succeeds", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates parallel-transport frames along a path using explicit sampling mode.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = PathUtils.resolvePath(inputValues.get(INPUT_PATH_ID));
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

        List<Vector3d> samples = new ArrayList<>(sample.points());
        if (deduplicateNearDuplicates) {
            samples = deduplicateContinuous(samples, Math.max(EPSILON, deduplicateEpsilon));
        }
        if (samples.size() < 2) {
            writeInvalid();
            return;
        }

        boolean closed = sample.closed();
        List<Vector3d> unique = closed && samples.size() > 1 && samples.getFirst().equals(samples.getLast())
            ? samples.subList(0, samples.size() - 1) : samples;
        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        double total = sample.totalLength();

        List<Vector3d> tangents;
        if (mode == SamplingMode.ORIGINAL) {
            tangents = new ArrayList<>(samples.size());
            for (int i = 0; i < samples.size(); i++) {
                tangents.add(computeTangent(samples, i));
            }
        } else if (cumulative != null) {
            tangents = PathSamplingUtils.tangentsAtDistances(unique, closed, cumulative, total, sample.distances());
        } else {
            writeInvalid();
            return;
        }

        if (tangents.size() != samples.size()) {
            int limit = Math.min(samples.size(), tangents.size());
            samples = samples.subList(0, limit);
            tangents = tangents.subList(0, limit);
        }

        Vector3d up = resolveUp(inputValues.get(INPUT_UP_VECTOR_ID));
        List<FrameData> frames = PathFrameUtils.placementFramesFromSamples(samples, tangents, up);
        if (frames.isEmpty()) {
            writeInvalid();
            return;
        }

        List<Vector3d> tangentCopy = new ArrayList<>(tangents.size());
        for (Vector3d tangent : tangents) {
            tangentCopy.add(new Vector3d(tangent));
        }

        outputValues.put(OUTPUT_FRAMES_ID, List.copyOf(frames));
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(samples));
        outputValues.put(OUTPUT_TANGENTS_ID, List.copyOf(tangentCopy));
        outputValues.put(OUTPUT_COUNT_ID, frames.size());
        outputValues.put(OUTPUT_LENGTH_ID, total);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private static Vector3d computeTangent(List<Vector3d> points, int index) {
        if (points.size() < 2) {
            return new Vector3d(0.0d, 1.0d, 0.0d);
        }
        if (index <= 0) {
            return new Vector3d(points.get(1)).sub(points.get(0)).normalize();
        }
        if (index >= points.size() - 1) {
            return new Vector3d(points.get(index)).sub(points.get(index - 1)).normalize();
        }
        Vector3d forward = new Vector3d(points.get(index + 1)).sub(points.get(index));
        if (forward.lengthSquared() > EPSILON) {
            return forward.normalize();
        }
        return new Vector3d(points.get(index)).sub(points.get(index - 1)).normalize();
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_FRAMES_ID, List.of());
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_TANGENTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static List<Vector3d> deduplicateContinuous(List<Vector3d> points, double epsilon) {
        double epsSq = epsilon * epsilon;
        List<Vector3d> filtered = new ArrayList<>(points.size());
        Vector3d previous = null;
        for (Vector3d point : points) {
            if (previous == null || previous.distanceSquared(point) > epsSq) {
                filtered.add(new Vector3d(point));
                previous = point;
            }
        }
        return filtered;
    }

    private Vector3d resolveUp(Object value) {
        Vector3d resolved = SpatialValueResolver.resolveVector(value);
        if (resolved != null && resolved.lengthSquared() > EPSILON) {
            return new Vector3d(resolved).normalize();
        }
        return new Vector3d(0.0d, 1.0d, 0.0d);
    }

    public SamplingMode getSamplingMode() {
        return samplingMode;
    }

    public void setSamplingMode(SamplingMode samplingMode) {
        if (this.samplingMode != samplingMode) {
            this.samplingMode = samplingMode;
            markDirty();
        }
    }

    public boolean isDeduplicateNearDuplicates() {
        return deduplicateNearDuplicates;
    }

    public void setDeduplicateNearDuplicates(boolean deduplicateNearDuplicates) {
        if (this.deduplicateNearDuplicates != deduplicateNearDuplicates) {
            this.deduplicateNearDuplicates = deduplicateNearDuplicates;
            markDirty();
        }
    }

    public double getDeduplicateEpsilon() {
        return deduplicateEpsilon;
    }

    public void setDeduplicateEpsilon(double deduplicateEpsilon) {
        if (Double.compare(this.deduplicateEpsilon, deduplicateEpsilon) != 0) {
            this.deduplicateEpsilon = deduplicateEpsilon;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return Map.of(
            "samplingMode", samplingMode.name(),
            "defaultSpacing", defaultSpacing,
            "defaultCount", defaultCount,
            "deduplicateNearDuplicates", deduplicateNearDuplicates,
            "deduplicateEpsilon", deduplicateEpsilon
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("samplingMode") instanceof String value) {
            setSamplingMode(SamplingMode.fromObject(value, SamplingMode.ORIGINAL));
        }
        if (map.get("defaultSpacing") instanceof Number value) {
            defaultSpacing = value.doubleValue();
        }
        if (map.get("defaultCount") instanceof Number value) {
            defaultCount = value.intValue();
        }
        if (map.get("deduplicateNearDuplicates") instanceof Boolean value) {
            setDeduplicateNearDuplicates(value);
        } else if (map.get("deduplicateAnchors") instanceof Boolean) {
            setDeduplicateNearDuplicates(false);
        }
        if (map.get("deduplicateEpsilon") instanceof Number value) {
            setDeduplicateEpsilon(value.doubleValue());
        }
    }
}

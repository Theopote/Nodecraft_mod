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
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PathFrameUtils;
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
    description = "Generates continuous parallel-transport frames along a path (FRAME_LIST + origins/axes).",
    category = "pattern.linear",
    order = 4
)
public class PathInstancesNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(
        displayName = "Deduplicate Near Duplicates",
        category = "Instances",
        order = 1,
        description = "When true, skips consecutive samples closer than Epsilon. Never snaps to BlockPos."
    )
    private boolean deduplicateNearDuplicates = false;

    @NodeProperty(displayName = "Deduplicate Epsilon", category = "Instances", order = 2)
    private double deduplicateEpsilon = 1.0e-6d;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_PATH_POINTS_ID = "input_path_points";
    private static final String INPUT_UP_VECTOR_ID = "input_up_vector";

    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_ORIGINS_ID = "output_origins";
    private static final String OUTPUT_X_AXES_ID = "output_x_axes";
    private static final String OUTPUT_Y_AXES_ID = "output_y_axes";
    private static final String OUTPUT_Z_AXES_ID = "output_z_axes";
    private static final String OUTPUT_TANGENTS_ID = "output_tangents";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PathInstancesNode() {
        super(UUID.randomUUID(), "pattern.linear.path_instances");
        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to sample (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PATH_POINTS_ID, "Path Points",
            "Legacy fallback ordered point list when Path is unconnected. Prefer Points To Path → PATH.", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_UP_VECTOR_ID, "Up Vector", "Reference up vector for frame construction", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Parallel-transport frames along the path", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ORIGINS_ID, "Origins", "Continuous frame origins along path", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_X_AXES_ID, "X Axes", "Frame X axes (tangent)", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_Y_AXES_ID, "Y Axes", "Frame Y axes (normal)", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_Z_AXES_ID, "Z Axes", "Frame Z axes (binormal)", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TANGENTS_ID, "Tangents", "Alias of X axes (path direction)", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of generated frames", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame generation succeeds", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates continuous parallel-transport frames along a path (FRAME_LIST + origins/axes).";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> points = resolvePathPoints();
        if (points.size() < 2) {
            writeInvalid();
            return;
        }

        Vector3d up = resolveUp(inputValues.get(INPUT_UP_VECTOR_ID));
        int pointLimit = Math.min(points.size(), GenerationLimits.MAX_LIST_ELEMENTS);
        List<Vector3d> samples = new ArrayList<>(pointLimit);
        for (int i = 0; i < pointLimit; i++) {
            samples.add(points.get(i));
        }
        if (deduplicateNearDuplicates) {
            samples = deduplicateContinuous(samples, Math.max(EPSILON, deduplicateEpsilon));
        }
        if (samples.size() < 2) {
            writeInvalid();
            return;
        }

        List<FrameData> frames = PathFrameUtils.placementFramesAlongPolyline(samples, up);
        if (frames.isEmpty()) {
            writeInvalid();
            return;
        }

        List<Vector3d> origins = new ArrayList<>(frames.size());
        List<Vector3d> xAxes = new ArrayList<>(frames.size());
        List<Vector3d> yAxes = new ArrayList<>(frames.size());
        List<Vector3d> zAxes = new ArrayList<>(frames.size());
        for (FrameData frame : frames) {
            origins.add(new Vector3d(frame.getOrigin()));
            xAxes.add(new Vector3d(frame.getXAxis()));
            yAxes.add(new Vector3d(frame.getYAxis()));
            zAxes.add(new Vector3d(frame.getZAxis()));
        }

        outputValues.put(OUTPUT_FRAMES_ID, List.copyOf(frames));
        outputValues.put(OUTPUT_ORIGINS_ID, SpatialValueResolver.toPointDataList(origins));
        outputValues.put(OUTPUT_X_AXES_ID, List.copyOf(xAxes));
        outputValues.put(OUTPUT_Y_AXES_ID, List.copyOf(yAxes));
        outputValues.put(OUTPUT_Z_AXES_ID, List.copyOf(zAxes));
        outputValues.put(OUTPUT_TANGENTS_ID, List.copyOf(xAxes));
        outputValues.put(OUTPUT_COUNT_ID, frames.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_FRAMES_ID, List.of());
        outputValues.put(OUTPUT_ORIGINS_ID, List.of());
        outputValues.put(OUTPUT_X_AXES_ID, List.of());
        outputValues.put(OUTPUT_Y_AXES_ID, List.of());
        outputValues.put(OUTPUT_Z_AXES_ID, List.of());
        outputValues.put(OUTPUT_TANGENTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Vector3d> resolvePathPoints() {
        return PathUtils.resolvePathOrPointList(
            inputValues.get(INPUT_PATH_ID),
            inputValues.get(INPUT_PATH_POINTS_ID)
        );
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
            "deduplicateNearDuplicates", deduplicateNearDuplicates,
            "deduplicateEpsilon", deduplicateEpsilon
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("deduplicateNearDuplicates") instanceof Boolean value) {
            setDeduplicateNearDuplicates(value);
        } else if (map.get("deduplicateAnchors") instanceof Boolean value) {
            // Legacy BlockPos-flooring dedupe is retired; keep off for continuous geometry.
            setDeduplicateNearDuplicates(false);
        }
        if (map.get("deduplicateEpsilon") instanceof Number value) {
            setDeduplicateEpsilon(value.doubleValue());
        }
    }
}

package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PathFrameUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.nodecraft.nodesystem.util.VectorUtils;

/**
 * Generates parallel-transport frames at path vertices.
 * <p>
 * Output Points/Count reflect path-frame sampling cleanup (continuous dedupe),
 * not the raw PATH vertex count.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.linear.path_frames",
    displayName = "Path Frames",
    description = "Generates parallel-transport frames at path vertices.",
    category = "pattern.linear",
    order = 1
)
public class PathFramesNode extends AbstractPatternLinearNode {

    private static final double DEDUPE_EPSILON = PathUtils.CLOSED_DISTANCE_EPSILON;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_UP_VECTOR_ID = "input_up_vector";

    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_TANGENTS_ID = "output_tangents";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_LENGTH_ID = "output_length";

    public PathFramesNode() {
        super(UUID.randomUUID(), "pattern.linear.path_frames");
        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to frame (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_UP_VECTOR_ID, "Up Vector", "Reference up vector for frame construction", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Parallel-transport frames along the path", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Frame origin points (after sampling cleanup)", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TANGENTS_ID, "Tangents", "Path direction at each frame", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of generated frames (deduplicated samples)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Total path length", NodeDataType.DOUBLE, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Generates parallel-transport frames at path vertices.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = PathUtils.resolvePath(inputValues.get(INPUT_PATH_ID));
        if (verts == null || verts.size() < 2) {
            writeInvalid("Missing or invalid Path");
            return;
        }

        List<Vector3d> samples = deduplicateContinuous(new ArrayList<>(verts), DEDUPE_EPSILON);
        if (samples.size() < 2) {
            writeInvalid("Path has fewer than 2 samples after cleanup");
            return;
        }

        PathUtils.ClosedVertices closedVerts = PathUtils.closedUniqueVertices(samples);
        List<Vector3d> vertices = closedVerts.vertices();
        boolean closed = closedVerts.closed();
        if (vertices.size() < 2) {
            writeInvalid("Path has fewer than 2 unique vertices");
            return;
        }
        if (vertices.size() > GenerationLimits.MAX_LAYOUT_INSTANCES) {
            writeInvalid("Path sample count exceeds MAX_LAYOUT_INSTANCES");
            return;
        }

        Vector3d up = resolveOptionalUpVector(this, INPUT_UP_VECTOR_ID);
        if (up == null) {
            writeInvalid(OptionalPortDrive.isConnected(this, INPUT_UP_VECTOR_ID)
                ? "Up Vector connected but invalid or zero"
                : "Up Vector is invalid");
            return;
        }

        double[] cumulative = PathUtils.buildCumulative(vertices, closed);
        double total = cumulative != null && cumulative.length > 0 ? cumulative[cumulative.length - 1] : 0.0d;

        List<Vector3d> tangents = new ArrayList<>(vertices.size());
        for (int i = 0; i < vertices.size(); i++) {
            Vector3d tangent = PathFrameUtils.tryComputeTangent(vertices, i, closed);
            if (tangent == null) {
                writeInvalid("Degenerate path tangent");
                return;
            }
            tangents.add(tangent);
        }

        boolean requireUp = OptionalPortDrive.isConnected(this, INPUT_UP_VECTOR_ID);
        List<FrameData> frames = PathFrameUtils.placementFramesFromSamples(
            vertices, tangents, up, requireUp, closed);
        if (frames == null) {
            writeInvalid("Up Vector is parallel to path tangent");
            return;
        }
        if (frames.isEmpty()) {
            writeInvalid("Failed to construct path frames");
            return;
        }

        List<Vector3d> tangentCopy = new ArrayList<>(tangents.size());
        for (Vector3d tangent : tangents) {
            tangentCopy.add(new Vector3d(tangent));
        }

        outputValues.put(OUTPUT_FRAMES_ID, List.copyOf(frames));
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(vertices));
        outputValues.put(OUTPUT_TANGENTS_ID, VectorUtils.toVectorPortList(tangentCopy));
        putIntOutputs(frames.size(), OUTPUT_COUNT_ID);
        outputValues.put(OUTPUT_LENGTH_ID, total);
        markSuccess();
    }

    private void writeInvalid(String error) {
        markInvalid(error);
        putEmptyListOutputs(OUTPUT_FRAMES_ID, OUTPUT_POINTS_ID, OUTPUT_TANGENTS_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
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

    @Override
    public Object getNodeState() {
        return null;
    }

    @Override
    public void setNodeState(Object state) {
        // No persisted properties.
    }
}

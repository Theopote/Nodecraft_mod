package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialTolerance;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.orientation.project_path_to_plane",
    displayName = "Project Path To Plane",
    description = "Projects a path onto a target plane",
    category = "transform.orientation",
    order = 4
)
public class ProjectPathToPlaneNode extends AbstractOrientationNode {

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public ProjectPathToPlaneNode() {
        super("transform.orientation.project_path_to_plane");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to project (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target plane for projection", NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Projected path on the target plane", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Projected points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances", "Absolute distances from source vertices to the plane", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of projected points", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Projects a path onto a target plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        PlaneData plane = planeObj instanceof PlaneData p ? p : null;
        PlaneData normalized = OrientationUtils.resolveNormalizedPlane(plane);
        if (normalized == null) {
            writeInvalid("Plane is missing or invalid");
            return;
        }

        List<Vector3d> sourceVertices = PathUtils.resolvePath(inputValues.get(INPUT_PATH_ID));
        if (sourceVertices == null || sourceVertices.size() < 2) {
            writeInvalid("Path is missing, invalid, or has fewer than 2 vertices");
            return;
        }
        if (sourceVertices.size() > GenerationLimits.MAX_CURVE_SAMPLES) {
            writeInvalid("Path sample count exceeds MAX_CURVE_SAMPLES");
            return;
        }

        List<Vector3d> projectedVectors = new ArrayList<>(sourceVertices.size());
        List<Double> distances = new ArrayList<>(sourceVertices.size());
        for (Vector3d source : sourceVertices) {
            OrientationUtils.PointProjection projection = OrientationUtils.projectPoint(normalized, source);
            if (projection == null) {
                writeInvalid("Path contains a non-finite vertex");
                return;
            }
            projectedVectors.add(projection.projected());
            distances.add(projection.distance());
        }

        List<Vector3d> canonical = adjacentDedupe(projectedVectors);
        List<Double> canonicalDistances = adjacentDedupeDistances(projectedVectors, distances);
        if (canonical.size() < 2) {
            writeInvalid("Projected path collapsed to fewer than 2 distinct vertices");
            return;
        }
        if (PathUtils.hasDegenerateSegment(canonical)) {
            writeInvalid("Projected path contains a zero-length segment");
            return;
        }

        PathData path = PathUtils.toPathData(canonical);
        if (path == null) {
            writeInvalid("Projected path is invalid");
            return;
        }

        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(canonical));
        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(canonicalDistances));
        outputValues.put(OUTPUT_COUNT_ID, canonical.size());
        markSuccess();
    }

    private static List<Vector3d> adjacentDedupe(List<Vector3d> points) {
        List<Vector3d> out = new ArrayList<>(points.size());
        double epsSq = SpatialTolerance.EPS_SQ;
        for (Vector3d point : points) {
            if (out.isEmpty() || out.getLast().distanceSquared(point) > epsSq) {
                out.add(new Vector3d(point));
            }
        }
        return out;
    }

    private static List<Double> adjacentDedupeDistances(List<Vector3d> points, List<Double> distances) {
        List<Double> out = new ArrayList<>(distances.size());
        double epsSq = SpatialTolerance.EPS_SQ;
        Vector3d lastKept = null;
        for (int i = 0; i < points.size(); i++) {
            Vector3d point = points.get(i);
            if (lastKept == null || lastKept.distanceSquared(point) > epsSq) {
                out.add(distances.get(i));
                lastKept = point;
            }
        }
        return out;
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of();
    }

    @Override
    public void setNodeState(Object state) {
        // stateless
    }

    private void writeInvalid(String error) {
        putNullOutputs(OUTPUT_PATH_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID, OUTPUT_DISTANCES_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }
}

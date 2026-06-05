package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "transform.orientation.project_points_to_plane",
    displayName = "Project Points To Plane",
    description = "Projects a list of points or vectors onto a target plane",
    category = "transform.orientation",
    order = 3
)
public class ProjectPointsToPlaneNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_POINT_DATA_ID = "output_point_data";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_SIGNED_DISTANCES_ID = "output_signed_distances";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ProjectPointsToPlaneNode() {
        super(UUID.randomUUID(), "transform.orientation.project_points_to_plane");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "List of points, vectors, positions, or block coordinates to project", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target plane for projection", NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Projected points as Vector3d list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POINT_DATA_ID, "Point Data", "Projected points as PointData list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances", "Absolute distances from source points to the plane", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SIGNED_DISTANCES_ID, "Signed Distances", "Signed distances from source points to the plane", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of projected points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when points were projected", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Projects a list of points or vectors onto a target plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(pointsObj instanceof List<?> points) || !(planeObj instanceof PlaneData plane) || !OrientationUtils.isUsablePlane(plane)) {
            writeInvalid();
            return;
        }

        List<Vector3d> projectedPoints = new ArrayList<>(points.size());
        List<PointData> pointData = new ArrayList<>(points.size());
        List<Double> distances = new ArrayList<>(points.size());
        List<Double> signedDistances = new ArrayList<>(points.size());

        for (Object entry : points) {
            Vector3d point = OrientationUtils.resolveVector(entry);
            if (!OrientationUtils.isFinite(point)) {
                continue;
            }
            Vector3d projected = plane.projectPoint(point);
            double signedDistance = plane.signedDistanceTo(point);
            projectedPoints.add(projected);
            pointData.add(new PointData(projected));
            distances.add(Math.abs(signedDistance));
            signedDistances.add(signedDistance);
        }

        if (projectedPoints.isEmpty()) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(projectedPoints));
        outputValues.put(OUTPUT_POINT_DATA_ID, List.copyOf(pointData));
        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
        outputValues.put(OUTPUT_SIGNED_DISTANCES_ID, List.copyOf(signedDistances));
        outputValues.put(OUTPUT_COUNT_ID, projectedPoints.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of();
    }

    @Override
    public void setNodeState(Object state) {
        // stateless
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_POINT_DATA_ID, List.of());
        outputValues.put(OUTPUT_DISTANCES_ID, List.of());
        outputValues.put(OUTPUT_SIGNED_DISTANCES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

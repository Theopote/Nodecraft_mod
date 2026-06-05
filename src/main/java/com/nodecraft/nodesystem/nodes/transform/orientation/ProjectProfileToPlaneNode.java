package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "transform.orientation.project_profile_to_plane",
    displayName = "Project Profile To Plane",
    description = "Projects a polygon profile boundary onto a target plane",
    category = "transform.orientation",
    order = 5
)
public class ProjectProfileToPlaneNode extends BaseNode {

    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_EDGE_COUNT_ID = "output_edge_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ProjectProfileToPlaneNode() {
        super(UUID.randomUUID(), "transform.orientation.project_profile_to_plane");

        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Polygon profile to project", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target plane for projection", NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Projected polygon profile on the target plane", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Projected profile boundary", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Projected closed points as Vector3d list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances", "Absolute distances from source vertices to the target plane", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_COUNT_ID, "Edge Count", "Number of projected profile edges", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when projection succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Projects a polygon profile boundary onto a target plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object profileObj = inputValues.get(INPUT_PROFILE_ID);
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(profileObj instanceof PolygonProfileData profile)
            || !(planeObj instanceof PlaneData plane)
            || !OrientationUtils.isUsablePlane(plane)) {
            writeInvalid();
            return;
        }

        List<Vector3d> sourcePoints = profile.getClosedPoints();
        if (sourcePoints.size() < 4) {
            writeInvalid();
            return;
        }

        List<Vector3d> projectedPoints = new ArrayList<>(sourcePoints.size());
        List<Double> distances = new ArrayList<>(sourcePoints.size());
        for (Vector3d source : sourcePoints) {
            projectedPoints.add(plane.projectPoint(source));
            distances.add(Math.abs(plane.signedDistanceTo(source)));
        }

        PolygonProfileData projectedProfile;
        PolylineData boundary;
        try {
            projectedProfile = new PolygonProfileData(projectedPoints, plane);
            boundary = projectedProfile.getBoundary();
        } catch (IllegalArgumentException ex) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_PROFILE_ID, projectedProfile);
        outputValues.put(OUTPUT_BOUNDARY_ID, boundary);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(projectedPoints));
        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
        outputValues.put(OUTPUT_EDGE_COUNT_ID, projectedProfile.getEdgeCount());
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
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_DISTANCES_ID, List.of());
        outputValues.put(OUTPUT_EDGE_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

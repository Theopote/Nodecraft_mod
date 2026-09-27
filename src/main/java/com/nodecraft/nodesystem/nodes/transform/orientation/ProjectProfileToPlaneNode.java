package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.orientation.project_profile_to_plane",
    displayName = "Project Profile To Plane",
    description = "Projects a polygon profile boundary onto a target plane",
    category = "transform.orientation",
    order = 5
)
public class ProjectProfileToPlaneNode extends AbstractOrientationNode {

    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_EDGE_COUNT_ID = "output_edge_count";

    public ProjectProfileToPlaneNode() {
        super("transform.orientation.project_profile_to_plane");

        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Polygon profile to project", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target plane for projection", NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Projected polygon profile on the target plane", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Projected profile boundary", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Projected closed points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances", "Absolute distances from source vertices to the target plane", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_COUNT_ID, "Edge Count", "Number of projected profile edges", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Projects a polygon profile boundary onto a target plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object profileObj = inputValues.get(INPUT_PROFILE_ID);
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(profileObj instanceof PolygonProfileData profile)) {
            writeInvalid("Profile is missing or invalid");
            return;
        }
        PlaneData normalized = OrientationUtils.resolveNormalizedPlane(planeObj instanceof PlaneData p ? p : null);
        if (normalized == null) {
            writeInvalid("Plane is missing or invalid");
            return;
        }

        List<Vector3d> sourcePoints = profile.closedPoints();
        if (sourcePoints.size() < 4) {
            writeInvalid("Profile has fewer than 4 closed points");
            return;
        }

        List<Vector3d> projectedPoints = new ArrayList<>(sourcePoints.size());
        List<Double> distances = new ArrayList<>(sourcePoints.size());
        for (Vector3d source : sourcePoints) {
            OrientationUtils.PointProjection projection = OrientationUtils.projectPoint(normalized, source);
            if (projection == null) {
                writeInvalid("Profile contains a non-finite vertex");
                return;
            }
            projectedPoints.add(projection.projected());
            distances.add(projection.distance());
        }

        PolygonProfileData projectedProfile;
        PathData boundary;
        try {
            projectedProfile = new PolygonProfileData(projectedPoints, normalized);
            boundary = projectedProfile.getBoundaryPath();
        } catch (IllegalArgumentException ex) {
            writeInvalid("Projected profile is degenerate or invalid");
            return;
        }

        outputValues.put(OUTPUT_PROFILE_ID, projectedProfile);
        outputValues.put(OUTPUT_BOUNDARY_ID, boundary);
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(projectedPoints));
        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
        outputValues.put(OUTPUT_EDGE_COUNT_ID, projectedProfile.getEdgeCount());
        markSuccess();
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
        putNullOutputs(OUTPUT_PROFILE_ID, OUTPUT_BOUNDARY_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID, OUTPUT_DISTANCES_ID);
        putIntOutputs(0, OUTPUT_EDGE_COUNT_ID);
        markInvalid(error);
    }
}

package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.orientation.project_points_to_plane",
    displayName = "Project Points To Plane",
    description = "Projects a list of points onto a target plane",
    category = "transform.orientation",
    order = 3
)
public class ProjectPointsToPlaneNode extends AbstractOrientationNode {

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_SIGNED_DISTANCES_ID = "output_signed_distances";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public ProjectPointsToPlaneNode() {
        super("transform.orientation.project_points_to_plane");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Point list to project", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target plane for projection", NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Projected points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances", "Absolute distances from source points to the plane", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SIGNED_DISTANCES_ID, "Signed Distances", "Signed distances from source points to the plane", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of projected points", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Projects a list of points onto a target plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> sourcePoints = PointUtils.resolveStrictPointListBounded(
            inputValues.get(INPUT_POINTS_ID),
            GenerationLimits.MAX_LIST_ELEMENTS
        );
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        PlaneData plane = planeObj instanceof PlaneData p ? p : null;
        PlaneData normalized = OrientationUtils.resolveNormalizedPlane(plane);
        if (sourcePoints == null || normalized == null) {
            writeInvalid(sourcePoints == null
                ? "Point list is missing, empty, invalid, or exceeds MAX_LIST_ELEMENTS"
                : "Plane is missing or invalid");
            return;
        }

        List<Vector3d> projectedPoints = new ArrayList<>(sourcePoints.size());
        List<Double> distances = new ArrayList<>(sourcePoints.size());
        List<Double> signedDistances = new ArrayList<>(sourcePoints.size());

        for (Vector3d point : sourcePoints) {
            OrientationUtils.PointProjection projection = OrientationUtils.projectPoint(normalized, point);
            if (projection == null) {
                writeInvalid("Point list contains a non-finite point");
                return;
            }
            projectedPoints.add(projection.projected());
            distances.add(projection.distance());
            signedDistances.add(projection.signedDistance());
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(projectedPoints));
        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
        outputValues.put(OUTPUT_SIGNED_DISTANCES_ID, List.copyOf(signedDistances));
        outputValues.put(OUTPUT_COUNT_ID, projectedPoints.size());
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
        putEmptyListOutputs(OUTPUT_POINTS_ID, OUTPUT_DISTANCES_ID, OUTPUT_SIGNED_DISTANCES_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }
}

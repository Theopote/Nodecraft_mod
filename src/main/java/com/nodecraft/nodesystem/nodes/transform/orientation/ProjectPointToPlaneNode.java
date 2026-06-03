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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "transform.orientation.project_to_plane",
    displayName = "Project Point To Plane",
    description = "Projects a geometric point onto a plane and reports the projection distance",
    category = "transform.orientation",
    order = 0
)
public class ProjectPointToPlaneNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_SIGNED_DISTANCE_ID = "output_signed_distance";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ProjectPointToPlaneNode() {
        super(UUID.randomUUID(), "transform.orientation.project_to_plane");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Point to project. Supports Point, Vector, Position, or Block Coordinate.",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Target plane for projection",
            NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Projected Point",
            "Projected point on the target plane", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Projected Vector",
            "Projected point as a Vector3d position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Absolute distance from the input point to the plane", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIGNED_DISTANCE_ID, "Signed Distance",
            "Signed distance from the input point to the plane", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when both point and plane inputs are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Project Point To Plane";
    }

    @Override
    public String getDescription() {
        return "Projects a geometric point onto a plane and reports the projection distance";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d point = resolvePoint(inputValues.get(INPUT_POINT_ID));
        Object planeObj = inputValues.get(INPUT_PLANE_ID);

        if (!OrientationUtils.isFinite(point) || !(planeObj instanceof PlaneData plane) || !OrientationUtils.isUsablePlane(plane)) {
            outputValues.put(OUTPUT_POINT_ID, null);
            outputValues.put(OUTPUT_VECTOR_ID, null);
            outputValues.put(OUTPUT_DISTANCE_ID, Double.NaN);
            outputValues.put(OUTPUT_SIGNED_DISTANCE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d projected = plane.projectPoint(point);
        double signedDistance = plane.signedDistanceTo(point);
        double distance = Math.abs(signedDistance);

        outputValues.put(OUTPUT_POINT_ID, new PointData(projected));
        outputValues.put(OUTPUT_VECTOR_ID, projected);
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
        outputValues.put(OUTPUT_SIGNED_DISTANCE_ID, signedDistance);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        return new HashMap<>();
    }

    @Override
    public void setNodeState(Object state) {
        // stateless
    }

    private Vector3d resolvePoint(Object value) {
        Vector3d point = OrientationUtils.resolveVector(value);
        return OrientationUtils.isFinite(point) ? point : null;
    }
}

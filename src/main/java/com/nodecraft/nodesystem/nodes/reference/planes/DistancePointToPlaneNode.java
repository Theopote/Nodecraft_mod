package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.UUID;

@NodeInfo(
    id = "reference.planes.distance_point_to_plane",
    displayName = "Distance Point To Plane",
    description = "Measures the absolute and signed distance from a geometric point to a plane",
    category = "reference.planes",
    order = 3
)
public class DistancePointToPlaneNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_SIGNED_DISTANCE_ID = "output_signed_distance";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DistancePointToPlaneNode() {
        super(UUID.randomUUID(), "reference.planes.distance_point_to_plane");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Point to measure. Supports Point, Vector, Position, or Block Coordinate.",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Reference plane for the distance measurement",
            NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Absolute distance from the point to the plane", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIGNED_DISTANCE_ID, "Signed Distance",
            "Signed distance from the point to the plane", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when both point and plane inputs are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Distance Point To Plane";
    }

    @Override
    public String getDescription() {
        return "Measures the absolute and signed distance from a geometric point to a plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d point = PlaneUtils.resolvePoint(inputValues.get(INPUT_POINT_ID));
        Object planeObj = inputValues.get(INPUT_PLANE_ID);

        if (!PlaneUtils.isFinite(point) || !(planeObj instanceof PlaneData plane)) {
            outputValues.put(OUTPUT_DISTANCE_ID, Double.NaN);
            outputValues.put(OUTPUT_SIGNED_DISTANCE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double signedDistance = plane.signedDistanceTo(point);
        double distance = Math.abs(signedDistance);

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

}

package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PlaneUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.planes.distance_point_to_plane",
    displayName = "Distance Point To Plane",
    description = "Measures the absolute and signed distance from a geometric point to a plane",
    category = "reference.planes",
    order = 5
)
public class DistancePointToPlaneNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_SIGNED_DISTANCE_ID = "output_signed_distance";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public DistancePointToPlaneNode() {
        super(UUID.randomUUID(), "reference.planes.distance_point_to_plane");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Geometric point to measure",
            NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Reference plane for the distance measurement",
            NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Absolute distance from the point to the plane", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIGNED_DISTANCE_ID, "Signed Distance",
            "Signed distance from the point to the plane", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when both point and plane inputs are valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
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
        Object pointObj = inputValues.get(INPUT_POINT_ID);
        if (!(pointObj instanceof PointData)) {
            writeInvalid("Point must be a finite POINT");
            return;
        }
        Vector3d point = SpatialValueResolver.resolvePoint(pointObj);
        if (!PlaneUtils.isFinite(point)) {
            writeInvalid("Point must be a finite POINT");
            return;
        }

        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(planeObj instanceof PlaneData plane)) {
            writeInvalid("Plane input must be PLANE");
            return;
        }

        PlaneData canonical = plane.normalized();
        if (canonical == null) {
            writeInvalid("Plane must be finite with non-zero normal");
            return;
        }

        double signedDistance = canonical.signedDistanceTo(point);
        if (!Double.isFinite(signedDistance)) {
            writeInvalid("Signed distance is not finite");
            return;
        }

        outputValues.put(OUTPUT_DISTANCE_ID, Math.abs(signedDistance));
        outputValues.put(OUTPUT_SIGNED_DISTANCE_ID, signedDistance);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_DISTANCE_ID, Double.NaN);
        outputValues.put(OUTPUT_SIGNED_DISTANCE_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
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

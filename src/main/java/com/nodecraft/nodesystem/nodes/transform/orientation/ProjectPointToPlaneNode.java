package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.orientation.project_to_plane",
    displayName = "Project Point To Plane",
    description = "Projects a geometric point onto a plane and reports the projection distance",
    category = "transform.orientation",
    order = 0
)
public class ProjectPointToPlaneNode extends AbstractOrientationNode {

    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_SIGNED_DISTANCE_ID = "output_signed_distance";

    public ProjectPointToPlaneNode() {
        super("transform.orientation.project_to_plane");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Point to project onto the plane",
            NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Target plane for projection",
            NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Projected Point",
            "Projected point on the target plane", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Absolute distance from the input point to the plane", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIGNED_DISTANCE_ID, "Signed Distance",
            "Signed distance from the input point to the plane", NodeDataType.DOUBLE, this));
        addValidAndErrorOutputs();
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
        Vector3d point = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_POINT_ID));
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        PlaneData plane = planeObj instanceof PlaneData p ? p : null;

        OrientationUtils.PointProjection projection = OrientationUtils.projectPoint(plane, point);
        if (projection == null) {
            writeInvalid("Missing or invalid point or plane");
            return;
        }

        outputValues.put(OUTPUT_POINT_ID, new PointData(projection.projected()));
        outputValues.put(OUTPUT_DISTANCE_ID, projection.distance());
        outputValues.put(OUTPUT_SIGNED_DISTANCE_ID, projection.signedDistance());
        markSuccess();
    }

    @Override
    public Object getNodeState() {
        return new HashMap<>();
    }

    @Override
    public void setNodeState(Object state) {
        // stateless
    }

    private void writeInvalid(String error) {
        putNullOutputs(OUTPUT_POINT_ID);
        putDoubleOutputs(Double.NaN, OUTPUT_DISTANCE_ID, OUTPUT_SIGNED_DISTANCE_ID);
        markInvalid(error);
    }
}

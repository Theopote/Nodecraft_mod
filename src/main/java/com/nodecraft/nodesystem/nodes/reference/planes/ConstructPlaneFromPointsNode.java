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
    id = "reference.planes.plane_from_points",
    displayName = "Construct Plane From Points",
    description = "Constructs a plane from three non-collinear points",
    category = "reference.planes",
    order = 2
)
public class ConstructPlaneFromPointsNode extends BaseNode {

    private static final String INPUT_POINT_A_ID = "input_point_a";
    private static final String INPUT_POINT_B_ID = "input_point_b";
    private static final String INPUT_POINT_C_ID = "input_point_c";

    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ConstructPlaneFromPointsNode() {
        super(UUID.randomUUID(), "reference.planes.plane_from_points");

        addInputPort(new BasePort(INPUT_POINT_A_ID, "Point A", "First geometric point on the plane", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_POINT_B_ID, "Point B", "Second geometric point on the plane", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_POINT_C_ID, "Point C", "Third geometric point on the plane", NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Constructed plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the three points formed a valid plane", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d a = resolveRequiredPoint(INPUT_POINT_A_ID, "Point A");
        if (a == null) {
            return;
        }
        Vector3d b = resolveRequiredPoint(INPUT_POINT_B_ID, "Point B");
        if (b == null) {
            return;
        }
        Vector3d c = resolveRequiredPoint(INPUT_POINT_C_ID, "Point C");
        if (c == null) {
            return;
        }

        PlaneData plane = PlaneUtils.fromThreePoints(a, b, c);
        if (plane == null) {
            writeInvalid("Points are collinear or nearly collinear");
            return;
        }
        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private @Nullable Vector3d resolveRequiredPoint(String portId, String label) {
        Object raw = inputValues.get(portId);
        if (!(raw instanceof PointData)) {
            writeInvalid(label + " must be a finite POINT");
            return null;
        }
        Vector3d point = SpatialValueResolver.resolvePoint(raw);
        if (!PlaneUtils.isFinite(point)) {
            writeInvalid(label + " must be a finite POINT");
            return null;
        }
        return point;
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_PLANE_ID, null);
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

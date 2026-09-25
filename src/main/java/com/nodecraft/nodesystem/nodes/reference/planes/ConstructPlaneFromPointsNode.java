package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
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

    public ConstructPlaneFromPointsNode() {
        super(UUID.randomUUID(), "reference.planes.plane_from_points");

        addInputPort(new BasePort(INPUT_POINT_A_ID, "Point A", "First geometric point on the plane", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_POINT_B_ID, "Point B", "Second geometric point on the plane", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_POINT_C_ID, "Point C", "Third geometric point on the plane", NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Constructed plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the three points formed a valid plane", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d av = PlaneUtils.resolvePoint(inputValues.get(INPUT_POINT_A_ID));
        Vector3d bv = PlaneUtils.resolvePoint(inputValues.get(INPUT_POINT_B_ID));
        Vector3d cv = PlaneUtils.resolvePoint(inputValues.get(INPUT_POINT_C_ID));

        PlaneData plane = null;
        boolean valid = false;

        if (PlaneUtils.isFinite(av) && PlaneUtils.isFinite(bv) && PlaneUtils.isFinite(cv)) {
            Vector3d ab = new Vector3d(bv).sub(av);
            Vector3d ac = new Vector3d(cv).sub(av);
            Vector3d cross = ab.cross(ac, new Vector3d());

            if (cross.lengthSquared() > 1e-9) {
                plane = new PlaneData(av, bv, cv);
                valid = true;
            }
        }

        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_VALID_ID, valid);
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

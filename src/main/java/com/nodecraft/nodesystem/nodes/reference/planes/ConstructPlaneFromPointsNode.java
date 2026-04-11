package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.UUID;

@NodeInfo(
    id = "reference.planes.plane_from_points",
    displayName = "Construct Plane From Points",
    description = "Constructs a plane from three non-collinear points",
    category = "reference.planes"
)
public class ConstructPlaneFromPointsNode extends BaseNode {

    private static final String INPUT_POINT_A_ID = "input_point_a";
    private static final String INPUT_POINT_B_ID = "input_point_b";
    private static final String INPUT_POINT_C_ID = "input_point_c";

    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_NORMAL_ID = "output_normal";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ConstructPlaneFromPointsNode() {
        super(UUID.randomUUID(), "reference.planes.plane_from_points");

        addInputPort(new BasePort(INPUT_POINT_A_ID, "Point A", "First point on the plane", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_POINT_B_ID, "Point B", "Second point on the plane", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_POINT_C_ID, "Point C", "Third point on the plane", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Constructed plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Plane normal vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the three points formed a valid plane", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a plane from three non-collinear points";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object aObj = inputValues.get(INPUT_POINT_A_ID);
        Object bObj = inputValues.get(INPUT_POINT_B_ID);
        Object cObj = inputValues.get(INPUT_POINT_C_ID);

        PlaneData plane = null;
        Vector3d normal = null;
        boolean valid = false;

        if (aObj instanceof BlockPos a && bObj instanceof BlockPos b && cObj instanceof BlockPos c) {
            Vector3d av = toVector(a);
            Vector3d bv = toVector(b);
            Vector3d cv = toVector(c);

            Vector3d ab = new Vector3d(bv).sub(av);
            Vector3d ac = new Vector3d(cv).sub(av);
            Vector3d cross = ab.cross(ac, new Vector3d());

            if (cross.lengthSquared() > 1e-9) {
                normal = cross.normalize();
                plane = new PlaneData(av, bv, cv);
                valid = true;
            }
        }

        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_NORMAL_ID, normal);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private Vector3d toVector(BlockPos pos) {
        return new Vector3d(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public Object getNodeState() {
        return new HashMap<String, Object>();
    }

    @Override
    public void setNodeState(Object state) {
        // stateless
    }
}

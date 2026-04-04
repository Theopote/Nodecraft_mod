package com.nodecraft.nodesystem.nodes.math.vector;

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
    id = "math.vector.construct_plane",
    displayName = "Construct Plane",
    description = "Constructs a plane from an origin point and a normal vector",
    category = "math.vector"
)
public class ConstructPlaneNode extends BaseNode {

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_NORMAL_ID = "input_normal";

    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_NORMALIZED_NORMAL_ID = "output_normalized_normal";

    public ConstructPlaneNode() {
        super(UUID.randomUUID(), "math.vector.construct_plane");

        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "A point on the plane", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_NORMAL_ID, "Normal", "Plane normal vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Constructed plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_NORMALIZED_NORMAL_ID, "Normalized Normal", "Normalized normal vector", NodeDataType.VECTOR, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a plane from an origin point and a normal vector";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object originObj = inputValues.get(INPUT_ORIGIN_ID);
        Object normalObj = inputValues.get(INPUT_NORMAL_ID);

        PlaneData plane = null;
        Vector3d normalizedNormal = null;

        if (originObj instanceof BlockPos origin && normalObj instanceof Vector3d normal && normal.lengthSquared() > 1e-9) {
            Vector3d originVec = new Vector3d(origin.getX(), origin.getY(), origin.getZ());
            normalizedNormal = new Vector3d(normal).normalize();
            plane = new PlaneData(originVec, normalizedNormal);
        }

        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_NORMALIZED_NORMAL_ID, normalizedNormal);
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

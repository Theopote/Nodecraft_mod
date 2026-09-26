package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PlaneUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.planes.construct_plane",
    displayName = "Construct Plane",
    description = "Constructs a plane from an origin point and a normal vector",
    category = "reference.planes",
    order = 1
)
public class ConstructPlaneNode extends BaseNode {

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_NORMAL_ID = "input_normal";

    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ConstructPlaneNode() {
        super(UUID.randomUUID(), "reference.planes.construct_plane");

        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "A geometric point on the plane", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_NORMAL_ID, "Normal", "Plane normal vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Constructed plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the origin and normal formed a valid plane", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d origin = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_ORIGIN_ID));
        Vector3d normal = SpatialValueResolver.resolveVector(inputValues.get(INPUT_NORMAL_ID));

        PlaneData plane = PlaneUtils.fromOriginNormal(origin, normal);
        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_VALID_ID, plane != null);
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

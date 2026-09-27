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
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ConstructPlaneNode() {
        super(UUID.randomUUID(), "reference.planes.construct_plane");

        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "A geometric point on the plane", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_NORMAL_ID, "Normal", "Plane normal vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Constructed plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the origin and normal formed a valid plane", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object originObj = inputValues.get(INPUT_ORIGIN_ID);
        if (!(originObj instanceof PointData)) {
            writeInvalid("Origin must be a finite POINT");
            return;
        }
        Vector3d origin = SpatialValueResolver.resolvePoint(originObj);
        if (!PlaneUtils.isFinite(origin)) {
            writeInvalid("Origin must be a finite POINT");
            return;
        }

        Object normalObj = inputValues.get(INPUT_NORMAL_ID);
        if (!(normalObj instanceof Vector3d)) {
            writeInvalid("Normal must be a finite non-zero VECTOR");
            return;
        }
        Vector3d normal = SpatialValueResolver.resolveVector(normalObj);
        if (!PlaneUtils.isUsableNormal(normal)) {
            writeInvalid("Normal must be a finite non-zero VECTOR");
            return;
        }

        PlaneData plane = PlaneUtils.fromOriginNormal(origin, normal);
        if (plane == null) {
            writeInvalid("Plane construction failed");
            return;
        }
        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
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

package com.nodecraft.nodesystem.nodes.math.vector;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
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

        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "A point on the plane. Supports Point, Vector, or Block Coordinate.", NodeDataType.ANY, this));
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

        Vector3d originVec = resolvePoint(originObj);
        if (originVec != null && normalObj instanceof Vector3d normal && normal.lengthSquared() > 1e-9) {
            normalizedNormal = new Vector3d(normal).normalize();
            plane = new PlaneData(new Vector3d(originVec), normalizedNormal);
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

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }
}

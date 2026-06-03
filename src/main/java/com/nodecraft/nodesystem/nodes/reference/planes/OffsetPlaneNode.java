package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "reference.planes.offset_plane",
    displayName = "Offset Plane",
    description = "Offsets a plane along its normal by a signed distance",
    category = "reference.planes",
    order = 5
)
public class OffsetPlaneNode extends BaseNode {

    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_ORIGIN_ID = "output_origin";
    private static final String OUTPUT_NORMAL_ID = "output_normal";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public OffsetPlaneNode() {
        super(UUID.randomUUID(), "reference.planes.offset_plane");
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Source plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Signed offset distance along the plane normal", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Offset plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_ORIGIN_ID, "Origin", "A point on the offset plane", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Offset plane normal", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when offset succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Offsets a plane along its normal by a signed distance";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(planeObj instanceof PlaneData plane)) {
            writeEmpty();
            return;
        }

        double distance = inputValues.get(INPUT_DISTANCE_ID) instanceof Number n ? n.doubleValue() : 0.0d;
        if (!Double.isFinite(distance)) {
            writeEmpty();
            return;
        }
        Vector3d normal = plane.getNormal();
        if (!PlaneUtils.isUsableNormal(normal)) {
            writeEmpty();
            return;
        }
        normal.normalize();
        Vector3d origin = plane.getPoint().add(new Vector3d(normal).mul(distance));
        PlaneData offsetPlane = new PlaneData(origin, normal);

        outputValues.put(OUTPUT_PLANE_ID, offsetPlane);
        outputValues.put(OUTPUT_ORIGIN_ID, origin);
        outputValues.put(OUTPUT_NORMAL_ID, normal);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmpty() {
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_ORIGIN_ID, null);
        outputValues.put(OUTPUT_NORMAL_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

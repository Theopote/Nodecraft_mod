package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PlaneUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.planes.offset_plane",
    displayName = "Offset Plane",
    description = "Offsets a plane along its normal by a signed distance",
    category = "reference.planes",
    order = 4
)
public class OffsetPlaneNode extends BaseNode {

    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public OffsetPlaneNode() {
        super(UUID.randomUUID(), "reference.planes.offset_plane");
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Source plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Signed offset distance along the plane normal", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Offset plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when offset succeeded", NodeDataType.BOOLEAN, this));
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

        PlaneData canonical = plane.normalized();
        if (canonical == null) {
            writeEmpty();
            return;
        }

        Vector3d normal = canonical.getNormal();
        Vector3d newOrigin = canonical.getPoint().add(new Vector3d(normal).mul(distance));
        PlaneData offset = PlaneUtils.fromOriginNormal(newOrigin, normal);
        if (offset == null) {
            writeEmpty();
            return;
        }

        outputValues.put(OUTPUT_PLANE_ID, offset);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmpty() {
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

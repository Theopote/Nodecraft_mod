package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PlaneUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
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
    private static final String OUTPUT_ERROR_ID = "output_error";

    public OffsetPlaneNode() {
        super(UUID.randomUUID(), "reference.planes.offset_plane");
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Source plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Signed offset distance along the plane normal", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Offset plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when offset succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(planeObj instanceof PlaneData plane)) {
            writeInvalid("Plane input must be PLANE");
            return;
        }

        Double distance = OptionalPortDrive.resolveOptionalDouble(this, INPUT_DISTANCE_ID, 0.0d);
        if (distance == null) {
            writeInvalid("Distance connected but invalid");
            return;
        }

        if (!plane.isCanonical()) {
            writeInvalid("Plane must be canonical");
            return;
        }

        Vector3d normal = plane.getNormal();
        Vector3d offsetVec = VectorUtils.safeScale(normal, distance);
        Vector3d newOrigin = VectorUtils.safeAdd(plane.getPoint(), offsetVec);
        if (newOrigin == null) {
            writeInvalid("Offset plane origin became non-finite");
            return;
        }
        PlaneData offset = PlaneUtils.fromOriginNormal(newOrigin, normal);
        if (offset == null) {
            writeInvalid("Plane construction failed");
            return;
        }

        outputValues.put(OUTPUT_PLANE_ID, offset);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

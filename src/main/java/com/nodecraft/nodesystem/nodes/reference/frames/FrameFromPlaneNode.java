package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.frames.frame_from_plane",
    displayName = "Frame From Plane",
    description = "Builds a right-handed orthonormal FRAME on a plane (Z = normal, X from hint)",
    category = "reference.frames",
    order = 4
)
public class FrameFromPlaneNode extends BaseNode {

    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_X_HINT_ID = "input_x_hint";

    private static final String OUTPUT_FRAME_ID = "output_frame";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public FrameFromPlaneNode() {
        super(UUID.randomUUID(), "reference.frames.frame_from_plane");

        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Source plane (origin + normal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_X_HINT_ID, "X Hint", "Optional in-plane X direction hint", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "Orthonormal frame on the plane", NodeDataType.FRAME, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame construction succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(planeObj instanceof PlaneData plane)) {
            writeInvalid();
            return;
        }

        Vector3d xHint = SpatialValueResolver.resolveVector(inputValues.get(INPUT_X_HINT_ID));
        FrameData frame = FrameUtils.fromPlane(plane, xHint);
        if (frame == null) {
            writeInvalid();
            return;
        }
        outputValues.put(OUTPUT_FRAME_ID, frame);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_FRAME_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

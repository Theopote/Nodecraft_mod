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
import com.nodecraft.nodesystem.util.OptionalPortDrive;
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
    private static final String OUTPUT_ERROR_ID = "output_error";

    public FrameFromPlaneNode() {
        super(UUID.randomUUID(), "reference.frames.frame_from_plane");

        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Source plane (origin + normal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_X_HINT_ID, "X Hint", "Optional in-plane X direction hint", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "Orthonormal frame on the plane", NodeDataType.FRAME, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame construction succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Builds a right-handed orthonormal FRAME on a plane (Z = normal, X from hint)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(planeObj instanceof PlaneData plane)) {
            writeInvalid("Plane input must be PLANE");
            return;
        }

        FrameData frame;
        if (OptionalPortDrive.isConnected(this, INPUT_X_HINT_ID)) {
            Vector3d xHint = OptionalPortDrive.resolveOptionalVector(this, INPUT_X_HINT_ID, null);
            if (xHint == null) {
                writeInvalid("X Hint connected but invalid");
                return;
            }
            frame = FrameUtils.fromPlaneRequireHint(plane, xHint);
            if (frame == null) {
                writeInvalid("X Hint has zero length when projected onto the plane");
                return;
            }
        } else {
            frame = FrameUtils.fromPlane(plane, null);
            if (frame == null) {
                writeInvalid("Could not build orthonormal frame on plane");
                return;
            }
        }

        outputValues.put(OUTPUT_FRAME_ID, frame);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_FRAME_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

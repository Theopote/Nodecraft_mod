package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.FrameUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.frames.construct_frame",
    displayName = "Construct Frame",
    description = "Builds an orthonormal right-handed FRAME from origin, X axis, and Y axis (Z = X × Y)",
    category = "reference.frames",
    order = 3
)
public class ConstructFrameNode extends BaseNode {

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_X_AXIS_ID = "input_x_axis";
    private static final String INPUT_Y_AXIS_ID = "input_y_axis";

    private static final String OUTPUT_FRAME_ID = "output_frame";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ConstructFrameNode() {
        super(UUID.randomUUID(), "reference.frames.construct_frame");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Optional frame origin; defaults to world origin (0,0,0)", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_X_AXIS_ID, "X Axis", "Optional X direction; defaults to world +X", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Y_AXIS_ID, "Y Axis", "Optional Y direction; defaults to world +Y", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "Orthonormal right-handed frame", NodeDataType.FRAME, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame axes are usable", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Builds an orthonormal right-handed FRAME from origin, X axis, and Y axis (Z = X × Y)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean originConnected = inputValues.get(INPUT_ORIGIN_ID) != null;
        boolean xConnected = inputValues.get(INPUT_X_AXIS_ID) != null;
        boolean yConnected = inputValues.get(INPUT_Y_AXIS_ID) != null;

        Vector3d origin = FrameUtils.resolvePoint(inputValues.get(INPUT_ORIGIN_ID));
        Vector3d x = FrameUtils.resolveVector(inputValues.get(INPUT_X_AXIS_ID));
        Vector3d y = FrameUtils.resolveVector(inputValues.get(INPUT_Y_AXIS_ID));

        if (originConnected && origin == null) {
            writeInvalid();
            return;
        }
        if (xConnected && !FrameUtils.isUsableAxis(x)) {
            writeInvalid();
            return;
        }
        if (yConnected && !FrameUtils.isUsableAxis(y)) {
            writeInvalid();
            return;
        }

        if (origin == null) {
            origin = new Vector3d();
        }
        if (x == null) {
            x = new Vector3d(1, 0, 0);
        }
        if (y == null) {
            y = new Vector3d(0, 1, 0);
        }

        if (xConnected && yConnected && FrameUtils.areParallel(x, y)) {
            writeInvalid();
            return;
        }

        FrameData frame = FrameData.orthonormal(origin, x, y, null);
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

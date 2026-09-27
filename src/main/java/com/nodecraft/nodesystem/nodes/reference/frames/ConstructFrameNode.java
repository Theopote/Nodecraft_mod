package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
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

    private static final Vector3d DEFAULT_ORIGIN = new Vector3d();
    private static final Vector3d DEFAULT_X = new Vector3d(1, 0, 0);
    private static final Vector3d DEFAULT_Y = new Vector3d(0, 1, 0);

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_X_AXIS_ID = "input_x_axis";
    private static final String INPUT_Y_AXIS_ID = "input_y_axis";

    private static final String OUTPUT_FRAME_ID = "output_frame";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ConstructFrameNode() {
        super(UUID.randomUUID(), "reference.frames.construct_frame");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Optional frame origin; defaults to world origin (0,0,0)", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_X_AXIS_ID, "X Axis", "Optional X direction; defaults to world +X", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Y_AXIS_ID, "Y Axis", "Optional Y direction; defaults to world +Y", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "Orthonormal right-handed frame", NodeDataType.FRAME, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame axes are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Builds an orthonormal right-handed FRAME from origin, X axis, and Y axis (Z = X × Y)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d origin = OptionalPortDrive.resolveOptionalPoint(this, INPUT_ORIGIN_ID, DEFAULT_ORIGIN);
        if (origin == null) {
            writeInvalid("Origin connected but invalid");
            return;
        }

        Vector3d x = OptionalPortDrive.resolveOptionalVector(this, INPUT_X_AXIS_ID, DEFAULT_X);
        if (x == null) {
            writeInvalid("X Axis connected but invalid");
            return;
        }
        if (!FrameUtils.isUsableAxis(x)) {
            writeInvalid("X Axis must be finite and non-zero");
            return;
        }

        Vector3d y = OptionalPortDrive.resolveOptionalVector(this, INPUT_Y_AXIS_ID, DEFAULT_Y);
        if (y == null) {
            writeInvalid("Y Axis connected but invalid");
            return;
        }
        if (!FrameUtils.isUsableAxis(y)) {
            writeInvalid("Y Axis must be finite and non-zero");
            return;
        }

        boolean xConnected = OptionalPortDrive.isConnected(this, INPUT_X_AXIS_ID);
        boolean yConnected = OptionalPortDrive.isConnected(this, INPUT_Y_AXIS_ID);
        if (xConnected && yConnected && FrameUtils.areParallel(x, y)) {
            writeInvalid("Connected X and Y axes must not be parallel");
            return;
        }

        FrameData frame = FrameData.orthonormal(origin, x, y, null);
        if (frame == null) {
            writeInvalid("Could not build orthonormal frame from axes");
            return;
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

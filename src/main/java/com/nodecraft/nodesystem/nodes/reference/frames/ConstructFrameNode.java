package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.frames.construct_frame",
    displayName = "Construct Frame",
    description = "Builds an orthonormal right-handed FRAME from origin, X axis, and Y axis (Z = X × Y)",
    category = "reference.frames",
    order = 4
)
public class ConstructFrameNode extends BaseNode {

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_X_AXIS_ID = "input_x_axis";
    private static final String INPUT_Y_AXIS_ID = "input_y_axis";

    private static final String OUTPUT_FRAME_ID = "output_frame";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ConstructFrameNode() {
        super(UUID.randomUUID(), "reference.frames.construct_frame");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Frame origin point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_X_AXIS_ID, "X Axis", "Frame X axis direction", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Y_AXIS_ID, "Y Axis", "Frame Y axis direction", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "Orthonormal right-handed frame", NodeDataType.FRAME, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame axes are usable", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Builds an orthonormal right-handed FRAME from origin, X axis, and Y axis (Z = X × Y)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d origin = FrameUtils.resolvePoint(inputValues.get(INPUT_ORIGIN_ID));
        Vector3d x = FrameUtils.resolveVector(inputValues.get(INPUT_X_AXIS_ID));
        Vector3d y = FrameUtils.resolveVector(inputValues.get(INPUT_Y_AXIS_ID));

        if (origin == null) {
            origin = new Vector3d();
        }
        if (x == null) {
            x = new Vector3d(1, 0, 0);
        }
        if (y == null) {
            y = new Vector3d(0, 1, 0);
        }

        FrameData frame = FrameData.orthonormal(origin, x, y, null);
        if (frame == null) {
            outputValues.put(OUTPUT_FRAME_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }
        outputValues.put(OUTPUT_FRAME_ID, frame);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

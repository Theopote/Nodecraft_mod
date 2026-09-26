package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.frames.deconstruct_frame",
    displayName = "Deconstruct Frame",
    description = "Splits a FRAME into origin point, X/Y/Z axes, and plane",
    category = "reference.frames",
    order = 6
)
public class DeconstructFrameNode extends BaseNode {

    private static final String INPUT_FRAME_ID = "input_frame";

    private static final String OUTPUT_ORIGIN_ID = "output_origin";
    private static final String OUTPUT_X_AXIS_ID = "output_x_axis";
    private static final String OUTPUT_Y_AXIS_ID = "output_y_axis";
    private static final String OUTPUT_Z_AXIS_ID = "output_z_axis";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructFrameNode() {
        super(UUID.randomUUID(), "reference.frames.deconstruct_frame");
        addInputPort(new BasePort(INPUT_FRAME_ID, "Frame", "Frame to deconstruct", NodeDataType.FRAME, this));

        addOutputPort(new BasePort(OUTPUT_ORIGIN_ID, "Origin", "Frame origin point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_X_AXIS_ID, "X Axis", "Frame X axis", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Y_AXIS_ID, "Y Axis", "Frame Y axis", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Z_AXIS_ID, "Z Axis", "Frame Z axis", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Plane from origin + Z axis", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame input is a usable orthonormal frame", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Splits a FRAME into origin point, X/Y/Z axes, and plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object frameObj = inputValues.get(INPUT_FRAME_ID);
        if (!(frameObj instanceof FrameData frame)) {
            writeInvalid();
            return;
        }

        FrameData canonical = frame.orthonormalized();
        if (canonical == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_ORIGIN_ID, canonical.getOriginPoint());
        outputValues.put(OUTPUT_X_AXIS_ID, canonical.getXAxis());
        outputValues.put(OUTPUT_Y_AXIS_ID, canonical.getYAxis());
        outputValues.put(OUTPUT_Z_AXIS_ID, canonical.getZAxis());
        outputValues.put(OUTPUT_PLANE_ID, canonical.toPlane());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_ORIGIN_ID, null);
        outputValues.put(OUTPUT_X_AXIS_ID, null);
        outputValues.put(OUTPUT_Y_AXIS_ID, null);
        outputValues.put(OUTPUT_Z_AXIS_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

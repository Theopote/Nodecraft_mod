package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.FrameUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.frames.frame_from_face",
    displayName = "Face Center Frame",
    description = "Builds an orthonormal frame at the center of a box face",
    category = "reference.frames",
    order = 0
)
public class FaceCenterFrameNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";

    private static final String OUTPUT_FRAME_ID = "output_frame";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public FaceCenterFrameNode() {
        super(UUID.randomUUID(), "reference.frames.frame_from_face");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "The box face used to build the local frame", NodeDataType.BOX_FACE, this));

        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "Oriented frame at the face center", NodeDataType.FRAME, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Face center point used as frame origin", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether a valid face frame could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Builds an orthonormal frame at the center of a box face";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        if (!(faceObj instanceof BoxFaceData face)) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> corners = face.getCorners();
        if (corners.size() < 4) {
            writeEmptyOutputs();
            return;
        }

        Vector3d xAxis = FrameUtils.normalizedDirection(corners.get(0), corners.get(1));
        Vector3d yAxis = FrameUtils.normalizedDirection(corners.get(0), corners.get(3));
        Vector3d center = face.getCenter();
        Vector3d zAxis = new Vector3d(face.getNormal());

        if (xAxis == null || yAxis == null || !FrameUtils.isFinite(center) || !FrameUtils.isUsableAxis(zAxis)) {
            writeEmptyOutputs();
            return;
        }
        zAxis.normalize();

        FrameData frame = FrameData.orthonormal(center, xAxis, yAxis, zAxis);
        if (frame == null) {
            writeEmptyOutputs();
            return;
        }

        outputValues.put(OUTPUT_FRAME_ID, frame);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_FRAME_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

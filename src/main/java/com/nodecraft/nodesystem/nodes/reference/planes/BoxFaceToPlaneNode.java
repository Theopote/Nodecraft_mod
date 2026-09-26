package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.planes.box_face_plane",
    displayName = "Box Face To Plane",
    description = "Converts a box face into its supporting plane",
    category = "reference.planes",
    order = 3
)
public class BoxFaceToPlaneNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";

    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public BoxFaceToPlaneNode() {
        super(UUID.randomUUID(), "reference.planes.box_face_plane");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "The box face to convert", NodeDataType.BOX_FACE, this));

        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Plane that contains the box face", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether a valid face was provided", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);

        if (!(faceObj instanceof BoxFaceData face)) {
            writeInvalid();
            return;
        }

        PlaneData canonical = face.getPlane().normalized();
        if (canonical == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_PLANE_ID, canonical);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

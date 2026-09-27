package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BoxFaceValidator;
import com.nodecraft.nodesystem.util.PlaneUtils;
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
    private static final String OUTPUT_ERROR_ID = "output_error";

    public BoxFaceToPlaneNode() {
        super(UUID.randomUUID(), "reference.planes.box_face_plane");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "The box face to convert", NodeDataType.BOX_FACE, this));

        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Plane that contains the box face", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether a valid face was provided", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);

        if (!(faceObj instanceof BoxFaceData face)) {
            writeInvalid("Face input must be BOX_FACE");
            return;
        }

        String faceError = BoxFaceValidator.validate(face);
        if (faceError != null) {
            writeInvalid(faceError);
            return;
        }

        PlaneData canonical = PlaneUtils.fromOriginNormal(face.getCenter(), face.getNormal());
        if (canonical == null) {
            writeInvalid("Plane construction failed");
            return;
        }

        outputValues.put(OUTPUT_PLANE_ID, canonical);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

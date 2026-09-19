package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.planes.block_face_plane",
    displayName = "Box Face To Plane",
    description = "Explicitly converts a box face into its supporting plane and related face frame data",
    category = "reference.planes",
    order = 4
)
public class BoxFaceToPlaneNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";

    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_NORMAL_ID = "output_normal";
    private static final String OUTPUT_NAME_ID = "output_name";
    private static final String OUTPUT_INDEX_ID = "output_index";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public BoxFaceToPlaneNode() {
        super(UUID.randomUUID(), "reference.planes.block_face_plane");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "The box face to convert", NodeDataType.BOX_FACE, this));

        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Plane that contains the box face", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Center point of the face", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Face normal vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Face name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_INDEX_ID, "Index", "Face index", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether a valid face was provided", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Explicitly converts a box face into its supporting plane and related face frame data";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);

        if (!(faceObj instanceof BoxFaceData face)) {
            outputValues.put(OUTPUT_PLANE_ID, null);
            outputValues.put(OUTPUT_CENTER_ID, null);
            outputValues.put(OUTPUT_NORMAL_ID, null);
            outputValues.put(OUTPUT_NAME_ID, null);
            outputValues.put(OUTPUT_INDEX_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_PLANE_ID, face.getPlane());
        outputValues.put(OUTPUT_CENTER_ID, face.getCenter());
        outputValues.put(OUTPUT_NORMAL_ID, face.getNormal());
        outputValues.put(OUTPUT_NAME_ID, face.getName());
        outputValues.put(OUTPUT_INDEX_ID, face.getIndex());
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

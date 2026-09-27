package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BoxFaceValidator;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.basic_transforms.offset_face",
    displayName = "Offset Box Face",
    description = "Offsets a box face along its normal without modifying the source box geometry",
    category = "transform.basic_transforms",
    order = 7
)
public class OffsetBoxFaceNode extends AbstractBasicTransformNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_FACE_ID = "output_face";

    public OffsetBoxFaceNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.offset_face");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "The box face to offset", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Required signed offset distance along the face normal", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FACE_ID, "Face", "Offset face", NodeDataType.BOX_FACE, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Offsets a box face along its normal without modifying the source box geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        if (!(faceObj instanceof BoxFaceData face)) {
            writeInvalid("Box face is required");
            return;
        }

        String faceError = BoxFaceValidator.validate(face);
        if (faceError != null) {
            writeInvalid(faceError);
            return;
        }

        Object distanceObj = inputValues.get(INPUT_DISTANCE_ID);
        if (!(distanceObj instanceof Number number)) {
            writeInvalid("Distance is required");
            return;
        }

        double distance = number.doubleValue();
        if (!Double.isFinite(distance)) {
            writeInvalid("Distance must be finite");
            return;
        }

        Vector3d normal = new Vector3d(face.getNormal()).normalize();
        Vector3d offset = new Vector3d(normal).mul(distance);

        List<Vector3d> sourceCorners = face.getCorners();
        List<Vector3d> shiftedCorners = new ArrayList<>(sourceCorners.size());
        for (Vector3d corner : sourceCorners) {
            shiftedCorners.add(new Vector3d(corner).add(offset));
        }

        Vector3d shiftedCenter = new Vector3d(face.getCenter()).add(offset);
        outputValues.put(OUTPUT_FACE_ID, new BoxFaceData(
            face.getIndex(),
            face.getName(),
            face.getCornerIndices(),
            shiftedCorners,
            shiftedCenter,
            normal
        ));
        markSuccess();
    }

    private void writeInvalid(String error) {
        putNullOutputs(OUTPUT_FACE_ID);
        markInvalid(error);
    }
}

package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.VectorUtils;
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
public class OffsetBoxFaceNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_FACE_ID = "output_face";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public OffsetBoxFaceNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.offset_face");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "The box face to offset", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Required signed offset distance along the face normal", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FACE_ID, "Face", "Offset face", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether a valid offset face was produced", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Offsets a box face along its normal without modifying the source box geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        Object distanceObj = inputValues.get(INPUT_DISTANCE_ID);

        if (!(faceObj instanceof BoxFaceData face) || !(distanceObj instanceof Number number)) {
            writeInvalid();
            return;
        }

        double distance = number.doubleValue();
        Vector3d normal = new Vector3d(face.getNormal());
        if (!Double.isFinite(distance) || !VectorUtils.isFinite(normal) || !VectorUtils.isNonZero(normal)) {
            writeInvalid();
            return;
        }
        normal.normalize();
        Vector3d offset = new Vector3d(normal).mul(distance);

        List<Vector3d> sourceCorners = face.getCorners();
        if (sourceCorners.size() < 3) {
            writeInvalid();
            return;
        }

        List<Vector3d> shiftedCorners = new ArrayList<>(sourceCorners.size());
        for (Vector3d corner : sourceCorners) {
            if (!VectorUtils.isFinite(corner)) {
                writeInvalid();
                return;
            }
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
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_FACE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

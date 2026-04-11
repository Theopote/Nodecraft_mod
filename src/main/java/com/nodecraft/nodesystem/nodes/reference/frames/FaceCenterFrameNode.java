package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "reference.frames.frame_from_face",
    displayName = "Face Center Frame",
    description = "Builds a local frame at the center of a box face using the face plane and boundary directions",
    category = "reference.frames"
)
public class FaceCenterFrameNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";

    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_X_AXIS_ID = "output_x_axis";
    private static final String OUTPUT_Y_AXIS_ID = "output_y_axis";
    private static final String OUTPUT_Z_AXIS_ID = "output_z_axis";
    private static final String OUTPUT_NORMAL_ID = "output_normal";
    private static final String OUTPUT_CORNER_INDICES_ID = "output_corner_indices";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public FaceCenterFrameNode() {
        super(UUID.randomUUID(), "reference.frames.frame_from_face");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "The box face used to build the local frame", NodeDataType.BOX_FACE, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Face center point used as frame origin", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Supporting plane of the face", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_X_AXIS_ID, "X Axis", "First in-plane frame axis derived from the face boundary", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Y_AXIS_ID, "Y Axis", "Second in-plane frame axis derived from the face boundary", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Z_AXIS_ID, "Z Axis", "Frame Z axis aligned with the face normal", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Face normal vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_CORNER_INDICES_ID, "Corner Indices", "Corner indices that define the face winding", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether a valid face frame could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Builds a local frame at the center of a box face using the face plane and boundary directions";
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

        Vector3d xAxis = direction(corners.get(0), corners.get(1));
        Vector3d yAxis = direction(corners.get(0), corners.get(3));
        Vector3d zAxis = new Vector3d(face.getNormal());

        if (xAxis == null || yAxis == null || zAxis.lengthSquared() < 1.0e-12) {
            writeEmptyOutputs();
            return;
        }

        zAxis.normalize();

        outputValues.put(OUTPUT_CENTER_ID, face.getCenter());
        outputValues.put(OUTPUT_PLANE_ID, face.getPlane());
        outputValues.put(OUTPUT_X_AXIS_ID, xAxis);
        outputValues.put(OUTPUT_Y_AXIS_ID, yAxis);
        outputValues.put(OUTPUT_Z_AXIS_ID, zAxis);
        outputValues.put(OUTPUT_NORMAL_ID, face.getNormal());
        outputValues.put(OUTPUT_CORNER_INDICES_ID, face.getCornerIndices());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_X_AXIS_ID, null);
        outputValues.put(OUTPUT_Y_AXIS_ID, null);
        outputValues.put(OUTPUT_Z_AXIS_ID, null);
        outputValues.put(OUTPUT_NORMAL_ID, null);
        outputValues.put(OUTPUT_CORNER_INDICES_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d direction(Vector3d from, Vector3d to) {
        Vector3d axis = new Vector3d(to).sub(from);
        if (axis.lengthSquared() < 1.0e-12) {
            return null;
        }
        return axis.normalize();
    }
}

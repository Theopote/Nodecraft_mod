package com.nodecraft.nodesystem.nodes.spatial.analysis;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "spatial.analysis.deconstruct_box_geometry",
    displayName = "Deconstruct Box Geometry",
    description = "Extracts center, half extents, orientation, corners, and faces from box geometry",
    category = "spatial.analysis"
)
public class DeconstructBoxGeometryNode extends BaseNode {

    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";

    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_HALF_EXTENTS_ID = "output_half_extents";
    private static final String OUTPUT_IS_ORIENTED_ID = "output_is_oriented";
    private static final String OUTPUT_CORNERS_ID = "output_corners";
    private static final String OUTPUT_CORNER_NAMES_ID = "output_corner_names";
    private static final String OUTPUT_FACES_ID = "output_faces";
    private static final String OUTPUT_FACE_NAMES_ID = "output_face_names";
    private static final String OUTPUT_CORNER_COUNT_ID = "output_corner_count";
    private static final String OUTPUT_FACE_COUNT_ID = "output_face_count";
    private static final String OUTPUT_X_AXIS_ID = "output_x_axis";
    private static final String OUTPUT_Y_AXIS_ID = "output_y_axis";
    private static final String OUTPUT_Z_AXIS_ID = "output_z_axis";

    public DeconstructBoxGeometryNode() {
        super(UUID.randomUUID(), "spatial.analysis.deconstruct_box_geometry");

        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "The box geometry to deconstruct", NodeDataType.BOX_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Box center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HALF_EXTENTS_ID, "Half Extents", "Half size along local X/Y/Z", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_IS_ORIENTED_ID, "Is Oriented", "Whether the box uses an oriented basis", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_CORNERS_ID, "Corners", "Ordered list of the 8 box corners", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CORNER_NAMES_ID, "Corner Names", "Names that correspond to the ordered corner list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_FACES_ID, "Faces", "Ordered list of the 6 box faces", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_FACE_NAMES_ID, "Face Names", "Names that correspond to the ordered face list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_CORNER_COUNT_ID, "Corner Count", "Number of corners in the box definition", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FACE_COUNT_ID, "Face Count", "Number of faces in the box definition", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_X_AXIS_ID, "X Axis", "Local X axis in world space", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Y_AXIS_ID, "Y Axis", "Local Y axis in world space", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Z_AXIS_ID, "Z Axis", "Local Z axis in world space", NodeDataType.VECTOR, this));
    }

    @Override
    public String getDescription() {
        return "Extracts center, half extents, orientation, corners, and faces from box geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_BOX_GEOMETRY_ID);
        if (!(geometryObj instanceof BoxGeometryData geometry)) {
            outputValues.put(OUTPUT_CENTER_ID, null);
            outputValues.put(OUTPUT_HALF_EXTENTS_ID, null);
            outputValues.put(OUTPUT_IS_ORIENTED_ID, false);
            outputValues.put(OUTPUT_CORNERS_ID, List.of());
            outputValues.put(OUTPUT_CORNER_NAMES_ID, List.of());
            outputValues.put(OUTPUT_FACES_ID, List.of());
            outputValues.put(OUTPUT_FACE_NAMES_ID, List.of());
            outputValues.put(OUTPUT_CORNER_COUNT_ID, 0);
            outputValues.put(OUTPUT_FACE_COUNT_ID, 0);
            outputValues.put(OUTPUT_X_AXIS_ID, null);
            outputValues.put(OUTPUT_Y_AXIS_ID, null);
            outputValues.put(OUTPUT_Z_AXIS_ID, null);
            return;
        }

        Vector3d xAxis = new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d yAxis = new Vector3d(0.0d, 1.0d, 0.0d);
        Vector3d zAxis = new Vector3d(0.0d, 0.0d, 1.0d);
        geometry.getOrientationMatrix().transform(xAxis);
        geometry.getOrientationMatrix().transform(yAxis);
        geometry.getOrientationMatrix().transform(zAxis);

        List<Vector3d> corners = geometry.getCorners();
        List<BoxFaceData> faces = geometry.getFaces();

        outputValues.put(OUTPUT_CENTER_ID, geometry.getCenter());
        outputValues.put(OUTPUT_HALF_EXTENTS_ID, geometry.getHalfExtents());
        outputValues.put(OUTPUT_IS_ORIENTED_ID, geometry.isOriented());
        outputValues.put(OUTPUT_CORNERS_ID, corners);
        outputValues.put(OUTPUT_CORNER_NAMES_ID, geometry.getCornerNames());
        outputValues.put(OUTPUT_FACES_ID, faces);
        outputValues.put(OUTPUT_FACE_NAMES_ID, geometry.getFaceNames());
        outputValues.put(OUTPUT_CORNER_COUNT_ID, geometry.getCornerCount());
        outputValues.put(OUTPUT_FACE_COUNT_ID, geometry.getFaceCount());
        outputValues.put(OUTPUT_X_AXIS_ID, xAxis);
        outputValues.put(OUTPUT_Y_AXIS_ID, yAxis);
        outputValues.put(OUTPUT_Z_AXIS_ID, zAxis);
    }
}

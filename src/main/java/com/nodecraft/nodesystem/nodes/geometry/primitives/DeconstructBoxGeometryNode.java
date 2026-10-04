package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import com.nodecraft.nodesystem.util.PrimitiveNumericUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import com.nodecraft.nodesystem.util.VectorUtils;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.deconstruct_box",
    displayName = "Deconstruct Box Geometry",
    description = "Extracts center, half extents, orientation, corners, and faces from box geometry",
    category = "geometry.primitives",
    order = 17
)
public class DeconstructBoxGeometryNode extends AbstractPrimitiveDeconstructNode {

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
        super("geometry.primitives.deconstruct_box");

        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "The box geometry to deconstruct", NodeDataType.BOX_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Box center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_HALF_EXTENTS_ID, "Half Extents", "Half size along local X/Y/Z", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_IS_ORIENTED_ID, "Is Oriented", "Whether the box uses an oriented basis", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_CORNERS_ID, "Corners", "Ordered list of the 8 box corners", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CORNER_NAMES_ID, "Corner Names", "Names that correspond to the ordered corner list", NodeDataType.STRING_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FACES_ID, "Faces", "Ordered list of the 6 box faces", NodeDataType.BOX_FACE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FACE_NAMES_ID, "Face Names", "Names that correspond to the ordered face list", NodeDataType.STRING_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CORNER_COUNT_ID, "Corner Count", "Number of corners in the box definition", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FACE_COUNT_ID, "Face Count", "Number of faces in the box definition", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_X_AXIS_ID, "X Axis", "Local X axis in world space", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Y_AXIS_ID, "Y Axis", "Local Y axis in world space", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Z_AXIS_ID, "Z Axis", "Local Z axis in world space", NodeDataType.VECTOR, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Extracts center, half extents, orientation, corners, and faces from box geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_BOX_GEOMETRY_ID);
        if (!(geometryObj instanceof BoxGeometryData geometry)) {
            writeEmptyOutputs("Valid box geometry is required");
            return;
        }

        String error = PrimitiveGeometryValidator.validateBox(geometry);
        if (error != null) {
            writeEmptyOutputs(error);
            return;
        }

        Vector3d xAxis = new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d yAxis = new Vector3d(0.0d, 1.0d, 0.0d);
        Vector3d zAxis = new Vector3d(0.0d, 0.0d, 1.0d);
        geometry.getOrientationMatrix().transform(xAxis);
        geometry.getOrientationMatrix().transform(yAxis);
        geometry.getOrientationMatrix().transform(zAxis);
        if (!VectorUtils.isFinite(geometry.getCenter())
            || !VectorUtils.isFinite(geometry.getHalfExtents())
            || !VectorUtils.isFinite(xAxis)
            || !VectorUtils.isFinite(yAxis)
            || !VectorUtils.isFinite(zAxis)
            || !PrimitiveNumericUtils.isFiniteMatrix(geometry.getOrientationMatrix())) {
            writeEmptyOutputs("Derived analytical values are non-finite");
            return;
        }

        List<Vector3d> corners = geometry.getCorners();
        List<BoxFaceData> faces = geometry.getFaces();

        outputValues.put(OUTPUT_CENTER_ID, new PointData(geometry.getCenter()));
        outputValues.put(OUTPUT_HALF_EXTENTS_ID, VectorUtils.toVectorPort(geometry.getHalfExtents()));
        outputValues.put(OUTPUT_IS_ORIENTED_ID, geometry.isOriented());
        outputValues.put(OUTPUT_CORNERS_ID, SpatialValueResolver.toPointDataList(corners));
        outputValues.put(OUTPUT_CORNER_NAMES_ID, geometry.getCornerNames());
        outputValues.put(OUTPUT_FACES_ID, faces);
        outputValues.put(OUTPUT_FACE_NAMES_ID, geometry.getFaceNames());
        outputValues.put(OUTPUT_CORNER_COUNT_ID, geometry.getCornerCount());
        outputValues.put(OUTPUT_FACE_COUNT_ID, geometry.getFaceCount());
        outputValues.put(OUTPUT_X_AXIS_ID, VectorUtils.toVectorPort(xAxis));
        outputValues.put(OUTPUT_Y_AXIS_ID, VectorUtils.toVectorPort(yAxis));
        outputValues.put(OUTPUT_Z_AXIS_ID, VectorUtils.toVectorPort(zAxis));
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(OUTPUT_CENTER_ID, OUTPUT_HALF_EXTENTS_ID, OUTPUT_X_AXIS_ID, OUTPUT_Y_AXIS_ID, OUTPUT_Z_AXIS_ID);
        outputValues.put(OUTPUT_IS_ORIENTED_ID, false);
        putEmptyListOutputs(OUTPUT_CORNERS_ID, OUTPUT_CORNER_NAMES_ID, OUTPUT_FACES_ID, OUTPUT_FACE_NAMES_ID);
        putIntOutputs(0, OUTPUT_CORNER_COUNT_ID, OUTPUT_FACE_COUNT_ID);
        markInvalid(reason);
    }
}

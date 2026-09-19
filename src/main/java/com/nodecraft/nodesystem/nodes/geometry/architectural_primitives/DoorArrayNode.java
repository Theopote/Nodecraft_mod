package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates a rectangular array of inset door opening boxes on a box face.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.door_array",
    displayName = "Door Array",
    description = "Generates a rectangular array of inset door opening boxes on a box face",
    category = "geometry.architectural_primitives",
    order = 1
)
public class DoorArrayNode extends AbstractFaceArrayNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_COLUMNS_ID = "input_columns";
    private static final String INPUT_ROWS_ID = "input_rows";
    private static final String INPUT_DOOR_WIDTH_ID = "input_door_width";
    private static final String INPUT_DOOR_HEIGHT_ID = "input_door_height";
    private static final String INPUT_MARGIN_ID = "input_margin";
    private static final String INPUT_DEPTH_ID = "input_depth";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DoorArrayNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.door_array");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the facade surface", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_COLUMNS_ID, "Columns", "Number of doors across the face width", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROWS_ID, "Rows", "Number of doors stacked vertically", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_DOOR_WIDTH_ID, "Door Width", "Door opening width in blocks/meters", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DOOR_HEIGHT_ID, "Door Height", "Door opening height in blocks/meters", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MARGIN_ID, "Margin", "Outer margin from the face edge to the first opening", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DEPTH_ID, "Depth", "Inset depth of each opening into the solid", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing all door opening boxes", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of door opening boxes created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid door array could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a rectangular array of inset door opening boxes on a box face";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);

        GeometryData geometry = null;
        int count = 0;
        boolean valid = false;

        if (faceObj instanceof BoxFaceData face) {
            int columns = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_COLUMNS_ID), 1);
            int rows = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_ROWS_ID), 1);
            double doorWidth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_DOOR_WIDTH_ID), 1.0d);
            double doorHeight = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_DOOR_HEIGHT_ID), 2.0d);
            double margin = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_MARGIN_ID), 0.0d);
            double depth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_DEPTH_ID), 1.0d);
            FaceArrayLayout layout = resolveFaceArrayLayout(face, columns, rows, doorWidth, doorHeight, margin, VerticalAnchor.BOTTOM);
            if (layout != null) {
                List<BoxGeometryData> openings = buildOpeningBoxes(layout, depth);
                if (!openings.isEmpty()) {
                    geometry = new CompositeGeometryData(new ArrayList<>(openings));
                    count = openings.size();
                    valid = true;
                }
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private List<BoxGeometryData> buildOpeningBoxes(
        FaceArrayLayout layout,
        double depth
    ) {
        return buildFaceArray(layout, placement -> {
            Vector3d center = placement.centerOnFace().fma(-depth / 2.0d, layout.frame().zAxis());
            Vector3d halfExtents = new Vector3d(layout.elementWidth() / 2.0d, layout.elementHeight() / 2.0d, depth / 2.0d);
            return ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, layout.frame().xAxis(), layout.frame().yAxis(), layout.frame().zAxis());
        });
    }
}
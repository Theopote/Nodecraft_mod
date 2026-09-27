package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Generates a rectangular array of facade panels or cladding boxes on a box face.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.facade_panel_array",
    displayName = "Facade Panel Array",
    description = "Generates a rectangular array of facade panels on a box face",
    category = "geometry.architectural_primitives",
    order = 7
)
public class FacadePanelArrayNode extends AbstractFaceArrayNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_COLUMNS_ID = "input_columns";
    private static final String INPUT_ROWS_ID = "input_rows";
    private static final String INPUT_PANEL_WIDTH_ID = "input_panel_width";
    private static final String INPUT_PANEL_HEIGHT_ID = "input_panel_height";
    private static final String INPUT_MARGIN_ID = "input_margin";
    private static final String INPUT_THICKNESS_ID = "input_thickness";
    private static final String INPUT_OFFSET_ID = "input_offset";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public FacadePanelArrayNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.facade_panel_array");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the facade surface", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_COLUMNS_ID, "Columns", "Number of panels across the face width", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROWS_ID, "Rows", "Number of panels across the face height", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PANEL_WIDTH_ID, "Panel Width", "Width of each panel", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PANEL_HEIGHT_ID, "Panel Height", "Height of each panel", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MARGIN_ID, "Margin", "Outer margin from the face edge to the first panel", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Panel thickness along the face normal", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Offset from the face toward the panel side", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the facade panels", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of panels created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid panel array could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates a rectangular array of facade panels on a box face";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BoxFaceData face = ArchitecturalInputUtils.resolveRequiredFace(this, INPUT_FACE_ID);
        if (face == null) {
            writeInvalid("Face is required");
            return;
        }

        Integer columns = ArchitecturalInputUtils.resolveOptionalExactPositiveInteger(this, INPUT_COLUMNS_ID, 3);
        if (columns == null) {
            writeInvalid("Columns must be an exact positive integer");
            return;
        }

        Integer rows = ArchitecturalInputUtils.resolveOptionalExactPositiveInteger(this, INPUT_ROWS_ID, 3);
        if (rows == null) {
            writeInvalid("Rows must be an exact positive integer");
            return;
        }

        Double panelWidth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_PANEL_WIDTH_ID, 1.0d);
        if (panelWidth == null) {
            writeInvalid("Panel Width must be a positive finite number");
            return;
        }

        Double panelHeight = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_PANEL_HEIGHT_ID, 1.0d);
        if (panelHeight == null) {
            writeInvalid("Panel Height must be a positive finite number");
            return;
        }

        Double margin = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_MARGIN_ID, 0.0d);
        if (margin == null) {
            writeInvalid("Margin must be a non-negative finite number");
            return;
        }

        Double thickness = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_THICKNESS_ID, 0.15d);
        if (thickness == null) {
            writeInvalid("Thickness must be a positive finite number");
            return;
        }

        Double offset = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_OFFSET_ID, 0.0d);
        if (offset == null) {
            writeInvalid("Offset must be a non-negative finite number");
            return;
        }

        if (!GeometryOutputUtils.fitsArchitecturalInstanceBudget(columns, rows)) {
            writeInvalid("Requested instance count exceeds limit");
            return;
        }

        FaceArrayLayout layout = resolveFaceArrayLayout(
            face, columns, rows, panelWidth, panelHeight, margin, VerticalAnchor.BOTTOM);
        if (layout == null) {
            writeInvalid("Requested array does not fit on face");
            return;
        }

        List<BoxGeometryData> panels = buildPanels(layout, thickness, offset);
        outputValues.put(OUTPUT_GEOMETRY_ID, GeometryOutputUtils.packGeometry(panels));
        outputValues.put(OUTPUT_COUNT_ID, columns * rows);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private List<BoxGeometryData> buildPanels(
        FaceArrayLayout layout,
        double thickness,
        double offset
    ) {
        Vector3d normalOffset = new Vector3d(layout.frame().zAxis()).mul(offset + thickness / 2.0d);
        return buildFaceArray(layout, placement -> {
            Vector3d center = placement.centerOnFace().add(normalOffset);
            Vector3d halfExtents = new Vector3d(layout.elementWidth() / 2.0d, layout.elementHeight() / 2.0d, thickness / 2.0d);
            return ArchitecturalPrimitiveSupport.createOrientedBox(
                center, halfExtents, layout.frame().xAxis(), layout.frame().yAxis(), layout.frame().zAxis());
        });
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

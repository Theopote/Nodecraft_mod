package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Generates a rectangular array of door opening cutters centered on a box face.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.door_array",
    displayName = "Door Array",
    description = "Generates a rectangular array of door opening cutters centered on a box face",
    category = "geometry.architectural_primitives",
    order = 1
)
public class DoorArrayNode extends AbstractFaceArrayNode {

    private static final Set<String> LAYOUT_MODES = Set.of("distribute", "fixed_gap", "bay");

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_COLUMNS_ID = "input_columns";
    private static final String INPUT_ROWS_ID = "input_rows";
    private static final String INPUT_DOOR_WIDTH_ID = "input_door_width";
    private static final String INPUT_DOOR_HEIGHT_ID = "input_door_height";
    private static final String INPUT_MARGIN_ID = "input_margin";
    private static final String INPUT_DEPTH_ID = "input_depth";
    private static final String INPUT_LAYOUT_MODE_ID = "input_layout_mode";
    private static final String INPUT_HORIZONTAL_GAP_ID = "input_horizontal_gap";
    private static final String INPUT_VERTICAL_GAP_ID = "input_vertical_gap";
    private static final String INPUT_BAY_WIDTH_ID = "input_bay_width";

    private static final String OUTPUT_OPENINGS_ID = "output_openings";
    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_CENTERS_ID = "output_centers";
    private static final String OUTPUT_ROW_INDICES_ID = "output_row_indices";
    private static final String OUTPUT_COLUMN_INDICES_ID = "output_column_indices";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public DoorArrayNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.door_array");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the facade surface", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_COLUMNS_ID, "Columns", "Number of doors across the face width", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROWS_ID, "Rows", "Number of doors stacked vertically", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_DOOR_WIDTH_ID, "Door Width", "Door opening width in blocks/meters", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DOOR_HEIGHT_ID, "Door Height", "Door opening height in blocks/meters", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MARGIN_ID, "Margin", "Outer margin from the face edge to the first opening", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DEPTH_ID, "Depth",
            "Cutter thickness across the face plane (centered; ±Depth/2 along the outward normal)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_LAYOUT_MODE_ID, "Layout Mode",
            "Spacing mode: distribute, fixed_gap, or bay", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_HORIZONTAL_GAP_ID, "Horizontal Gap",
            "Fixed pier width between columns when Layout Mode is fixed_gap", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_VERTICAL_GAP_ID, "Vertical Gap",
            "Fixed pier height between rows when Layout Mode is fixed_gap (also used for BAY vertical spacing)",
            NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BAY_WIDTH_ID, "Bay Width",
            "Horizontal center-to-center bay width when Layout Mode is bay", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_OPENINGS_ID, "Openings",
            "Opening boxes for Difference (centered on the face; thickness = Depth)", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Placement frames at each door center (face-aligned)", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CENTERS_ID, "Centers", "Door center points on the face", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ROW_INDICES_ID, "Row Indices", "1-based row index per opening", NodeDataType.INTEGER_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COLUMN_INDICES_ID, "Column Indices", "1-based column index per opening", NodeDataType.INTEGER_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of door opening boxes created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid door array could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates a rectangular array of door opening cutters centered on a box face";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BoxFaceData face = ArchitecturalInputUtils.resolveRequiredFace(this, INPUT_FACE_ID);
        if (face == null) {
            writeInvalid("Face is required");
            return;
        }

        Integer columns = ArchitecturalInputUtils.resolveOptionalExactPositiveInteger(this, INPUT_COLUMNS_ID, 1);
        if (columns == null) {
            writeInvalid("Columns must be an exact positive integer");
            return;
        }

        Integer rows = ArchitecturalInputUtils.resolveOptionalExactPositiveInteger(this, INPUT_ROWS_ID, 1);
        if (rows == null) {
            writeInvalid("Rows must be an exact positive integer");
            return;
        }

        Double doorWidth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_DOOR_WIDTH_ID, 1.0d);
        if (doorWidth == null) {
            writeInvalid("Door Width must be a positive finite number");
            return;
        }

        Double doorHeight = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_DOOR_HEIGHT_ID, 2.0d);
        if (doorHeight == null) {
            writeInvalid("Door Height must be a positive finite number");
            return;
        }

        Double margin = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_MARGIN_ID, 0.0d);
        if (margin == null) {
            writeInvalid("Margin must be a non-negative finite number");
            return;
        }

        Double depth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_DEPTH_ID, 1.0d);
        if (depth == null) {
            writeInvalid("Depth must be a positive finite number");
            return;
        }

        String layoutModeText = ArchitecturalInputUtils.resolveKnownStringEnum(
            this, INPUT_LAYOUT_MODE_ID, "distribute", LAYOUT_MODES);
        if (layoutModeText == null) {
            writeInvalid("Layout Mode must be one of: distribute, fixed_gap, bay");
            return;
        }
        LayoutMode layoutMode = LayoutMode.fromString(layoutModeText);
        if (layoutMode == null) {
            writeInvalid("Layout Mode must be one of: distribute, fixed_gap, bay");
            return;
        }

        Double horizontalGap = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_HORIZONTAL_GAP_ID, 0.5d);
        if (horizontalGap == null) {
            writeInvalid("Horizontal Gap must be a non-negative finite number");
            return;
        }
        Double verticalGap = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_VERTICAL_GAP_ID, 0.5d);
        if (verticalGap == null) {
            writeInvalid("Vertical Gap must be a non-negative finite number");
            return;
        }
        Double bayWidth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_BAY_WIDTH_ID, doorWidth + 0.5d);
        if (bayWidth == null) {
            writeInvalid("Bay Width must be a positive finite number");
            return;
        }

        if (!GeometryOutputUtils.fitsArchitecturalInstanceBudget(columns, rows)) {
            writeInvalid("Requested instance count exceeds limit");
            return;
        }

        LayoutSpacingOptions spacingOptions = new LayoutSpacingOptions(layoutMode, horizontalGap, verticalGap, bayWidth);
        FaceArrayLayout layout = resolveFaceArrayLayout(
            face, columns, rows, doorWidth, doorHeight, margin, VerticalAnchor.BOTTOM, spacingOptions);
        if (layout == null) {
            writeInvalid("Requested array does not fit on face");
            return;
        }

        List<BoxGeometryData> openings = buildOpeningBoxes(layout, depth);
        if (openings == null) {
            writeInvalid("Failed to generate array geometry");
            return;
        }
        List<FrameData> frames = buildPlacementFrames(layout);
        List<PointData> centers = buildCenters(layout);

        var packedOpenings = GeometryOutputUtils.packGeometry(openings);
        outputValues.put(OUTPUT_OPENINGS_ID, packedOpenings);
        outputValues.put(OUTPUT_FRAMES_ID, frames);
        outputValues.put(OUTPUT_CENTERS_ID, centers);
        outputValues.put(OUTPUT_ROW_INDICES_ID, buildRowIndices(layout));
        outputValues.put(OUTPUT_COLUMN_INDICES_ID, buildColumnIndices(layout));
        outputValues.put(OUTPUT_COUNT_ID, columns * rows);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private @Nullable List<BoxGeometryData> buildOpeningBoxes(FaceArrayLayout layout, double depth) {
        return buildFaceArray(layout, placement -> ArchitecturalPrimitiveSupport.createCenteredFaceOpening(
            placement.centerOnFace(),
            layout.frame(),
            layout.elementWidth(),
            layout.elementHeight(),
            depth
        ));
    }

    private void writeInvalid(String error) {
        ArchitecturalNodeOutputs.putNull(outputValues, OUTPUT_OPENINGS_ID);
        ArchitecturalNodeOutputs.putEmptyLists(outputValues,
            OUTPUT_FRAMES_ID, OUTPUT_CENTERS_ID, OUTPUT_ROW_INDICES_ID, OUTPUT_COLUMN_INDICES_ID);
        ArchitecturalNodeOutputs.putCount(outputValues, OUTPUT_COUNT_ID, 0);
        ArchitecturalNodeOutputs.markInvalid(outputValues, error);
    }
}

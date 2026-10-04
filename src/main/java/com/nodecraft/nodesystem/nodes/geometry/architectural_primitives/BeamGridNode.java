package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Structural beam grid on a BOX_FACE (combinable with Floor Slab).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.beam_grid",
    displayName = "Beam Grid",
    description = "Generates a support beam grid on a box face footprint",
    category = "geometry.architectural_primitives",
    order = 13
)
public class BeamGridNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_COLUMNS_ID = "input_columns";
    private static final String INPUT_ROWS_ID = "input_rows";
    private static final String INPUT_BEAM_WIDTH_ID = "input_beam_width";
    private static final String INPUT_BEAM_DEPTH_ID = "input_beam_depth";
    private static final String INPUT_BEAM_DROP_ID = "input_beam_drop";
    private static final String INPUT_MARGIN_ID = "input_margin";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_CENTERS_ID = "output_centers";
    private static final String OUTPUT_CENTER_LINES_ID = "output_center_lines";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public BeamGridNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.beam_grid");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face",
            "Reference face for the beam grid (use Floor Slab Bottom Face when hanging beams below a slab)",
            NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_COLUMNS_ID, "Columns", "Number of beams running along the width", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROWS_ID, "Rows", "Number of beams running along the height", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_BEAM_WIDTH_ID, "Beam Width", "Beam width across its short axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BEAM_DEPTH_ID, "Beam Depth", "Beam depth along the face normal", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BEAM_DROP_ID, "Beam Drop", "Distance beams hang below the face / slab", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MARGIN_ID, "Margin", "Margin from the face edge to the beam grid", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Beam solids", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Placement frames at each beam center", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CENTERS_ID, "Centers", "Beam center points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_LINES_ID, "Center Lines", "Beam centerline paths", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of beams created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when at least one beam was generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates a support beam grid on a box face footprint";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BoxFaceData face = ArchitecturalInputUtils.resolveRequiredFace(this, INPUT_FACE_ID);
        if (face == null) {
            writeInvalid("Face is required");
            return;
        }
        ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
        if (frame == null) {
            writeInvalid("Face is required (non-degenerate box face)");
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
        long beamCount;
        try {
            beamCount = Math.addExact((long) columns, (long) rows);
        } catch (ArithmeticException overflow) {
            writeInvalid("Requested instance count exceeds limit ("
                + GenerationLimits.MAX_ARCHITECTURAL_INSTANCES + ")");
            return;
        }
        if (beamCount > GenerationLimits.MAX_ARCHITECTURAL_INSTANCES) {
            writeInvalid("Requested instance count exceeds limit ("
                + GenerationLimits.MAX_ARCHITECTURAL_INSTANCES + ")");
            return;
        }

        Double beamWidth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_BEAM_WIDTH_ID, 0.25d);
        if (beamWidth == null) {
            writeInvalid("Beam Width must be a positive finite number");
            return;
        }
        Double beamDepth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_BEAM_DEPTH_ID, 0.35d);
        if (beamDepth == null) {
            writeInvalid("Beam Depth must be a positive finite number");
            return;
        }
        Double beamDrop = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_BEAM_DROP_ID, 0.1d);
        if (beamDrop == null) {
            writeInvalid("Beam Drop must be a non-negative finite number");
            return;
        }
        Double margin = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_MARGIN_ID, 0.0d);
        if (margin == null) {
            writeInvalid("Margin must be a non-negative finite number");
            return;
        }

        FloorStructureSupport.BeamGridResult result = FloorStructureSupport.buildBeamGrid(
            frame, columns, rows, beamWidth, beamDepth, beamDrop, margin);
        if (result.beams().isEmpty()) {
            writeInvalid("Requested beam grid does not fit on face");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, GeometryOutputUtils.packGeometry(result.beams()));
        outputValues.put(OUTPUT_FRAMES_ID, result.frames());
        outputValues.put(OUTPUT_CENTERS_ID, result.centers());
        outputValues.put(OUTPUT_CENTER_LINES_ID, result.centerLines());
        outputValues.put(OUTPUT_COUNT_ID, result.beams().size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        ArchitecturalNodeOutputs.putNull(outputValues, OUTPUT_GEOMETRY_ID);
        ArchitecturalNodeOutputs.putEmptyLists(outputValues, OUTPUT_FRAMES_ID, OUTPUT_CENTERS_ID, OUTPUT_CENTER_LINES_ID);
        ArchitecturalNodeOutputs.putCount(outputValues, OUTPUT_COUNT_ID, 0);
        ArchitecturalNodeOutputs.markInvalid(outputValues, error);
    }
}

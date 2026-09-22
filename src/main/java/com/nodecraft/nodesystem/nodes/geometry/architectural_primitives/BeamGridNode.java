package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
    private static final String INPUT_SLAB_THICKNESS_ID = "input_slab_thickness";
    private static final String INPUT_MARGIN_ID = "input_margin";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_CENTERS_ID = "output_centers";
    private static final String OUTPUT_CENTER_LINES_ID = "output_center_lines";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public BeamGridNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.beam_grid");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the beam-grid footprint", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_COLUMNS_ID, "Columns", "Number of beams running along the width", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROWS_ID, "Rows", "Number of beams running along the height", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_BEAM_WIDTH_ID, "Beam Width", "Beam width across its short axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BEAM_DEPTH_ID, "Beam Depth", "Beam depth along the face normal", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BEAM_DROP_ID, "Beam Drop", "Distance beams hang below the face / slab", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SLAB_THICKNESS_ID, "Slab Thickness",
            "Optional slab thickness used to hang beams below a Floor Slab", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MARGIN_ID, "Margin", "Margin from the face edge to the beam grid", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite beam solids", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Placement frames at each beam center", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CENTERS_ID, "Centers", "Beam center points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_LINES_ID, "Center Lines", "Beam centerline paths", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of beams created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when at least one beam was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a support beam grid on a box face footprint";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        GeometryData geometry = null;
        List<FrameData> frames = null;
        List<PointData> centers = null;
        List<PathData> centerLines = null;
        int count = 0;
        boolean valid = false;

        if (inputValues.get(INPUT_FACE_ID) instanceof BoxFaceData face) {
            ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
            if (frame != null) {
                int columns = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_COLUMNS_ID), 3);
                int rows = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_ROWS_ID), 3);
                double beamWidth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_BEAM_WIDTH_ID), 0.25d);
                double beamDepth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_BEAM_DEPTH_ID), 0.35d);
                double beamDrop = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_BEAM_DROP_ID), 0.1d);
                double slabThickness = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(
                    inputValues.get(INPUT_SLAB_THICKNESS_ID), 0.0d);
                double margin = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_MARGIN_ID), 0.0d);

                FloorStructureSupport.BeamGridResult result = FloorStructureSupport.buildBeamGrid(
                    frame, columns, rows, beamWidth, beamDepth, beamDrop, margin, slabThickness);
                if (!result.beams().isEmpty()) {
                    geometry = new CompositeGeometryData(result.beams());
                    frames = result.frames();
                    centers = result.centers();
                    centerLines = result.centerLines();
                    count = result.beams().size();
                    valid = true;
                }
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_FRAMES_ID, frames);
        outputValues.put(OUTPUT_CENTERS_ID, centers);
        outputValues.put(OUTPUT_CENTER_LINES_ID, centerLines);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}

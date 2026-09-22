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
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Convenience host: Floor Slab + Beam Grid in one node.
 * Prefer composing {@code Floor Slab} and {@code Beam Grid} for new graphs.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.floor_slab_with_beams",
    displayName = "Floor Slab With Beams",
    description = "Convenience: floor slab plus support beam grid (prefer Floor Slab + Beam Grid)",
    category = "geometry.architectural_primitives",
    order = 12
)
public class FloorSlabWithBeamsNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_SLAB_THICKNESS_ID = "input_slab_thickness";
    private static final String INPUT_BEAM_COLUMNS_ID = "input_beam_columns";
    private static final String INPUT_BEAM_ROWS_ID = "input_beam_rows";
    private static final String INPUT_BEAM_WIDTH_ID = "input_beam_width";
    private static final String INPUT_BEAM_DEPTH_ID = "input_beam_depth";
    private static final String INPUT_BEAM_DROP_ID = "input_beam_drop";
    private static final String INPUT_MARGIN_ID = "input_margin";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_SLAB_ID = "output_slab";
    private static final String OUTPUT_BEAMS_ID = "output_beams";
    private static final String OUTPUT_BEAM_FRAMES_ID = "output_beam_frames";
    private static final String OUTPUT_BEAM_CENTER_LINES_ID = "output_beam_center_lines";
    private static final String OUTPUT_TOP_FACE_ID = "output_top_face";
    private static final String OUTPUT_BOTTOM_FACE_ID = "output_bottom_face";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public FloorSlabWithBeamsNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.floor_slab_with_beams");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the slab footprint", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_SLAB_THICKNESS_ID, "Slab Thickness", "Thickness of the floor slab", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BEAM_COLUMNS_ID, "Beam Columns", "Number of beams running along the width", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_BEAM_ROWS_ID, "Beam Rows", "Number of beams running along the height", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_BEAM_WIDTH_ID, "Beam Width", "Beam width across its short axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BEAM_DEPTH_ID, "Beam Depth", "Beam depth along the slab normal", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BEAM_DROP_ID, "Beam Drop", "Distance beams hang below the slab", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MARGIN_ID, "Margin", "Margin from the face edge to the beam grid", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry",
            "Composite slab + beams (convenience)", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_SLAB_ID, "Slab Geometry", "Floor slab solid only", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BEAMS_ID, "Beam Geometry", "Support beam grid only", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BEAM_FRAMES_ID, "Beam Frames", "Placement frames at each beam center", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BEAM_CENTER_LINES_ID, "Beam Center Lines", "Beam centerline paths", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TOP_FACE_ID, "Top Face", "Top face of the slab", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_BOTTOM_FACE_ID, "Bottom Face", "Bottom face of the slab", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Total geometry pieces created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid slab with beams could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Convenience: floor slab plus support beam grid (prefer Floor Slab + Beam Grid)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        GeometryData geometry = null;
        GeometryData slabGeometry = null;
        GeometryData beamGeometry = null;
        List<FrameData> beamFrames = null;
        List<PathData> beamCenterLines = null;
        BoxFaceData topFace = null;
        BoxFaceData bottomFace = null;
        int count = 0;
        boolean valid = false;

        if (inputValues.get(INPUT_FACE_ID) instanceof BoxFaceData face) {
            ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
            if (frame != null) {
                double slabThickness = ArchitecturalPrimitiveSupport.resolvePositiveDouble(
                    inputValues.get(INPUT_SLAB_THICKNESS_ID), 0.3d);
                int beamColumns = ArchitecturalPrimitiveSupport.resolvePositiveInt(
                    inputValues.get(INPUT_BEAM_COLUMNS_ID), 3);
                int beamRows = ArchitecturalPrimitiveSupport.resolvePositiveInt(
                    inputValues.get(INPUT_BEAM_ROWS_ID), 3);
                double beamWidth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(
                    inputValues.get(INPUT_BEAM_WIDTH_ID), 0.25d);
                double beamDepth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(
                    inputValues.get(INPUT_BEAM_DEPTH_ID), 0.35d);
                double beamDrop = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(
                    inputValues.get(INPUT_BEAM_DROP_ID), 0.1d);
                double margin = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(
                    inputValues.get(INPUT_MARGIN_ID), 0.0d);

                slabGeometry = FloorStructureSupport.createSlab(frame, slabThickness);
                FloorStructureSupport.BeamGridResult beams = FloorStructureSupport.buildBeamGrid(
                    frame, beamColumns, beamRows, beamWidth, beamDepth, beamDrop, margin, slabThickness);
                beamGeometry = beams.beams().isEmpty() ? null : new CompositeGeometryData(beams.beams());
                beamFrames = beams.frames().isEmpty() ? null : beams.frames();
                beamCenterLines = beams.centerLines().isEmpty() ? null : beams.centerLines();
                topFace = FloorStructureSupport.topFace(frame, slabThickness);
                bottomFace = FloorStructureSupport.bottomFace(frame);

                List<GeometryData> pieces = new ArrayList<>();
                pieces.add(slabGeometry);
                pieces.addAll(beams.beams());
                geometry = new CompositeGeometryData(pieces);
                count = pieces.size();
                valid = true;
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_SLAB_ID, slabGeometry);
        outputValues.put(OUTPUT_BEAMS_ID, beamGeometry);
        outputValues.put(OUTPUT_BEAM_FRAMES_ID, beamFrames);
        outputValues.put(OUTPUT_BEAM_CENTER_LINES_ID, beamCenterLines);
        outputValues.put(OUTPUT_TOP_FACE_ID, topFace);
        outputValues.put(OUTPUT_BOTTOM_FACE_ID, bottomFace);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}

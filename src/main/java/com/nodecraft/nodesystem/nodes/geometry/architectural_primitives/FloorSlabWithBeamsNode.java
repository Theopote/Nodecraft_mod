package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
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
 * Generates a floor slab with a simple grid of support beams beneath it.
 */
@NodeInfo(
    id = "geometry.architectural_primitives.floor_slab_with_beams",
    displayName = "Floor Slab With Beams",
    description = "Generates a floor slab and a configurable support beam grid",
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

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the slab and support beams", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Total geometry pieces created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid slab with beams could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a floor slab and a configurable support beam grid";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        GeometryData geometry = null;
        int count = 0;
        boolean valid = false;

        if (faceObj instanceof BoxFaceData face) {
            ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
            if (frame != null) {
                double slabThickness = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_SLAB_THICKNESS_ID), 0.3d);
                int beamColumns = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_BEAM_COLUMNS_ID), 3);
                int beamRows = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_BEAM_ROWS_ID), 3);
                double beamWidth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_BEAM_WIDTH_ID), 0.25d);
                double beamDepth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_BEAM_DEPTH_ID), 0.35d);
                double beamDrop = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_BEAM_DROP_ID), 0.1d);
                double margin = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_MARGIN_ID), 0.0d);

                List<GeometryData> pieces = new ArrayList<>();
                pieces.add(createSlab(frame, slabThickness));
                pieces.addAll(buildBeams(frame, beamColumns, beamRows, beamWidth, beamDepth, beamDrop, margin, slabThickness));

                geometry = new CompositeGeometryData(pieces);
                count = pieces.size();
                valid = true;
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private BoxGeometryData createSlab(ArchitecturalPrimitiveSupport.FaceFrame frame, double slabThickness) {
        Vector3d center = new Vector3d(frame.center()).fma(slabThickness / 2.0d, frame.zAxis());
        Vector3d halfExtents = new Vector3d(frame.width() / 2.0d, frame.height() / 2.0d, slabThickness / 2.0d);
        return ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis());
    }

    private List<GeometryData> buildBeams(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        int beamColumns,
        int beamRows,
        double beamWidth,
        double beamDepth,
        double beamDrop,
        double margin,
        double slabThickness
    ) {
        double usableWidth = frame.width() - 2.0d * margin;
        double usableHeight = frame.height() - 2.0d * margin;
        if (usableWidth < beamWidth || usableHeight < beamWidth) {
            return List.of();
        }

        double spacingX = beamColumns > 1 ? (usableWidth - beamColumns * beamWidth) / (beamColumns - 1) : 0.0d;
        double spacingY = beamRows > 1 ? (usableHeight - beamRows * beamWidth) / (beamRows - 1) : 0.0d;
        if (spacingX < -1.0e-9d || spacingY < -1.0e-9d) {
            return List.of();
        }

        double startX = -frame.width() / 2.0d + margin + beamWidth / 2.0d;
        double startY = -frame.height() / 2.0d + margin + beamWidth / 2.0d;
        Vector3d beamCenterOffset = new Vector3d(frame.zAxis()).mul(-(slabThickness / 2.0d + beamDrop + beamDepth / 2.0d));

        List<GeometryData> beams = new ArrayList<>(beamColumns + beamRows);

        for (int column = 0; column < beamColumns; column++) {
            double offsetX = startX + column * (beamWidth + spacingX);
            Vector3d center = new Vector3d(frame.center())
                .fma(offsetX, frame.xAxis())
                .add(beamCenterOffset);
            Vector3d halfExtents = new Vector3d(beamWidth / 2.0d, frame.height() / 2.0d - margin, beamDepth / 2.0d);
            beams.add(ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis()));
        }

        for (int row = 0; row < beamRows; row++) {
            double offsetY = startY + row * (beamWidth + spacingY);
            Vector3d center = new Vector3d(frame.center())
                .fma(offsetY, frame.yAxis())
                .add(beamCenterOffset);
            Vector3d halfExtents = new Vector3d(frame.width() / 2.0d - margin, beamWidth / 2.0d, beamDepth / 2.0d);
            beams.add(ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis()));
        }

        return List.copyOf(beams);
    }
}
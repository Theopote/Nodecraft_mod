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
 * Generates a wall slab plus a grid of window or door opening volumes.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.wall_with_openings",
    displayName = "Wall With Openings",
    description = "Generates a wall slab with an opening grid",
    category = "geometry.architectural_primitives",
    order = 8
)
public class WallWithOpeningsNode extends AbstractFaceArrayNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_COLUMNS_ID = "input_columns";
    private static final String INPUT_ROWS_ID = "input_rows";
    private static final String INPUT_WALL_THICKNESS_ID = "input_wall_thickness";
    private static final String INPUT_OPENING_WIDTH_ID = "input_opening_width";
    private static final String INPUT_OPENING_HEIGHT_ID = "input_opening_height";
    private static final String INPUT_MARGIN_ID = "input_margin";
    private static final String INPUT_OPENING_DEPTH_ID = "input_opening_depth";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public WallWithOpeningsNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.wall_with_openings");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the wall footprint", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_COLUMNS_ID, "Columns", "Number of openings across the face width", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROWS_ID, "Rows", "Number of openings across the face height", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_WALL_THICKNESS_ID, "Wall Thickness", "Thickness of the wall slab", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OPENING_WIDTH_ID, "Opening Width", "Width of each opening", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OPENING_HEIGHT_ID, "Opening Height", "Height of each opening", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MARGIN_ID, "Margin", "Margin from the face edge to the opening grid", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OPENING_DEPTH_ID, "Opening Depth", "Depth of each opening volume", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the wall and opening volumes", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Total geometry pieces created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid wall with openings could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a wall slab with an opening grid";
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
                int columns = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_COLUMNS_ID), 3);
                int rows = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_ROWS_ID), 2);
                double wallThickness = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_WALL_THICKNESS_ID), 0.4d);
                double openingWidth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_OPENING_WIDTH_ID), 1.0d);
                double openingHeight = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_OPENING_HEIGHT_ID), 1.5d);
                double margin = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_MARGIN_ID), 0.0d);
                double openingDepth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_OPENING_DEPTH_ID), wallThickness);
                FaceArrayLayout layout = resolveFaceArrayLayout(frame, columns, rows, openingWidth, openingHeight, margin, VerticalAnchor.BOTTOM);

                List<GeometryData> pieces = new ArrayList<>();
                pieces.add(createWall(frame, wallThickness));
                if (layout != null) {
                    pieces.addAll(buildOpenings(layout, openingDepth));
                }

                geometry = new CompositeGeometryData(pieces);
                count = pieces.size();
                valid = true;
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private BoxGeometryData createWall(ArchitecturalPrimitiveSupport.FaceFrame frame, double wallThickness) {
        Vector3d center = new Vector3d(frame.center()).fma(wallThickness / 2.0d, frame.zAxis());
        Vector3d halfExtents = new Vector3d(frame.width() / 2.0d, frame.height() / 2.0d, wallThickness / 2.0d);
        return ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis());
    }

    private List<GeometryData> buildOpenings(
        FaceArrayLayout layout,
        double openingDepth
    ) {
        Vector3d inwardNormal = new Vector3d(layout.frame().zAxis()).mul(openingDepth / 2.0d);
        return buildFaceArray(layout, placement -> {
            Vector3d center = placement.centerOnFace().add(inwardNormal);
            Vector3d halfExtents = new Vector3d(layout.elementWidth() / 2.0d, layout.elementHeight() / 2.0d, openingDepth / 2.0d);
            return ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, layout.frame().xAxis(), layout.frame().yAxis(), layout.frame().zAxis());
        });
    }
}
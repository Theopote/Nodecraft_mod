package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Generates a solid wall slab and a separate opening-volume set.
 * <p>
 * Does <strong>not</strong> boolean-subtract openings into the wall. Connect
 * {@code output_geometry} and {@code output_openings} to a Difference node
 * when a cut wall is required.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.wall_with_openings",
    displayName = "Wall With Openings",
    description = "Generates a wall slab and separate opening volumes (use Difference to cut holes)",
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
    private static final String OUTPUT_OPENINGS_ID = "output_openings";
    private static final String OUTPUT_TOP_EDGE_ID = "output_top_edge";
    private static final String OUTPUT_BOTTOM_EDGE_ID = "output_bottom_edge";
    private static final String OUTPUT_CENTER_LINE_ID = "output_center_line";
    private static final String OUTPUT_EXTERIOR_FACE_ID = "output_exterior_face";
    private static final String OUTPUT_INTERIOR_FACE_ID = "output_interior_face";
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

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Wall Geometry",
            "Solid wall slab only (openings are not subtracted)", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_OPENINGS_ID, "Openings",
            "Opening volumes for Difference / window framing", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_TOP_EDGE_ID, "Top Edge",
            "Top edge path of the wall face", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_BOTTOM_EDGE_ID, "Bottom Edge",
            "Bottom edge path of the wall face", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_LINE_ID, "Center Line",
            "Horizontal centerline path of the wall face", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_EXTERIOR_FACE_ID, "Exterior Face",
            "Exterior face of the wall slab", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_INTERIOR_FACE_ID, "Interior Face",
            "Interior face of the wall slab", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of opening volumes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid wall could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a wall slab and separate opening volumes (use Difference to cut holes)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        GeometryData wallGeometry = null;
        GeometryData openingsGeometry = null;
        PathData topEdge = null;
        PathData bottomEdge = null;
        PathData centerLine = null;
        BoxFaceData exteriorFace = null;
        BoxFaceData interiorFace = null;
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

                wallGeometry = createWall(frame, wallThickness);
                List<GeometryData> openings = layout != null ? buildOpenings(layout, openingDepth) : List.of();
                openingsGeometry = openings.isEmpty() ? null : new CompositeGeometryData(openings);
                count = openings.size();

                topEdge = edgePath(frame, frame.height() / 2.0d);
                bottomEdge = edgePath(frame, -frame.height() / 2.0d);
                centerLine = edgePath(frame, 0.0d);
                exteriorFace = planarFace("exterior", frame, 0.0d, new Vector3d(frame.zAxis()).negate());
                interiorFace = planarFace("interior", frame, wallThickness, frame.zAxis());
                valid = true;
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, wallGeometry);
        outputValues.put(OUTPUT_OPENINGS_ID, openingsGeometry);
        outputValues.put(OUTPUT_TOP_EDGE_ID, topEdge);
        outputValues.put(OUTPUT_BOTTOM_EDGE_ID, bottomEdge);
        outputValues.put(OUTPUT_CENTER_LINE_ID, centerLine);
        outputValues.put(OUTPUT_EXTERIOR_FACE_ID, exteriorFace);
        outputValues.put(OUTPUT_INTERIOR_FACE_ID, interiorFace);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private BoxGeometryData createWall(ArchitecturalPrimitiveSupport.FaceFrame frame, double wallThickness) {
        Vector3d center = new Vector3d(frame.center()).fma(wallThickness / 2.0d, frame.zAxis());
        Vector3d halfExtents = new Vector3d(frame.width() / 2.0d, frame.height() / 2.0d, wallThickness / 2.0d);
        return ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis());
    }

    private List<GeometryData> buildOpenings(FaceArrayLayout layout, double openingDepth) {
        Vector3d inwardNormal = new Vector3d(layout.frame().zAxis()).mul(openingDepth / 2.0d);
        return buildFaceArray(layout, placement -> {
            Vector3d center = placement.centerOnFace().add(inwardNormal);
            Vector3d halfExtents = new Vector3d(layout.elementWidth() / 2.0d, layout.elementHeight() / 2.0d, openingDepth / 2.0d);
            return ArchitecturalPrimitiveSupport.createOrientedBox(
                center, halfExtents, layout.frame().xAxis(), layout.frame().yAxis(), layout.frame().zAxis());
        });
    }

    private static PathData edgePath(ArchitecturalPrimitiveSupport.FaceFrame frame, double heightOffset) {
        Vector3d left = new Vector3d(frame.center())
            .fma(-frame.width() / 2.0d, frame.xAxis())
            .fma(heightOffset, frame.yAxis());
        Vector3d right = new Vector3d(frame.center())
            .fma(frame.width() / 2.0d, frame.xAxis())
            .fma(heightOffset, frame.yAxis());
        return PathData.fromLine(new LineData(
            new Vec3d(left.x, left.y, left.z),
            new Vec3d(right.x, right.y, right.z)
        ));
    }

    private static BoxFaceData planarFace(
        String name,
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        double depthAlongNormal,
        Vector3d outwardNormal
    ) {
        Vector3d center = new Vector3d(frame.center()).fma(depthAlongNormal, frame.zAxis());
        Vector3d hx = new Vector3d(frame.xAxis()).mul(frame.width() / 2.0d);
        Vector3d hy = new Vector3d(frame.yAxis()).mul(frame.height() / 2.0d);
        List<Vector3d> corners = List.of(
            new Vector3d(center).sub(hx).sub(hy),
            new Vector3d(center).add(hx).sub(hy),
            new Vector3d(center).add(hx).add(hy),
            new Vector3d(center).sub(hx).add(hy)
        );
        return new BoxFaceData(0, name, List.of(0, 1, 2, 3), corners, center, outwardNormal);
    }
}

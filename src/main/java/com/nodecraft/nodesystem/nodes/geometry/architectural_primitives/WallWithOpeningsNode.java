package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Generates a solid wall slab and a separate opening-volume set.
 * <p>
 * Host slab grows on the <strong>+face normal (outward)</strong> side of the
 * footprint face. That is this node's host-side rule -- not a redefinition of
 * {@code BoxFace.normal}. Window/Door Array cutters are independent and centered
 * on the face plane.
 * <p>
 * Does <strong>not</strong> boolean-subtract openings into the wall. Connect
 * {@code output_geometry} and {@code output_openings} to a Difference node
 * when a cut wall is required.
 * <p>
 * Prefer {@code wall_slab} + Window/Door Array + Difference when openings are
 * owned by a separate array node (avoids a second unused opening layout).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.wall_with_openings",
    displayName = "Wall With Openings",
    description = "Wall slab plus opening volumes (prefer Wall Slab + Window Array + Difference when openings are owned elsewhere)",
    category = "geometry.architectural_primitives",
    order = 9
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
    private static final String OUTPUT_ERROR_ID = "output_error";

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
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Wall slab plus opening volumes (prefer Wall Slab + Window Array + Difference when openings are owned elsewhere)";
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
            writeInvalid("Face is required");
            return;
        }

        Integer columns = ArchitecturalInputUtils.resolveOptionalExactPositiveInteger(this, INPUT_COLUMNS_ID, 3);
        if (columns == null) {
            writeInvalid("Columns must be an exact positive integer");
            return;
        }

        Integer rows = ArchitecturalInputUtils.resolveOptionalExactPositiveInteger(this, INPUT_ROWS_ID, 2);
        if (rows == null) {
            writeInvalid("Rows must be an exact positive integer");
            return;
        }

        Double wallThickness = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(
            this, INPUT_WALL_THICKNESS_ID, 0.4d);
        if (wallThickness == null) {
            writeInvalid("Wall Thickness must be a positive finite number");
            return;
        }

        Double openingWidth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(
            this, INPUT_OPENING_WIDTH_ID, 1.0d);
        if (openingWidth == null) {
            writeInvalid("Opening Width must be a positive finite number");
            return;
        }

        Double openingHeight = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(
            this, INPUT_OPENING_HEIGHT_ID, 1.5d);
        if (openingHeight == null) {
            writeInvalid("Opening Height must be a positive finite number");
            return;
        }

        Double margin = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_MARGIN_ID, 0.0d);
        if (margin == null) {
            writeInvalid("Margin must be a non-negative finite number");
            return;
        }

        final double openingDepth;
        if (ArchitecturalInputUtils.isConnected(this, INPUT_OPENING_DEPTH_ID)) {
            Double resolvedDepth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(
                this, INPUT_OPENING_DEPTH_ID, wallThickness);
            if (resolvedDepth == null) {
                writeInvalid("Opening Depth must be a positive finite number");
                return;
            }
            openingDepth = resolvedDepth;
        } else {
            openingDepth = wallThickness;
        }

        if (!GeometryOutputUtils.fitsArchitecturalInstanceBudget(columns, rows)) {
            writeInvalid("Requested instance count exceeds limit");
            return;
        }

        FaceArrayLayout layout = resolveFaceArrayLayout(
            frame, columns, rows, openingWidth, openingHeight, margin, VerticalAnchor.BOTTOM);
        if (layout == null) {
            writeInvalid("Requested array does not fit on face");
            return;
        }

        GeometryData wallGeometry = WallHostSupport.createWallSlab(frame, wallThickness);
        List<GeometryData> openings = buildOpenings(layout, openingDepth);
        if (openings == null) {
            writeInvalid("Failed to generate array geometry");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, wallGeometry);
        outputValues.put(OUTPUT_OPENINGS_ID, GeometryOutputUtils.packGeometry(openings));
        outputValues.put(OUTPUT_TOP_EDGE_ID, WallHostSupport.edgePath(frame, frame.height() / 2.0d));
        outputValues.put(OUTPUT_BOTTOM_EDGE_ID, WallHostSupport.edgePath(frame, -frame.height() / 2.0d));
        outputValues.put(OUTPUT_CENTER_LINE_ID, WallHostSupport.edgePath(frame, 0.0d));
        outputValues.put(OUTPUT_EXTERIOR_FACE_ID, WallHostSupport.exteriorFace(frame));
        outputValues.put(OUTPUT_INTERIOR_FACE_ID, WallHostSupport.interiorFace(frame, wallThickness));
        outputValues.put(OUTPUT_COUNT_ID, columns * rows);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private @Nullable List<GeometryData> buildOpenings(FaceArrayLayout layout, double openingDepth) {
        return buildFaceArray(layout, placement -> {
            Vector3d center = placement.centerOnFace().fma(openingDepth / 2.0d, layout.frame().zAxis());
            Vector3d halfExtents = new Vector3d(layout.elementWidth() / 2.0d, layout.elementHeight() / 2.0d, openingDepth / 2.0d);
            return ArchitecturalPrimitiveSupport.createOrientedBox(
                center, halfExtents, layout.frame().xAxis(), layout.frame().yAxis(), layout.frame().zAxis());
        });
    }

    private void writeInvalid(String error) {
        ArchitecturalNodeOutputs.putNull(outputValues,
            OUTPUT_GEOMETRY_ID, OUTPUT_OPENINGS_ID, OUTPUT_TOP_EDGE_ID, OUTPUT_BOTTOM_EDGE_ID,
            OUTPUT_CENTER_LINE_ID, OUTPUT_EXTERIOR_FACE_ID, OUTPUT_INTERIOR_FACE_ID);
        ArchitecturalNodeOutputs.putCount(outputValues, OUTPUT_COUNT_ID, 0);
        ArchitecturalNodeOutputs.markInvalid(outputValues, error);
    }
}

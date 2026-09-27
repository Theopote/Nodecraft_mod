package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Generates a rectangular grid of architectural columns with placement frames.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.column_grid",
    displayName = "Column Grid",
    description = "Generates a rectangular grid of columns with base/top points and placement frames",
    category = "geometry.architectural_primitives",
    order = 2
)
public class ColumnGridNode extends AbstractFaceArrayNode {

    private static final Set<String> ALLOWED_SHAPES = Set.of("cylinder", "box", "frustum");

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_COLUMNS_ID = "input_columns";
    private static final String INPUT_ROWS_ID = "input_rows";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_MARGIN_ID = "input_margin";
    private static final String INPUT_SHAPE_ID = "input_shape";
    private static final String INPUT_TOP_SCALE_ID = "input_top_scale";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_BASE_POINTS_ID = "output_base_points";
    private static final String OUTPUT_TOP_POINTS_ID = "output_top_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ColumnGridNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.column_grid");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the placement surface", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_COLUMNS_ID, "Columns", "Number of columns across the face width", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROWS_ID, "Rows", "Number of columns across the face height", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Base column radius or half-width", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Column height measured along the face normal", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MARGIN_ID, "Margin", "Outer margin from the face edge to the first column", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SHAPE_ID, "Shape", "Column shape: cylinder, box, or frustum", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_TOP_SCALE_ID, "Top Scale", "Top radius scale used for frustum columns", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing all columns", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Placement frames at each column base (face-aligned)", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BASE_POINTS_ID, "Base Points", "Column base points on the face", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TOP_POINTS_ID, "Top Points", "Column top points along the face normal", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of columns created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid column grid could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates a rectangular grid of columns with base/top points and placement frames";
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

        Double radius = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_RADIUS_ID, 0.5d);
        if (radius == null) {
            writeInvalid("Radius must be a positive finite number");
            return;
        }

        Double height = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_HEIGHT_ID, 3.0d);
        if (height == null) {
            writeInvalid("Height must be a positive finite number");
            return;
        }

        Double margin = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_MARGIN_ID, 0.0d);
        if (margin == null) {
            writeInvalid("Margin must be a non-negative finite number");
            return;
        }

        String shape = ArchitecturalInputUtils.resolveKnownStringEnum(this, INPUT_SHAPE_ID, "cylinder", ALLOWED_SHAPES);
        if (shape == null) {
            writeInvalid("Shape must be cylinder, box, or frustum");
            return;
        }

        Double topScale = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_TOP_SCALE_ID, 1.0d);
        if (topScale == null) {
            writeInvalid("Top Scale must be a positive finite number");
            return;
        }

        if (!GeometryOutputUtils.fitsArchitecturalInstanceBudget(columns, rows)) {
            writeInvalid("Requested instance count exceeds limit");
            return;
        }

        FaceArrayLayout layout = resolveFaceArrayLayout(
            face, columns, rows, radius * 2.0d, radius * 2.0d, margin, VerticalAnchor.BOTTOM);
        if (layout == null) {
            writeInvalid("Requested array does not fit on face");
            return;
        }

        List<GeometryData> columnsGeometry = buildColumns(layout, radius, height, topScale, shape);
        if (columnsGeometry == null) {
            writeInvalid("Failed to generate array geometry");
            return;
        }
        List<FrameData> frames = buildPlacementFrames(layout);
        List<PointData> basePoints = buildCenters(layout);
        List<PointData> topPoints = buildTopPoints(layout, height);

        outputValues.put(OUTPUT_GEOMETRY_ID, GeometryOutputUtils.packGeometry(columnsGeometry));
        outputValues.put(OUTPUT_FRAMES_ID, frames);
        outputValues.put(OUTPUT_BASE_POINTS_ID, basePoints);
        outputValues.put(OUTPUT_TOP_POINTS_ID, topPoints);
        outputValues.put(OUTPUT_COUNT_ID, columns * rows);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private List<PointData> buildTopPoints(FaceArrayLayout layout, double height) {
        List<PointData> tops = new ArrayList<>(layout.columns() * layout.rows());
        Vector3d up = layout.frame().zAxis();
        for (FaceArrayPlacement placement : enumeratePlacements(layout)) {
            tops.add(new PointData(new Vector3d(placement.centerOnFace()).fma(height, up)));
        }
        return List.copyOf(tops);
    }

    private @Nullable List<GeometryData> buildColumns(
        FaceArrayLayout layout,
        double radius,
        double height,
        double topScale,
        String shape
    ) {
        return buildFaceArray(layout, placement -> {
            Vector3d base = placement.centerOnFace();
            Vector3d top = new Vector3d(base).fma(height, layout.frame().zAxis());
            return createColumnGeometry(base, top, radius, topScale, shape, layout.frame());
        });
    }

    private GeometryData createColumnGeometry(
        Vector3d base,
        Vector3d top,
        double radius,
        double topScale,
        String shape,
        ArchitecturalPrimitiveSupport.FaceFrame frame
    ) {
        Vector3d axis = new Vector3d(top).sub(base);
        if ("box".equals(shape)) {
            Vector3d center = new Vector3d(base).add(top).mul(0.5d);
            Vector3d halfExtents = new Vector3d(radius, axis.length() / 2.0d, radius);
            return ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, frame.xAxis(), frame.zAxis(), frame.yAxis());
        }
        if ("frustum".equals(shape)) {
            return new FrustumConeGeometryData(base, top, radius, radius * topScale);
        }
        return new CylinderGeometryData(base, top, radius);
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_FRAMES_ID, null);
        outputValues.put(OUTPUT_BASE_POINTS_ID, null);
        outputValues.put(OUTPUT_TOP_POINTS_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

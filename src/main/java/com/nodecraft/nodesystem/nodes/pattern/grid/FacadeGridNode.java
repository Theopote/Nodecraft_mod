package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates evenly spaced facade cells on a box face.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.grid.facade_grid",
    displayName = "Facade Grid",
    description = "Generates facade cell centers and boundaries on a box face",
    category = "pattern.grid",
    order = 1
)
public class FacadeGridNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_COLUMNS_ID = "input_columns";
    private static final String INPUT_ROWS_ID = "input_rows";
    private static final String INPUT_MARGIN_X_ID = "input_margin_x";
    private static final String INPUT_MARGIN_Y_ID = "input_margin_y";

    private static final String OUTPUT_CENTER_POINTS_ID = "output_center_points";
    private static final String OUTPUT_CELL_BOUNDARIES_ID = "output_cell_boundaries";
    private static final String OUTPUT_CELL_WIDTH_ID = "output_cell_width";
    private static final String OUTPUT_CELL_HEIGHT_ID = "output_cell_height";
    private static final String OUTPUT_CELL_COUNT_ID = "output_cell_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public FacadeGridNode() {
        super(UUID.randomUUID(), "pattern.grid.facade_grid");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the facade surface", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_COLUMNS_ID, "Columns", "Number of facade cells across the width", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROWS_ID, "Rows", "Number of facade cells across the height", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MARGIN_X_ID, "Margin X", "Horizontal margin from the face edge", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MARGIN_Y_ID, "Margin Y", "Vertical margin from the face edge", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_POINTS_ID, "Center Points", "Center point of each facade cell", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CELL_BOUNDARIES_ID, "Cell Boundaries", "Closed polyline for each facade cell boundary", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CELL_WIDTH_ID, "Cell Width", "Resolved facade cell width", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CELL_HEIGHT_ID, "Cell Height", "Resolved facade cell height", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CELL_COUNT_ID, "Cell Count", "Total number of facade cells", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid facade grid was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates facade cell centers and boundaries on a box face";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        if (!(faceObj instanceof BoxFaceData face)) {
            writeEmptyOutputs();
            return;
        }

        Integer columnsValue = inputValues.get(INPUT_COLUMNS_ID) instanceof Integer i ? i : null;
        Integer rowsValue = inputValues.get(INPUT_ROWS_ID) instanceof Integer i ? i : null;
        int columns = columnsValue != null ? columnsValue : 3;
        int rows = rowsValue != null ? rowsValue : 3;
        if (columns <= 0 || rows <= 0) {
            writeEmptyOutputs();
            return;
        }

        Double marginXValue = resolveMargin(inputValues.get(INPUT_MARGIN_X_ID), 0.0d);
        Double marginYValue = resolveMargin(inputValues.get(INPUT_MARGIN_Y_ID), 0.0d);
        if (marginXValue == null || marginYValue == null) {
            writeEmptyOutputs();
            return;
        }

        GenerationLimits.GridAxisCounts gridCounts = GenerationLimits.clampExclusiveGridCounts(columns, rows, 1, 1);
        columns = gridCounts.xCount();
        rows = gridCounts.yCount();

        GridGeometry geometry = resolveGridGeometry(face, columns, rows, marginXValue, marginYValue);
        if (geometry == null) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> centerPoints = new ArrayList<>(rows * columns);
        List<PolylineData> boundaries = new ArrayList<>(rows * columns);

        double startX = -geometry.faceWidth / 2.0d + marginXValue + geometry.cellWidth / 2.0d;
        double startY = geometry.faceHeight / 2.0d - marginYValue - geometry.cellHeight / 2.0d;

        for (int row = 0; row < rows; row++) {
            double offsetY = startY - row * geometry.cellHeight;
            for (int column = 0; column < columns; column++) {
                double offsetX = startX + column * geometry.cellWidth;

                Vector3d center = new Vector3d(geometry.faceCenter)
                    .fma(offsetX, geometry.xAxis)
                    .fma(offsetY, geometry.yAxis);
                centerPoints.add(center);
                boundaries.add(createBoundary(center, geometry));
            }
        }

        outputValues.put(OUTPUT_CENTER_POINTS_ID, SpatialValueResolver.toPointDataList(centerPoints));
        outputValues.put(OUTPUT_CELL_BOUNDARIES_ID, List.copyOf(boundaries));
        outputValues.put(OUTPUT_CELL_WIDTH_ID, geometry.cellWidth);
        outputValues.put(OUTPUT_CELL_HEIGHT_ID, geometry.cellHeight);
        outputValues.put(OUTPUT_CELL_COUNT_ID, centerPoints.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private @Nullable Double resolveMargin(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            return null;
        }
        double margin = number.doubleValue();
        if (!Double.isFinite(margin) || margin < 0.0d) {
            return null;
        }
        return margin;
    }

    private GridGeometry resolveGridGeometry(BoxFaceData face, int columns, int rows, double marginX, double marginY) {
        List<Vector3d> corners = face.getCorners();
        if (corners.size() < 4) {
            return null;
        }

        Vector3d c0 = corners.get(0);
        Vector3d c1 = corners.get(1);
        Vector3d c3 = corners.get(3);

        Vector3d xAxis = new Vector3d(c1).sub(c0);
        Vector3d yHint = new Vector3d(c3).sub(c0);
        double faceWidth = xAxis.length();
        double faceHeight = yHint.length();
        if (faceWidth <= 1.0e-9d || faceHeight <= 1.0e-9d) {
            return null;
        }

        xAxis.normalize();
        Vector3d zAxis = new Vector3d(xAxis).cross(yHint);
        if (zAxis.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        zAxis.normalize();
        if (zAxis.dot(face.getNormal()) < 0.0d) {
            zAxis.negate();
        }
        Vector3d yAxis = new Vector3d(zAxis).cross(xAxis).normalize();

        double usableWidth = faceWidth - 2.0d * marginX;
        double usableHeight = faceHeight - 2.0d * marginY;
        if (usableWidth <= 1.0e-9d || usableHeight <= 1.0e-9d) {
            return null;
        }

        double cellWidth = usableWidth / columns;
        double cellHeight = usableHeight / rows;
        if (cellWidth <= 1.0e-9d || cellHeight <= 1.0e-9d) {
            return null;
        }

        return new GridGeometry(face.getCenter(), xAxis, yAxis, faceWidth, faceHeight, cellWidth, cellHeight);
    }

    private PolylineData createBoundary(Vector3d center, GridGeometry geometry) {
        double halfWidth = geometry.cellWidth / 2.0d;
        double halfHeight = geometry.cellHeight / 2.0d;

        Vector3d topLeft = new Vector3d(center).fma(-halfWidth, geometry.xAxis).fma(halfHeight, geometry.yAxis);
        Vector3d topRight = new Vector3d(center).fma(halfWidth, geometry.xAxis).fma(halfHeight, geometry.yAxis);
        Vector3d bottomRight = new Vector3d(center).fma(halfWidth, geometry.xAxis).fma(-halfHeight, geometry.yAxis);
        Vector3d bottomLeft = new Vector3d(center).fma(-halfWidth, geometry.xAxis).fma(-halfHeight, geometry.yAxis);

        List<Vec3d> polylinePoints = List.of(
            toVec3d(topLeft),
            toVec3d(topRight),
            toVec3d(bottomRight),
            toVec3d(bottomLeft),
            toVec3d(topLeft)
        );
        return new PolylineData(polylinePoints);
    }

    private Vec3d toVec3d(Vector3d point) {
        return new Vec3d(point.x, point.y, point.z);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_CENTER_POINTS_ID, List.of());
        outputValues.put(OUTPUT_CELL_BOUNDARIES_ID, List.of());
        outputValues.put(OUTPUT_CELL_WIDTH_ID, 0.0d);
        outputValues.put(OUTPUT_CELL_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_CELL_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private record GridGeometry(
        Vector3d faceCenter,
        Vector3d xAxis,
        Vector3d yAxis,
        double faceWidth,
        double faceHeight,
        double cellWidth,
        double cellHeight
    ) {
    }
}

package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BoxFaceValidator;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class FacadeGridNode extends AbstractPatternGridNode {

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

    @NodeProperty(displayName = "Columns", category = "Facade", order = 1)
    private int columns = 3;

    @NodeProperty(displayName = "Rows", category = "Facade", order = 2)
    private int rows = 3;

    @NodeProperty(displayName = "Margin X", category = "Facade", order = 3)
    private double marginX = 0.0d;

    @NodeProperty(displayName = "Margin Y", category = "Facade", order = 4)
    private double marginY = 0.0d;

    public FacadeGridNode() {
        super(UUID.randomUUID(), "pattern.grid.facade_grid");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the facade surface", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_COLUMNS_ID, "Columns", "Number of facade cells across the width", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROWS_ID, "Rows", "Number of facade cells across the height", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MARGIN_X_ID, "Margin X", "Horizontal margin from the face edge", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MARGIN_Y_ID, "Margin Y", "Vertical margin from the face edge", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_POINTS_ID, "Center Points", "Center point of each facade cell", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CELL_BOUNDARIES_ID, "Cell Boundaries", "Closed path for each facade cell boundary", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CELL_WIDTH_ID, "Cell Width", "Resolved facade cell width", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CELL_HEIGHT_ID, "Cell Height", "Resolved facade cell height", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CELL_COUNT_ID, "Cell Count", "Total number of facade cells", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Generates facade cell centers and boundaries on a box face";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        if (!(faceObj instanceof BoxFaceData face)) {
            writeFail("Missing or invalid BoxFace");
            return;
        }

        String faceError = BoxFaceValidator.validate(face);
        if (faceError != null) {
            writeFail(faceError);
            return;
        }

        Integer columnsValue = OptionalPortDrive.resolveOptionalInteger(this, INPUT_COLUMNS_ID, columns);
        Integer rowsValue = OptionalPortDrive.resolveOptionalInteger(this, INPUT_ROWS_ID, rows);
        if (columnsValue == null) {
            writeFail("Columns connected but invalid");
            return;
        }
        if (rowsValue == null) {
            writeFail("Rows connected but invalid");
            return;
        }

        String productError = GenerationLimits.validateGridProduct(
            columnsValue, rowsValue, GenerationLimits.MAX_LAYOUT_INSTANCES);
        if (productError != null) {
            writeFail(productError);
            return;
        }

        Double marginXValue = OptionalPortDrive.resolveOptionalDouble(this, INPUT_MARGIN_X_ID, marginX);
        Double marginYValue = OptionalPortDrive.resolveOptionalDouble(this, INPUT_MARGIN_Y_ID, marginY);
        if (marginXValue == null) {
            writeFail("Margin X connected but invalid");
            return;
        }
        if (marginYValue == null) {
            writeFail("Margin Y connected but invalid");
            return;
        }
        if (marginXValue < 0.0d) {
            writeFail("Margin X must be >= 0");
            return;
        }
        if (marginYValue < 0.0d) {
            writeFail("Margin Y must be >= 0");
            return;
        }

        GridGeometry geometry = resolveGridGeometry(face, columnsValue, rowsValue, marginXValue, marginYValue);
        if (geometry == null) {
            return;
        }

        List<Vector3d> centerPoints = new ArrayList<>(rowsValue * columnsValue);
        List<PolylineData> boundaries = new ArrayList<>(rowsValue * columnsValue);

        double startX = -geometry.faceWidth / 2.0d + marginXValue + geometry.cellWidth / 2.0d;
        double startY = geometry.faceHeight / 2.0d - marginYValue - geometry.cellHeight / 2.0d;

        for (int row = 0; row < rowsValue; row++) {
            double offsetY = startY - row * geometry.cellHeight;
            for (int column = 0; column < columnsValue; column++) {
                double offsetX = startX + column * geometry.cellWidth;

                Vector3d center = new Vector3d(geometry.faceCenter)
                    .fma(offsetX, geometry.xAxis)
                    .fma(offsetY, geometry.yAxis);
                if (!PointUtils.isFinite(center)) {
                    writeFail("Non-finite facade cell center");
                    return;
                }
                centerPoints.add(center);
                boundaries.add(createBoundary(center, geometry));
            }
        }

        markSuccess();
        outputValues.put(OUTPUT_CENTER_POINTS_ID, SpatialValueResolver.toPointDataList(centerPoints));
        outputValues.put(OUTPUT_CELL_BOUNDARIES_ID, List.copyOf(boundaries));
        outputValues.put(OUTPUT_CELL_WIDTH_ID, geometry.cellWidth);
        outputValues.put(OUTPUT_CELL_HEIGHT_ID, geometry.cellHeight);
        putIntOutputs(centerPoints.size(), OUTPUT_CELL_COUNT_ID);
    }

    private @Nullable GridGeometry resolveGridGeometry(
        BoxFaceData face,
        int columns,
        int rows,
        double marginX,
        double marginY
    ) {
        List<Vector3d> corners = face.getCorners();
        Vector3d c0 = corners.get(0);
        Vector3d c1 = corners.get(1);
        Vector3d c3 = corners.get(3);

        Vector3d xAxis = new Vector3d(c1).sub(c0);
        Vector3d yHint = new Vector3d(c3).sub(c0);
        double faceWidth = xAxis.length();
        double faceHeight = yHint.length();
        if (faceWidth <= 1.0e-9d || faceHeight <= 1.0e-9d) {
            writeFail("BoxFace has zero usable width or height");
            return null;
        }

        xAxis.normalize();
        Vector3d zAxis = new Vector3d(xAxis).cross(yHint);
        if (zAxis.lengthSquared() <= 1.0e-12d) {
            writeFail("BoxFace axes are degenerate");
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
            writeFail("Margin too large for usable facade area");
            return null;
        }

        double cellWidth = usableWidth / columns;
        double cellHeight = usableHeight / rows;
        if (!Double.isFinite(cellWidth) || !Double.isFinite(cellHeight)
                || cellWidth <= 1.0e-9d || cellHeight <= 1.0e-9d) {
            writeFail("Cell Width/Height must be finite and positive");
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

    private void writeFail(String error) {
        markInvalid(error);
        putEmptyListOutputs(OUTPUT_CENTER_POINTS_ID, OUTPUT_CELL_BOUNDARIES_ID);
        outputValues.put(OUTPUT_CELL_WIDTH_ID, 0.0d);
        outputValues.put(OUTPUT_CELL_HEIGHT_ID, 0.0d);
        putIntOutputs(0, OUTPUT_CELL_COUNT_ID);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("columns", columns);
        state.put("rows", rows);
        state.put("marginX", marginX);
        state.put("marginY", marginY);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("columns") instanceof Number value) {
            columns = value.intValue();
        }
        if (map.get("rows") instanceof Number value) {
            rows = value.intValue();
        }
        if (map.get("marginX") instanceof Number value) {
            marginX = value.doubleValue();
        }
        if (map.get("marginY") instanceof Number value) {
            marginY = value.doubleValue();
        }
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

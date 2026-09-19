package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates a rectangular grid of architectural columns from a box face.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.column_grid",
    displayName = "Column Grid",
    description = "Generates a rectangular grid of columns on a box face",
    category = "geometry.architectural_primitives",
    order = 2
)
public class ColumnGridNode extends AbstractFaceArrayNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_COLUMNS_ID = "input_columns";
    private static final String INPUT_ROWS_ID = "input_rows";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_MARGIN_ID = "input_margin";
    private static final String INPUT_SHAPE_ID = "input_shape";
    private static final String INPUT_TOP_SCALE_ID = "input_top_scale";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

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
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of columns created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid column grid could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a rectangular grid of columns on a box face";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);

        GeometryData geometry = null;
        int count = 0;
        boolean valid = false;

        if (faceObj instanceof BoxFaceData face) {
            int columns = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_COLUMNS_ID), 1);
            int rows = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_ROWS_ID), 1);
            double radius = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_RADIUS_ID), 0.5d);
            double height = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_HEIGHT_ID), 3.0d);
            double margin = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_MARGIN_ID), 0.0d);
            double topScale = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_TOP_SCALE_ID), 1.0d);
            String shape = resolveShape(inputValues.get(INPUT_SHAPE_ID));
            FaceArrayLayout layout = resolveFaceArrayLayout(face, columns, rows, radius * 2.0d, radius * 2.0d, margin, VerticalAnchor.BOTTOM);
            if (layout != null) {
                List<GeometryData> columnsGeometry = buildColumns(layout, radius, height, topScale, shape);
                if (!columnsGeometry.isEmpty()) {
                    geometry = new CompositeGeometryData(columnsGeometry);
                    count = columnsGeometry.size();
                    valid = true;
                }
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private List<GeometryData> buildColumns(
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
        String normalizedShape = shape.toLowerCase(Locale.ROOT);
        if ("box".equals(normalizedShape)) {
            Vector3d center = new Vector3d(base).add(top).mul(0.5d);
            Vector3d halfExtents = new Vector3d(radius, axis.length() / 2.0d, radius);
            return ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, frame.xAxis(), frame.zAxis(), frame.yAxis());
        }
        if ("frustum".equals(normalizedShape)) {
            return new FrustumConeGeometryData(base, top, radius, radius * topScale);
        }
        return new CylinderGeometryData(base, top, radius);
    }

    private String resolveShape(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim();
        }
        return "cylinder";
    }
}
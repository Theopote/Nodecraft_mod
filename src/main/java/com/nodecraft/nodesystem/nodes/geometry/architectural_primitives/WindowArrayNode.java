package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a rectangular array of inset opening boxes on a box face.
 */
@NodeInfo(
    id = "geometry.architectural_primitives.window_array",
    displayName = "Window Array",
    description = "Generates a rectangular array of inset window opening boxes on a box face",
    category = "geometry.architectural_primitives",
    order = 0
)
public class WindowArrayNode extends AbstractFaceArrayNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_COLUMNS_ID = "input_columns";
    private static final String INPUT_ROWS_ID = "input_rows";
    private static final String INPUT_WINDOW_WIDTH_ID = "input_window_width";
    private static final String INPUT_WINDOW_HEIGHT_ID = "input_window_height";
    private static final String INPUT_MARGIN_ID = "input_margin";
    private static final String INPUT_DEPTH_ID = "input_depth";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(
        displayName = "Default Depth",
        category = "Openings",
        order = 1,
        description = "Fallback opening depth when no depth input is connected."
    )
    private double defaultDepth = 1.0d;

    public WindowArrayNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.window_array");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the facade surface", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_COLUMNS_ID, "Columns", "Number of windows across the face width", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROWS_ID, "Rows", "Number of windows across the face height", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_WINDOW_WIDTH_ID, "Window Width", "Window opening width in blocks/meters", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_WINDOW_HEIGHT_ID, "Window Height", "Window opening height in blocks/meters", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MARGIN_ID, "Margin", "Outer margin from the face edge to the first opening", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DEPTH_ID, "Depth", "Inset depth of each opening into the solid", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing all opening boxes", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of opening boxes created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid opening array could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a rectangular array of inset window opening boxes on a box face";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        Object columnsObj = inputValues.get(INPUT_COLUMNS_ID);
        Object rowsObj = inputValues.get(INPUT_ROWS_ID);
        Object widthObj = inputValues.get(INPUT_WINDOW_WIDTH_ID);
        Object heightObj = inputValues.get(INPUT_WINDOW_HEIGHT_ID);
        Object marginObj = inputValues.get(INPUT_MARGIN_ID);
        Object depthObj = inputValues.get(INPUT_DEPTH_ID);

        GeometryData geometry = null;
        int count = 0;
        boolean valid = false;

        if (faceObj instanceof BoxFaceData face) {
            int columns = ArchitecturalPrimitiveSupport.resolvePositiveInt(columnsObj, 1);
            int rows = ArchitecturalPrimitiveSupport.resolvePositiveInt(rowsObj, 1);
            double windowWidth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(widthObj, 1.0d);
            double windowHeight = ArchitecturalPrimitiveSupport.resolvePositiveDouble(heightObj, 1.0d);
            double margin = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(marginObj, 0.0d);
            double depth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(depthObj, defaultDepth);

            FaceArrayLayout layout = resolveFaceArrayLayout(face, columns, rows, windowWidth, windowHeight, margin, VerticalAnchor.TOP);
            if (layout != null) {
                List<BoxGeometryData> openings = buildOpeningBoxes(layout, depth);
                geometry = new CompositeGeometryData(new ArrayList<>(openings));
                count = openings.size();
                valid = true;
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private List<BoxGeometryData> buildOpeningBoxes(
        FaceArrayLayout layout,
        double depth
    ) {
        return buildFaceArray(layout, placement -> {
            Vector3d center = placement.centerOnFace().fma(-depth / 2.0d, layout.frame().zAxis());
            Vector3d halfExtents = new Vector3d(layout.elementWidth() / 2.0d, layout.elementHeight() / 2.0d, depth / 2.0d);
            return ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, layout.frame().xAxis(), layout.frame().yAxis(), layout.frame().zAxis());
        });
    }

    public double getDefaultDepth() {
        return defaultDepth;
    }

    public void setDefaultDepth(double defaultDepth) {
        double resolved = Math.max(1.0e-6d, defaultDepth);
        if (Double.compare(this.defaultDepth, resolved) != 0) {
            this.defaultDepth = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("defaultDepth", defaultDepth);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map && map.get("defaultDepth") instanceof Number value) {
            setDefaultDepth(value.doubleValue());
        }
    }
}

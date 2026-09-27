package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a rectangular array of inset opening boxes on a box face.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
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
    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_CENTERS_ID = "output_centers";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

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
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Placement frames at each window center (face-aligned)", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CENTERS_ID, "Centers", "Window center points on the face", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of opening boxes created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid opening array could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates a rectangular array of inset window openings with placement frames";
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

        Double windowWidth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_WINDOW_WIDTH_ID, 1.0d);
        if (windowWidth == null) {
            writeInvalid("Window Width must be a positive finite number");
            return;
        }

        Double windowHeight = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_WINDOW_HEIGHT_ID, 1.0d);
        if (windowHeight == null) {
            writeInvalid("Window Height must be a positive finite number");
            return;
        }

        Double margin = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_MARGIN_ID, 0.0d);
        if (margin == null) {
            writeInvalid("Margin must be a non-negative finite number");
            return;
        }

        Double depth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_DEPTH_ID, defaultDepth);
        if (depth == null) {
            writeInvalid("Depth must be a positive finite number");
            return;
        }

        if (!GeometryOutputUtils.fitsArchitecturalInstanceBudget(columns, rows)) {
            writeInvalid("Requested instance count exceeds limit");
            return;
        }

        FaceArrayLayout layout = resolveFaceArrayLayout(
            face, columns, rows, windowWidth, windowHeight, margin, VerticalAnchor.TOP);
        if (layout == null) {
            writeInvalid("Requested array does not fit on face");
            return;
        }

        List<BoxGeometryData> openings = buildOpeningBoxes(layout, depth);
        List<FrameData> frames = buildPlacementFrames(layout);
        List<PointData> centers = buildCenters(layout);

        outputValues.put(OUTPUT_GEOMETRY_ID, GeometryOutputUtils.packGeometry(openings));
        outputValues.put(OUTPUT_FRAMES_ID, frames);
        outputValues.put(OUTPUT_CENTERS_ID, centers);
        outputValues.put(OUTPUT_COUNT_ID, columns * rows);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private List<BoxGeometryData> buildOpeningBoxes(FaceArrayLayout layout, double depth) {
        return buildFaceArray(layout, placement -> {
            Vector3d center = placement.centerOnFace().fma(-depth / 2.0d, layout.frame().zAxis());
            Vector3d halfExtents = new Vector3d(layout.elementWidth() / 2.0d, layout.elementHeight() / 2.0d, depth / 2.0d);
            return ArchitecturalPrimitiveSupport.createOrientedBox(
                center, halfExtents, layout.frame().xAxis(), layout.frame().yAxis(), layout.frame().zAxis());
        });
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_FRAMES_ID, null);
        outputValues.put(OUTPUT_CENTERS_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
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

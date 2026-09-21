package com.nodecraft.nodesystem.nodes.transform.placement;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryTransform;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Places geometry at one or more frames: local axes align to frame X/Y/Z, pivot maps to frame origin.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.placement.place_geometry_on_frames",
    displayName = "Place Geometry On Frames",
    description = "Places geometry copies onto FRAME / FRAME_LIST: pivot maps to each frame origin and local axes align to frame X/Y/Z",
    category = "transform.placement",
    order = 0
)
public class PlaceGeometryOnFramesNode extends BaseNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_PIVOT_ID = "input_pivot";
    private static final String INPUT_FRAME_ID = "input_frame";
    private static final String INPUT_FRAMES_ID = "input_frames";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_GEOMETRIES_ID = "output_geometries";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PlaceGeometryOnFramesNode() {
        super(UUID.randomUUID(), "transform.placement.place_geometry_on_frames");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to place", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PIVOT_ID, "Pivot", "Local pivot point that maps to each frame origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_FRAME_ID, "Frame", "Optional single placement frame", NodeDataType.FRAME, this));
        addInputPort(new BasePort(INPUT_FRAMES_ID, "Frames", "Optional list of placement frames", NodeDataType.FRAME_LIST, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Placed geometry (composite when multiple frames)", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRIES_ID, "Geometries", "List of placed geometry copies", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of placed copies", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when placement fails", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when at least one copy was placed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Places geometry copies onto FRAME / FRAME_LIST: pivot maps to each frame origin and local axes align to frame X/Y/Z";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeResult(List.of(), false, "Missing geometry input");
            return;
        }

        List<FrameData> frames = resolveFrames();
        if (frames.isEmpty()) {
            writeResult(List.of(), false, "Connect Frame or Frames");
            return;
        }

        Vector3d pivot = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_PIVOT_ID));
        if (pivot == null) {
            pivot = new Vector3d();
        }
        if (!isFinite(pivot)) {
            writeResult(List.of(), false, "Pivot contains NaN or Infinity");
            return;
        }

        List<GeometryData> copies = new ArrayList<>(frames.size());
        for (FrameData frame : frames) {
            GeometryData placed = placeOnFrame(geometry, pivot, frame);
            if (placed != null) {
                copies.add(placed);
            }
        }

        writeResult(copies, !copies.isEmpty(), copies.isEmpty() ? "Unsupported geometry placement" : "");
    }

    /**
     * Shared placement kernel: align geometry local axes to frame X/Y/Z and map pivot to frame origin.
     */
    public static @Nullable GeometryData placeOnFrame(GeometryData geometry, Vector3d pivot, FrameData frame) {
        if (geometry == null || frame == null) {
            return null;
        }
        FrameData basis = frame.orthonormalized();
        if (basis == null) {
            return null;
        }
        Matrix3d rotation = basis.toRotationMatrix();
        Vector3d rotatedPivot = rotation.transform(new Vector3d(pivot), new Vector3d());
        Vector3d translation = new Vector3d(basis.getOrigin()).sub(rotatedPivot);
        return GeometryTransform.transform(geometry, translation, rotation, 1.0d);
    }

    private List<FrameData> resolveFrames() {
        List<FrameData> frames = new ArrayList<>();
        Object listObj = inputValues.get(INPUT_FRAMES_ID);
        if (listObj instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof FrameData frame) {
                    frames.add(frame);
                }
            }
        }
        if (!frames.isEmpty()) {
            return frames;
        }
        if (inputValues.get(INPUT_FRAME_ID) instanceof FrameData frame) {
            return List.of(frame);
        }
        return List.of();
    }

    private void writeResult(List<GeometryData> copies, boolean valid, String error) {
        outputValues.put(OUTPUT_GEOMETRIES_ID, List.copyOf(copies));
        if (copies.isEmpty()) {
            outputValues.put(OUTPUT_GEOMETRY_ID, null);
        } else if (copies.size() == 1) {
            outputValues.put(OUTPUT_GEOMETRY_ID, copies.getFirst());
        } else {
            outputValues.put(OUTPUT_GEOMETRY_ID, new CompositeGeometryData(copies));
        }
        outputValues.put(OUTPUT_COUNT_ID, copies.size());
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private static boolean isFinite(Vector3d vector) {
        return vector != null
            && Double.isFinite(vector.x)
            && Double.isFinite(vector.y)
            && Double.isFinite(vector.z);
    }
}

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
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryTransform;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
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
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PlaceGeometryOnFramesNode() {
        super(UUID.randomUUID(), "transform.placement.place_geometry_on_frames");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to place", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PIVOT_ID, "Pivot", "Local pivot point that maps to each frame origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_FRAME_ID, "Frame", "Single placement frame (mutually exclusive with Frames)", NodeDataType.FRAME, this));
        addInputPort(new BasePort(INPUT_FRAMES_ID, "Frames", "Placement frame list (mutually exclusive with Frame)", NodeDataType.FRAME_LIST, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Placed geometry (composite when multiple frames)", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of placed copies", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when placement fails", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when placement succeeded for every frame", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Places geometry copies onto FRAME / FRAME_LIST: pivot maps to each frame origin and local axes align to frame X/Y/Z";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeFail("Missing geometry input");
            return;
        }

        List<FrameData> frames = resolveFramesExclusive();
        if (frames == null) {
            return; // already wrote fail
        }
        if (frames.size() > GenerationLimits.MAX_GEOMETRY_INSTANCES) {
            writeFail("Frame count exceeds MAX_GEOMETRY_INSTANCES");
            return;
        }

        Vector3d pivot = OptionalPortDrive.resolveOptionalPoint(this, INPUT_PIVOT_ID, new Vector3d());
        if (pivot == null) {
            writeFail("Pivot connected but invalid");
            return;
        }

        List<GeometryData> copies = new ArrayList<>(frames.size());
        for (FrameData frame : frames) {
            GeometryData placed = placeOnFrame(geometry, pivot, frame);
            if (placed == null) {
                writeFail("Unsupported geometry placement");
                return;
            }
            copies.add(placed);
        }

        writeSuccess(copies);
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

    /**
     * Frames XOR Frame by connection. Returns null after writing failure.
     */
    private @Nullable List<FrameData> resolveFramesExclusive() {
        boolean framesConnected = OptionalPortDrive.isConnected(this, INPUT_FRAMES_ID);
        boolean frameConnected = OptionalPortDrive.isConnected(this, INPUT_FRAME_ID);

        if (framesConnected && frameConnected) {
            writeFail("Connect either Frame or Frames, not both");
            return null;
        }
        if (!framesConnected && !frameConnected) {
            writeFail("Connect Frame or Frames");
            return null;
        }

        if (framesConnected) {
            List<FrameData> frames = FrameUtils.resolveStrictFrameList(getInput(INPUT_FRAMES_ID));
            if (frames == null) {
                writeFail("Frames list is null, empty, or contains non-FRAME entries");
                return null;
            }
            return frames;
        }

        Object frameObj = getInput(INPUT_FRAME_ID);
        if (!(frameObj instanceof FrameData frame)) {
            writeFail("Frame connected but invalid");
            return null;
        }
        return List.of(frame);
    }

    private void writeSuccess(List<GeometryData> copies) {
        if (copies.isEmpty()) {
            writeFail("No geometry copies");
            return;
        }
        if (copies.size() == 1) {
            outputValues.put(OUTPUT_GEOMETRY_ID, copies.getFirst());
        } else {
            outputValues.put(OUTPUT_GEOMETRY_ID, new CompositeGeometryData(copies));
        }
        outputValues.put(OUTPUT_COUNT_ID, copies.size());
        outputValues.put(OUTPUT_ERROR_ID, "");
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeFail(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

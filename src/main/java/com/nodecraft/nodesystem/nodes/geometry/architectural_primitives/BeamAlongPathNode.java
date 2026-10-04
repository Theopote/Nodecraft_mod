package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Structural beams following a PATH centerline (one box per polyline edge).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.beam_along_path",
    displayName = "Beam Along Path",
    description = "Generates one structural beam box per path segment (not a continuous sweep)",
    category = "geometry.architectural_primitives",
    order = 16
)
public class BeamAlongPathNode extends BaseNode {

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_WIDTH_ID = "input_width";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_OFFSET_ID = "input_offset";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public BeamAlongPathNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.beam_along_path");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Beam centerline path", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width",
            "Beam width along local side (path right), not world axes", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height",
            "Beam depth along local frame up (follows path tilt; not world Y)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Signed sideways offset from the path (+ = path right)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Beams along the path", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Placement frames at each beam segment center", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of beam segments", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid beam could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates one structural beam box per path segment (not a continuous sweep)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ArchitecturalPathSupport.PathGeometry path =
            ArchitecturalPathSupport.resolve(getInput(INPUT_PATH_ID));
        if (path == null) {
            writeInvalid("Path is required (finite polyline with at least 2 points)");
            return;
        }

        List<ArchitecturalPathSupport.Segment> segments = ArchitecturalPathSupport.segments(path);
        if (segments.size() > GenerationLimits.MAX_ARCHITECTURAL_PATH_SEGMENTS) {
            writeInvalid("Path segment count exceeds limit ("
                + GenerationLimits.MAX_ARCHITECTURAL_PATH_SEGMENTS + ")");
            return;
        }
        if (segments.size() > GenerationLimits.MAX_ARCHITECTURAL_INSTANCES) {
            writeInvalid("Requested instance count exceeds limit ("
                + GenerationLimits.MAX_ARCHITECTURAL_INSTANCES + ")");
            return;
        }

        Double width = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_WIDTH_ID, 0.3d);
        if (width == null) {
            writeInvalid("Width must be a positive finite number");
            return;
        }
        Double height = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_HEIGHT_ID, 0.4d);
        if (height == null) {
            writeInvalid("Height must be a positive finite number");
            return;
        }
        Double offset = ArchitecturalInputUtils.resolveOptionalFiniteDouble(this, INPUT_OFFSET_ID, 0.0d);
        if (offset == null) {
            writeInvalid("Offset must be a finite number");
            return;
        }

        List<GeometryData> pieces = new ArrayList<>();
        List<FrameData> placementFrames = new ArrayList<>();
        for (ArchitecturalPathSupport.Segment segment : segments) {
            Vector3d direction = new Vector3d(segment.end()).sub(segment.start());
            double length = direction.length();
            if (length <= 1.0e-9d) {
                continue;
            }
            ArchitecturalPathSupport.SampleFrame frame =
                ArchitecturalPathSupport.frameForDirection(segment.start(), direction);
            Vector3d mid = new Vector3d(segment.start()).lerp(segment.end(), 0.5d)
                .fma(offset, frame.side());
            Vector3d halfExtents = new Vector3d(length / 2.0d, height / 2.0d, width / 2.0d);
            pieces.add(ArchitecturalPrimitiveSupport.createOrientedBox(
                mid, halfExtents, frame.tangent(), frame.up(), frame.side()));
            placementFrames.add(new FrameData(mid, frame.tangent(), frame.up(), frame.side()));
        }
        if (pieces.isEmpty()) {
            writeInvalid("Could not generate beam segments from the path");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, GeometryOutputUtils.packGeometry(pieces));
        outputValues.put(OUTPUT_FRAMES_ID, List.copyOf(placementFrames));
        outputValues.put(OUTPUT_COUNT_ID, pieces.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        ArchitecturalNodeOutputs.putNull(outputValues, OUTPUT_GEOMETRY_ID);
        ArchitecturalNodeOutputs.putEmptyLists(outputValues, OUTPUT_FRAMES_ID);
        ArchitecturalNodeOutputs.putCount(outputValues, OUTPUT_COUNT_ID, 0);
        ArchitecturalNodeOutputs.markInvalid(outputValues, error);
    }
}

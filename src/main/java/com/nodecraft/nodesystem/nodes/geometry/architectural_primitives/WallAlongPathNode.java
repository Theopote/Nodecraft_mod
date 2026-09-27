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
 * Continuous wall slabs following a PATH centerline (one segment box per polyline edge).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.wall_along_path",
    displayName = "Wall Along Path",
    description = "Generates continuous wall slabs along a path (line, polyline, or curve)",
    category = "geometry.architectural_primitives",
    order = 15
)
public class WallAlongPathNode extends BaseNode {

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_THICKNESS_ID = "input_thickness";
    private static final String INPUT_OFFSET_ID = "input_offset";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public WallAlongPathNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.wall_along_path");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Wall centerline path", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Wall height measured upward from the path", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Wall thickness across the path", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Sideways offset from the path", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Wall slabs along the path", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Placement frames at each wall segment center", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of wall segments", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid wall could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates continuous wall slabs along a path (line, polyline, or curve)";
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

        Double height = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_HEIGHT_ID, 3.0d);
        if (height == null) {
            writeInvalid("Height must be a positive finite number");
            return;
        }
        Double thickness = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_THICKNESS_ID, 0.4d);
        if (thickness == null) {
            writeInvalid("Thickness must be a positive finite number");
            return;
        }
        Double offset = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_OFFSET_ID, 0.0d);
        if (offset == null) {
            writeInvalid("Offset must be a non-negative finite number");
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
                .fma(offset, frame.side())
                .fma(height / 2.0d, frame.up());
            Vector3d halfExtents = new Vector3d(length / 2.0d, height / 2.0d, thickness / 2.0d);
            pieces.add(ArchitecturalPrimitiveSupport.createOrientedBox(
                mid, halfExtents, frame.tangent(), frame.up(), frame.side()));
            placementFrames.add(new FrameData(mid, frame.tangent(), frame.up(), frame.side()));
        }
        if (pieces.isEmpty()) {
            writeInvalid("Could not generate wall segments from the path");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, GeometryOutputUtils.packGeometry(pieces));
        outputValues.put(OUTPUT_FRAMES_ID, List.copyOf(placementFrames));
        outputValues.put(OUTPUT_COUNT_ID, pieces.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_FRAMES_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

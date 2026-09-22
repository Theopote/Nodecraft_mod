package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Structural beams following a PATH centerline (one segment box per polyline edge).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.beam_along_path",
    displayName = "Beam Along Path",
    description = "Generates structural beams along a path (line, polyline, or curve)",
    category = "geometry.architectural_primitives",
    order = 21
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

    public BeamAlongPathNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.beam_along_path");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Beam centerline path", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Beam width across the path", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Beam depth measured along local up", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Sideways offset from the path", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite beams along the path", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Placement frames at each beam segment center", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of beam segments", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid beam could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates structural beams along a path (line, polyline, or curve)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ArchitecturalPathSupport.PathGeometry path =
            ArchitecturalPathSupport.resolve(inputValues.get(INPUT_PATH_ID));

        GeometryData geometry = null;
        List<FrameData> frames = null;
        int count = 0;
        boolean valid = false;

        if (path != null) {
            double width = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_WIDTH_ID), 0.3d);
            double height = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_HEIGHT_ID), 0.4d);
            double offset = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_OFFSET_ID), 0.0d);

            List<GeometryData> pieces = new ArrayList<>();
            List<FrameData> placementFrames = new ArrayList<>();
            for (ArchitecturalPathSupport.Segment segment : ArchitecturalPathSupport.segments(path)) {
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
            if (!pieces.isEmpty()) {
                geometry = new CompositeGeometryData(pieces);
                frames = List.copyOf(placementFrames);
                count = pieces.size();
                valid = true;
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_FRAMES_ID, frames);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}

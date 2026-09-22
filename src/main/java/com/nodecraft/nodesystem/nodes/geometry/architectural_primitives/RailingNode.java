package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates a railing or balustrade that follows the true PATH centerline
 * (line, polyline, or curve) — not a first→last chord.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.railing",
    displayName = "Railing",
    description = "Generates a railing or balustrade that follows a path (line, polyline, or curve)",
    category = "geometry.architectural_primitives",
    order = 3
)
public class RailingNode extends BaseNode {

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_POST_COUNT_ID = "input_post_count";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_POST_RADIUS_ID = "input_post_radius";
    private static final String INPUT_RAIL_COUNT_ID = "input_rail_count";
    private static final String INPUT_RAIL_RADIUS_ID = "input_rail_radius";
    private static final String INPUT_OFFSET_ID = "input_offset";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RailingNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.railing");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Path used for the railing run (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_POST_COUNT_ID, "Post Count", "Number of posts placed along the path", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Railing height measured upward from the path", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_POST_RADIUS_ID, "Post Radius", "Radius of the balustrade posts", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RAIL_COUNT_ID, "Rail Count", "Number of horizontal rails", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_RAIL_RADIUS_ID, "Rail Radius", "Radius of the horizontal rails", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Sideways offset from the path", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the railing components", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of railing components created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid railing could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a railing or balustrade that follows a path (line, polyline, or curve)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ArchitecturalPathSupport.PathGeometry path =
            ArchitecturalPathSupport.resolve(inputValues.get(INPUT_PATH_ID));

        GeometryData geometry = null;
        int count = 0;
        boolean valid = false;

        if (path != null) {
            int postCount = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_POST_COUNT_ID), 2);
            int railCount = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_RAIL_COUNT_ID), 2);
            double height = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_HEIGHT_ID), 1.2d);
            double postRadius = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_POST_RADIUS_ID), 0.05d);
            double railRadius = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_RAIL_RADIUS_ID), postRadius * 0.65d);
            double offset = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_OFFSET_ID), 0.0d);

            List<GeometryData> railing = buildRailing(path, postCount, railCount, height, postRadius, railRadius, offset);
            if (!railing.isEmpty()) {
                geometry = new CompositeGeometryData(railing);
                count = railing.size();
                valid = true;
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private List<GeometryData> buildRailing(
        ArchitecturalPathSupport.PathGeometry path,
        int postCount,
        int railCount,
        double height,
        double postRadius,
        double railRadius,
        double offset
    ) {
        List<GeometryData> results = new ArrayList<>();

        List<ArchitecturalPathSupport.SampleFrame> posts = ArchitecturalPathSupport.sampleEvenly(path, postCount);
        for (ArchitecturalPathSupport.SampleFrame frame : posts) {
            Vector3d base = new Vector3d(frame.origin()).fma(offset, frame.side());
            Vector3d top = new Vector3d(base).fma(height, frame.up());
            results.add(new CylinderGeometryData(base, top, postRadius));
        }

        double railSpacing = railCount > 1 ? height / railCount : height;
        List<ArchitecturalPathSupport.Segment> segments = ArchitecturalPathSupport.segments(path);
        for (int level = 0; level < railCount; level++) {
            double railHeight = railCount > 1 ? railSpacing * (level + 1) : height;
            for (ArchitecturalPathSupport.Segment segment : segments) {
                Vector3d direction = new Vector3d(segment.end()).sub(segment.start());
                ArchitecturalPathSupport.SampleFrame frame =
                    ArchitecturalPathSupport.frameForDirection(segment.start(), direction);
                Vector3d railStart = new Vector3d(segment.start())
                    .fma(offset, frame.side())
                    .fma(railHeight, frame.up());
                Vector3d railEnd = new Vector3d(segment.end())
                    .fma(offset, frame.side())
                    .fma(railHeight, frame.up());
                if (railStart.distanceSquared(railEnd) > 1.0e-12d) {
                    results.add(new CylinderGeometryData(railStart, railEnd, railRadius));
                }
            }
        }

        return List.copyOf(results);
    }
}

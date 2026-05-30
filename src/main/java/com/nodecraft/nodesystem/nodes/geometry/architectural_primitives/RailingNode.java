package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates a straight railing or balustrade along a line segment.
 */
@NodeInfo(
    id = "geometry.architectural_primitives.railing",
    displayName = "Railing",
    description = "Generates a straight railing or balustrade along a line segment",
    category = "geometry.architectural_primitives",
    order = 3
)
public class RailingNode extends BaseNode {

    private static final String INPUT_LINE_ID = "input_line";
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

        addInputPort(new BasePort(INPUT_LINE_ID, "Line", "Straight path used for the railing run", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_POST_COUNT_ID, "Post Count", "Number of posts placed along the line", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Railing height measured upward from the path", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_POST_RADIUS_ID, "Post Radius", "Radius of the balustrade posts", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RAIL_COUNT_ID, "Rail Count", "Number of horizontal rails", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_RAIL_RADIUS_ID, "Rail Radius", "Radius of the horizontal rails", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Sideways offset from the line path", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the railing components", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of railing components created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid railing could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a straight railing or balustrade along a line segment";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object lineObj = inputValues.get(INPUT_LINE_ID);

        GeometryData geometry = null;
        int count = 0;
        boolean valid = false;

        if (lineObj instanceof LineData line) {
            Vec3d startVec = line.getStart();
            Vec3d endVec = line.getEnd();
            ArchitecturalPrimitiveSupport.LineFrame frame = ArchitecturalPrimitiveSupport.resolveLineFrame(startVec, endVec);
            if (frame != null) {
                int postCount = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_POST_COUNT_ID), 2);
                int railCount = ArchitecturalPrimitiveSupport.resolvePositiveInt(inputValues.get(INPUT_RAIL_COUNT_ID), 2);
                double height = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_HEIGHT_ID), 1.2d);
                double postRadius = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_POST_RADIUS_ID), 0.05d);
                double railRadius = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_RAIL_RADIUS_ID), postRadius * 0.65d);
                double offset = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_OFFSET_ID), 0.0d);

                List<GeometryData> railing = buildRailing(frame, postCount, railCount, height, postRadius, railRadius, offset);
                if (!railing.isEmpty()) {
                    geometry = new CompositeGeometryData(railing);
                    count = railing.size();
                    valid = true;
                }
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private List<GeometryData> buildRailing(
        ArchitecturalPrimitiveSupport.LineFrame frame,
        int postCount,
        int railCount,
        double height,
        double postRadius,
        double railRadius,
        double offset
    ) {
        List<GeometryData> results = new ArrayList<>(postCount + railCount);

        Vector3d baseOffset = new Vector3d(frame.sideAxis()).mul(offset);
        Vector3d topOffset = new Vector3d(frame.upAxis()).mul(height);

        double postSpacing = postCount > 1 ? frame.length() / (postCount - 1) : 0.0d;
        for (int index = 0; index < postCount; index++) {
            double distance = Math.min(frame.length(), index * postSpacing);
            Vector3d base = new Vector3d(frame.start()).fma(distance, frame.runAxis()).add(baseOffset);
            Vector3d top = new Vector3d(base).add(topOffset);
            results.add(new CylinderGeometryData(base, top, postRadius));
        }

        double railSpacing = railCount > 1 ? height / railCount : height;
        for (int level = 0; level < railCount; level++) {
            double railHeight = railCount > 1 ? railSpacing * (level + 1) : height;
            Vector3d railBase = new Vector3d(frame.start()).add(baseOffset).fma(railHeight, frame.upAxis());
            Vector3d railTop = new Vector3d(frame.end()).add(baseOffset).fma(railHeight, frame.upAxis());
            results.add(new CylinderGeometryData(railBase, railTop, railRadius));
        }

        return List.copyOf(results);
    }
}
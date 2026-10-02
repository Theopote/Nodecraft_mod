package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
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
 * Generates a railing that follows a joined offset centerline along a planar PATH.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.railing",
    displayName = "Railing",
    description = "Generates a railing or balustrade along a joined offset path (line or polyline)",
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
    private static final String INPUT_JOIN_ID = "input_join";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public RailingNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.railing");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Path used for the railing run (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_POST_COUNT_ID, "Post Count", "Number of posts placed along the path", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Railing height measured upward from the path", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_POST_RADIUS_ID, "Post Radius", "Radius of the balustrade posts", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RAIL_COUNT_ID, "Rail Count", "Number of horizontal rails", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_RAIL_RADIUS_ID, "Rail Radius", "Radius of the horizontal rails", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Signed sideways offset from the path (+ = path right)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_JOIN_ID, "Join", "Corner join policy: miter, bevel, or butt", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the railing components", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of railing components created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid railing could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates a railing or balustrade along a joined offset path (line or polyline)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ArchitecturalPathSupport.PathGeometry path =
            ArchitecturalPathSupport.resolve(inputValues.get(INPUT_PATH_ID));
        if (path == null) {
            writeInvalid(ArchitecturalInputUtils.isConnected(this, INPUT_PATH_ID)
                ? "Path must be a finite PATH with at least 2 points"
                : "Path is required");
            return;
        }

        Integer postCount = ArchitecturalInputUtils.resolveOptionalExactPositiveInteger(this, INPUT_POST_COUNT_ID, 2);
        if (postCount == null) {
            writeInvalid("Post Count must be an exact positive INTEGER");
            return;
        }
        if (!path.closed() && postCount < 2) {
            writeInvalid("Open paths require Post Count >= 2");
            return;
        }
        if (path.closed() && postCount < 3) {
            writeInvalid("Closed paths require Post Count >= 3");
            return;
        }
        Integer railCount = ArchitecturalInputUtils.resolveOptionalExactPositiveInteger(this, INPUT_RAIL_COUNT_ID, 2);
        if (railCount == null) {
            writeInvalid("Rail Count must be an exact positive INTEGER");
            return;
        }
        Double height = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_HEIGHT_ID, 1.2d);
        if (height == null) {
            writeInvalid("Height must be a finite positive DOUBLE");
            return;
        }
        Double postRadius = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_POST_RADIUS_ID, 0.05d);
        if (postRadius == null) {
            writeInvalid("Post Radius must be a finite positive DOUBLE");
            return;
        }
        Double railRadius = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(
            this, INPUT_RAIL_RADIUS_ID, postRadius * 0.65d);
        if (railRadius == null) {
            writeInvalid("Rail Radius must be a finite positive DOUBLE");
            return;
        }
        Double offset = ArchitecturalInputUtils.resolveOptionalFiniteDouble(this, INPUT_OFFSET_ID, 0.0d);
        if (offset == null) {
            writeInvalid("Offset must be a finite DOUBLE");
            return;
        }
        String joinText = ArchitecturalInputUtils.resolveKnownStringEnum(
            this, INPUT_JOIN_ID, "miter", ArchitecturalInputUtils.PATH_JOIN_MODES);
        if (joinText == null) {
            writeInvalid("Join must be one of: miter, bevel, butt");
            return;
        }
        ArchitecturalPathJoinSupport.JoinMode join = ArchitecturalPathJoinSupport.JoinMode.fromString(joinText);
        if (join == null) {
            writeInvalid("Join must be one of: miter, bevel, butt");
            return;
        }

        ArchitecturalPathSupport.PathGeometry offsetPath =
            ArchitecturalPathJoinSupport.offsetPath(path, offset, join);
        if (offsetPath == null) {
            writeInvalid("Could not build joined offset path (planar polyline required)");
            return;
        }

        List<ArchitecturalPathSupport.Segment> segments = ArchitecturalPathSupport.segments(offsetPath);
        if (segments.size() > GenerationLimits.MAX_ARCHITECTURAL_PATH_SEGMENTS) {
            writeInvalid("Path segment count exceeds MAX_ARCHITECTURAL_PATH_SEGMENTS ("
                + GenerationLimits.MAX_ARCHITECTURAL_PATH_SEGMENTS + ")");
            return;
        }

        long instanceBudget;
        try {
            instanceBudget = Math.addExact(
                (long) postCount,
                Math.multiplyExact((long) railCount, (long) segments.size())
            );
        } catch (ArithmeticException overflow) {
            writeInvalid("Requested railing instance count exceeds MAX_ARCHITECTURAL_INSTANCES ("
                + GenerationLimits.MAX_ARCHITECTURAL_INSTANCES + ")");
            return;
        }
        if (instanceBudget > GenerationLimits.MAX_ARCHITECTURAL_INSTANCES) {
            writeInvalid("Requested railing instance count exceeds MAX_ARCHITECTURAL_INSTANCES ("
                + GenerationLimits.MAX_ARCHITECTURAL_INSTANCES + ")");
            return;
        }

        List<GeometryData> railing = buildRailing(
            offsetPath, segments, postCount, railCount, height, postRadius, railRadius);
        if (railing.isEmpty()) {
            writeInvalid("Unable to generate railing geometry from the given path and parameters");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, GeometryOutputUtils.packGeometry(railing));
        outputValues.put(OUTPUT_COUNT_ID, railing.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private List<GeometryData> buildRailing(
        ArchitecturalPathSupport.PathGeometry path,
        List<ArchitecturalPathSupport.Segment> segments,
        int postCount,
        int railCount,
        double height,
        double postRadius,
        double railRadius
    ) {
        List<GeometryData> results = new ArrayList<>();

        List<ArchitecturalPathSupport.SampleFrame> posts = ArchitecturalPathSupport.sampleEvenly(path, postCount);
        for (ArchitecturalPathSupport.SampleFrame frame : posts) {
            Vector3d base = new Vector3d(frame.origin());
            Vector3d top = new Vector3d(base).fma(height, frame.up());
            results.add(new CylinderGeometryData(base, top, postRadius));
        }

        double railSpacing = railCount > 1 ? height / railCount : height;
        for (int level = 0; level < railCount; level++) {
            double railHeight = railCount > 1 ? railSpacing * (level + 1) : height;
            for (ArchitecturalPathSupport.Segment segment : segments) {
                Vector3d direction = new Vector3d(segment.end()).sub(segment.start());
                ArchitecturalPathSupport.SampleFrame frame =
                    ArchitecturalPathSupport.frameForDirection(segment.start(), direction);
                Vector3d railStart = new Vector3d(segment.start()).fma(railHeight, frame.up());
                Vector3d railEnd = new Vector3d(segment.end()).fma(railHeight, frame.up());
                if (railStart.distanceSquared(railEnd) > 1.0e-12d) {
                    results.add(new CylinderGeometryData(railStart, railEnd, railRadius));
                }
            }
        }

        return List.copyOf(results);
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Joined wall footprint extruded along a planar PATH centerline.
 * Historical Graph V97 residue. Height is always world +Y (vertical wall).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.wall_along_path",
    displayName = "Wall Along Path",
    description = "Generates a vertical wall (height along world +Y) along a planar XZ path; Offset resolves the bottom centerline",
    category = "geometry.architectural_primitives",
    order = 15
)
public class WallAlongPathNode extends BaseNode {

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_THICKNESS_ID = "input_thickness";
    private static final String INPUT_OFFSET_ID = "input_offset";
    private static final String INPUT_JOIN_ID = "input_join";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_BOTTOM_PATH_ID = "output_bottom_path";
    private static final String OUTPUT_TOP_PATH_ID = "output_top_path";
    private static final String OUTPUT_EXTERIOR_PATH_ID = "output_exterior_path";
    private static final String OUTPUT_INTERIOR_PATH_ID = "output_interior_path";
    private static final String OUTPUT_CENTER_LINE_ID = "output_center_line";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public WallAlongPathNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.wall_along_path");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Wall centerline path", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height",
            "Wall height along world +Y (vertical wall; path orientation does not tilt the wall)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Wall thickness across the path", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Signed sideways offset from the path (+ = path right)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_JOIN_ID, "Join", "Corner join policy: miter, bevel, or butt", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Joined wall extrusion along the path", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Placement frames at each path segment center", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BOTTOM_PATH_ID, "Bottom Centerline",
            "Resolved wall base centerline after Offset", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_LINE_ID, "Center Line",
            "Alias of Bottom Centerline (architect wall centerline)", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_TOP_PATH_ID, "Top Path",
            "Bottom centerline elevated along world +Y by Height", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_EXTERIOR_PATH_ID, "Exterior Path", "Exterior face centerline path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_INTERIOR_PATH_ID, "Interior Path", "Interior face centerline path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of extrusion pieces", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid wall could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates a vertical wall (height along world +Y) along a planar XZ path";
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
        Double offset = ArchitecturalInputUtils.resolveOptionalFiniteDouble(this, INPUT_OFFSET_ID, 0.0d);
        if (offset == null) {
            writeInvalid("Offset must be a finite number");
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

        GeometryData geometry = ArchitecturalPathJoinSupport.extrudeWallFootprint(
            path, thickness, height, offset, join);
        if (geometry == null) {
            writeInvalid("Could not generate joined wall from the path (planar polyline required)");
            return;
        }

        int pieceCount = countExtrusionPieces(geometry);
        if (pieceCount > GenerationLimits.MAX_ARCHITECTURAL_INSTANCES) {
            writeInvalid("Requested instance count exceeds limit ("
                + GenerationLimits.MAX_ARCHITECTURAL_INSTANCES + ")");
            return;
        }

        ArchitecturalPathSupport.PathGeometry offsetPath =
            ArchitecturalPathJoinSupport.offsetPath(path, offset, join);
        List<FrameData> placementFrames = new ArrayList<>();
        if (offsetPath != null) {
            for (ArchitecturalPathSupport.Segment segment : ArchitecturalPathSupport.segments(offsetPath)) {
                Vector3d direction = VectorUtils.safeSubtract(segment.end(), segment.start());
                double length = VectorUtils.safeLength(direction);
                if (!Double.isFinite(length) || length <= 1.0e-9d) {
                    continue;
                }
                ArchitecturalPathSupport.SampleFrame frame =
                    ArchitecturalPathSupport.frameForDirection(segment.start(), direction);
                Vector3d mid = VectorUtils.safeLerp(segment.start(), segment.end(), 0.5d);
                if (mid == null) {
                    continue;
                }
                mid.fma(height / 2.0d, frame.up());
                if (!VectorUtils.isFinite(mid)) {
                    continue;
                }
                FrameData placement = FrameData.orthonormal(mid, frame.tangent(), frame.up(), frame.side());
                if (placement == null) {
                    continue;
                }
                placementFrames.add(placement);
            }
        }

        double halfThickness = thickness / 2.0d;
        ArchitecturalPathSupport.PathGeometry bottomPath = offsetPath != null ? offsetPath : path;
        ArchitecturalPathSupport.PathGeometry exteriorPath =
            ArchitecturalPathJoinSupport.offsetPath(bottomPath, halfThickness, join);
        ArchitecturalPathSupport.PathGeometry interiorPath =
            ArchitecturalPathJoinSupport.offsetPath(bottomPath, -halfThickness, join);

        PathData bottom = pathToPathData(bottomPath);
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_FRAMES_ID, List.copyOf(placementFrames));
        outputValues.put(OUTPUT_BOTTOM_PATH_ID, bottom);
        outputValues.put(OUTPUT_CENTER_LINE_ID, bottom);
        outputValues.put(OUTPUT_TOP_PATH_ID, elevatePath(bottomPath, height));
        outputValues.put(OUTPUT_EXTERIOR_PATH_ID, pathToPathData(exteriorPath));
        outputValues.put(OUTPUT_INTERIOR_PATH_ID, pathToPathData(interiorPath));
        outputValues.put(OUTPUT_COUNT_ID, pieceCount);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private static @Nullable PathData pathToPathData(@Nullable ArchitecturalPathSupport.PathGeometry path) {
        if (path == null) {
            return null;
        }
        List<Vec3d> points = new ArrayList<>(path.unique().size() + (path.closed() ? 1 : 0));
        for (Vector3d point : path.unique()) {
            points.add(new Vec3d(point.x, point.y, point.z));
        }
        if (path.closed() && !points.isEmpty()) {
            Vec3d first = points.getFirst();
            points.add(new Vec3d(first.x, first.y, first.z));
        }
        return PathData.fromPolyline(new PolylineData(points));
    }

    private static @Nullable PathData elevatePath(@Nullable ArchitecturalPathSupport.PathGeometry path, double height) {
        if (path == null) {
            return null;
        }
        List<Vec3d> elevated = new ArrayList<>(path.unique().size());
        for (Vector3d point : path.unique()) {
            Vector3d raised = new Vector3d(point).fma(height, new Vector3d(0.0d, 1.0d, 0.0d));
            elevated.add(new Vec3d(raised.x, raised.y, raised.z));
        }
        ArchitecturalPathSupport.PathGeometry elevatedPath = ArchitecturalPathSupport.resolve(
            PathData.fromPolyline(new PolylineData(elevated)));
        return pathToPathData(elevatedPath);
    }

    private static int countExtrusionPieces(GeometryData geometry) {
        if (geometry instanceof CompositeGeometryData composite) {
            return composite.size();
        }
        if (geometry instanceof PrismGeometryData) {
            return 1;
        }
        return 1;
    }

    private void writeInvalid(String error) {
        ArchitecturalNodeOutputs.putNull(outputValues,
            OUTPUT_GEOMETRY_ID, OUTPUT_BOTTOM_PATH_ID, OUTPUT_CENTER_LINE_ID,
            OUTPUT_TOP_PATH_ID, OUTPUT_EXTERIOR_PATH_ID, OUTPUT_INTERIOR_PATH_ID);
        ArchitecturalNodeOutputs.putEmptyLists(outputValues, OUTPUT_FRAMES_ID);
        ArchitecturalNodeOutputs.putCount(outputValues, OUTPUT_COUNT_ID, 0);
        ArchitecturalNodeOutputs.markInvalid(outputValues, error);
    }
}

package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Vector3;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.extrude_from_points",
    displayName = "Extrude Point List",
    description = "Extrudes an ordered point list by a direction vector and emits source path, top path, and side segments",
    category = "geometry.solids",
    order = 1
)
public class ExtrudePointListNode extends BaseNode {

    @NodeProperty(displayName = "Close Path", category = "Extrude", order = 1)
    private boolean closePath = true;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_DIRECTION_ID = "input_direction";

    private static final String OUTPUT_SOURCE_POINTS_ID = "output_source_points";
    private static final String OUTPUT_EXTRUDED_POINTS_ID = "output_extruded_points";
    private static final String OUTPUT_SOURCE_PATH_ID = "output_source_path";
    private static final String OUTPUT_EXTRUDED_PATH_ID = "output_extruded_path";
    private static final String OUTPUT_SIDE_SEGMENTS_ID = "output_side_segments";
    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ExtrudePointListNode() {
        super(UUID.randomUUID(), "geometry.solids.extrude_from_points");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Ordered point list to extrude", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_DIRECTION_ID, "Direction", "Extrusion direction vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_SOURCE_POINTS_ID, "Source Points", "Original ordered point list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_EXTRUDED_POINTS_ID, "Extruded Points", "Extruded ordered point list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SOURCE_PATH_ID, "Source Path", "Polyline describing the source contour", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_EXTRUDED_PATH_ID, "Extruded Path", "Polyline describing the extruded contour", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_SEGMENTS_ID, "Side Segments", "List of line segments connecting source and extruded points", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Reusable strip surface connecting source and extruded sections", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of source points used for the extrusion", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid point list and direction were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extrudes an ordered point list by a direction vector and emits source path, top path, and side segments";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        Vector3d direction = resolveDirection(inputValues.get(INPUT_DIRECTION_ID));

        if (!(pointsObj instanceof List<?> pointsInput) || direction == null) {
            writeEmptyOutputs();
            return;
        }

        if (direction.lengthSquared() <= 1.0e-12d) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> sourcePoints = new ArrayList<>();
        List<Vector3d> extrudedPoints = new ArrayList<>();
        List<LineData> sideSegments = new ArrayList<>();

        for (Object entry : pointsInput) {
            Vector3d point = resolvePoint(entry);
            if (point == null) {
                continue;
            }

            Vector3d sourcePoint = new Vector3d(point);
            Vector3d extrudedPoint = new Vector3d(point).add(direction);
            sourcePoints.add(sourcePoint);
            extrudedPoints.add(extrudedPoint);
            sideSegments.add(new LineData(
                new Vec3d(sourcePoint.x, sourcePoint.y, sourcePoint.z),
                new Vec3d(extrudedPoint.x, extrudedPoint.y, extrudedPoint.z)
            ));
        }

        if (sourcePoints.size() < 2) {
            writeEmptyOutputs();
            return;
        }

        PolylineData sourcePath = createPolyline(sourcePoints, closePath);
        PolylineData extrudedPath = createPolyline(extrudedPoints, closePath);
        SurfaceStripData surfaceStrip = new SurfaceStripData(
            List.of(List.copyOf(sourcePoints), List.copyOf(extrudedPoints)),
            List.of(closePath, closePath)
        );

        outputValues.put(OUTPUT_SOURCE_POINTS_ID, List.copyOf(sourcePoints));
        outputValues.put(OUTPUT_EXTRUDED_POINTS_ID, List.copyOf(extrudedPoints));
        outputValues.put(OUTPUT_SOURCE_PATH_ID, sourcePath);
        outputValues.put(OUTPUT_EXTRUDED_PATH_ID, extrudedPath);
        outputValues.put(OUTPUT_SIDE_SEGMENTS_ID, List.copyOf(sideSegments));
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
        outputValues.put(OUTPUT_COUNT_ID, sourcePoints.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public boolean isClosePath() {
        return closePath;
    }

    public void setClosePath(boolean closePath) {
        this.closePath = closePath;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("closePath", closePath);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map && map.get("closePath") instanceof Boolean closePathValue) {
            setClosePath(closePathValue);
        }
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SOURCE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_EXTRUDED_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SOURCE_PATH_ID, null);
        outputValues.put(OUTPUT_EXTRUDED_PATH_ID, null);
        outputValues.put(OUTPUT_SIDE_SEGMENTS_ID, List.of());
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }

    private @Nullable Vector3d resolveDirection(@Nullable Object value) {
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vector) {
            return new Vector3d(vector.x, vector.y, vector.z);
        }
        if (value instanceof Vector3 vector) {
            return new Vector3d(vector.getX(), vector.getY(), vector.getZ());
        }
        return null;
    }

    private PolylineData createPolyline(List<Vector3d> points, boolean closed) {
        List<Vec3d> polylinePoints = new ArrayList<>(points.size() + 1);
        for (Vector3d point : points) {
            polylinePoints.add(new Vec3d(point.x, point.y, point.z));
        }
        if (closed && points.size() >= 2) {
            Vector3d first = points.getFirst();
            Vector3d last = points.getLast();
            if (!first.equals(last)) {
                polylinePoints.add(new Vec3d(first.x, first.y, first.z));
            }
        }
        return polylinePoints.size() >= 2 ? new PolylineData(polylinePoints) : null;
    }
}

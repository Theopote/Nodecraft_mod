package com.nodecraft.nodesystem.nodes.spatial.construct;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "spatial.construct.polygon_by_points",
    displayName = "Polygon By Points",
    description = "Constructs a planar polygon profile from an ordered point list",
    category = "spatial.construct"
)
public class PolygonByPointsNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_EDGE_COUNT_ID = "output_edge_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    private static final double PLANAR_TOLERANCE = 1.0e-5d;

    public PolygonByPointsNode() {
        super(UUID.randomUUID(), "spatial.construct.polygon_by_points");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Ordered polygon points", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed planar polygon points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Resolved polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed polygon boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved polygon plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Average polygon center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_COUNT_ID, "Edges", "Number of polygon edges", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the input resolves to a planar polygon", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a planar polygon profile from an ordered point list";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        if (!(pointsObj instanceof List<?> pointListInput)) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> points = resolvePointList(pointListInput);
        if (points.size() >= 2 && points.get(0).distance(points.get(points.size() - 1)) <= PLANAR_TOLERANCE) {
            points = new ArrayList<>(points.subList(0, points.size() - 1));
        }
        if (points.size() < 3) {
            writeEmptyOutputs();
            return;
        }

        PlaneData plane = computePlane(points);
        if (plane == null || !isCoplanar(points, plane)) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> closedPoints = new ArrayList<>(points);
        closedPoints.add(new Vector3d(points.get(0)));

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(closedPoints));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(closedPoints, plane));
        outputValues.put(OUTPUT_BOUNDARY_ID, toPolyline(closedPoints));
        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_CENTER_ID, averagePoint(points));
        outputValues.put(OUTPUT_EDGE_COUNT_ID, points.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_EDGE_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Vector3d> resolvePointList(List<?> input) {
        List<Vector3d> resolved = new ArrayList<>(input.size());
        for (Object value : input) {
            Vector3d point = resolvePoint(value);
            if (point != null) {
                resolved.add(point);
            }
        }
        return resolved;
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

    private PlaneData computePlane(List<Vector3d> points) {
        Vector3d first = points.get(0);
        for (int i = 1; i < points.size() - 1; i++) {
            for (int j = i + 1; j < points.size(); j++) {
                Vector3d a = new Vector3d(points.get(i)).sub(first);
                Vector3d b = new Vector3d(points.get(j)).sub(first);
                Vector3d normal = a.cross(b, new Vector3d());
                if (normal.lengthSquared() > PLANAR_TOLERANCE * PLANAR_TOLERANCE) {
                    return new PlaneData(first, normal.normalize());
                }
            }
        }
        return null;
    }

    private boolean isCoplanar(List<Vector3d> points, PlaneData plane) {
        for (Vector3d point : points) {
            if (Math.abs(plane.signedDistanceTo(point)) > PLANAR_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private Vector3d averagePoint(List<Vector3d> points) {
        Vector3d average = new Vector3d();
        for (Vector3d point : points) {
            average.add(point);
        }
        return average.div(points.size());
    }

    private PolylineData toPolyline(List<Vector3d> points) {
        List<Vec3d> vecPoints = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            vecPoints.add(new Vec3d(point.x, point.y, point.z));
        }
        return new PolylineData(vecPoints);
    }
}

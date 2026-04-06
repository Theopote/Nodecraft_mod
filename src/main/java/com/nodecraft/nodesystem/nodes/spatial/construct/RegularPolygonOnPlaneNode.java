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
    id = "spatial.construct.regular_polygon_on_plane",
    displayName = "Regular Polygon On Plane",
    description = "Constructs a regular polygon from a center point, radius, side count, and plane",
    category = "spatial.construct"
)
public class RegularPolygonOnPlaneNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_SIDE_COUNT_ID = "input_side_count";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_START_DIRECTION_ID = "input_start_direction";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_SIDE_COUNT_ID = "output_side_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RegularPolygonOnPlaneNode() {
        super(UUID.randomUUID(), "spatial.construct.regular_polygon_on_plane");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Polygon center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Circumradius of the polygon", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SIDE_COUNT_ID, "Sides", "Number of polygon sides", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_START_DIRECTION_ID, "Start Direction", "Optional in-plane direction to the first vertex", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed regular polygon points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Regular polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed regular polygon boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved polygon center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Resolved radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_COUNT_ID, "Sides", "Resolved side count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a polygon could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a regular polygon from a center point, radius, side count, and plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);
        Object sideCountObj = inputValues.get(INPUT_SIDE_COUNT_ID);
        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData p ? p : PlaneData.XY_PLANE;
        Vector3d preferredAxis = inputValues.get(INPUT_START_DIRECTION_ID) instanceof Vector3d vector ? new Vector3d(vector) : null;

        if (center == null || !(radiusObj instanceof Number radiusNumber) || !(sideCountObj instanceof Number sideCountNumber)) {
            writeEmptyOutputs();
            return;
        }

        double radius = radiusNumber.doubleValue();
        int sideCount = sideCountNumber.intValue();
        if (!Double.isFinite(radius) || radius <= 0.0d || sideCount < 3) {
            writeEmptyOutputs();
            return;
        }

        Basis basis = createBasis(plane, preferredAxis);
        if (basis == null) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> points = new ArrayList<>(sideCount + 1);
        double step = (Math.PI * 2.0d) / sideCount;
        for (int i = 0; i < sideCount; i++) {
            double angle = step * i;
            Vector3d point = new Vector3d(center)
                .add(new Vector3d(basis.xAxis).mul(Math.cos(angle) * radius))
                .add(new Vector3d(basis.yAxis).mul(Math.sin(angle) * radius));
            points.add(point);
        }
        points.add(new Vector3d(points.get(0)));

        PlaneData resolvedPlane = new PlaneData(center, basis.normal);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(points, resolvedPlane));
        outputValues.put(OUTPUT_BOUNDARY_ID, toPolyline(points));
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_RADIUS_ID, radius);
        outputValues.put(OUTPUT_SIDE_COUNT_ID, sideCount);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_SIDE_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private PolylineData toPolyline(List<Vector3d> points) {
        List<Vec3d> vecPoints = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            vecPoints.add(new Vec3d(point.x, point.y, point.z));
        }
        return new PolylineData(vecPoints);
    }

    private Basis createBasis(PlaneData plane, @Nullable Vector3d preferredXAxis) {
        Vector3d normal = plane.getNormal();
        if (normal.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        normal.normalize();

        Vector3d xAxis = preferredXAxis != null ? new Vector3d(preferredXAxis) : null;
        if (xAxis != null) {
            xAxis.sub(new Vector3d(normal).mul(xAxis.dot(normal)));
        }
        if (xAxis == null || xAxis.lengthSquared() <= 1.0e-12d) {
            xAxis = fallbackAxis(normal);
        }
        if (xAxis.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        xAxis.normalize();

        Vector3d yAxis = new Vector3d(normal).cross(xAxis);
        if (yAxis.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        yAxis.normalize();
        xAxis = new Vector3d(yAxis).cross(normal).normalize();

        return new Basis(xAxis, yAxis, normal);
    }

    private Vector3d fallbackAxis(Vector3d normal) {
        Vector3d reference = Math.abs(normal.z) < 0.99d
            ? new Vector3d(0.0d, 0.0d, 1.0d)
            : new Vector3d(0.0d, 1.0d, 0.0d);
        return reference.sub(new Vector3d(normal).mul(reference.dot(normal)));
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

    private record Basis(Vector3d xAxis, Vector3d yAxis, Vector3d normal) { }
}

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
    id = "spatial.construct.rectangle_on_plane",
    displayName = "Rectangle On Plane",
    description = "Constructs a planar rectangle from a center point, width, height, and plane",
    category = "spatial.construct"
)
public class RectangleOnPlaneNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_WIDTH_ID = "input_width";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_X_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_WIDTH_ID = "output_width";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RectangleOnPlaneNode() {
        super(UUID.randomUUID(), "spatial.construct.rectangle_on_plane");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Rectangle center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Rectangle width along local X axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Rectangle height along local Y axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_X_AXIS_ID, "X Axis", "Optional in-plane axis to control rectangle rotation", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Rectangle corner points in closed order", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Rectangle polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed rectangle boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved rectangle center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_WIDTH_ID, "Width", "Resolved width", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Resolved height", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a rectangle could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a planar rectangle from a center point, width, height, and plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object widthObj = inputValues.get(INPUT_WIDTH_ID);
        Object heightObj = inputValues.get(INPUT_HEIGHT_ID);
        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData p ? p : PlaneData.XY_PLANE;
        Vector3d preferredXAxis = inputValues.get(INPUT_X_AXIS_ID) instanceof Vector3d vector ? new Vector3d(vector) : null;

        if (center == null || !(widthObj instanceof Number widthNumber) || !(heightObj instanceof Number heightNumber)) {
            writeEmptyOutputs();
            return;
        }

        double width = widthNumber.doubleValue();
        double height = heightNumber.doubleValue();
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0.0d || height <= 0.0d) {
            writeEmptyOutputs();
            return;
        }

        Basis basis = createBasis(plane, preferredXAxis);
        if (basis == null) {
            writeEmptyOutputs();
            return;
        }

        Vector3d halfX = new Vector3d(basis.xAxis).mul(width * 0.5d);
        Vector3d halfY = new Vector3d(basis.yAxis).mul(height * 0.5d);

        List<Vector3d> corners = new ArrayList<>(5);
        corners.add(new Vector3d(center).sub(halfX).sub(halfY));
        corners.add(new Vector3d(center).add(halfX).sub(halfY));
        corners.add(new Vector3d(center).add(halfX).add(halfY));
        corners.add(new Vector3d(center).sub(halfX).add(halfY));
        corners.add(new Vector3d(corners.get(0)));

        PlaneData resolvedPlane = new PlaneData(center, basis.normal);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(corners));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(corners, resolvedPlane));
        outputValues.put(OUTPUT_BOUNDARY_ID, toPolyline(corners));
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_WIDTH_ID, width);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_WIDTH_ID, 0.0d);
        outputValues.put(OUTPUT_HEIGHT_ID, 0.0d);
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

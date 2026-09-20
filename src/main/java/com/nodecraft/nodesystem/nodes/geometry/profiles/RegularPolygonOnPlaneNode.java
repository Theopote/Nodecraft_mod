package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.profiles.polygon_profile",
    displayName = "Regular Polygon On Plane",
    description = "Constructs a regular polygon from radius, sides, and an optional center/plane (defaults to XZ)",
    category = "geometry.profiles",
    order = 1
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

    @NodeProperty(displayName = "Radius", category = "Size", order = 1)
    private double radius = 5.0d;

    @NodeProperty(displayName = "Sides", category = "Size", order = 2)
    private int sides = 6;

    public RegularPolygonOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.polygon_profile");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override; otherwise uses Plane origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Circumradius of the polygon", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SIDE_COUNT_ID, "Sides", "Number of polygon sides", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XZ (horizontal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_START_DIRECTION_ID, "Start Direction", "Optional in-plane direction to the first vertex", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed regular polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Regular polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed regular polygon boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved polygon center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Resolved radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_COUNT_ID, "Sides", "Resolved side count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a polygon could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a regular polygon from radius, sides, and an optional center/plane (defaults to XZ)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlaneData plane = ProfilePlaneUtils.resolvePlane(inputValues.get(INPUT_PLANE_ID));
        Vector3d center = ProfilePlaneUtils.resolveCenter(inputValues.get(INPUT_CENTER_ID), plane);
        double resolvedRadius = resolveDouble(inputValues.get(INPUT_RADIUS_ID), radius);
        int sideCount = resolveSides();
        Vector3d preferredAxis = inputValues.get(INPUT_START_DIRECTION_ID) instanceof Vector3d vector ? new Vector3d(vector) : null;

        if (!Double.isFinite(resolvedRadius) || resolvedRadius <= 0.0d || sideCount < 3) {
            writeEmptyOutputs();
            return;
        }

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferredAxis);
        if (basis == null) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> points = new ArrayList<>(sideCount + 1);
        double step = (Math.PI * 2.0d) / sideCount;
        for (int i = 0; i < sideCount; i++) {
            double angle = step * i;
            Vector3d point = new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(Math.cos(angle) * resolvedRadius))
                .add(new Vector3d(basis.yAxis()).mul(Math.sin(angle) * resolvedRadius));
            points.add(point);
        }
        points.add(new Vector3d(points.get(0)));

        PlaneData resolvedPlane = new PlaneData(center, basis.normal());
        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(points));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(points, resolvedPlane));
        outputValues.put(OUTPUT_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(points));
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_RADIUS_ID, resolvedRadius);
        outputValues.put(OUTPUT_SIDE_COUNT_ID, sideCount);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private int resolveSides() {
        Object sideCountObj = inputValues.get(INPUT_SIDE_COUNT_ID);
        if (sideCountObj instanceof Number number) {
            return number.intValue();
        }
        return sides;
    }

    private static double resolveDouble(@Nullable Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
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

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("radius", radius);
        state.put("sides", sides);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("radius") instanceof Number n) radius = n.doubleValue();
        if (map.get("sides") instanceof Number n) sides = n.intValue();
    }
}

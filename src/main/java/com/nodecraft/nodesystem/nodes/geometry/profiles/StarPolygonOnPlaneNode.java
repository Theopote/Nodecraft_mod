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
    id = "geometry.profiles.star_polygon_profile",
    displayName = "Star Polygon On Plane",
    description = "Constructs a star polygon profile from center, inner/outer radii, point count, and plane (defaults to XZ)",
    category = "geometry.profiles",
    order = 15
)
public class StarPolygonOnPlaneNode extends BaseNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_OUTER_RADIUS_ID = "input_outer_radius";
    private static final String INPUT_INNER_RADIUS_ID = "input_inner_radius";
    private static final String INPUT_POINT_COUNT_ID = "input_point_count";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_start_direction";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Outer Radius", category = "Size", order = 1)
    private double outerRadius = 5.0d;

    @NodeProperty(displayName = "Inner Radius", category = "Size", order = 2)
    private double innerRadius = 2.5d;

    @NodeProperty(displayName = "Points", category = "Size", order = 3)
    private int pointCount = 5;

    public StarPolygonOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.star_polygon_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override; otherwise uses Plane origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_OUTER_RADIUS_ID, "Outer Radius", "Radius of outer star points", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_INNER_RADIUS_ID, "Inner Radius", "Radius of inner star points", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_POINT_COUNT_ID, "Points", "Number of outer points", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XZ (horizontal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Start Direction", "Optional in-plane direction to first outer point", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed star polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Star polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed star polygon boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved star polygon center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when star polygon profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a star polygon profile from center, inner/outer radii, point count, and plane (defaults to XZ)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlaneData plane = ProfilePlaneUtils.resolvePlane(inputValues.get(INPUT_PLANE_ID));
        Vector3d center = ProfilePlaneUtils.resolveCenter(inputValues.get(INPUT_CENTER_ID), plane);
        double outer = resolveDouble(inputValues.get(INPUT_OUTER_RADIUS_ID), outerRadius);
        double inner = resolveDouble(inputValues.get(INPUT_INNER_RADIUS_ID), innerRadius);
        int resolvedPointCount = resolvePointCount();
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (!Double.isFinite(outer) || !Double.isFinite(inner) || outer <= 0.0d || inner <= 0.0d || inner >= outer || resolvedPointCount < 3) {
            writeInvalid();
            return;
        }

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        int totalVertices = resolvedPointCount * 2;
        double step = (Math.PI * 2.0d) / totalVertices;
        List<Vector3d> points = new ArrayList<>(totalVertices + 1);
        for (int i = 0; i < totalVertices; i++) {
            double radius = (i % 2 == 0) ? outer : inner;
            double a = i * step;
            points.add(new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(Math.cos(a) * radius))
                .add(new Vector3d(basis.yAxis()).mul(Math.sin(a) * radius)));
        }
        points.add(new Vector3d(points.get(0)));

        PlaneData resolvedPlane = new PlaneData(center, basis.normal());
        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(points));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(points, resolvedPlane));
        outputValues.put(OUTPUT_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(points));
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private int resolvePointCount() {
        Object countObj = inputValues.get(INPUT_POINT_COUNT_ID);
        return countObj instanceof Number number ? Math.max(3, number.intValue()) : pointCount;
    }

    private static double resolveDouble(@Nullable Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("outerRadius", outerRadius);
        state.put("innerRadius", innerRadius);
        state.put("pointCount", pointCount);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("outerRadius") instanceof Number n) outerRadius = n.doubleValue();
        if (map.get("innerRadius") instanceof Number n) innerRadius = n.doubleValue();
        if (map.get("pointCount") instanceof Number n) pointCount = n.intValue();
    }
}

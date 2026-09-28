package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.ProfileConstructionUtils;
import com.nodecraft.nodesystem.util.ProfileInputUtils;
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
    order = 6
)
public class StarPolygonOnPlaneNode extends AbstractProfileNode {
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
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed star polygon boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved star polygon center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when star polygon profile was constructed", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Constructs a star polygon profile from center, inner/outer radii, point count, and plane (defaults to XZ)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlaneData plane = resolveConstructionPlane(INPUT_PLANE_ID);
        if (plane == null) {
            writeInvalid("Plane is invalid");
            return;
        }
        Vector3d center = resolveConstructionCenter(INPUT_CENTER_ID, plane);
        if (center == null) {
            writeInvalid("Center is invalid");
            return;
        }
        Double outer = resolvePositiveDouble(INPUT_OUTER_RADIUS_ID, outerRadius);
        if (outer == null) {
            writeInvalid("Outer radius must be a positive finite number");
            return;
        }
        Double inner = resolvePositiveDouble(INPUT_INNER_RADIUS_ID, innerRadius);
        if (inner == null) {
            writeInvalid("Inner radius must be a positive finite number");
            return;
        }
        Integer resolvedPointCount = resolveBoundedInteger(
            INPUT_POINT_COUNT_ID, pointCount, 3, GenerationLimits.MAX_PROFILE_VERTICES / 2);
        if (resolvedPointCount == null) {
            writeInvalid("Points must be an exact integer from 3 to " + (GenerationLimits.MAX_PROFILE_VERTICES / 2));
            return;
        }
        if (inner >= outer) {
            writeInvalid("Inner radius must be less than outer radius");
            return;
        }

        ProfilePlaneUtils.Basis basis = resolveConstructionBasis(plane, INPUT_AXIS_ID);
        if (basis == null) {
            writeInvalid(ProfileInputUtils.isConnected(this, INPUT_AXIS_ID)
                ? "In-plane axis is invalid"
                : "Failed to create profile basis");
            return;
        }

        int totalVertices = resolvedPointCount * 2;
        if (!isWithinProfileVertices(totalVertices)) {
            writeInvalid("Polygon profile vertex count exceeds limit (" + GenerationLimits.MAX_PROFILE_VERTICES + ")");
            return;
        }

        double step = (Math.PI * 2.0d) / totalVertices;
        List<Vector3d> points = new ArrayList<>(totalVertices + 1);
        for (int i = 0; i < totalVertices; i++) {
            double radius = (i % 2 == 0) ? outer : inner;
            double a = i * step;
            points.add(new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(Math.cos(a) * radius))
                .add(new Vector3d(basis.yAxis()).mul(Math.sin(a) * radius)));
        }
        points.add(new Vector3d(points.getFirst()));

        PlaneData resolvedPlane = new PlaneData(center, basis.normal());
        StringBuilder error = new StringBuilder();
        PolygonProfileData profile = ProfileConstructionUtils.tryCreateProfile(points, resolvedPlane, error);
        if (profile == null) {
            writeInvalid(error.isEmpty() ? "Failed to create star profile" : error.toString());
            return;
        }
        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(points));
        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_BOUNDARY_ID, pathFromProfile(profile));
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        markSuccess();
    }

    private void writeInvalid(String error) {
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putNullOutputs(OUTPUT_PROFILE_ID, OUTPUT_BOUNDARY_ID, OUTPUT_PLANE_ID, OUTPUT_CENTER_ID);
        markInvalid(error);
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

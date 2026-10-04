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
    id = "geometry.profiles.rounded_rectangle_profile",
    displayName = "Rounded Rectangle On Plane",
    description = "Constructs a rounded-rectangle profile from center, width, height, corner radius, and plane (defaults to XZ)",
    category = "geometry.profiles",
    order = 2
)
public class RoundedRectangleOnPlaneNode extends AbstractProfileNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_WIDTH_ID = "input_width";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_CORNER_SEGMENTS_ID = "input_corner_segments";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";

    @NodeProperty(displayName = "Width", category = "Size", order = 1)
    private double width = 5.0d;

    @NodeProperty(displayName = "Height", category = "Size", order = 2)
    private double height = 5.0d;

    @NodeProperty(displayName = "Corner Radius", category = "Size", order = 3)
    private double cornerRadius = 1.0d;

    @NodeProperty(displayName = "Corner Segments", category = "Resolution", order = 4)
    private int cornerSegments = 4;

    public RoundedRectangleOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.rounded_rectangle_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override; otherwise uses Plane origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Total width", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Total height", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Corner Radius", "Corner fillet radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CORNER_SEGMENTS_ID, "Corner Segments", "Segments per corner arc", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XZ (horizontal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "X Axis", "Optional in-plane rectangle X axis", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed rounded-rectangle points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Rounded-rectangle polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed rounded-rectangle boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved rounded-rectangle center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when rounded-rectangle profile was constructed", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Constructs a rounded-rectangle profile from center, width, height, corner radius, and plane (defaults to XZ)";
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
        Double resolvedWidth = resolvePositiveDouble(INPUT_WIDTH_ID, width);
        if (resolvedWidth == null) {
            writeInvalid("Width must be a positive finite number");
            return;
        }
        Double resolvedHeight = resolvePositiveDouble(INPUT_HEIGHT_ID, height);
        if (resolvedHeight == null) {
            writeInvalid("Height must be a positive finite number");
            return;
        }
        Double radius = resolveNonNegativeDouble(INPUT_RADIUS_ID, cornerRadius);
        if (radius == null) {
            writeInvalid("Corner radius must be a non-negative finite number");
            return;
        }
        Integer resolvedCornerSegments = resolvePositiveInteger(INPUT_CORNER_SEGMENTS_ID, cornerSegments);
        if (resolvedCornerSegments == null) {
            writeInvalid("Corner segments must be an exact positive integer");
            return;
        }

        double halfW = resolvedWidth * 0.5d;
        double halfH = resolvedHeight * 0.5d;
        if (radius > Math.min(halfW, halfH)) {
            writeInvalid("Corner radius exceeds half width/height");
            return;
        }

        ProfilePlaneUtils.Basis basis = resolveConstructionBasis(plane, INPUT_AXIS_ID);
        if (basis == null) {
            writeInvalid(ProfileInputUtils.isConnected(this, INPUT_AXIS_ID)
                ? "In-plane axis is invalid"
                : "Failed to create profile basis");
            return;
        }

        int vertexCount = ProfileConstructionUtils.uniqueRoundedRectangleVertices(
            resolvedCornerSegments, radius <= 1.0e-9d);
        if (!ProfileConstructionUtils.requireUniqueVertices(vertexCount)) {
            writeInvalid("Polygon profile vertex count exceeds limit (" + GenerationLimits.MAX_PROFILE_VERTICES + ")");
            return;
        }

        List<Vector3d> points = new ArrayList<>();
        if (radius <= 1.0e-9d) {
            points.add(toWorld(center, basis, -halfW, -halfH));
            points.add(toWorld(center, basis, halfW, -halfH));
            points.add(toWorld(center, basis, halfW, halfH));
            points.add(toWorld(center, basis, -halfW, halfH));
        } else {
            appendCorner(points, center, basis, halfW - radius, -halfH + radius, -Math.PI * 0.5d, 0.0d, radius, resolvedCornerSegments, true);
            appendCorner(points, center, basis, halfW - radius, halfH - radius, 0.0d, Math.PI * 0.5d, radius, resolvedCornerSegments, false);
            appendCorner(points, center, basis, -halfW + radius, halfH - radius, Math.PI * 0.5d, Math.PI, radius, resolvedCornerSegments, false);
            appendCorner(points, center, basis, -halfW + radius, -halfH + radius, Math.PI, Math.PI * 1.5d, radius, resolvedCornerSegments, false);
        }
        points.add(new Vector3d(points.getFirst()));

        PlaneData resolvedPlane = new PlaneData(center, basis.normal());
        StringBuilder error = new StringBuilder();
        PolygonProfileData profile = ProfileConstructionUtils.tryCreateProfile(points, resolvedPlane, error);
        if (profile == null) {
            writeInvalid(error.isEmpty() ? "Failed to create rounded rectangle profile" : error.toString());
            return;
        }
        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(points));
        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_BOUNDARY_ID, pathFromProfile(profile));
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        markSuccess();
    }

    private void appendCorner(List<Vector3d> points, Vector3d center, ProfilePlaneUtils.Basis basis,
                              double cx, double cy, double start, double end, double radius, int segments, boolean includeStart) {
        for (int i = includeStart ? 0 : 1; i <= segments; i++) {
            double t = i / (double) segments;
            double a = start + (end - start) * t;
            double lx = cx + Math.cos(a) * radius;
            double ly = cy + Math.sin(a) * radius;
            points.add(toWorld(center, basis, lx, ly));
        }
    }

    private Vector3d toWorld(Vector3d center, ProfilePlaneUtils.Basis basis, double localX, double localY) {
        return new Vector3d(center)
            .add(new Vector3d(basis.xAxis()).mul(localX))
            .add(new Vector3d(basis.yAxis()).mul(localY));
    }

    private void writeInvalid(String error) {
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putNullOutputs(OUTPUT_PROFILE_ID, OUTPUT_BOUNDARY_ID, OUTPUT_PLANE_ID, OUTPUT_CENTER_ID);
        markInvalid(error);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("width", width);
        state.put("height", height);
        state.put("cornerRadius", cornerRadius);
        state.put("cornerSegments", cornerSegments);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        restoreFiniteDouble(map, "width", v -> width = v);
        restoreFiniteDouble(map, "height", v -> height = v);
        restoreFiniteDouble(map, "cornerRadius", v -> cornerRadius = v);
        restoreInteger(map, "cornerSegments", v -> cornerSegments = v);
    }
}

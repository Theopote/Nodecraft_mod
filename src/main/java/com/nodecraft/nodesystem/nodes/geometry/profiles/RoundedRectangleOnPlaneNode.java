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
    id = "geometry.profiles.rounded_rectangle_profile",
    displayName = "Rounded Rectangle On Plane",
    description = "Constructs a rounded-rectangle profile from center, width, height, corner radius, and plane (defaults to XZ)",
    category = "geometry.profiles",
    order = 14
)
public class RoundedRectangleOnPlaneNode extends BaseNode {
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
    private static final String OUTPUT_VALID_ID = "output_valid";

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
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed rounded-rectangle boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved rounded-rectangle center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when rounded-rectangle profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a rounded-rectangle profile from center, width, height, corner radius, and plane (defaults to XZ)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlaneData plane = ProfilePlaneUtils.resolvePlane(inputValues.get(INPUT_PLANE_ID));
        Vector3d center = ProfilePlaneUtils.resolveCenter(inputValues.get(INPUT_CENTER_ID), plane);
        double resolvedWidth = resolveDouble(inputValues.get(INPUT_WIDTH_ID), width);
        double resolvedHeight = resolveDouble(inputValues.get(INPUT_HEIGHT_ID), height);
        double radius = resolveDouble(inputValues.get(INPUT_RADIUS_ID), cornerRadius);
        int resolvedCornerSegments = resolveCornerSegments();
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (!Double.isFinite(resolvedWidth) || !Double.isFinite(resolvedHeight) || !Double.isFinite(radius)
            || resolvedWidth <= 0.0d || resolvedHeight <= 0.0d) {
            writeInvalid();
            return;
        }

        double halfW = resolvedWidth * 0.5d;
        double halfH = resolvedHeight * 0.5d;
        double clampedRadius = Math.max(0.0d, Math.min(radius, Math.min(halfW, halfH)));

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        List<Vector3d> points = new ArrayList<>();
        if (clampedRadius <= 1.0e-9d) {
            points.add(toWorld(center, basis, -halfW, -halfH));
            points.add(toWorld(center, basis, halfW, -halfH));
            points.add(toWorld(center, basis, halfW, halfH));
            points.add(toWorld(center, basis, -halfW, halfH));
        } else {
            appendCorner(points, center, basis, halfW - clampedRadius, -halfH + clampedRadius, -Math.PI * 0.5d, 0.0d, clampedRadius, resolvedCornerSegments, true);
            appendCorner(points, center, basis, halfW - clampedRadius, halfH - clampedRadius, 0.0d, Math.PI * 0.5d, clampedRadius, resolvedCornerSegments, false);
            appendCorner(points, center, basis, -halfW + clampedRadius, halfH - clampedRadius, Math.PI * 0.5d, Math.PI, clampedRadius, resolvedCornerSegments, false);
            appendCorner(points, center, basis, -halfW + clampedRadius, -halfH + clampedRadius, Math.PI, Math.PI * 1.5d, clampedRadius, resolvedCornerSegments, false);
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

    private int resolveCornerSegments() {
        Object cornerSegmentsObj = inputValues.get(INPUT_CORNER_SEGMENTS_ID);
        int raw = cornerSegmentsObj instanceof Number number ? number.intValue() : cornerSegments;
        return Math.max(1, raw);
    }

    private static double resolveDouble(@Nullable Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
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
        if (map.get("width") instanceof Number n) width = n.doubleValue();
        if (map.get("height") instanceof Number n) height = n.doubleValue();
        if (map.get("cornerRadius") instanceof Number n) cornerRadius = n.doubleValue();
        if (map.get("cornerSegments") instanceof Number n) cornerSegments = n.intValue();
    }
}

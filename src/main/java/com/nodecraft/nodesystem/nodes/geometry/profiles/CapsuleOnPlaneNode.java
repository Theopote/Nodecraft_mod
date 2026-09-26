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
    id = "geometry.profiles.capsule_profile",
    displayName = "Capsule On Plane",
    description = "Constructs a capsule (stadium) profile from center, length, radius, and plane",
    category = "geometry.profiles",
    order = 18
)
public class CapsuleOnPlaneNode extends BaseNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_LENGTH_ID = "input_length";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_CAP_SEGMENTS_ID = "input_cap_segments";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Length", category = "Size", order = 1)
    private double length = 10.0d;

    @NodeProperty(displayName = "Radius", category = "Size", order = 2)
    private double radius = 3.0d;

    @NodeProperty(displayName = "Cap Segments", category = "Resolution", order = 3)
    private int capSegments = 16;

    public CapsuleOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.capsule_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override; otherwise uses Plane origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_LENGTH_ID, "Length", "Total capsule length along local X axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Capsule radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CAP_SEGMENTS_ID, "Cap Segments", "Segments per semicircle cap", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XZ (horizontal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "X Axis", "Optional in-plane capsule axis", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed capsule points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Capsule polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed capsule boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved capsule center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when capsule profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a capsule (stadium) profile from center, length, radius, and plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlaneData plane = ProfilePlaneUtils.resolvePlane(inputValues.get(INPUT_PLANE_ID));
        Vector3d center = ProfilePlaneUtils.resolveCenter(inputValues.get(INPUT_CENTER_ID), plane);
        double resolvedLength = resolveDouble(inputValues.get(INPUT_LENGTH_ID), length);
        double resolvedRadius = resolveDouble(inputValues.get(INPUT_RADIUS_ID), radius);
        int resolvedCapSegments = resolveCapSegments();
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (!Double.isFinite(resolvedLength) || !Double.isFinite(resolvedRadius) || resolvedLength <= 0.0d || resolvedRadius <= 0.0d) {
            writeInvalid();
            return;
        }

        double minLength = resolvedRadius * 2.0d;
        double clampedLength = Math.max(resolvedLength, minLength);
        double halfRectLength = (clampedLength * 0.5d) - resolvedRadius;

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        List<Vector3d> points = new ArrayList<>();
        appendArc(points, center, basis, halfRectLength, 0.0d, -Math.PI * 0.5d, Math.PI * 0.5d, resolvedRadius, resolvedCapSegments, true);
        appendArc(points, center, basis, -halfRectLength, 0.0d, Math.PI * 0.5d, Math.PI * 1.5d, resolvedRadius, resolvedCapSegments, false);
        points.add(new Vector3d(points.getFirst()));

        PlaneData resolvedPlane = new PlaneData(center, basis.normal());
        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(points));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(points, resolvedPlane));
        outputValues.put(OUTPUT_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(points));
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private int resolveCapSegments() {
        Object capSegmentsObj = inputValues.get(INPUT_CAP_SEGMENTS_ID);
        if (capSegmentsObj instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        return capSegments;
    }

    private static double resolveDouble(@Nullable Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private void appendArc(List<Vector3d> points, Vector3d center, ProfilePlaneUtils.Basis basis,
                           double cx, double cy, double start, double end, double arcRadius, int segments, boolean includeStart) {
        for (int i = includeStart ? 0 : 1; i <= segments; i++) {
            double t = i / (double) segments;
            double a = start + (end - start) * t;
            double lx = cx + Math.cos(a) * arcRadius;
            double ly = cy + Math.sin(a) * arcRadius;
            points.add(new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(lx))
                .add(new Vector3d(basis.yAxis()).mul(ly)));
        }
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
        state.put("length", length);
        state.put("radius", radius);
        state.put("capSegments", capSegments);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("length") instanceof Number n) length = n.doubleValue();
        if (map.get("radius") instanceof Number n) radius = n.doubleValue();
        if (map.get("capSegments") instanceof Number n) capSegments = n.intValue();
    }
}

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
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.profiles.semicircle_profile",
    displayName = "SemiCircle On Plane",
    description = "Constructs a semicircle profile from center, radius, plane, and segment count (defaults to XZ)",
    category = "geometry.profiles",
    order = 16
)
public class SemiCircleOnPlaneNode extends BaseNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_SEGMENTS_ID = "input_segments";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_start_direction";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Radius", category = "Size", order = 1)
    private double radius = 5.0d;

    @NodeProperty(displayName = "Arc Segments", category = "Resolution", order = 2)
    private int segments = 16;

    public SemiCircleOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.semicircle_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override; otherwise uses Plane origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Semicircle radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Arc Segments", "Arc segment count", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XZ (horizontal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Start Direction", "Optional in-plane diameter direction", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed semicircle points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Semicircle polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed semicircle boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved semicircle center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when semicircle profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a semicircle profile from center, radius, plane, and segment count (defaults to XZ)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlaneData plane = ProfilePlaneUtils.resolvePlane(inputValues.get(INPUT_PLANE_ID));
        Vector3d center = ProfilePlaneUtils.resolveCenter(inputValues.get(INPUT_CENTER_ID), plane);
        double resolvedRadius = resolveDouble(inputValues.get(INPUT_RADIUS_ID), radius);
        int resolvedSegments = resolveSegments();
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (!Double.isFinite(resolvedRadius) || resolvedRadius <= 0.0d) {
            writeInvalid();
            return;
        }

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        List<Vector3d> points = new ArrayList<>(resolvedSegments + 3);
        for (int i = 0; i <= resolvedSegments; i++) {
            double t = i / (double) resolvedSegments;
            double a = Math.PI - Math.PI * t;
            points.add(new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(Math.cos(a) * resolvedRadius))
                .add(new Vector3d(basis.yAxis()).mul(Math.sin(a) * resolvedRadius)));
        }
        points.add(new Vector3d(points.getFirst()));

        PlaneData resolvedPlane = new PlaneData(center, basis.normal());
        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(points));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(points, resolvedPlane));
        outputValues.put(OUTPUT_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(points));
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private int resolveSegments() {
        Object segmentsObj = inputValues.get(INPUT_SEGMENTS_ID);
        int raw = segmentsObj instanceof Number number ? number.intValue() : segments;
        return GenerationLimits.clampSegments(1, raw);
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
        state.put("radius", radius);
        state.put("segments", segments);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("radius") instanceof Number n) radius = n.doubleValue();
        if (map.get("segments") instanceof Number n) segments = n.intValue();
    }
}

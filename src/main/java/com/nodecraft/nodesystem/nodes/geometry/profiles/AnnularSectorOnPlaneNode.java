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
    id = "geometry.profiles.annular_sector_profile",
    displayName = "Annular Sector On Plane",
    description = "Constructs an annular sector boundary from center, inner/outer radii, angle range, and plane (defaults to XZ)",
    category = "geometry.profiles",
    order = 20
)
public class AnnularSectorOnPlaneNode extends BaseNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_INNER_RADIUS_ID = "input_inner_radius";
    private static final String INPUT_OUTER_RADIUS_ID = "input_outer_radius";
    private static final String INPUT_START_ANGLE_ID = "input_start_angle";
    private static final String INPUT_END_ANGLE_ID = "input_end_angle";
    private static final String INPUT_SEGMENTS_ID = "input_segments";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_start_direction";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Inner Radius", category = "Size", order = 1)
    private double innerRadius = 2.0d;

    @NodeProperty(displayName = "Outer Radius", category = "Size", order = 2)
    private double outerRadius = 5.0d;

    @NodeProperty(displayName = "Start Angle", category = "Angles", order = 3)
    private double startAngle = 0.0d;

    @NodeProperty(displayName = "End Angle", category = "Angles", order = 4)
    private double endAngle = 90.0d;

    @NodeProperty(displayName = "Arc Segments", category = "Resolution", order = 5)
    private int segments = 16;

    public AnnularSectorOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.annular_sector_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override; otherwise uses Plane origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_INNER_RADIUS_ID, "Inner Radius", "Inner radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OUTER_RADIUS_ID, "Outer Radius", "Outer radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "Start angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_END_ANGLE_ID, "End Angle", "End angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Arc Segments", "Segments used for each arc", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XZ (horizontal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Start Direction", "Optional in-plane zero-angle direction", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed annular-sector points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Annular-sector polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed annular-sector boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved annular-sector center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when annular-sector profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs an annular sector boundary from center, inner/outer radii, angle range, and plane (defaults to XZ)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlaneData plane = ProfilePlaneUtils.resolvePlane(inputValues.get(INPUT_PLANE_ID));
        Vector3d center = ProfilePlaneUtils.resolveCenter(inputValues.get(INPUT_CENTER_ID), plane);
        double inner = resolveDouble(inputValues.get(INPUT_INNER_RADIUS_ID), innerRadius);
        double outer = resolveDouble(inputValues.get(INPUT_OUTER_RADIUS_ID), outerRadius);
        double start = Math.toRadians(resolveDouble(inputValues.get(INPUT_START_ANGLE_ID), startAngle));
        double end = Math.toRadians(resolveDouble(inputValues.get(INPUT_END_ANGLE_ID), endAngle));
        int resolvedSegments = resolveSegments();
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (!Double.isFinite(inner) || !Double.isFinite(outer) || inner <= 0.0d || outer <= 0.0d || inner >= outer || Math.abs(end - start) < 1.0e-9d) {
            writeInvalid();
            return;
        }

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        List<Vector3d> points = new ArrayList<>(resolvedSegments * 2 + 3);
        for (int i = 0; i <= resolvedSegments; i++) {
            double t = i / (double) resolvedSegments;
            double a = start + (end - start) * t;
            points.add(world(center, basis, outer, a));
        }
        for (int i = resolvedSegments; i >= 0; i--) {
            double t = i / (double) resolvedSegments;
            double a = start + (end - start) * t;
            points.add(world(center, basis, inner, a));
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

    private Vector3d world(Vector3d center, ProfilePlaneUtils.Basis basis, double radius, double angle) {
        return new Vector3d(center)
            .add(new Vector3d(basis.xAxis()).mul(Math.cos(angle) * radius))
            .add(new Vector3d(basis.yAxis()).mul(Math.sin(angle) * radius));
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
        state.put("innerRadius", innerRadius);
        state.put("outerRadius", outerRadius);
        state.put("startAngle", startAngle);
        state.put("endAngle", endAngle);
        state.put("segments", segments);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("innerRadius") instanceof Number n) innerRadius = n.doubleValue();
        if (map.get("outerRadius") instanceof Number n) outerRadius = n.doubleValue();
        if (map.get("startAngle") instanceof Number n) startAngle = n.doubleValue();
        if (map.get("endAngle") instanceof Number n) endAngle = n.doubleValue();
        if (map.get("segments") instanceof Number n) segments = n.intValue();
    }
}

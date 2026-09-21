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
    id = "geometry.profiles.gear_profile",
    displayName = "Gear On Plane",
    description = "Constructs a gear-like profile from center, tooth count, root/tip radii, and plane (defaults to XZ)",
    category = "geometry.profiles",
    order = 22
)
public class GearOnPlaneNode extends BaseNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_TOOTH_COUNT_ID = "input_tooth_count";
    private static final String INPUT_ROOT_RADIUS_ID = "input_root_radius";
    private static final String INPUT_TIP_RADIUS_ID = "input_tip_radius";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_start_direction";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Teeth", category = "Size", order = 1)
    private int teeth = 8;

    @NodeProperty(displayName = "Root Radius", category = "Size", order = 2)
    private double rootRadius = 3.0d;

    @NodeProperty(displayName = "Tip Radius", category = "Size", order = 3)
    private double tipRadius = 5.0d;

    public GearOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.gear_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override; otherwise uses Plane origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_TOOTH_COUNT_ID, "Teeth", "Number of gear teeth", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROOT_RADIUS_ID, "Root Radius", "Radius at tooth root", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TIP_RADIUS_ID, "Tip Radius", "Radius at tooth tip", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XZ (horizontal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Start Direction", "Optional in-plane zero-angle direction", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed gear points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Gear polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed gear boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved gear center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when gear profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a gear-like profile from center, tooth count, root/tip radii, and plane (defaults to XZ)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlaneData plane = ProfilePlaneUtils.resolvePlane(inputValues.get(INPUT_PLANE_ID));
        Vector3d center = ProfilePlaneUtils.resolveCenter(inputValues.get(INPUT_CENTER_ID), plane);
        int resolvedTeeth = resolveTeeth();
        double root = resolveDouble(inputValues.get(INPUT_ROOT_RADIUS_ID), rootRadius);
        double tip = resolveDouble(inputValues.get(INPUT_TIP_RADIUS_ID), tipRadius);
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (resolvedTeeth < 3 || !Double.isFinite(root) || !Double.isFinite(tip) || root <= 0.0d || tip <= root) {
            writeInvalid();
            return;
        }

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        int totalVertices = resolvedTeeth * 4;
        double step = (Math.PI * 2.0d) / totalVertices;
        List<Vector3d> points = new ArrayList<>(totalVertices + 1);
        for (int i = 0; i < totalVertices; i++) {
            double angle = step * i;
            double radius = (i % 4 == 1 || i % 4 == 2) ? tip : root;
            points.add(new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(Math.cos(angle) * radius))
                .add(new Vector3d(basis.yAxis()).mul(Math.sin(angle) * radius)));
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

    private int resolveTeeth() {
        Object toothObj = inputValues.get(INPUT_TOOTH_COUNT_ID);
        return toothObj instanceof Number number ? Math.max(3, number.intValue()) : teeth;
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
        state.put("teeth", teeth);
        state.put("rootRadius", rootRadius);
        state.put("tipRadius", tipRadius);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("teeth") instanceof Number n) teeth = n.intValue();
        if (map.get("rootRadius") instanceof Number n) rootRadius = n.doubleValue();
        if (map.get("tipRadius") instanceof Number n) tipRadius = n.doubleValue();
    }
}

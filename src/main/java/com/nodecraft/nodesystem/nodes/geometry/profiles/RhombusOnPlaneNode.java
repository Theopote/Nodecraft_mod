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
    id = "geometry.profiles.rhombus_profile",
    displayName = "Rhombus On Plane",
    description = "Constructs a rhombus profile from center, horizontal diagonal, vertical diagonal, and plane (defaults to XZ)",
    category = "geometry.profiles",
    order = 17
)
public class RhombusOnPlaneNode extends BaseNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_DIAGONAL_X_ID = "input_diagonal_x";
    private static final String INPUT_DIAGONAL_Y_ID = "input_diagonal_y";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Diagonal X", category = "Size", order = 1)
    private double diagonalX = 5.0d;

    @NodeProperty(displayName = "Diagonal Y", category = "Size", order = 2)
    private double diagonalY = 5.0d;

    public RhombusOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.rhombus_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override; otherwise uses Plane origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_DIAGONAL_X_ID, "Diagonal X", "Diagonal length along local X axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DIAGONAL_Y_ID, "Diagonal Y", "Diagonal length along local Y axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XZ (horizontal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "X Axis", "Optional in-plane rhombus X axis", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed rhombus points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Rhombus polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed rhombus boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved rhombus center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when rhombus profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a rhombus profile from center, horizontal diagonal, vertical diagonal, and plane (defaults to XZ)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlaneData plane = ProfilePlaneUtils.resolvePlane(inputValues.get(INPUT_PLANE_ID));
        Vector3d center = ProfilePlaneUtils.resolveCenter(inputValues.get(INPUT_CENTER_ID), plane);
        double dx = resolveDouble(inputValues.get(INPUT_DIAGONAL_X_ID), diagonalX);
        double dy = resolveDouble(inputValues.get(INPUT_DIAGONAL_Y_ID), diagonalY);
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (!Double.isFinite(dx) || !Double.isFinite(dy) || dx <= 0.0d || dy <= 0.0d) {
            writeInvalid();
            return;
        }

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        double hx = dx * 0.5d;
        double hy = dy * 0.5d;
        List<Vector3d> points = new ArrayList<>(5);
        points.add(toWorld(center, basis, 0.0d, -hy));
        points.add(toWorld(center, basis, hx, 0.0d));
        points.add(toWorld(center, basis, 0.0d, hy));
        points.add(toWorld(center, basis, -hx, 0.0d));
        points.add(new Vector3d(points.getFirst()));

        PlaneData resolvedPlane = new PlaneData(center, basis.normal());
        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(points));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(points, resolvedPlane));
        outputValues.put(OUTPUT_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(points));
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private static double resolveDouble(@Nullable Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
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
        state.put("diagonalX", diagonalX);
        state.put("diagonalY", diagonalY);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("diagonalX") instanceof Number n) diagonalX = n.doubleValue();
        if (map.get("diagonalY") instanceof Number n) diagonalY = n.doubleValue();
    }
}

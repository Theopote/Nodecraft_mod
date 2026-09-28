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
    id = "geometry.profiles.rhombus_profile",
    displayName = "Rhombus On Plane",
    description = "Constructs a rhombus profile from center, horizontal diagonal, vertical diagonal, and plane (defaults to XZ)",
    category = "geometry.profiles",
    order = 7
)
public class RhombusOnPlaneNode extends AbstractProfileNode {
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
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed rhombus boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved rhombus center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when rhombus profile was constructed", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Constructs a rhombus profile from center, horizontal diagonal, vertical diagonal, and plane (defaults to XZ)";
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
        Double dx = resolvePositiveDouble(INPUT_DIAGONAL_X_ID, diagonalX);
        if (dx == null) {
            writeInvalid("Diagonal X must be a positive finite number");
            return;
        }
        Double dy = resolvePositiveDouble(INPUT_DIAGONAL_Y_ID, diagonalY);
        if (dy == null) {
            writeInvalid("Diagonal Y must be a positive finite number");
            return;
        }

        ProfilePlaneUtils.Basis basis = resolveConstructionBasis(plane, INPUT_AXIS_ID);
        if (basis == null) {
            writeInvalid(ProfileInputUtils.isConnected(this, INPUT_AXIS_ID)
                ? "In-plane axis is invalid"
                : "Failed to create profile basis");
            return;
        }

        if (!isWithinProfileVertices(4)) {
            writeInvalid("Polygon profile vertex count exceeds limit (" + GenerationLimits.MAX_PROFILE_VERTICES + ")");
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
        StringBuilder error = new StringBuilder();
        PolygonProfileData profile = ProfileConstructionUtils.tryCreateProfile(points, resolvedPlane, error);
        if (profile == null) {
            writeInvalid(error.isEmpty() ? "Failed to create rhombus profile" : error.toString());
            return;
        }
        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(points));
        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_BOUNDARY_ID, pathFromProfile(profile));
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        markSuccess();
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

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
    id = "geometry.profiles.gear_profile",
    displayName = "Gear On Plane",
    description = "Constructs a gear-like profile from center, tooth count, root/tip radii, and plane (defaults to XZ)",
    category = "geometry.profiles",
    order = 22
)
public class GearOnPlaneNode extends AbstractProfileNode {
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
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed gear boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved gear center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when gear profile was constructed", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Constructs a gear-like profile from center, tooth count, root/tip radii, and plane (defaults to XZ)";
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
        Integer resolvedTeeth = resolveBoundedInteger(INPUT_TOOTH_COUNT_ID, teeth, 3, GenerationLimits.MAX_PROFILE_VERTICES / 4);
        if (resolvedTeeth == null) {
            writeInvalid("Teeth must be an exact integer from 3 to " + (GenerationLimits.MAX_PROFILE_VERTICES / 4));
            return;
        }
        Double root = resolvePositiveDouble(INPUT_ROOT_RADIUS_ID, rootRadius);
        if (root == null) {
            writeInvalid("Root radius must be a positive finite number");
            return;
        }
        Double tip = resolvePositiveDouble(INPUT_TIP_RADIUS_ID, tipRadius);
        if (tip == null) {
            writeInvalid("Tip radius must be a positive finite number");
            return;
        }
        if (tip <= root) {
            writeInvalid("Tip radius must be greater than root radius");
            return;
        }

        ProfilePlaneUtils.Basis basis = resolveConstructionBasis(plane, INPUT_AXIS_ID);
        if (basis == null) {
            writeInvalid(ProfileInputUtils.isConnected(this, INPUT_AXIS_ID)
                ? "In-plane axis is invalid"
                : "Failed to create profile basis");
            return;
        }

        int totalVertices = resolvedTeeth * 4;
        if (!isWithinProfileVertices(totalVertices)) {
            writeInvalid("Polygon profile vertex count exceeds limit (" + GenerationLimits.MAX_PROFILE_VERTICES + ")");
            return;
        }

        double step = (Math.PI * 2.0d) / totalVertices;
        List<Vector3d> points = new ArrayList<>(totalVertices + 1);
        for (int i = 0; i < totalVertices; i++) {
            double angle = step * i;
            double radius = (i % 4 == 1 || i % 4 == 2) ? tip : root;
            points.add(new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(Math.cos(angle) * radius))
                .add(new Vector3d(basis.yAxis()).mul(Math.sin(angle) * radius)));
        }
        points.add(new Vector3d(points.getFirst()));

        PlaneData resolvedPlane = PlaneData.canonical(center, basis.normal());
        if (resolvedPlane == null) {
            writeInvalid("Failed to create profile plane");
            return;
        }

        StringBuilder error = new StringBuilder();
        PolygonProfileData profile = ProfileConstructionUtils.tryCreateProfile(points, resolvedPlane, error);
        if (profile == null) {
            writeInvalid(error.isEmpty() ? "Failed to create gear profile" : error.toString());
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
        restoreInteger(map, "teeth", v -> teeth = v);
        restoreFiniteDouble(map, "rootRadius", v -> rootRadius = v);
        restoreFiniteDouble(map, "tipRadius", v -> tipRadius = v);
    }
}

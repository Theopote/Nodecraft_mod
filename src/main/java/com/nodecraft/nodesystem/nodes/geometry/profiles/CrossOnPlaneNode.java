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
    id = "geometry.profiles.cross_profile",
    displayName = "Cross On Plane",
    description = "Constructs a plus-shaped cross profile from arm length, arm width, center, and plane",
    category = "geometry.profiles",
    order = 8
)
public class CrossOnPlaneNode extends AbstractProfileNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_ARM_LENGTH_ID = "input_arm_length";
    private static final String INPUT_ARM_WIDTH_ID = "input_arm_width";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";

    @NodeProperty(displayName = "Arm Length", category = "Size", order = 1)
    private double armLength = 5.0d;

    @NodeProperty(displayName = "Arm Width", category = "Size", order = 2)
    private double armWidth = 2.0d;

    public CrossOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.cross_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override; otherwise uses Plane origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_ARM_LENGTH_ID, "Arm Length", "Length from center to each arm tip", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ARM_WIDTH_ID, "Arm Width", "Width of each arm", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XZ (horizontal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "X Axis", "Optional in-plane cross X axis", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed cross points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Cross polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed cross boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved cross center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when cross profile was constructed", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Constructs a plus-shaped cross profile from arm length, arm width, center, and plane";
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
        Double resolvedArmLength = resolvePositiveDouble(INPUT_ARM_LENGTH_ID, armLength);
        if (resolvedArmLength == null) {
            writeInvalid("Arm length must be a positive finite number");
            return;
        }
        Double resolvedArmWidth = resolvePositiveDouble(INPUT_ARM_WIDTH_ID, armWidth);
        if (resolvedArmWidth == null) {
            writeInvalid("Arm width must be a positive finite number");
            return;
        }
        double halfWidth = resolvedArmWidth * 0.5d;
        if (!Double.isFinite(halfWidth) || halfWidth >= resolvedArmLength) {
            writeInvalid("Arm width must be less than twice the arm length");
            return;
        }

        ProfilePlaneUtils.Basis basis = resolveConstructionBasis(plane, INPUT_AXIS_ID);
        if (basis == null) {
            writeInvalid(ProfileInputUtils.isConnected(this, INPUT_AXIS_ID)
                ? "In-plane axis is invalid"
                : "Failed to create profile basis");
            return;
        }

        if (!ProfileConstructionUtils.requireUniqueVertices(12)) {
            writeInvalid("Polygon profile vertex count exceeds limit (" + GenerationLimits.MAX_PROFILE_VERTICES + ")");
            return;
        }

        double h = halfWidth;
        double l = resolvedArmLength;
        double[][] local = {
            {-h, -l}, {h, -l}, {h, -h}, {l, -h}, {l, h}, {h, h},
            {h, l}, {-h, l}, {-h, h}, {-l, h}, {-l, -h}, {-h, -h}
        };

        List<Vector3d> points = new ArrayList<>(local.length + 1);
        for (double[] p : local) {
            points.add(toWorld(center, basis, p[0], p[1]));
        }
        points.add(new Vector3d(points.getFirst()));

        PlaneData resolvedPlane = new PlaneData(center, basis.normal());
        StringBuilder error = new StringBuilder();
        PolygonProfileData profile = ProfileConstructionUtils.tryCreateProfile(points, resolvedPlane, error);
        if (profile == null) {
            writeInvalid(error.isEmpty() ? "Failed to create cross profile" : error.toString());
            return;
        }
        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(points));
        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_BOUNDARY_ID, pathFromProfile(profile));
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        markSuccess();
    }

    private Vector3d toWorld(Vector3d center, ProfilePlaneUtils.Basis basis, double x, double y) {
        return new Vector3d(center)
            .add(new Vector3d(basis.xAxis()).mul(x))
            .add(new Vector3d(basis.yAxis()).mul(y));
    }

    private void writeInvalid(String error) {
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putNullOutputs(OUTPUT_PROFILE_ID, OUTPUT_BOUNDARY_ID, OUTPUT_PLANE_ID, OUTPUT_CENTER_ID);
        markInvalid(error);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("armLength", armLength);
        state.put("armWidth", armWidth);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        restoreFiniteDouble(map, "armLength", v -> armLength = v);
        restoreFiniteDouble(map, "armWidth", v -> armWidth = v);
    }
}

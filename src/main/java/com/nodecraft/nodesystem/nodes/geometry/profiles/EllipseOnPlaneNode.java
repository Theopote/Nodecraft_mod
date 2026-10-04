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
    id = "geometry.profiles.ellipse_profile",
    displayName = "Ellipse On Plane",
    description = "Constructs an ellipse profile from center, major/minor radii, plane, and segment count (defaults to XZ)",
    category = "geometry.profiles",
    order = 4
)
public class EllipseOnPlaneNode extends AbstractProfileNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_X_ID = "input_radius_x";
    private static final String INPUT_RADIUS_Y_ID = "input_radius_y";
    private static final String INPUT_SEGMENTS_ID = "input_segments";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_start_direction";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";

    @NodeProperty(displayName = "Radius X", category = "Size", order = 1)
    private double radiusX = 5.0d;

    @NodeProperty(displayName = "Radius Y", category = "Size", order = 2)
    private double radiusY = 3.0d;

    @NodeProperty(displayName = "Segments", category = "Resolution", order = 3)
    private int segments = 32;

    public EllipseOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.ellipse_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override; otherwise uses Plane origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_X_ID, "Radius X", "Ellipse local X radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_Y_ID, "Radius Y", "Ellipse local Y radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Segments", "Boundary segment count", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XZ (horizontal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Start Direction", "Optional in-plane major-axis direction", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed ellipse points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Ellipse polygon profile approximation", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed ellipse boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved ellipse center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when ellipse profile was constructed", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Constructs an ellipse profile from center, major/minor radii, plane, and segment count (defaults to XZ)";
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
        Double rx = resolvePositiveDouble(INPUT_RADIUS_X_ID, radiusX);
        if (rx == null) {
            writeInvalid("Radius X must be a positive finite number");
            return;
        }
        Double ry = resolvePositiveDouble(INPUT_RADIUS_Y_ID, radiusY);
        if (ry == null) {
            writeInvalid("Radius Y must be a positive finite number");
            return;
        }
        Integer resolvedSegments = resolveBoundedInteger(
            INPUT_SEGMENTS_ID, segments, 3, GenerationLimits.MAX_PROFILE_VERTICES);
        if (resolvedSegments == null) {
            writeInvalid("Segments must be an exact integer from 3 to " + GenerationLimits.MAX_PROFILE_VERTICES);
            return;
        }

        ProfilePlaneUtils.Basis basis = resolveConstructionBasis(plane, INPUT_AXIS_ID);
        if (basis == null) {
            writeInvalid(ProfileInputUtils.isConnected(this, INPUT_AXIS_ID)
                ? "In-plane axis is invalid"
                : "Failed to create profile basis");
            return;
        }

        if (!ProfileConstructionUtils.requireUniqueVertices(
            ProfileConstructionUtils.uniqueCircleVertices(resolvedSegments))) {
            writeInvalid("Polygon profile vertex count exceeds limit (" + GenerationLimits.MAX_PROFILE_VERTICES + ")");
            return;
        }

        List<Vector3d> points = new ArrayList<>(resolvedSegments + 1);
        double step = (Math.PI * 2.0d) / resolvedSegments;
        for (int i = 0; i < resolvedSegments; i++) {
            double a = step * i;
            points.add(new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(Math.cos(a) * rx))
                .add(new Vector3d(basis.yAxis()).mul(Math.sin(a) * ry)));
        }
        points.add(new Vector3d(points.getFirst()));

        PlaneData resolvedPlane = new PlaneData(center, basis.normal());
        StringBuilder error = new StringBuilder();
        PolygonProfileData profile = ProfileConstructionUtils.tryCreateProfile(points, resolvedPlane, error);
        if (profile == null) {
            writeInvalid(error.isEmpty() ? "Failed to create ellipse profile" : error.toString());
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
        state.put("radiusX", radiusX);
        state.put("radiusY", radiusY);
        state.put("segments", segments);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        restoreFiniteDouble(map, "radiusX", v -> radiusX = v);
        restoreFiniteDouble(map, "radiusY", v -> radiusY = v);
        restoreInteger(map, "segments", v -> segments = v);
    }
}

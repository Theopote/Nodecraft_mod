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
    id = "geometry.profiles.annular_sector_profile",
    displayName = "Annular Sector On Plane",
    description = "Constructs an annular sector boundary from center, inner/outer radii, angle range, and plane (defaults to XZ)",
    category = "geometry.profiles",
    order = 14
)
public class AnnularSectorOnPlaneNode extends AbstractProfileNode {
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
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed annular-sector boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved annular-sector center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when annular-sector profile was constructed", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Constructs an annular sector boundary from center, inner/outer radii, angle range, and plane (defaults to XZ)";
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
        Double inner = resolvePositiveDouble(INPUT_INNER_RADIUS_ID, innerRadius);
        if (inner == null) {
            writeInvalid("Inner radius must be a positive finite number");
            return;
        }
        Double outer = resolvePositiveDouble(INPUT_OUTER_RADIUS_ID, outerRadius);
        if (outer == null) {
            writeInvalid("Outer radius must be a positive finite number");
            return;
        }
        if (inner >= outer) {
            writeInvalid("Inner radius must be less than outer radius");
            return;
        }
        Double startDegrees = resolveFiniteDouble(INPUT_START_ANGLE_ID, startAngle);
        if (startDegrees == null) {
            writeInvalid("Start angle must be finite");
            return;
        }
        Double endDegrees = resolveFiniteDouble(INPUT_END_ANGLE_ID, endAngle);
        if (endDegrees == null) {
            writeInvalid("End angle must be finite");
            return;
        }
        double start = Math.toRadians(startDegrees);
        double end = Math.toRadians(endDegrees);
        if (Math.abs(end - start) < 1.0e-9d) {
            writeInvalid("Start and end angles must differ");
            return;
        }
        Integer resolvedSegments = resolveBoundedInteger(
            INPUT_SEGMENTS_ID, segments, 1, GenerationLimits.MAX_PROFILE_VERTICES);
        if (resolvedSegments == null) {
            writeInvalid("Arc segments must be an exact integer from 1 to " + GenerationLimits.MAX_PROFILE_VERTICES);
            return;
        }

        ProfilePlaneUtils.Basis basis = resolveConstructionBasis(plane, INPUT_AXIS_ID);
        if (basis == null) {
            writeInvalid(ProfileInputUtils.isConnected(this, INPUT_AXIS_ID)
                ? "In-plane axis is invalid"
                : "Failed to create profile basis");
            return;
        }

        int vertexCount = resolvedSegments * 2 + 2;
        if (!isWithinProfileVertices(vertexCount)) {
            writeInvalid("Polygon profile vertex count exceeds limit (" + GenerationLimits.MAX_PROFILE_VERTICES + ")");
            return;
        }

        List<Vector3d> points = new ArrayList<>(vertexCount + 1);
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

        PlaneData resolvedPlane = PlaneData.canonical(center, basis.normal());
        if (resolvedPlane == null) {
            writeInvalid("Failed to create profile plane");
            return;
        }

        PolygonProfileData profile = new PolygonProfileData(points, resolvedPlane);
        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(points));
        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_BOUNDARY_ID, pathFromProfile(profile));
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        markSuccess();
    }

    private Vector3d world(Vector3d center, ProfilePlaneUtils.Basis basis, double radius, double angle) {
        return new Vector3d(center)
            .add(new Vector3d(basis.xAxis()).mul(Math.cos(angle) * radius))
            .add(new Vector3d(basis.yAxis()).mul(Math.sin(angle) * radius));
    }

    private void writeInvalid(String error) {
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putNullOutputs(OUTPUT_PROFILE_ID, OUTPUT_BOUNDARY_ID, OUTPUT_PLANE_ID, OUTPUT_CENTER_ID);
        markInvalid(error);
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

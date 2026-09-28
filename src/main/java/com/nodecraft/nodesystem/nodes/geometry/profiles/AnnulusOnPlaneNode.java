package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
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
    id = "geometry.profiles.annulus_profile",
    displayName = "Annulus On Plane",
    description = "Constructs an annulus planar region (outer + inner hole) plus separate outer/inner profiles",
    category = "geometry.profiles",
    order = 13
)
public class AnnulusOnPlaneNode extends AbstractProfileNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_INNER_RADIUS_ID = "input_inner_radius";
    private static final String INPUT_OUTER_RADIUS_ID = "input_outer_radius";
    private static final String INPUT_SEGMENTS_ID = "input_segments";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_start_direction";

    private static final String OUTPUT_OUTER_POINTS_ID = "output_outer_points";
    private static final String OUTPUT_INNER_POINTS_ID = "output_inner_points";
    private static final String OUTPUT_OUTER_PROFILE_ID = "output_outer_profile";
    private static final String OUTPUT_INNER_PROFILE_ID = "output_inner_profile";
    private static final String OUTPUT_OUTER_BOUNDARY_ID = "output_outer_boundary";
    private static final String OUTPUT_INNER_BOUNDARY_ID = "output_inner_boundary";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_AREA_ID = "output_area";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";

    @NodeProperty(displayName = "Inner Radius", category = "Size", order = 1)
    private double innerRadius = 2.0d;

    @NodeProperty(displayName = "Outer Radius", category = "Size", order = 2)
    private double outerRadius = 5.0d;

    @NodeProperty(displayName = "Segments", category = "Resolution", order = 3)
    private int segments = 32;

    public AnnulusOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.annulus_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Optional center override; otherwise uses Plane origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_INNER_RADIUS_ID, "Inner Radius", "Inner ring radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OUTER_RADIUS_ID, "Outer Radius", "Outer ring radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Segments", "Boundary segment count", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XZ (horizontal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Start Direction", "Optional in-plane zero-angle direction", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_OUTER_POINTS_ID, "Outer Points", "Closed outer ring points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_INNER_POINTS_ID, "Inner Points", "Closed inner ring points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_OUTER_PROFILE_ID, "Outer Profile", "Outer ring profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_INNER_PROFILE_ID, "Inner Profile", "Inner ring profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_OUTER_BOUNDARY_ID, "Outer Boundary", "Closed outer ring boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_INNER_BOUNDARY_ID, "Inner Boundary", "Closed inner ring boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region",
            "Canonical annulus planar region (outer + one hole)", NodeDataType.PLANAR_REGION, this));
        addOutputPort(new BasePort(OUTPUT_AREA_ID, "Area", "Annulus area π(R²−r²)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved construction plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved annulus center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when annulus boundaries were constructed", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Constructs an annulus planar region (outer + inner hole) plus separate outer/inner profiles";
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

        List<Vector3d> outerPts = buildRing(center, basis, outer, resolvedSegments, false);
        List<Vector3d> innerPts = buildRing(center, basis, inner, resolvedSegments, true);
        PlaneData resolvedPlane = PlaneData.canonical(center, basis.normal());
        if (resolvedPlane == null) {
            writeInvalid("Failed to create profile plane");
            return;
        }

        StringBuilder error = new StringBuilder();
        PolygonProfileData outerProfile = ProfileConstructionUtils.tryCreateProfile(outerPts, resolvedPlane, error);
        if (outerProfile == null) {
            writeInvalid(error.isEmpty() ? "Failed to create outer profile" : error.toString());
            return;
        }
        error.setLength(0);
        PolygonProfileData innerProfile = ProfileConstructionUtils.tryCreateProfile(innerPts, resolvedPlane, error);
        if (innerProfile == null) {
            writeInvalid(error.isEmpty() ? "Failed to create inner profile" : error.toString());
            return;
        }
        error.setLength(0);
        PlanarRegionData region = PlanarRegionData.tryCreate(
            outerProfile, List.of(innerProfile), resolvedPlane, error);
        if (region == null) {
            writeInvalid(error.isEmpty() ? "Failed to create annulus region" : error.toString());
            return;
        }

        double area = Math.PI * (outer * outer - inner * inner);
        outputValues.put(OUTPUT_OUTER_POINTS_ID, ProfilePlaneUtils.toPointList(outerPts));
        outputValues.put(OUTPUT_INNER_POINTS_ID, ProfilePlaneUtils.toPointList(innerPts));
        outputValues.put(OUTPUT_OUTER_PROFILE_ID, outerProfile);
        outputValues.put(OUTPUT_INNER_PROFILE_ID, innerProfile);
        outputValues.put(OUTPUT_OUTER_BOUNDARY_ID, pathFromProfile(outerProfile));
        outputValues.put(OUTPUT_INNER_BOUNDARY_ID, pathFromProfile(innerProfile));
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_AREA_ID, area);
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        markSuccess();
    }

    private List<Vector3d> buildRing(Vector3d center, ProfilePlaneUtils.Basis basis, double ringRadius, int segmentCount, boolean clockwise) {
        List<Vector3d> points = new ArrayList<>(segmentCount + 1);
        double step = (Math.PI * 2.0d) / segmentCount;
        for (int i = 0; i < segmentCount; i++) {
            int index = clockwise ? (segmentCount - i) : i;
            double a = step * index;
            points.add(new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(Math.cos(a) * ringRadius))
                .add(new Vector3d(basis.yAxis()).mul(Math.sin(a) * ringRadius)));
        }
        points.add(new Vector3d(points.getFirst()));
        return points;
    }

    private void writeInvalid(String error) {
        putEmptyListOutputs(OUTPUT_OUTER_POINTS_ID, OUTPUT_INNER_POINTS_ID);
        putNullOutputs(
            OUTPUT_OUTER_PROFILE_ID, OUTPUT_INNER_PROFILE_ID,
            OUTPUT_OUTER_BOUNDARY_ID, OUTPUT_INNER_BOUNDARY_ID,
            OUTPUT_REGION_ID, OUTPUT_PLANE_ID, OUTPUT_CENTER_ID
        );
        putDoubleOutputs(0.0d, OUTPUT_AREA_ID);
        markInvalid(error);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("innerRadius", innerRadius);
        state.put("outerRadius", outerRadius);
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
        if (map.get("segments") instanceof Number n) segments = n.intValue();
    }
}

package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.profiles.annular_sector_profile",
    displayName = "Annular Sector On Plane",
    description = "Constructs an annular sector boundary from center, inner/outer radii, angle range, and plane",
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
    private static final String OUTPUT_VALID_ID = "output_valid";

    public AnnularSectorOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.annular_sector_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Annular sector center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_INNER_RADIUS_ID, "Inner Radius", "Inner radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OUTER_RADIUS_ID, "Outer Radius", "Outer radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_START_ANGLE_ID, "Start Angle", "Start angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_END_ANGLE_ID, "End Angle", "End angle in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Arc Segments", "Segments used for each arc", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Start Direction", "Optional in-plane zero-angle direction", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed annular-sector points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Annular-sector polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed annular-sector boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when annular-sector profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs an annular sector boundary from center, inner/outer radii, angle range, and plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = ProfilePlaneUtils.resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object innerObj = inputValues.get(INPUT_INNER_RADIUS_ID);
        Object outerObj = inputValues.get(INPUT_OUTER_RADIUS_ID);
        Object startObj = inputValues.get(INPUT_START_ANGLE_ID);
        Object endObj = inputValues.get(INPUT_END_ANGLE_ID);
        Object segmentsObj = inputValues.get(INPUT_SEGMENTS_ID);
        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData p ? p : PlaneData.XY_PLANE;
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (center == null || !(innerObj instanceof Number inN) || !(outerObj instanceof Number outN)
            || !(startObj instanceof Number sN) || !(endObj instanceof Number eN) || !(segmentsObj instanceof Number segN)) {
            writeInvalid();
            return;
        }
        double inner = inN.doubleValue();
        double outer = outN.doubleValue();
        double start = Math.toRadians(sN.doubleValue());
        double end = Math.toRadians(eN.doubleValue());
        int segments = GenerationLimits.clampSegments(1, segN.intValue());
        if (!Double.isFinite(inner) || !Double.isFinite(outer) || inner <= 0.0d || outer <= 0.0d || inner >= outer || Math.abs(end - start) < 1.0e-9d) {
            writeInvalid();
            return;
        }

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        List<Vector3d> points = new ArrayList<>(segments * 2 + 3);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double a = start + (end - start) * t;
            points.add(world(center, basis, outer, a));
        }
        for (int i = segments; i >= 0; i--) {
            double t = i / (double) segments;
            double a = start + (end - start) * t;
            points.add(world(center, basis, inner, a));
        }
        points.add(new Vector3d(points.get(0)));

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(points, new PlaneData(center, basis.normal())));
        outputValues.put(OUTPUT_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(points));
        outputValues.put(OUTPUT_VALID_ID, true);
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
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

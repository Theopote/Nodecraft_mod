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
    id = "geometry.profiles.annulus_profile",
    displayName = "Annulus On Plane",
    description = "Constructs annulus boundaries from center, inner/outer radii, plane, and segment count",
    category = "geometry.profiles",
    order = 13
)
public class AnnulusOnPlaneNode extends BaseNode {
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
    private static final String OUTPUT_VALID_ID = "output_valid";

    public AnnulusOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.annulus_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Annulus center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_INNER_RADIUS_ID, "Inner Radius", "Inner ring radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OUTER_RADIUS_ID, "Outer Radius", "Outer ring radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Segments", "Boundary segment count", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Start Direction", "Optional in-plane zero-angle direction", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_OUTER_POINTS_ID, "Outer Points", "Closed outer ring points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_INNER_POINTS_ID, "Inner Points", "Closed inner ring points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_OUTER_PROFILE_ID, "Outer Profile", "Outer ring profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_INNER_PROFILE_ID, "Inner Profile", "Inner ring profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_OUTER_BOUNDARY_ID, "Outer Boundary", "Closed outer ring boundary", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_INNER_BOUNDARY_ID, "Inner Boundary", "Closed inner ring boundary", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when annulus boundaries were constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs annulus boundaries from center, inner/outer radii, plane, and segment count";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = ProfilePlaneUtils.resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object innerObj = inputValues.get(INPUT_INNER_RADIUS_ID);
        Object outerObj = inputValues.get(INPUT_OUTER_RADIUS_ID);
        Object segmentsObj = inputValues.get(INPUT_SEGMENTS_ID);
        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData p ? p : PlaneData.XY_PLANE;
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (center == null || !(innerObj instanceof Number inN) || !(outerObj instanceof Number outN) || !(segmentsObj instanceof Number segN)) {
            writeInvalid();
            return;
        }
        double inner = inN.doubleValue();
        double outer = outN.doubleValue();
        int segments = GenerationLimits.clampSegments(3, segN.intValue());
        if (!Double.isFinite(inner) || !Double.isFinite(outer) || inner <= 0.0d || outer <= 0.0d || inner >= outer) {
            writeInvalid();
            return;
        }

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        List<Vector3d> outerPts = buildRing(center, basis, outer, segments, false);
        List<Vector3d> innerPts = buildRing(center, basis, inner, segments, true);
        PlaneData resolvedPlane = new PlaneData(center, basis.normal());

        outputValues.put(OUTPUT_OUTER_POINTS_ID, List.copyOf(outerPts));
        outputValues.put(OUTPUT_INNER_POINTS_ID, List.copyOf(innerPts));
        outputValues.put(OUTPUT_OUTER_PROFILE_ID, new PolygonProfileData(outerPts, resolvedPlane));
        outputValues.put(OUTPUT_INNER_PROFILE_ID, new PolygonProfileData(innerPts, resolvedPlane));
        outputValues.put(OUTPUT_OUTER_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(outerPts));
        outputValues.put(OUTPUT_INNER_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(innerPts));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private List<Vector3d> buildRing(Vector3d center, ProfilePlaneUtils.Basis basis, double radius, int segments, boolean clockwise) {
        List<Vector3d> points = new ArrayList<>(segments + 1);
        double step = (Math.PI * 2.0d) / segments;
        for (int i = 0; i < segments; i++) {
            int index = clockwise ? (segments - i) : i;
            double a = step * index;
            points.add(new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(Math.cos(a) * radius))
                .add(new Vector3d(basis.yAxis()).mul(Math.sin(a) * radius)));
        }
        points.add(new Vector3d(points.get(0)));
        return points;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_OUTER_POINTS_ID, List.of());
        outputValues.put(OUTPUT_INNER_POINTS_ID, List.of());
        outputValues.put(OUTPUT_OUTER_PROFILE_ID, null);
        outputValues.put(OUTPUT_INNER_PROFILE_ID, null);
        outputValues.put(OUTPUT_OUTER_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_INNER_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

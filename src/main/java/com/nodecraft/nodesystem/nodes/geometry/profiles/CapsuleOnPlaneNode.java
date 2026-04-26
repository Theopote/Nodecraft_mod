package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.profiles.capsule_profile",
    displayName = "Capsule On Plane",
    description = "Constructs a capsule (stadium) profile from center, length, radius, and plane",
    category = "geometry.profiles",
    order = 18
)
public class CapsuleOnPlaneNode extends BaseNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_LENGTH_ID = "input_length";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_CAP_SEGMENTS_ID = "input_cap_segments";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public CapsuleOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.capsule_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Capsule center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_LENGTH_ID, "Length", "Total capsule length along local X axis", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Capsule radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CAP_SEGMENTS_ID, "Cap Segments", "Segments per semicircle cap", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "X Axis", "Optional in-plane capsule axis", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed capsule points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Capsule polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed capsule boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when capsule profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a capsule (stadium) profile from center, length, radius, and plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = ProfilePlaneUtils.resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object lengthObj = inputValues.get(INPUT_LENGTH_ID);
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);
        Object capSegmentsObj = inputValues.get(INPUT_CAP_SEGMENTS_ID);
        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData p ? p : PlaneData.XY_PLANE;
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (center == null || !(lengthObj instanceof Number lN) || !(radiusObj instanceof Number rN) || !(capSegmentsObj instanceof Number cN)) {
            writeInvalid();
            return;
        }
        double length = lN.doubleValue();
        double radius = rN.doubleValue();
        int capSegments = Math.max(1, cN.intValue());
        if (!Double.isFinite(length) || !Double.isFinite(radius) || length <= 0.0d || radius <= 0.0d) {
            writeInvalid();
            return;
        }

        double minLength = radius * 2.0d;
        double clampedLength = Math.max(length, minLength);
        double halfRectLength = (clampedLength * 0.5d) - radius;

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        List<Vector3d> points = new ArrayList<>();
        appendArc(points, center, basis, halfRectLength, 0.0d, -Math.PI * 0.5d, Math.PI * 0.5d, radius, capSegments, true);
        appendArc(points, center, basis, -halfRectLength, 0.0d, Math.PI * 0.5d, Math.PI * 1.5d, radius, capSegments, false);
        points.add(new Vector3d(points.get(0)));

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(points, new PlaneData(center, basis.normal())));
        outputValues.put(OUTPUT_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(points));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void appendArc(List<Vector3d> points, Vector3d center, ProfilePlaneUtils.Basis basis,
                           double cx, double cy, double start, double end, double radius, int segments, boolean includeStart) {
        for (int i = includeStart ? 0 : 1; i <= segments; i++) {
            double t = i / (double) segments;
            double a = start + (end - start) * t;
            double lx = cx + Math.cos(a) * radius;
            double ly = cy + Math.sin(a) * radius;
            points.add(new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(lx))
                .add(new Vector3d(basis.yAxis()).mul(ly)));
        }
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

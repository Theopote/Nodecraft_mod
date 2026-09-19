package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
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
    effect = NodeEffect.PURE,
    id = "geometry.profiles.rounded_rectangle_profile",
    displayName = "Rounded Rectangle On Plane",
    description = "Constructs a rounded-rectangle profile from center, width, height, corner radius, and plane",
    category = "geometry.profiles",
    order = 14
)
public class RoundedRectangleOnPlaneNode extends BaseNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_WIDTH_ID = "input_width";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_RADIUS_ID = "input_radius";
    private static final String INPUT_CORNER_SEGMENTS_ID = "input_corner_segments";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RoundedRectangleOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.rounded_rectangle_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Rounded-rectangle center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Total width", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Total height", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Corner Radius", "Corner fillet radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CORNER_SEGMENTS_ID, "Corner Segments", "Segments per corner arc", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "X Axis", "Optional in-plane rectangle X axis", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed rounded-rectangle points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Rounded-rectangle polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed rounded-rectangle boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when rounded-rectangle profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a rounded-rectangle profile from center, width, height, corner radius, and plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = ProfilePlaneUtils.resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object widthObj = inputValues.get(INPUT_WIDTH_ID);
        Object heightObj = inputValues.get(INPUT_HEIGHT_ID);
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);
        Object cornerSegmentsObj = inputValues.get(INPUT_CORNER_SEGMENTS_ID);
        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData p ? p : PlaneData.XY_PLANE;
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (center == null || !(widthObj instanceof Number wN) || !(heightObj instanceof Number hN)
            || !(radiusObj instanceof Number rN) || !(cornerSegmentsObj instanceof Number csN)) {
            writeInvalid();
            return;
        }
        double width = wN.doubleValue();
        double height = hN.doubleValue();
        double radius = rN.doubleValue();
        int cornerSegments = Math.max(1, csN.intValue());
        if (!Double.isFinite(width) || !Double.isFinite(height) || !Double.isFinite(radius) || width <= 0.0d || height <= 0.0d) {
            writeInvalid();
            return;
        }

        double halfW = width * 0.5d;
        double halfH = height * 0.5d;
        double clampedRadius = Math.max(0.0d, Math.min(radius, Math.min(halfW, halfH)));

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        List<Vector3d> points = new ArrayList<>();
        if (clampedRadius <= 1.0e-9d) {
            points.add(toWorld(center, basis, -halfW, -halfH));
            points.add(toWorld(center, basis, halfW, -halfH));
            points.add(toWorld(center, basis, halfW, halfH));
            points.add(toWorld(center, basis, -halfW, halfH));
        } else {
            appendCorner(points, center, basis, halfW - clampedRadius, -halfH + clampedRadius, -Math.PI * 0.5d, 0.0d, clampedRadius, cornerSegments, true);
            appendCorner(points, center, basis, halfW - clampedRadius, halfH - clampedRadius, 0.0d, Math.PI * 0.5d, clampedRadius, cornerSegments, false);
            appendCorner(points, center, basis, -halfW + clampedRadius, halfH - clampedRadius, Math.PI * 0.5d, Math.PI, clampedRadius, cornerSegments, false);
            appendCorner(points, center, basis, -halfW + clampedRadius, -halfH + clampedRadius, Math.PI, Math.PI * 1.5d, clampedRadius, cornerSegments, false);
        }
        points.add(new Vector3d(points.get(0)));

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(points, new PlaneData(center, basis.normal())));
        outputValues.put(OUTPUT_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(points));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void appendCorner(List<Vector3d> points, Vector3d center, ProfilePlaneUtils.Basis basis,
                              double cx, double cy, double start, double end, double radius, int segments, boolean includeStart) {
        for (int i = includeStart ? 0 : 1; i <= segments; i++) {
            double t = i / (double) segments;
            double a = start + (end - start) * t;
            double lx = cx + Math.cos(a) * radius;
            double ly = cy + Math.sin(a) * radius;
            points.add(toWorld(center, basis, lx, ly));
        }
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
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

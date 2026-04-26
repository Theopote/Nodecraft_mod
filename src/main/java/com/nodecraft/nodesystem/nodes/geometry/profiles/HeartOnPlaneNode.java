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
    id = "geometry.profiles.heart_profile",
    displayName = "Heart On Plane",
    description = "Constructs a heart profile from center, width, height, plane, and segment count",
    category = "geometry.profiles",
    order = 19
)
public class HeartOnPlaneNode extends BaseNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_WIDTH_ID = "input_width";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_SEGMENTS_ID = "input_segments";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public HeartOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.heart_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Heart center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Total heart width", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Total heart height", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Segments", "Boundary segment count", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "X Axis", "Optional in-plane heart X axis", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed heart points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Heart polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed heart boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when heart profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a heart profile from center, width, height, plane, and segment count";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = ProfilePlaneUtils.resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object widthObj = inputValues.get(INPUT_WIDTH_ID);
        Object heightObj = inputValues.get(INPUT_HEIGHT_ID);
        Object segmentsObj = inputValues.get(INPUT_SEGMENTS_ID);
        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData p ? p : PlaneData.XY_PLANE;
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (center == null || !(widthObj instanceof Number wN) || !(heightObj instanceof Number hN) || !(segmentsObj instanceof Number sN)) {
            writeInvalid();
            return;
        }
        double width = wN.doubleValue();
        double height = hN.doubleValue();
        int segments = Math.max(24, sN.intValue());
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0.0d || height <= 0.0d) {
            writeInvalid();
            return;
        }

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        List<Vector3d> local = new ArrayList<>(segments);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < segments; i++) {
            double t = (Math.PI * 2.0d) * i / segments;
            double x = 16.0d * Math.pow(Math.sin(t), 3.0d);
            double y = 13.0d * Math.cos(t) - 5.0d * Math.cos(2.0d * t) - 2.0d * Math.cos(3.0d * t) - Math.cos(4.0d * t);
            local.add(new Vector3d(x, y, 0.0d));
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        double rangeX = Math.max(1.0e-9d, maxX - minX);
        double rangeY = Math.max(1.0e-9d, maxY - minY);
        List<Vector3d> points = new ArrayList<>(segments + 1);
        for (Vector3d p : local) {
            double nx = ((p.x - minX) / rangeX - 0.5d) * width;
            double ny = ((p.y - minY) / rangeY - 0.5d) * height;
            points.add(new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(nx))
                .add(new Vector3d(basis.yAxis()).mul(ny)));
        }
        points.add(new Vector3d(points.get(0)));

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_PROFILE_ID, new PolygonProfileData(points, new PlaneData(center, basis.normal())));
        outputValues.put(OUTPUT_BOUNDARY_ID, ProfilePlaneUtils.toPolyline(points));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

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
    id = "geometry.profiles.star_polygon_profile",
    displayName = "Star Polygon On Plane",
    description = "Constructs a star polygon profile from center, inner/outer radii, point count, and plane",
    category = "geometry.profiles",
    order = 15
)
public class StarPolygonOnPlaneNode extends BaseNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_OUTER_RADIUS_ID = "input_outer_radius";
    private static final String INPUT_INNER_RADIUS_ID = "input_inner_radius";
    private static final String INPUT_POINT_COUNT_ID = "input_point_count";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_start_direction";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public StarPolygonOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.star_polygon_profile");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Star center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_OUTER_RADIUS_ID, "Outer Radius", "Radius of outer star points", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_INNER_RADIUS_ID, "Inner Radius", "Radius of inner star points", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_POINT_COUNT_ID, "Points", "Number of outer points", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Start Direction", "Optional in-plane direction to first outer point", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed star polygon points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Star polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed star polygon boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when star polygon profile was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a star polygon profile from center, inner/outer radii, point count, and plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = ProfilePlaneUtils.resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object outerObj = inputValues.get(INPUT_OUTER_RADIUS_ID);
        Object innerObj = inputValues.get(INPUT_INNER_RADIUS_ID);
        Object countObj = inputValues.get(INPUT_POINT_COUNT_ID);
        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData p ? p : PlaneData.XY_PLANE;
        Vector3d preferred = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d v ? new Vector3d(v) : null;

        if (center == null || !(outerObj instanceof Number outN) || !(innerObj instanceof Number inN) || !(countObj instanceof Number cN)) {
            writeInvalid();
            return;
        }
        double outer = outN.doubleValue();
        double inner = inN.doubleValue();
        int pointCount = cN.intValue();
        if (!Double.isFinite(outer) || !Double.isFinite(inner) || outer <= 0.0d || inner <= 0.0d || inner >= outer || pointCount < 3) {
            writeInvalid();
            return;
        }

        ProfilePlaneUtils.Basis basis = ProfilePlaneUtils.createBasis(plane, preferred);
        if (basis == null) {
            writeInvalid();
            return;
        }

        int totalVertices = pointCount * 2;
        double step = (Math.PI * 2.0d) / totalVertices;
        List<Vector3d> points = new ArrayList<>(totalVertices + 1);
        for (int i = 0; i < totalVertices; i++) {
            double radius = (i % 2 == 0) ? outer : inner;
            double a = i * step;
            points.add(new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(Math.cos(a) * radius))
                .add(new Vector3d(basis.yAxis()).mul(Math.sin(a) * radius)));
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

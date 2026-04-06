package com.nodecraft.nodesystem.nodes.spatial.analysis;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "spatial.analysis.deconstruct_polygon_profile",
    displayName = "Deconstruct Polygon Profile",
    description = "Extracts points, boundary, plane, center, perimeter, and area from a polygon profile",
    category = "spatial.analysis"
)
public class DeconstructPolygonProfileNode extends BaseNode {

    private static final String INPUT_PROFILE_ID = "input_profile";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_EDGE_COUNT_ID = "output_edge_count";
    private static final String OUTPUT_PERIMETER_ID = "output_perimeter";
    private static final String OUTPUT_AREA_ID = "output_area";
    private static final String OUTPUT_NORMAL_ID = "output_normal";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructPolygonProfileNode() {
        super(UUID.randomUUID(), "spatial.analysis.deconstruct_polygon_profile");

        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Polygon profile to deconstruct", NodeDataType.POLYGON_PROFILE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed polygon points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed polygon boundary", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Polygon plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Average polygon center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_COUNT_ID, "Edges", "Number of polygon edges", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PERIMETER_ID, "Perimeter", "Boundary length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_AREA_ID, "Area", "Polygon area on its plane", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Polygon plane normal", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a polygon profile was provided", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts points, boundary, plane, center, perimeter, and area from a polygon profile";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object profileObj = inputValues.get(INPUT_PROFILE_ID);
        if (!(profileObj instanceof PolygonProfileData profile)) {
            writeEmptyOutputs();
            return;
        }

        PlaneData plane = profile.getPlane();

        outputValues.put(OUTPUT_POINTS_ID, profile.getClosedPoints());
        outputValues.put(OUTPUT_BOUNDARY_ID, profile.getBoundary());
        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_CENTER_ID, profile.getCenter());
        outputValues.put(OUTPUT_EDGE_COUNT_ID, profile.getEdgeCount());
        outputValues.put(OUTPUT_PERIMETER_ID, profile.getBoundary().getLength());
        outputValues.put(OUTPUT_AREA_ID, computeArea(profile));
        outputValues.put(OUTPUT_NORMAL_ID, plane.getNormal());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_EDGE_COUNT_ID, 0);
        outputValues.put(OUTPUT_PERIMETER_ID, 0.0d);
        outputValues.put(OUTPUT_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_NORMAL_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private double computeArea(PolygonProfileData profile) {
        List<Vector3d> points = profile.getUniquePoints();
        if (points.size() < 3) {
            return 0.0d;
        }

        Vector3d areaVector = new Vector3d();
        for (int i = 0; i < points.size(); i++) {
            Vector3d current = points.get(i);
            Vector3d next = points.get((i + 1) % points.size());
            areaVector.add(new Vector3d(current).cross(next));
        }

        Vector3d normal = profile.getPlane().getNormal().normalize();
        return Math.abs(areaVector.dot(normal)) * 0.5d;
    }
}

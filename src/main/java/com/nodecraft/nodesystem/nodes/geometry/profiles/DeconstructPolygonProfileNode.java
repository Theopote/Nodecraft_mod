package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PolygonProfileMetrics;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.profiles.deconstruct_profile",
    displayName = "Deconstruct Polygon Profile",
    description = "Extracts points, boundary, plane, center, perimeter, and area from a polygon profile",
    category = "geometry.profiles",
    order = 21
)
public class DeconstructPolygonProfileNode extends AbstractProfileNode {

    private static final String INPUT_PROFILE_ID = "input_profile";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_EDGE_COUNT_ID = "output_edge_count";
    private static final String OUTPUT_PERIMETER_ID = "output_perimeter";
    private static final String OUTPUT_AREA_ID = "output_area";
    private static final String OUTPUT_NORMAL_ID = "output_normal";

    public DeconstructPolygonProfileNode() {
        super(UUID.randomUUID(), "geometry.profiles.deconstruct_profile");

        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Polygon profile to deconstruct", NodeDataType.POLYGON_PROFILE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed polygon boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Polygon plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Average polygon center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_COUNT_ID, "Edges", "Number of polygon edges", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PERIMETER_ID, "Perimeter", "Boundary length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_AREA_ID, "Area", "Polygon area on its plane", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Polygon plane normal", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a polygon profile was provided", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Extracts points, boundary, plane, center, perimeter, and area from a polygon profile";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PolygonProfileData profile = resolveStrictProfile(INPUT_PROFILE_ID);
        if (profile == null) {
            writeFailure("Valid polygon profile is required");
            return;
        }

        PlaneData plane = profile.plane();
        Vector3d center = PolygonProfileMetrics.center(profile);
        double area = PolygonProfileMetrics.area(profile);
        double perimeter = PolygonProfileMetrics.perimeter(profile);
        if (center == null || !Double.isFinite(area) || !Double.isFinite(perimeter)) {
            writeFailure("Profile metrics are non-finite");
            return;
        }

        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(profile.closedPoints()));
        outputValues.put(OUTPUT_BOUNDARY_ID, profile.getBoundaryPath());
        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_EDGE_COUNT_ID, profile.getEdgeCount());
        outputValues.put(OUTPUT_PERIMETER_ID, perimeter);
        outputValues.put(OUTPUT_AREA_ID, area);
        outputValues.put(OUTPUT_NORMAL_ID, VectorUtils.toVectorPort(plane.getNormal()));
        markSuccess();
    }

    private void writeFailure(String error) {
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putNullOutputs(OUTPUT_BOUNDARY_ID, OUTPUT_PLANE_ID, OUTPUT_CENTER_ID, OUTPUT_NORMAL_ID);
        putIntOutputs(0, OUTPUT_EDGE_COUNT_ID);
        putDoubleOutputs(Double.NaN, OUTPUT_PERIMETER_ID, OUTPUT_AREA_ID);
        markInvalid(error);
    }
}

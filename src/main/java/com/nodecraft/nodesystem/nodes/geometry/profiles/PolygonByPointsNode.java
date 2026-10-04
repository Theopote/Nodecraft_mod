package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.PolygonProfileValidator;
import com.nodecraft.nodesystem.util.ProfileConstructionUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.profiles.custom_profile",
    displayName = "Polygon By Points",
    description = "Constructs a planar polygon profile from an ordered point list",
    category = "geometry.profiles",
    order = 0
)
public class PolygonByPointsNode extends AbstractProfileNode {

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_EDGE_COUNT_ID = "output_edge_count";

    private static final double PLANAR_TOLERANCE = PolygonProfileValidator.COPLANAR_EPS;

    public PolygonByPointsNode() {
        super(UUID.randomUUID(), "geometry.profiles.custom_profile");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Ordered polygon points", NodeDataType.POINT_LIST, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed planar polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Resolved polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Closed polygon boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved polygon plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Average polygon center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_COUNT_ID, "Edges", "Number of polygon edges", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the input resolves to a planar polygon", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Constructs a planar polygon profile from an ordered point list";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> points = PointUtils.resolveStrictPointListBounded(
            inputValues.get(INPUT_POINTS_ID),
            GenerationLimits.MAX_PROFILE_VERTICES + 1
        );
        if (points == null) {
            writeFailure("Points must be a finite POINT_LIST within the vertex budget");
            return;
        }

        if (points.size() >= 2 && points.getFirst().distance(points.getLast()) <= PLANAR_TOLERANCE) {
            points = new ArrayList<>(points.subList(0, points.size() - 1));
        }
        if (!ProfileConstructionUtils.requireUniqueVertices(points.size())) {
            writeFailure(points.size() < 3
                ? "At least 3 polygon points are required"
                : "Polygon profile vertex count exceeds limit (" + GenerationLimits.MAX_PROFILE_VERTICES + ")");
            return;
        }

        PlaneData plane = computePlane(points);
        if (plane == null) {
            writeFailure("Points do not define a valid plane");
            return;
        }
        if (!isCoplanar(points, plane)) {
            writeFailure("Points are not coplanar");
            return;
        }

        List<Vector3d> closedPoints = new ArrayList<>(points);
        closedPoints.add(new Vector3d(points.getFirst()));

        StringBuilder error = new StringBuilder();
        PolygonProfileData profile = ProfileConstructionUtils.tryCreateProfile(closedPoints, plane, error);
        if (profile == null) {
            writeFailure(error.isEmpty() ? "Failed to create polygon profile" : error.toString());
            return;
        }

        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(profile.closedPoints()));
        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_BOUNDARY_ID, profile.getBoundaryPath());
        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(profile.getCenter()));
        outputValues.put(OUTPUT_EDGE_COUNT_ID, points.size());
        markSuccess();
    }

    private void writeFailure(String error) {
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putNullOutputs(OUTPUT_PROFILE_ID, OUTPUT_BOUNDARY_ID, OUTPUT_PLANE_ID, OUTPUT_CENTER_ID);
        putIntOutputs(0, OUTPUT_EDGE_COUNT_ID);
        markInvalid(error);
    }

    private @Nullable PlaneData computePlane(List<Vector3d> points) {
        Vector3d first = points.getFirst();
        for (int i = 1; i < points.size() - 1; i++) {
            for (int j = i + 1; j < points.size(); j++) {
                Vector3d a = VectorUtils.safeSubtract(points.get(i), first);
                Vector3d b = VectorUtils.safeSubtract(points.get(j), first);
                Vector3d unit = VectorUtils.safeNormalize(VectorUtils.safeCross(a, b));
                if (unit == null) {
                    continue;
                }
                PlaneData plane = PlaneData.canonical(first, unit);
                if (plane != null) {
                    return plane;
                }
            }
        }
        return null;
    }

    private boolean isCoplanar(List<Vector3d> points, PlaneData plane) {
        for (Vector3d point : points) {
            if (Math.abs(plane.signedDistanceTo(point)) > PLANAR_TOLERANCE) {
                return false;
            }
        }
        return true;
    }
}

package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.ProfileConstructionUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.profiles.convex_hull_plane",
    displayName = "Convex Hull 2D On Plane",
    description = "Projects points into a plane, computes their 2D convex hull, and outputs a closed polygon profile",
    category = "geometry.profiles",
    order = 18
)
public class ConvexHull2DOnPlaneNode extends AbstractProfileNode {

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_CENTER_ID = "output_center";

    public ConvexHull2DOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.convex_hull_plane");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Point cloud to hull",
            NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Plane used for projection and polygon embedding. Defaults to XZ (horizontal)",
            NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points",
            "Closed convex hull points",
            NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile",
            "Closed convex polygon profile on the plane",
            NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary",
            "Closed convex hull boundary path",
            NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane",
            "Resolved construction plane",
            NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center",
            "Average hull center",
            NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when a hull with at least three vertices was created",
            NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDisplayName() {
        return "Convex Hull 2D On Plane";
    }

    @Override
    public String getDescription() {
        return "Projects points into a plane, computes their 2D convex hull, and outputs a closed polygon profile";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlaneData plane = resolveConstructionPlane(INPUT_PLANE_ID);
        if (plane == null) {
            writeFailure("Plane is invalid");
            return;
        }

        List<Vector3d> world = PointUtils.resolveStrictPointListBounded(
            inputValues.get(INPUT_POINTS_ID),
            GenerationLimits.MAX_LIST_ELEMENTS
        );
        if (world == null || world.size() < 3) {
            writeFailure("At least 3 finite PointData entries are required");
            return;
        }

        Vector3d anchor = world.getFirst();
        PlaneProjectionUtils.PlaneProjectionContext projection =
            PlaneProjectionUtils.PlaneProjectionContext.from(plane, anchor);
        List<Vector2d> uvPoints = new ArrayList<>(world.size());
        for (Vector3d p : world) {
            Vector3d proj = plane.projectPoint(p);
            uvPoints.add(projection.toLocal(proj));
        }

        List<Vector2d> hull2d = convexHullMonotoneChain(uvPoints);
        if (hull2d.size() < 3) {
            writeFailure("Points do not form a 2D convex hull with at least three vertices");
            return;
        }

        if (!isWithinProfileVertices(hull2d.size())) {
            writeFailure("Polygon profile vertex count exceeds limit");
            return;
        }

        List<Vector3d> unique3d = new ArrayList<>(hull2d.size());
        for (Vector2d uv : hull2d) {
            unique3d.add(projection.fromLocal(uv));
        }

        List<Vector3d> closed = new ArrayList<>(unique3d.size() + 1);
        closed.addAll(unique3d);
        closed.add(new Vector3d(unique3d.getFirst()));

        StringBuilder error = new StringBuilder();
        PolygonProfileData profile = ProfileConstructionUtils.tryCreateProfile(closed, plane, error);
        if (profile == null) {
            writeFailure(error.isEmpty() ? "Failed to create convex hull profile" : error.toString());
            return;
        }
        outputValues.put(OUTPUT_POINTS_ID, ProfilePlaneUtils.toPointList(closed));
        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_BOUNDARY_ID, pathFromProfile(profile));
        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(profile.getCenter()));
        markSuccess();
    }

    private void writeFailure(String error) {
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putNullOutputs(OUTPUT_PROFILE_ID, OUTPUT_BOUNDARY_ID, OUTPUT_PLANE_ID, OUTPUT_CENTER_ID);
        markInvalid(error);
    }

    private static List<Vector2d> convexHullMonotoneChain(List<Vector2d> input) {
        Set<String> seen = new LinkedHashSet<>();
        List<Vector2d> points = new ArrayList<>();
        for (Vector2d p : input) {
            String key = quant(p.x) + ":" + quant(p.y);
            if (seen.add(key)) {
                points.add(new Vector2d(p));
            }
        }
        if (points.size() < 3) {
            return List.of();
        }
        points.sort(Comparator.comparingDouble((Vector2d p) -> p.x).thenComparingDouble(p -> p.y));

        List<Vector2d> lower = new ArrayList<>();
        for (Vector2d p : points) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.getLast(), p) <= 0.0d) {
                lower.removeLast();
            }
            lower.add(p);
        }

        List<Vector2d> upper = new ArrayList<>();
        for (int i = points.size() - 1; i >= 0; i--) {
            Vector2d p = points.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.getLast(), p) <= 0.0d) {
                upper.removeLast();
            }
            upper.add(p);
        }

        lower.removeLast();
        upper.removeLast();
        lower.addAll(upper);
        return lower;
    }

    private static double cross(Vector2d o, Vector2d a, Vector2d b) {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
    }

    private static final double DEDUPE_GRID = 1.0e-6d;

    private static String quant(double v) {
        if (!Double.isFinite(v)) {
            return "nan";
        }
        double scaled = v / DEDUPE_GRID;
        if (scaled > Long.MAX_VALUE || scaled < Long.MIN_VALUE) {
            return Double.toString(v);
        }
        return Long.toString(Math.round(scaled));
    }
}

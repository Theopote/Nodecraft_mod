package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.PolylineOffsetInPlaneNode;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a planar convex hull polygon from 3D points projected into a plane.
 */
@NodeInfo(
    id = "geometry.profiles.convex_hull_plane",
    displayName = "Convex Hull 2D On Plane",
    description = "Projects points into a plane, computes their 2D convex hull, and outputs a closed polygon profile",
    category = "geometry.profiles",
    order = 5
)
public class ConvexHull2DOnPlaneNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ConvexHull2DOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.profiles.convex_hull_plane");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Point cloud (list or single Point / Vector / BlockPos)",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Plane used for projection and polygon embedding",
            NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile",
            "Closed convex polygon profile on the plane",
            NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary",
            "Closed polyline boundary of the hull",
            NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when a hull with at least three vertices was created",
            NodeDataType.BOOLEAN, this));
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
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(planeObj instanceof PlaneData plane)) {
            writeInvalid();
            return;
        }

        List<Vector3d> world = collectPoints(inputValues.get(INPUT_POINTS_ID));
        if (world.size() < 3) {
            writeInvalid();
            return;
        }

        PolylineOffsetInPlaneNode.PlaneAxes axes = PolylineOffsetInPlaneNode.PlaneAxes.from(plane);
        List<Vector2d> uvPoints = new ArrayList<>();
        for (Vector3d p : world) {
            Vector3d proj = plane.projectPoint(p);
            uvPoints.add(axes.to2d(proj));
        }

        List<Vector2d> hull2d = convexHullMonotoneChain(uvPoints);
        if (hull2d.size() < 3) {
            writeInvalid();
            return;
        }

        List<Vector3d> unique3d = new ArrayList<>(hull2d.size());
        for (Vector2d uv : hull2d) {
            unique3d.add(axes.from2d(uv));
        }

        List<Vector3d> closed = new ArrayList<>(unique3d.size() + 1);
        closed.addAll(unique3d);
        closed.add(new Vector3d(unique3d.get(0)));

        PolygonProfileData profile = new PolygonProfileData(closed, plane);
        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_BOUNDARY_ID, profile.getBoundary());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static List<Vector3d> collectPoints(Object value) {
        List<Vector3d> out = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object entry : collection) {
                Vector3d p = resolvePoint(entry);
                if (p != null) {
                    out.add(p);
                }
            }
        } else {
            Vector3d p = resolvePoint(value);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    private static Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pd) {
            return new Vector3d(pd.getPosition());
        }
        if (value instanceof Vector3d v) {
            return new Vector3d(v);
        }
        if (value instanceof BlockPos bp) {
            return new Vector3d(bp.getX(), bp.getY(), bp.getZ());
        }
        return null;
    }

    /**
     * Andrew monotone chain; removes duplicate UVs; collinear boundary points may be simplified.
     */
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
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), p) <= 0.0d) {
                lower.remove(lower.size() - 1);
            }
            lower.add(p);
        }

        List<Vector2d> upper = new ArrayList<>();
        for (int i = points.size() - 1; i >= 0; i--) {
            Vector2d p = points.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), p) <= 0.0d) {
                upper.remove(upper.size() - 1);
            }
            upper.add(p);
        }

        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return lower;
    }

    private static double cross(Vector2d o, Vector2d a, Vector2d b) {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
    }

    private static final double DEDUPE_GRID = 1.0e-6d;

    private static String quant(double v) {
        long q = Math.round(v / DEDUPE_GRID);
        return Long.toString(q);
    }
}

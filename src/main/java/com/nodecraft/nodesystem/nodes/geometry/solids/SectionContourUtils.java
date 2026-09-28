package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.ProfileConstructionUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SectionContourUtils {

    private SectionContourUtils() {
    }

    static SectionResult cutSection(BlockPosList filled, PlaneData plane, double thickness) {
        double half = Math.max(0.1d, thickness) * 0.5d;
        Vector3d normal = plane.getNormal();
        if (normal.lengthSquared() <= 1.0e-12d) {
            return SectionResult.invalid();
        }
        normal.normalize();
        Vector3d origin = plane.getPoint();

        BlockPosList sliceBlocks = new BlockPosList();
        List<Vector3d> projected = new ArrayList<>();
        Set<Cell> cells = new HashSet<>();
        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        for (BlockPos blockPos : filled) {
            Vector3d center = new Vector3d(blockPos.getX() + 0.5d, blockPos.getY() + 0.5d, blockPos.getZ() + 0.5d);
            double distance = new Vector3d(center).sub(origin).dot(normal);
            if (Math.abs(distance) <= half) {
                Vector3d projectedPoint = plane.projectPoint(center);
                Vector2d uv = axes.to2d(projectedPoint);
                sliceBlocks.add(blockPos);
                projected.add(projectedPoint);
                cells.add(new Cell((int) Math.round(uv.x), (int) Math.round(uv.y)));
            }
        }

        List<List<Vertex>> loops = traceBoundaryLoops(cells);
        List<PolygonProfileData> profiles = new ArrayList<>();
        List<PolylineData> boundaries = new ArrayList<>();
        for (List<Vertex> loop : loops) {
            List<Vector3d> closed = toClosedPoints(loop, axes);
            if (closed.size() < 4) {
                continue;
            }

            PolygonProfileData profile = ProfileConstructionUtils.tryCreateProfile(closed, plane, null);
            if (profile == null) {
                continue;
            }

            List<Vec3d> boundary = new ArrayList<>(closed.size());
            for (Vector3d point : closed) {
                boundary.add(new Vec3d(point.x, point.y, point.z));
            }
            try {
                profiles.add(profile);
                boundaries.add(new PolylineData(boundary));
            } catch (IllegalArgumentException ignored) {
                // Ignore degenerate loops produced by touching voxel corners.
            }
        }

        List<PlanarRegionData> regions = buildRegionsFromProfiles(profiles, axes, plane);
        return new SectionResult(
            List.copyOf(profiles),
            List.copyOf(boundaries),
            List.copyOf(regions),
            sliceBlocks,
            List.copyOf(projected)
        );
    }

    /**
     * Groups coplanar section loops into planar regions via containment nesting.
     * Even nesting depth = outer; odd depth = hole of nearest even-depth ancestor.
     */
    static List<PlanarRegionData> buildRegionsFromProfiles(
            List<PolygonProfileData> profiles,
            PlaneProjectionUtils.PlaneAxes axes,
            PlaneData plane
    ) {
        if (profiles.isEmpty()) {
            return List.of();
        }

        GeometryFactory gf = new GeometryFactory();
        int n = profiles.size();
        Polygon[] polygons = new Polygon[n];
        double[] areas = new double[n];
        for (int i = 0; i < n; i++) {
            polygons[i] = toJtsShell(profiles.get(i), axes, gf);
            areas[i] = polygons[i] == null ? 0.0d : Math.abs(polygons[i].getArea());
        }

        int[] parent = new int[n];
        Arrays.fill(parent, -1);
        for (int i = 0; i < n; i++) {
            if (polygons[i] == null || areas[i] <= 0.0d) {
                continue;
            }
            int bestParent = -1;
            double bestArea = Double.POSITIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                if (i == j || polygons[j] == null || areas[j] <= areas[i]) {
                    continue;
                }
                if (!polygonContains(polygons[j], polygons[i])) {
                    continue;
                }
                if (areas[j] < bestArea) {
                    bestArea = areas[j];
                    bestParent = j;
                }
            }
            parent[i] = bestParent;
        }

        int[] depth = new int[n];
        for (int i = 0; i < n; i++) {
            depth[i] = computeDepth(i, parent);
        }

        List<PlanarRegionData> regions = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (polygons[i] == null || (depth[i] % 2) != 0) {
                continue;
            }
            List<PolygonProfileData> holes = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                if (parent[j] == i && (depth[j] % 2) == 1) {
                    holes.add(profiles.get(j));
                }
            }
            PlanarRegionData region = PlanarRegionData.tryCreate(profiles.get(i), holes, plane, null);
            if (region != null) {
                regions.add(region);
            }
        }

        regions.sort((a, b) -> Double.compare(regionAbsArea(b, axes), regionAbsArea(a, axes)));
        return List.copyOf(regions);
    }

    private static int computeDepth(int index, int[] parent) {
        int depth = 0;
        int current = index;
        Set<Integer> seen = new HashSet<>();
        while (parent[current] >= 0) {
            if (!seen.add(current)) {
                break;
            }
            current = parent[current];
            depth++;
            if (depth > parent.length) {
                break;
            }
        }
        return depth;
    }

    private static double regionAbsArea(PlanarRegionData region, PlaneProjectionUtils.PlaneAxes axes) {
        double area = Math.abs(profileAbsArea(region.outer(), axes));
        for (PolygonProfileData hole : region.holes()) {
            area -= Math.abs(profileAbsArea(hole, axes));
        }
        return Math.max(0.0d, area);
    }

    private static double profileAbsArea(PolygonProfileData profile, PlaneProjectionUtils.PlaneAxes axes) {
        List<Vector3d> pts = profile.closedPoints();
        double area2 = 0.0d;
        for (int i = 0; i < pts.size() - 1; i++) {
            Vector2d a = axes.to2d(pts.get(i));
            Vector2d b = axes.to2d(pts.get(i + 1));
            area2 += (a.x * b.y - b.x * a.y);
        }
        return Math.abs(area2 * 0.5d);
    }

    private static boolean polygonContains(Polygon outer, Polygon inner) {
        try {
            if (outer.covers(inner)) {
                return true;
            }
            if (outer.contains(inner.getInteriorPoint())) {
                return true;
            }
            return outer.contains(inner.getCentroid());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Polygon toJtsShell(
            PolygonProfileData profile,
            PlaneProjectionUtils.PlaneAxes axes,
            GeometryFactory gf
    ) {
        List<Vector3d> closed = profile.closedPoints();
        if (closed.size() < 4) {
            return null;
        }
        Coordinate[] coords = new Coordinate[closed.size()];
        for (int i = 0; i < closed.size(); i++) {
            Vector2d uv = axes.to2d(closed.get(i));
            coords[i] = new Coordinate(uv.x, uv.y);
        }
        try {
            Polygon polygon = gf.createPolygon(coords);
            // Heal mild voxel-grid self-touches so containment predicates are reliable.
            if (!polygon.isValid()) {
                var healed = polygon.buffer(0.0d);
                if (healed instanceof Polygon healedPoly) {
                    return healedPoly;
                }
                if (healed.getNumGeometries() > 0 && healed.getGeometryN(0) instanceof Polygon first) {
                    return first;
                }
                return null;
            }
            return polygon;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static List<List<Vertex>> traceBoundaryLoops(Set<Cell> cells) {
        List<Edge> edges = new ArrayList<>();
        for (Cell cell : cells) {
            int x = cell.u() * 2;
            int y = cell.v() * 2;
            Vertex bottomLeft = new Vertex(x - 1, y - 1);
            Vertex bottomRight = new Vertex(x + 1, y - 1);
            Vertex topRight = new Vertex(x + 1, y + 1);
            Vertex topLeft = new Vertex(x - 1, y + 1);

            if (!cells.contains(new Cell(cell.u(), cell.v() - 1))) {
                edges.add(new Edge(bottomLeft, bottomRight));
            }
            if (!cells.contains(new Cell(cell.u() + 1, cell.v()))) {
                edges.add(new Edge(bottomRight, topRight));
            }
            if (!cells.contains(new Cell(cell.u(), cell.v() + 1))) {
                edges.add(new Edge(topRight, topLeft));
            }
            if (!cells.contains(new Cell(cell.u() - 1, cell.v()))) {
                edges.add(new Edge(topLeft, bottomLeft));
            }
        }

        Map<Vertex, List<Edge>> outgoing = new HashMap<>();
        for (Edge edge : edges) {
            outgoing.computeIfAbsent(edge.start(), ignored -> new ArrayList<>()).add(edge);
        }

        List<List<Vertex>> loops = new ArrayList<>();
        while (true) {
            Edge first = takeFirstEdge(outgoing);
            if (first == null) {
                break;
            }

            List<Vertex> loop = new ArrayList<>();
            loop.add(first.start());
            Vertex current = first.end();
            loop.add(current);
            while (!current.equals(first.start())) {
                Edge next = takeNextEdge(outgoing, current);
                if (next == null) {
                    break;
                }
                current = next.end();
                loop.add(current);
            }

            if (loop.size() >= 4 && loop.getFirst().equals(loop.getLast())) {
                List<Vertex> simplified = simplifyLoop(loop);
                if (simplified.size() >= 4) {
                    loops.add(simplified);
                }
            }
        }

        loops.sort((a, b) -> Double.compare(Math.abs(signedArea(b)), Math.abs(signedArea(a))));
        return loops;
    }

    private static Edge takeFirstEdge(Map<Vertex, List<Edge>> outgoing) {
        for (List<Edge> edges : outgoing.values()) {
            if (!edges.isEmpty()) {
                return edges.removeFirst();
            }
        }
        return null;
    }

    private static Edge takeNextEdge(Map<Vertex, List<Edge>> outgoing, Vertex current) {
        List<Edge> edges = outgoing.get(current);
        if (edges == null || edges.isEmpty()) {
            return null;
        }
        return edges.removeFirst();
    }

    private static List<Vertex> simplifyLoop(List<Vertex> loop) {
        List<Vertex> simplified = new ArrayList<>(loop);
        if (simplified.size() < 4) {
            return simplified;
        }

        boolean changed;
        do {
            changed = false;
            for (int i = 1; i < simplified.size() - 1; i++) {
                Vertex previous = simplified.get(i - 1);
                Vertex current = simplified.get(i);
                Vertex next = simplified.get(i + 1);
                int dx1 = Integer.compare(current.x2() - previous.x2(), 0);
                int dy1 = Integer.compare(current.y2() - previous.y2(), 0);
                int dx2 = Integer.compare(next.x2() - current.x2(), 0);
                int dy2 = Integer.compare(next.y2() - current.y2(), 0);
                if (dx1 == dx2 && dy1 == dy2) {
                    simplified.remove(i);
                    changed = true;
                    break;
                }
            }
        } while (changed);

        return simplified;
    }

    private static List<Vector3d> toClosedPoints(List<Vertex> loop, PlaneProjectionUtils.PlaneAxes axes) {
        List<Vector3d> points = new ArrayList<>(loop.size());
        for (Vertex vertex : loop) {
            points.add(axes.from2d(new Vector2d(vertex.x2() * 0.5d, vertex.y2() * 0.5d)));
        }
        return points;
    }

    private static double signedArea(List<Vertex> loop) {
        double area = 0.0d;
        for (int i = 0; i < loop.size() - 1; i++) {
            Vertex a = loop.get(i);
            Vertex b = loop.get(i + 1);
            area += (double) a.x2() * b.y2() - (double) b.x2() * a.y2();
        }
        return area * 0.25d * 0.5d;
    }

    record SectionResult(
        List<PolygonProfileData> profiles,
        List<PolylineData> boundaries,
        List<PlanarRegionData> regions,
        BlockPosList sliceBlocks,
        List<Vector3d> projectedPoints
    ) {
        boolean valid() {
            return !profiles.isEmpty() && !boundaries.isEmpty();
        }

        PolygonProfileData primaryProfile() {
            return profiles.isEmpty() ? null : profiles.getFirst();
        }

        PolylineData primaryBoundary() {
            return boundaries.isEmpty() ? null : boundaries.getFirst();
        }

        PlanarRegionData primaryRegion() {
            return regions.isEmpty() ? null : regions.getFirst();
        }

        static SectionResult invalid() {
            return new SectionResult(List.of(), List.of(), List.of(), new BlockPosList(), List.of());
        }
    }

    private record Cell(int u, int v) {
    }

    private record Vertex(int x2, int y2) {
    }

    private record Edge(Vertex start, Vertex end) {
    }
}

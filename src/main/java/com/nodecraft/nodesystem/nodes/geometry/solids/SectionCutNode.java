package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.section_cut",
    displayName = "Section Cut",
    description = "Cuts geometry by one or more planes and traces voxel slice contours as section profiles, boundaries, blocks, and tree-grouped data.",
    category = "geometry.solids",
    order = 10
)
public class SectionCutNode extends BaseNode {
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_PLANES_ID = "input_planes";
    private static final String INPUT_THICKNESS_ID = "input_thickness";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PROFILES_ID = "output_profiles";
    private static final String OUTPUT_BOUNDARIES_ID = "output_boundaries";
    private static final String OUTPUT_PROFILES_TREE_ID = "output_profiles_tree";
    private static final String OUTPUT_BOUNDARIES_TREE_ID = "output_boundaries_tree";
    private static final String OUTPUT_SLICE_BLOCKS_ID = "output_slice_blocks";
    private static final String OUTPUT_SLICE_POINTS_ID = "output_slice_points";
    private static final String OUTPUT_SLICE_BLOCKS_TREE_ID = "output_slice_blocks_tree";
    private static final String OUTPUT_SLICE_POINTS_TREE_ID = "output_slice_points_tree";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SectionCutNode() {
        super(UUID.randomUUID(), "geometry.solids.section_cut");
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry fallback", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry fallback", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry fallback", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry fallback", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Section plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_PLANES_ID, "Planes", "Optional list of section planes. When connected, each plane outputs one section branch", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Slice thickness in world units", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Primary traced section profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Primary traced section boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles", "Resolved section profiles for all planes", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARIES_ID, "Boundaries", "Resolved section boundary polylines for all planes", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_TREE_ID, "Profiles Tree", "Section profiles keyed by plane index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARIES_TREE_ID, "Boundaries Tree", "Section boundary polylines keyed by plane index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_BLOCKS_ID, "Slice Blocks", "Voxel blocks intersecting the section slab", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_POINTS_ID, "Slice Points", "Projected section sample points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_BLOCKS_TREE_ID, "Slice Blocks Tree", "Slice blocks keyed by plane index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_POINTS_TREE_ID, "Slice Points Tree", "Projected section sample points keyed by plane index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when section profile is resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Cuts geometry by one or more planes and traces voxel slice contours as section profiles, boundaries, blocks, and tree-grouped data.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        GeometryData geometry = GeometryVoxelizer.resolveGeometry(
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID)
        );
        if (geometry == null) {
            writeInvalid();
            return;
        }

        double thickness = Math.max(0.1d, getDouble(INPUT_THICKNESS_ID, 1.0d));
        List<PlaneData> planes = resolvePlanes(inputValues.get(INPUT_PLANES_ID), inputValues.get(INPUT_PLANE_ID));
        BlockPosList filled = GeometryVoxelizer.voxelize(geometry, true);

        List<PolygonProfileData> profiles = new ArrayList<>();
        List<PolylineData> boundaries = new ArrayList<>();
        BlockPosList allSliceBlocks = new BlockPosList();
        List<Vector3d> allSlicePoints = new ArrayList<>();
        List<DataTreeData.Branch> profileBranches = new ArrayList<>();
        List<DataTreeData.Branch> boundaryBranches = new ArrayList<>();
        List<DataTreeData.Branch> blockBranches = new ArrayList<>();
        List<DataTreeData.Branch> pointBranches = new ArrayList<>();
        SectionResult firstValid = null;

        for (int i = 0; i < planes.size(); i++) {
            SectionResult result = cutSection(filled, planes.get(i), thickness);
            if (!result.profiles().isEmpty()) {
                profiles.addAll(result.profiles());
                for (int j = 0; j < result.profiles().size(); j++) {
                    profileBranches.add(new DataTreeData.Branch(List.of(i, j), List.of(result.profiles().get(j))));
                }
            } else {
                profileBranches.add(new DataTreeData.Branch(List.of(i), List.of()));
            }
            if (!result.boundaries().isEmpty()) {
                boundaries.addAll(result.boundaries());
                for (int j = 0; j < result.boundaries().size(); j++) {
                    boundaryBranches.add(new DataTreeData.Branch(List.of(i, j), List.of(result.boundaries().get(j))));
                }
            } else {
                boundaryBranches.add(new DataTreeData.Branch(List.of(i), List.of()));
            }
            allSliceBlocks.addAll(result.sliceBlocks().getPositions());
            allSlicePoints.addAll(result.projectedPoints());
            blockBranches.add(new DataTreeData.Branch(List.of(i), new ArrayList<>(result.sliceBlocks().getPositions())));
            pointBranches.add(new DataTreeData.Branch(List.of(i), new ArrayList<>(result.projectedPoints())));
            if (firstValid == null && result.valid()) {
                firstValid = result;
            }
        }

        outputValues.put(OUTPUT_PROFILE_ID, firstValid != null ? firstValid.primaryProfile() : null);
        outputValues.put(OUTPUT_BOUNDARY_ID, firstValid != null ? firstValid.primaryBoundary() : null);
        outputValues.put(OUTPUT_PROFILES_ID, List.copyOf(profiles));
        outputValues.put(OUTPUT_BOUNDARIES_ID, List.copyOf(boundaries));
        outputValues.put(OUTPUT_PROFILES_TREE_ID, new DataTreeData(profileBranches));
        outputValues.put(OUTPUT_BOUNDARIES_TREE_ID, new DataTreeData(boundaryBranches));
        outputValues.put(OUTPUT_SLICE_BLOCKS_ID, allSliceBlocks);
        outputValues.put(OUTPUT_SLICE_POINTS_ID, List.copyOf(allSlicePoints));
        outputValues.put(OUTPUT_SLICE_BLOCKS_TREE_ID, new DataTreeData(blockBranches));
        outputValues.put(OUTPUT_SLICE_POINTS_TREE_ID, new DataTreeData(pointBranches));
        outputValues.put(OUTPUT_VALID_ID, firstValid != null);
    }

    private SectionResult cutSection(BlockPosList filled, PlaneData plane, double thickness) {
        double half = thickness * 0.5d;
        Vector3d n = plane.getNormal();
        if (n.lengthSquared() <= 1.0e-12d) {
            return SectionResult.invalid();
        }
        n.normalize();
        Vector3d p0 = plane.getPoint();

        BlockPosList sliceBlocks = new BlockPosList();
        List<Vector3d> projected = new ArrayList<>();
        Set<Cell> cells = new HashSet<>();
        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        for (BlockPos b : filled) {
            Vector3d c = new Vector3d(b.getX() + 0.5d, b.getY() + 0.5d, b.getZ() + 0.5d);
            double d = new Vector3d(c).sub(p0).dot(n);
            if (Math.abs(d) <= half) {
                Vector3d q = plane.projectPoint(c);
                Vector2d uv = axes.to2d(q);
                sliceBlocks.add(b);
                projected.add(q);
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

            PolygonProfileData profile;
            try {
                profile = new PolygonProfileData(closed, plane);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            List<Vec3d> boundary = new ArrayList<>(closed.size());
            for (Vector3d p : closed) {
                boundary.add(new Vec3d(p.x, p.y, p.z));
            }
            try {
                profiles.add(profile);
                boundaries.add(new PolylineData(boundary));
            } catch (IllegalArgumentException ignored) {
                // Ignore degenerate loops produced by touching voxel corners.
            }
        }

        return new SectionResult(List.copyOf(profiles), List.copyOf(boundaries), sliceBlocks, List.copyOf(projected));
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_PROFILES_ID, List.of());
        outputValues.put(OUTPUT_BOUNDARIES_ID, List.of());
        outputValues.put(OUTPUT_PROFILES_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_BOUNDARIES_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SLICE_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_SLICE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SLICE_BLOCKS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SLICE_POINTS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<PlaneData> resolvePlanes(@Nullable Object planesObj, @Nullable Object planeObj) {
        if (planesObj instanceof List<?> list) {
            List<PlaneData> planes = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof PlaneData plane) {
                    planes.add(plane);
                }
            }
            if (!planes.isEmpty()) {
                return List.copyOf(planes);
            }
        }
        return List.of(planeObj instanceof PlaneData plane ? plane : PlaneData.XY_PLANE);
    }

    private double getDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private List<List<Vertex>> traceBoundaryLoops(Set<Cell> cells) {
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

    private Edge takeFirstEdge(Map<Vertex, List<Edge>> outgoing) {
        for (List<Edge> edges : outgoing.values()) {
            if (!edges.isEmpty()) {
                return edges.removeFirst();
            }
        }
        return null;
    }

    private Edge takeNextEdge(Map<Vertex, List<Edge>> outgoing, Vertex current) {
        List<Edge> edges = outgoing.get(current);
        if (edges == null || edges.isEmpty()) {
            return null;
        }
        return edges.removeFirst();
    }

    private List<Vertex> simplifyLoop(List<Vertex> loop) {
        List<Vertex> simplified = new ArrayList<>(loop);
        if (simplified.size() < 4) {
            return simplified;
        }

        boolean changed;
        do {
            changed = false;
            for (int i = 1; i < simplified.size() - 1; i++) {
                Vertex prev = simplified.get(i - 1);
                Vertex current = simplified.get(i);
                Vertex next = simplified.get(i + 1);
                int dx1 = Integer.compare(current.x2() - prev.x2(), 0);
                int dy1 = Integer.compare(current.y2() - prev.y2(), 0);
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

    private List<Vector3d> toClosedPoints(List<Vertex> loop, PlaneProjectionUtils.PlaneAxes axes) {
        List<Vector3d> points = new ArrayList<>(loop.size());
        for (Vertex vertex : loop) {
            points.add(axes.from2d(new Vector2d(vertex.x2() * 0.5d, vertex.y2() * 0.5d)));
        }
        return points;
    }

    private double signedArea(List<Vertex> loop) {
        double area = 0.0d;
        for (int i = 0; i < loop.size() - 1; i++) {
            Vertex a = loop.get(i);
            Vertex b = loop.get(i + 1);
            area += (double) a.x2() * b.y2() - (double) b.x2() * a.y2();
        }
        return area * 0.25d * 0.5d;
    }

    private record SectionResult(
        List<PolygonProfileData> profiles,
        List<PolylineData> boundaries,
        BlockPosList sliceBlocks,
        List<Vector3d> projectedPoints
    ) {
        private boolean valid() {
            return !profiles.isEmpty() && !boundaries.isEmpty();
        }

        private @Nullable PolygonProfileData primaryProfile() {
            return profiles.isEmpty() ? null : profiles.getFirst();
        }

        private @Nullable PolylineData primaryBoundary() {
            return boundaries.isEmpty() ? null : boundaries.getFirst();
        }

        private static SectionResult invalid() {
            return new SectionResult(List.of(), List.of(), new BlockPosList(), List.of());
        }
    }

    private record Cell(int u, int v) {
    }

    private record Vertex(int x2, int y2) {
    }

    private record Edge(Vertex start, Vertex end) {
    }
}

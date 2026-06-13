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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.section_cut",
    displayName = "Section Cut",
    description = "Cuts geometry by one or more planes and outputs section profiles, boundaries, slice blocks, and tree-grouped contour data.",
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

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Primary section profile (convex hull)", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Section profile boundary polyline", NodeDataType.POLYLINE, this));
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
        return "Cuts geometry by one or more planes and outputs section profiles, boundaries, slice blocks, and tree-grouped contour data.";
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
            List<Integer> path = List.of(i);
            if (result.profile() != null) {
                profiles.add(result.profile());
                profileBranches.add(new DataTreeData.Branch(path, List.of(result.profile())));
            } else {
                profileBranches.add(new DataTreeData.Branch(path, List.of()));
            }
            if (result.boundary() != null) {
                boundaries.add(result.boundary());
                boundaryBranches.add(new DataTreeData.Branch(path, List.of(result.boundary())));
            } else {
                boundaryBranches.add(new DataTreeData.Branch(path, List.of()));
            }
            allSliceBlocks.addAll(result.sliceBlocks().getPositions());
            allSlicePoints.addAll(result.projectedPoints());
            blockBranches.add(new DataTreeData.Branch(path, new ArrayList<>(result.sliceBlocks().getPositions())));
            pointBranches.add(new DataTreeData.Branch(path, new ArrayList<>(result.projectedPoints())));
            if (firstValid == null && result.valid()) {
                firstValid = result;
            }
        }

        outputValues.put(OUTPUT_PROFILE_ID, firstValid != null ? firstValid.profile() : null);
        outputValues.put(OUTPUT_BOUNDARY_ID, firstValid != null ? firstValid.boundary() : null);
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
        List<Vector2d> uv = new ArrayList<>();
        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        for (BlockPos b : filled) {
            Vector3d c = new Vector3d(b.getX() + 0.5d, b.getY() + 0.5d, b.getZ() + 0.5d);
            double d = new Vector3d(c).sub(p0).dot(n);
            if (Math.abs(d) <= half) {
                Vector3d q = plane.projectPoint(c);
                sliceBlocks.add(b);
                projected.add(q);
                uv.add(axes.to2d(q));
            }
        }
        if (projected.size() < 3) {
            return new SectionResult(null, null, sliceBlocks, List.copyOf(projected));
        }

        List<Vector2d> hull = convexHull(uv);
        if (hull.size() < 3) {
            return new SectionResult(null, null, sliceBlocks, List.copyOf(projected));
        }

        List<Vector3d> closed = new ArrayList<>(hull.size() + 1);
        for (Vector2d p : hull) {
            closed.add(axes.from2d(p));
        }
        closed.add(new Vector3d(closed.getFirst()));

        PolygonProfileData profile;
        try {
            profile = new PolygonProfileData(closed, plane);
        } catch (IllegalArgumentException ex) {
            return new SectionResult(null, null, sliceBlocks, List.copyOf(projected));
        }

        List<Vec3d> boundary = new ArrayList<>(closed.size());
        for (Vector3d p : closed) {
            boundary.add(new Vec3d(p.x, p.y, p.z));
        }

        return new SectionResult(profile, new PolylineData(boundary), sliceBlocks, List.copyOf(projected));
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

    private List<Vector2d> convexHull(List<Vector2d> points) {
        List<Vector2d> pts = new ArrayList<>(points.size());
        for (Vector2d p : points) {
            pts.add(new Vector2d(p));
        }
        pts.sort(Comparator.<Vector2d>comparingDouble(v -> v.x).thenComparingDouble(v -> v.y));
        if (pts.size() <= 2) {
            return pts;
        }
        List<Vector2d> lower = new ArrayList<>();
        for (Vector2d p : pts) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.getLast(), p) <= 0.0d) {
                lower.removeLast();
            }
            lower.add(p);
        }
        List<Vector2d> upper = new ArrayList<>();
        for (int i = pts.size() - 1; i >= 0; i--) {
            Vector2d p = pts.get(i);
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

    private double cross(Vector2d a, Vector2d b, Vector2d c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private record SectionResult(
        @Nullable PolygonProfileData profile,
        @Nullable PolylineData boundary,
        BlockPosList sliceBlocks,
        List<Vector3d> projectedPoints
    ) {
        private boolean valid() {
            return profile != null && boundary != null;
        }

        private static SectionResult invalid() {
            return new SectionResult(null, null, new BlockPosList(), List.of());
        }
    }
}


import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
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
    description = "Cuts geometry by a plane and outputs a section profile extracted from voxelized intersection points.",
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
    private static final String INPUT_THICKNESS_ID = "input_thickness";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_SLICE_BLOCKS_ID = "output_slice_blocks";
    private static final String OUTPUT_SLICE_POINTS_ID = "output_slice_points";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SectionCutNode() {
        super(UUID.randomUUID(), "geometry.solids.section_cut");
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry fallback", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry fallback", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry fallback", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry fallback", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Section plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Slice thickness in world units", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Primary section profile (convex hull)", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Section profile boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_BLOCKS_ID, "Slice Blocks", "Voxel blocks intersecting the section slab", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_POINTS_ID, "Slice Points", "Projected section sample points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when section profile is resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Cuts geometry by a plane and outputs a section profile extracted from voxelized intersection points.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        PlaneData plane = planeObj instanceof PlaneData p ? p : PlaneData.XY_PLANE;
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
        double half = thickness * 0.5d;
        Vector3d n = plane.getNormal();
        if (n.lengthSquared() <= 1.0e-12d) {
            writeInvalid();
            return;
        }
        n.normalize();
        Vector3d p0 = plane.getPoint();

        BlockPosList filled = GeometryVoxelizer.voxelize(geometry, true);
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
            writeInvalid();
            outputValues.put(OUTPUT_SLICE_BLOCKS_ID, sliceBlocks);
            outputValues.put(OUTPUT_SLICE_POINTS_ID, List.copyOf(projected));
            return;
        }

        List<Vector2d> hull = convexHull(uv);
        if (hull.size() < 3) {
            writeInvalid();
            outputValues.put(OUTPUT_SLICE_BLOCKS_ID, sliceBlocks);
            outputValues.put(OUTPUT_SLICE_POINTS_ID, List.copyOf(projected));
            return;
        }

        List<Vector3d> closed = new ArrayList<>(hull.size() + 1);
        for (Vector2d p : hull) {
            closed.add(axes.from2d(p));
        }
        closed.add(new Vector3d(closed.get(0)));

        PolygonProfileData profile;
        try {
            profile = new PolygonProfileData(closed, plane);
        } catch (IllegalArgumentException ex) {
            writeInvalid();
            outputValues.put(OUTPUT_SLICE_BLOCKS_ID, sliceBlocks);
            outputValues.put(OUTPUT_SLICE_POINTS_ID, List.copyOf(projected));
            return;
        }

        List<Vec3d> boundary = new ArrayList<>(closed.size());
        for (Vector3d p : closed) {
            boundary.add(new Vec3d(p.x, p.y, p.z));
        }

        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_BOUNDARY_ID, new PolylineData(boundary));
        outputValues.put(OUTPUT_SLICE_BLOCKS_ID, sliceBlocks);
        outputValues.put(OUTPUT_SLICE_POINTS_ID, List.copyOf(projected));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_SLICE_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_SLICE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
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
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), p) <= 0.0d) {
                lower.remove(lower.size() - 1);
            }
            lower.add(p);
        }
        List<Vector2d> upper = new ArrayList<>();
        for (int i = pts.size() - 1; i >= 0; i--) {
            Vector2d p = pts.get(i);
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

    private double cross(Vector2d a, Vector2d b, Vector2d c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }
}

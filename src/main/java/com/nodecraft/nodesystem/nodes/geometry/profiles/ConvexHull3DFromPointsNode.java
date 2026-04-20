package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ConvexHull3d;
import com.nodecraft.nodesystem.util.ConvexHull3d.HullResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Computes a 3D convex hull from a moderate point cloud using brute-force facet enumeration.
 */
@NodeInfo(
    id = "geometry.profiles.convex_hull_3d_points",
    displayName = "Convex Hull 3D From Points",
    description = "Builds a 3D convex hull (triangle facets) from points; intended for small clouds due to brute-force enumeration; coplanar / collinear inputs yield no facets",
    category = "geometry.profiles",
    order = 7
)
public class ConvexHull3DFromPointsNode extends BaseNode {

    @NodeProperty(displayName = "Max Points", category = "Hull", order = 1,
        description = "Maximum number of input sites considered after light de-duplication (performance cap)")
    private int maxPoints = 96;

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_VERTICES_ID = "output_vertices";
    private static final String OUTPUT_FACES_ID = "output_faces";
    private static final String OUTPUT_TRIANGLE_COUNT_ID = "output_triangle_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ConvexHull3DFromPointsNode() {
        super(UUID.randomUUID(), "geometry.profiles.convex_hull_3d_points");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Point cloud (list or single Point / Vector / BlockPos)",
            NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_VERTICES_ID, "Hull Vertices",
            "Hull vertices (de-duplicated, stable order)",
            NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FACES_ID, "Triangles",
            "Each entry is a 3-point Vector3d list (one triangle)",
            NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_TRIANGLE_COUNT_ID, "Triangle Count",
            "Number of hull triangles",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when at least one hull triangle was created",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Convex Hull 3D From Points";
    }

    @Override
    public String getDescription() {
        return "Builds a 3D convex hull (triangle facets) from points; intended for small clouds due to brute-force enumeration; coplanar / collinear inputs yield no facets";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> world = collectPoints(inputValues.get(INPUT_POINTS_ID));
        if (world.size() < 4) {
            writeInvalid();
            return;
        }
        int cap = Math.max(4, maxPoints);
        if (world.size() > cap) {
            writeInvalid();
            return;
        }

        HullResult hull = ConvexHull3d.compute(world);
        if (hull.facetIndices().isEmpty()) {
            writeInvalid();
            return;
        }

        List<Vector3d> verts = hull.vertices();
        List<List<Vector3d>> triangles = new ArrayList<>(hull.facetIndices().size());
        for (int[] f : hull.facetIndices()) {
            List<Vector3d> tri = new ArrayList<>(3);
            tri.add(new Vector3d(verts.get(f[0])));
            tri.add(new Vector3d(verts.get(f[1])));
            tri.add(new Vector3d(verts.get(f[2])));
            triangles.add(tri);
        }

        List<Vector3d> vertCopy = new ArrayList<>(verts.size());
        for (Vector3d v : verts) {
            vertCopy.add(new Vector3d(v));
        }

        List<Object> faceObjects = new ArrayList<>(triangles.size());
        faceObjects.addAll(triangles);

        outputValues.put(OUTPUT_VERTICES_ID, vertCopy);
        outputValues.put(OUTPUT_FACES_ID, faceObjects);
        outputValues.put(OUTPUT_TRIANGLE_COUNT_ID, triangles.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_VERTICES_ID, List.of());
        outputValues.put(OUTPUT_FACES_ID, List.of());
        outputValues.put(OUTPUT_TRIANGLE_COUNT_ID, 0);
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
        if (value instanceof Vec3d vec) {
            return new Vector3d(vec.x, vec.y, vec.z);
        }
        return null;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(int maxPoints) {
        int v = Math.max(4, maxPoints);
        if (this.maxPoints != v) {
            this.maxPoints = v;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of("maxPoints", maxPoints);
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("maxPoints") instanceof Number n) {
            setMaxPoints(n.intValue());
        }
    }
}

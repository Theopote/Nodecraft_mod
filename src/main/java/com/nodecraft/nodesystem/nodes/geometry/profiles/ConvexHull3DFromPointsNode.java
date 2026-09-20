package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ConvexHull3d;
import com.nodecraft.nodesystem.util.ConvexHull3d.HullResult;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Computes a 3D convex hull from a moderate point cloud using brute-force facet enumeration.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
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
            "Point cloud to hull",
            NodeDataType.POINT_LIST, this));

        addOutputPort(new BasePort(OUTPUT_VERTICES_ID, "Hull Vertices",
            "Hull vertices (de-duplicated, stable order)",
            NodeDataType.POINT_LIST, this));
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
        List<Vector3d> world = SpatialValueResolver.resolvePointList(inputValues.get(INPUT_POINTS_ID));
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

        List<Object> faceObjects = new ArrayList<>(triangles.size());
        faceObjects.addAll(triangles);

        outputValues.put(OUTPUT_VERTICES_ID, SpatialValueResolver.toPointDataList(verts));
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

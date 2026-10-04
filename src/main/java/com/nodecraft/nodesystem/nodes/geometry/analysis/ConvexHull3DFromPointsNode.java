package com.nodecraft.nodesystem.nodes.geometry.analysis;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.TriangleMeshData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ConvexHull3d;
import com.nodecraft.nodesystem.util.ConvexHull3d.HullResult;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Computes a 3D convex hull from a moderate point cloud using brute-force facet enumeration
 * with planar facet grouping and indexed mesh output.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.analysis.convex_hull_3d",
    displayName = "Convex Hull 3D From Points",
    description = "Builds a 3D convex hull mesh from points; intended for small clouds due to brute-force enumeration; coplanar / collinear inputs yield no facets",
    category = "geometry.analysis",
    order = 2
)
public class ConvexHull3DFromPointsNode extends BaseNode {

    @NodeProperty(displayName = "Max Points", category = "Hull", order = 1,
        description = "Maximum number of input sites considered after light de-duplication (performance cap)")
    private int maxPoints = 96;

    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_MESH_ID = "output_mesh";
    private static final String OUTPUT_VERTICES_ID = "output_vertices";
    private static final String OUTPUT_TRIANGLE_COUNT_ID = "output_triangle_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ConvexHull3DFromPointsNode() {
        super(UUID.randomUUID(), "geometry.analysis.convex_hull_3d");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Point cloud to hull",
            NodeDataType.POINT_LIST, this));

        addOutputPort(new BasePort(OUTPUT_MESH_ID, "Mesh",
            "Indexed triangle mesh of the convex hull",
            NodeDataType.TRIANGLE_MESH, this));
        addOutputPort(new BasePort(OUTPUT_VERTICES_ID, "Hull Vertices",
            "Hull vertices (de-duplicated, stable order)",
            NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TRIANGLE_COUNT_ID, "Triangle Count",
            "Number of hull triangles",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when at least one hull triangle was created",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false",
            NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Convex Hull 3D From Points";
    }

    @Override
    public String getDescription() {
        return "Builds a 3D convex hull mesh from points; intended for small clouds due to brute-force enumeration; coplanar / collinear inputs yield no facets";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> world = PointUtils.resolveStrictPointListBounded(
            inputValues.get(INPUT_POINTS_ID),
            GenerationLimits.MAX_CONVEX_HULL_3D_POINTS
        );
        if (world == null) {
            writeFailure("Valid point list is required");
            return;
        }
        if (maxPoints < 4 || maxPoints > GenerationLimits.MAX_CONVEX_HULL_3D_POINTS) {
            writeFailure("Max points property must be between 4 and "
                    + GenerationLimits.MAX_CONVEX_HULL_3D_POINTS);
            return;
        }

        List<Vector3d> deduped = ConvexHull3d.dedupePoints(world);
        if (deduped.size() < 4) {
            writeFailure("At least 4 unique points are required");
            return;
        }
        if (deduped.size() > maxPoints) {
            writeFailure("Unique point count exceeds max points (" + maxPoints + ")");
            return;
        }

        HullResult hull = ConvexHull3d.compute(deduped);
        if (hull.facetIndices().isEmpty()) {
            writeFailure("Points do not form a 3D convex hull with facets");
            return;
        }

        TriangleMeshData mesh = TriangleMeshData.tryCreate(hull.vertices(), hull.facetIndices());
        if (mesh == null) {
            writeFailure("Failed to build hull mesh");
            return;
        }

        outputValues.put(OUTPUT_MESH_ID, mesh);
        outputValues.put(OUTPUT_VERTICES_ID, SpatialValueResolver.toPointDataList(mesh.vertices()));
        outputValues.put(OUTPUT_TRIANGLE_COUNT_ID, mesh.triangleCount());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeFailure(String error) {
        outputValues.put(OUTPUT_MESH_ID, null);
        outputValues.put(OUTPUT_VERTICES_ID, List.of());
        outputValues.put(OUTPUT_TRIANGLE_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(int maxPoints) {
        if (this.maxPoints != maxPoints) {
            this.maxPoints = maxPoints;
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
        if (map.get("maxPoints") instanceof Integer n) {
            setMaxPoints(n);
        }
    }
}

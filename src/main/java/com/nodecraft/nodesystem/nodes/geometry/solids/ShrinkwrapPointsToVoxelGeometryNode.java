package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizationResult;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VoxelizationStatus;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Projects query points to the closest voxel block center from a voxelized geometry shell or solid.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.shrinkwrap_points_voxel_geometry",
    displayName = "Shrinkwrap Points To Voxel Geometry",
    description = "Voxelizes geometry to blocks, then snaps each query point to the nearest voxel block center (shell when fill is off); distinct from triangle strip shrinkwrap",
    category = "geometry.solids",
    order = 21
)
public class ShrinkwrapPointsToVoxelGeometryNode extends AbstractSolidNode {

    @NodeProperty(displayName = "Fill Solid", category = "Voxel", order = 1,
        description = "When enabled, all interior voxels are included; when disabled, only the outer shell is used where supported")
    private boolean fillSolid = false;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";

    public ShrinkwrapPointsToVoxelGeometryNode() {
        super(UUID.randomUUID(), "geometry.solids.shrinkwrap_points_voxel_geometry");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Query point list to project onto voxel geometry",
            NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Geometry to voxelize before nearest-center projection",
            NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Projected Points",
            "Closest voxel block centers as point list",
            NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances",
            "Per-point distances from query to projected center",
            NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when projection succeeded",
            NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDisplayName() {
        return "Shrinkwrap Points To Voxel Geometry";
    }

    @Override
    public String getDescription() {
        return "Voxelizes geometry to blocks, then snaps each query point to the nearest voxel block center (shell when fill is off); distinct from triangle strip shrinkwrap";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            invalidate("Geometry is missing or invalid");
            return;
        }

        List<Vector3d> queries = SpatialValueResolver.resolvePointList(inputValues.get(INPUT_POINTS_ID));
        if (queries.isEmpty()) {
            invalidate("Query point list is empty");
            return;
        }

        GeometryVoxelizationResult voxelResult = GeometryVoxelizer.voxelizeStrict(geometry, fillSolid);
        if (!voxelResult.success()) {
            invalidate(voxelError(voxelResult));
            return;
        }

        BlockPosList voxels = voxelResult.blocks();
        if (voxels.isEmpty()) {
            invalidate("Voxelization produced no blocks");
            return;
        }

        List<BlockPos> voxelList = new ArrayList<>(voxels.size());
        for (BlockPos p : voxels) {
            voxelList.add(p);
        }

        List<Vector3d> projected = new ArrayList<>(queries.size());
        List<Double> distances = new ArrayList<>(queries.size());
        for (Vector3d q : queries) {
            BlockPos best = null;
            double bestSq = Double.MAX_VALUE;
            for (BlockPos bp : voxelList) {
                Vector3d c = blockCenter(bp);
                double dSq = q.distanceSquared(c);
                if (dSq < bestSq) {
                    bestSq = dSq;
                    best = bp;
                }
            }
            if (best == null) {
                invalidate("Nearest voxel could not be resolved");
                return;
            }
            projected.add(blockCenter(best));
            distances.add(Math.sqrt(bestSq));
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(projected));
        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
        markSuccess();
    }

    private static Vector3d blockCenter(BlockPos bp) {
        return new Vector3d(bp.getX() + 0.5d, bp.getY() + 0.5d, bp.getZ() + 0.5d);
    }

    private static String voxelError(GeometryVoxelizationResult result) {
        if (result.error() != null && !result.error().isBlank()) {
            return result.error();
        }
        VoxelizationStatus status = result.status();
        return switch (status) {
            case OVER_BUDGET -> "Voxelization exceeded budget";
            case UNSUPPORTED -> "Voxelization unsupported for geometry";
            case INVALID_BOUNDS -> "Voxelization bounds are invalid";
            case CHILD_FAILURE -> "Voxelization failed for geometry child";
            default -> "Voxelization failed";
        };
    }

    public boolean isFillSolid() {
        return fillSolid;
    }

    public void setFillSolid(boolean fillSolid) {
        markDirtyIfChanged(this.fillSolid, fillSolid);
        this.fillSolid = fillSolid;
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of("fillSolid", fillSolid);
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("fillSolid") instanceof Boolean b) {
            setFillSolid(b);
        }
    }

    private void invalidate(String error) {
        putEmptyListOutputs(OUTPUT_POINTS_ID, OUTPUT_DISTANCES_ID);
        markInvalid(error);
    }
}

package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
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
    id = "geometry.solids.shrinkwrap_points_voxel_geometry",
    displayName = "Shrinkwrap Points To Voxel Geometry",
    description = "Voxelizes geometry to blocks, then snaps each query point to the nearest voxel block center (shell when fill is off); distinct from triangle strip shrinkwrap",
    category = "geometry.solids",
    order = 21
)
public class ShrinkwrapPointsToVoxelGeometryNode extends BaseNode {

    @NodeProperty(displayName = "Fill Solid", category = "Voxel", order = 1,
        description = "When enabled, all interior voxels are included; when disabled, only the outer shell is used where supported")
    private boolean fillSolid = false;

    @NodeProperty(displayName = "Max Voxels", category = "Voxel", order = 2,
        description = "Safety cap on voxel count; increase for larger shapes (nearest search is O(queries × voxels))")
    private int maxVoxels = 65536;

    @NodeProperty(displayName = "Max Queries", category = "Voxel", order = 3,
        description = "Maximum number of query points processed (extra points are ignored)")
    private int maxQueries = 4096;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ShrinkwrapPointsToVoxelGeometryNode() {
        super(UUID.randomUUID(), "geometry.solids.shrinkwrap_points_voxel_geometry");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Point list (Point, Vector, BlockPos, etc.) or a single point value",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Geometry to voxelize before nearest-center projection",
            NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Projected Points",
            "Closest voxel block centers as Vector3d list",
            NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances",
            "Per-point distances from query to projected center",
            NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when projection succeeded",
            NodeDataType.BOOLEAN, this));
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
            writeInvalid();
            return;
        }

        List<Vector3d> queries = SolidNodeUtils.resolvePointList(inputValues.get(INPUT_POINTS_ID));
        if (queries.isEmpty()) {
            writeInvalid();
            return;
        }

        int qCap = Math.max(1, maxQueries);
        if (queries.size() > qCap) {
            queries = new ArrayList<>(queries.subList(0, qCap));
        }

        BlockPosList voxels = GeometryVoxelizer.voxelize(geometry, fillSolid);
        if (voxels.isEmpty()) {
            writeInvalid();
            return;
        }

        int vCap = Math.max(1, maxVoxels);
        List<BlockPos> voxelList = new ArrayList<>(Math.min(voxels.size(), vCap));
        int i = 0;
        for (BlockPos p : voxels) {
            if (i++ >= vCap) {
                break;
            }
            voxelList.add(p);
        }
        if (voxelList.isEmpty()) {
            writeInvalid();
            return;
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
                writeInvalid();
                return;
            }
            projected.add(blockCenter(best));
            distances.add(Math.sqrt(bestSq));
        }

        outputValues.put(OUTPUT_POINTS_ID, projected);
        outputValues.put(OUTPUT_DISTANCES_ID, distances);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private static Vector3d blockCenter(BlockPos bp) {
        return new Vector3d(bp.getX() + 0.5d, bp.getY() + 0.5d, bp.getZ() + 0.5d);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_DISTANCES_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    public boolean isFillSolid() {
        return fillSolid;
    }

    public void setFillSolid(boolean fillSolid) {
        if (this.fillSolid != fillSolid) {
            this.fillSolid = fillSolid;
            markDirty();
        }
    }

    public int getMaxVoxels() {
        return maxVoxels;
    }

    public void setMaxVoxels(int maxVoxels) {
        int v = Math.max(1, maxVoxels);
        if (this.maxVoxels != v) {
            this.maxVoxels = v;
            markDirty();
        }
    }

    public int getMaxQueries() {
        return maxQueries;
    }

    public void setMaxQueries(int maxQueries) {
        int v = Math.max(1, maxQueries);
        if (this.maxQueries != v) {
            this.maxQueries = v;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of(
            "fillSolid", fillSolid,
            "maxVoxels", maxVoxels,
            "maxQueries", maxQueries
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("fillSolid") instanceof Boolean b) {
            setFillSolid(b);
        }
        if (map.get("maxVoxels") instanceof Number n) {
            setMaxVoxels(n.intValue());
        }
        if (map.get("maxQueries") instanceof Number n) {
            setMaxQueries(n.intValue());
        }
    }
}

package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict geometry-tree validation and voxelization for {@code geometry.voxel} nodes.
 */
public final class VoxelInputUtils {

    private VoxelInputUtils() {
    }

    public record TreeVoxelizationOutcome(
        VoxelizationStatus status,
        BlockPosList blocks,
        DataTreeData blocksTree,
        @Nullable RegionData region,
        String error
    ) {
        public boolean success() {
            return status == VoxelizationStatus.SUCCESS;
        }

        static TreeVoxelizationOutcome ok(
                BlockPosList blocks,
                DataTreeData blocksTree,
                @Nullable RegionData region
        ) {
            return new TreeVoxelizationOutcome(VoxelizationStatus.SUCCESS, blocks, blocksTree, region, "");
        }

        static TreeVoxelizationOutcome fail(VoxelizationStatus status, String error) {
            return new TreeVoxelizationOutcome(
                status,
                new BlockPosList(),
                DataTreeData.empty(),
                null,
                error == null ? "" : error
            );
        }
    }

    /**
     * Transactional tree voxelization: every branch item must be {@link GeometryData};
     * any child failure or aggregate budget violation fails the whole tree.
     */
    public static TreeVoxelizationOutcome voxelizeGeometryTree(DataTreeData tree, boolean fillSolid) {
        if (tree.getBranchCount() == 0) {
            return TreeVoxelizationOutcome.ok(new BlockPosList(), DataTreeData.empty(), null);
        }

        int geometryItemCount = 0;
        for (DataTreeData.Branch branch : tree.getBranches()) {
            geometryItemCount += branch.items().size();
        }
        if (geometryItemCount > GenerationLimits.MAX_GEOMETRY_INSTANCES) {
            return TreeVoxelizationOutcome.fail(
                VoxelizationStatus.OVER_BUDGET,
                "Geometry Tree item count exceeds limit (" + GenerationLimits.MAX_GEOMETRY_INSTANCES + ")"
            );
        }

        Set<BlockPos> merged = new LinkedHashSet<>();
        List<DataTreeData.Branch> blockBranches = new ArrayList<>();
        long treeMaterializedItems = 0L;

        for (DataTreeData.Branch branch : tree.getBranches()) {
            Set<BlockPos> branchMerged = new LinkedHashSet<>();
            List<Integer> branchPath = branch.path();
            int itemIndex = 0;
            for (Object item : branch.items()) {
                if (!(item instanceof GeometryData geometry)) {
                    return TreeVoxelizationOutcome.fail(
                        VoxelizationStatus.UNSUPPORTED,
                        "Geometry Tree branch " + formatPath(branchPath)
                            + ", item " + itemIndex + ": expected GEOMETRY, got "
                            + (item == null ? "null" : item.getClass().getSimpleName())
                    );
                }

                GeometryVoxelizationResult childResult = GeometryVoxelizer.voxelizeStrict(geometry, fillSolid);
                if (!childResult.success()) {
                    VoxelizationStatus status = childResult.status() == VoxelizationStatus.SUCCESS
                        ? VoxelizationStatus.CHILD_FAILURE
                        : childResult.status();
                    String childError = childResult.error().isEmpty()
                        ? "Geometry Tree branch " + formatPath(branchPath) + ", item " + itemIndex + " voxelization failed"
                        : "Geometry Tree branch " + formatPath(branchPath) + ", item " + itemIndex + ": "
                            + childResult.error();
                    return TreeVoxelizationOutcome.fail(status, childError);
                }

                GeometryVoxelizationResult branchMergeError =
                    GeometryVoxelizer.mergeIntoSet(branchMerged, childResult.blocks());
                if (branchMergeError != null) {
                    return TreeVoxelizationOutcome.fail(branchMergeError.status(), branchMergeError.error());
                }
                GeometryVoxelizationResult mergeError = GeometryVoxelizer.mergeIntoSet(merged, childResult.blocks());
                if (mergeError != null) {
                    return TreeVoxelizationOutcome.fail(mergeError.status(), mergeError.error());
                }
                itemIndex++;
            }

            if (!branchMerged.isEmpty()) {
                try {
                    treeMaterializedItems = Math.addExact(treeMaterializedItems, branchMerged.size());
                } catch (ArithmeticException overflow) {
                    return TreeVoxelizationOutcome.fail(
                        VoxelizationStatus.OVER_BUDGET,
                        "Blocks Tree materialized item count overflow"
                    );
                }
                if (treeMaterializedItems > GenerationLimits.MAX_VOXEL_TREE_BLOCK_ITEMS) {
                    return TreeVoxelizationOutcome.fail(
                        VoxelizationStatus.OVER_BUDGET,
                        "Blocks Tree materialized item count exceeds MAX_VOXEL_TREE_BLOCK_ITEMS ("
                            + GenerationLimits.MAX_VOXEL_TREE_BLOCK_ITEMS + ")"
                    );
                }
                blockBranches.add(new DataTreeData.Branch(branchPath, new ArrayList<>(branchMerged)));
            }
        }

        return TreeVoxelizationOutcome.ok(
            new BlockPosList(merged),
            new DataTreeData(blockBranches),
            BlockListUtils.regionFromOccupiedBlocks(merged)
        );
    }

    private static String formatPath(List<Integer> path) {
        if (path.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(String.valueOf(path.get(i)));
        }
        builder.append(']');
        return builder.toString();
    }
}

package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.nodes.geometry.solids.SectionContourUtils.SectionResult;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;

/**
 * Shared Voxel Section / Voxel Contours preflight and aggregate output budgets.
 */
final class SectionWorkBudget {

    private long blockItems;
    private long pointItems;
    private long contourItems;

    private SectionWorkBudget() {
    }

    static @Nullable String preflight(int planeCount, int voxelCount) {
        if (planeCount < 1) {
            return "At least one section plane is required";
        }
        if (planeCount > GenerationLimits.MAX_SECTION_PLANES) {
            return "Plane count exceeds limit (" + GenerationLimits.MAX_SECTION_PLANES + ")";
        }
        if (voxelCount < 0) {
            return "Section workload exceeds limit (" + GenerationLimits.MAX_SECTION_WORK + ")";
        }
        long work = (long) planeCount * (long) voxelCount;
        if (work < 0L || work > GenerationLimits.MAX_SECTION_WORK) {
            return "Section workload exceeds limit (" + GenerationLimits.MAX_SECTION_WORK + ")";
        }
        return null;
    }

    static SectionWorkBudget create() {
        return new SectionWorkBudget();
    }

    @Nullable String accumulate(SectionResult result) {
        if (result == null) {
            return "Section result is missing";
        }
        contourItems += result.profiles().size();
        blockItems += result.sliceBlocks().size();
        pointItems += result.projectedPoints().size();
        if (contourItems < 0L || contourItems > GenerationLimits.MAX_SECTION_TOTAL_CONTOURS) {
            return "Section contour count exceeds limit (" + GenerationLimits.MAX_SECTION_TOTAL_CONTOURS + ")";
        }
        if (blockItems < 0L || blockItems > GenerationLimits.MAX_SECTION_TOTAL_BLOCK_ITEMS) {
            return "Section slice block count exceeds limit (" + GenerationLimits.MAX_SECTION_TOTAL_BLOCK_ITEMS + ")";
        }
        if (pointItems < 0L || pointItems > GenerationLimits.MAX_SECTION_TOTAL_POINT_ITEMS) {
            return "Section slice point count exceeds limit (" + GenerationLimits.MAX_SECTION_TOTAL_POINT_ITEMS + ")";
        }
        return null;
    }
}

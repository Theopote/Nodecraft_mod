package com.nodecraft.nodesystem.contract.support;

import com.nodecraft.nodesystem.preview.PreviewSampling;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.PlacementSubmitPlan;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static placement-set comparisons for Preview ↔ Apply acceptance contracts.
 * Do not compare surface analytic previews to Bake placements — only voxel / placement plans.
 */
public final class PreviewApplyPlacementAssert {

    private PreviewApplyPlacementAssert() {
    }

    public static Set<BlockPos> positionsOf(PlacementSubmitPlan.Plan plan) {
        Set<BlockPos> positions = new HashSet<>();
        for (PlacementSubmitPlan.Entry entry : plan.entries()) {
            positions.add(entry.pos().toImmutable());
        }
        return positions;
    }

    public static Set<BlockPos> positionsOf(BlockPosList blocks) {
        return ArchitecturalVoxelAssert.toSolidSet(blocks);
    }

    public static void assertSamePositions(Set<BlockPos> expected, Set<BlockPos> actual, String label) {
        if (expected.equals(actual)) {
            return;
        }
        Set<BlockPos> missing = new TreeSet<>(BlockPos::compareTo);
        missing.addAll(expected);
        missing.removeAll(actual);
        Set<BlockPos> extra = new TreeSet<>(BlockPos::compareTo);
        extra.addAll(actual);
        extra.removeAll(expected);
        assertEquals(expected, actual,
                label + " position mismatch; missing=" + summarize(missing) + " extra=" + summarize(extra));
    }

    public static void assertSamePlacementMap(
            Map<BlockPos, BlockState> expected,
            Map<BlockPos, BlockState> actual,
            String label
    ) {
        assertSamePositions(expected.keySet(), actual.keySet(), label + " keys");
        for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
            BlockState got = actual.get(entry.getKey());
            assertEquals(entry.getValue(), got,
                    label + " BlockState mismatch at " + entry.getKey()
                            + " expected=" + entry.getValue() + " actual=" + got);
        }
    }

    public static void assertSampleSubsetOfFull(
            PreviewSampling.BlockSample sample,
            Set<BlockPos> fullPositions,
            int maxPreviewBlocks
    ) {
        assertEquals(fullPositions.size(), sample.totalCount(), "sample total must match full Apply set size");
        assertTrue(sample.renderedCount() <= maxPreviewBlocks);
        Set<BlockPos> shown = positionsOf(sample.blocks());
        assertTrue(fullPositions.containsAll(shown), "sampled preview blocks must be a subset of the Apply set");
        if (fullPositions.size() > maxPreviewBlocks) {
            assertTrue(sample.sampled(), "expected sampled preview when full count exceeds cap");
            assertTrue(sample.renderedCount() < sample.totalCount());
        } else {
            assertFalse(sample.sampled());
            assertEquals(fullPositions.size(), sample.renderedCount());
        }
    }

    private static String summarize(Set<BlockPos> positions) {
        if (positions.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (BlockPos pos : positions) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ());
            if (++i >= 12) {
                sb.append(", …(").append(positions.size()).append(" total)");
                break;
            }
        }
        return sb.append(']').toString();
    }
}

package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.contract.support.PreviewApplyPlacementAssert;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.preview.PreviewSampling;
import com.nodecraft.nodesystem.util.GeometryVoxelizationResult;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5A: voxel-boolean preview sampling must not change the Apply placement set.
 */
class PreviewGeometryVoxelSamplingContractTest {

    @Test
    void sampledPreviewIsSubsetOfApplyPlanAndDoesNotShrinkApply() {
        // Large solid minus tiny cutter → many voxels (well above a tiny preview cap).
        BoxGeometryData base = new BoxGeometryData(new Vector3d(8, 4, 8), new Vector3d(8, 4, 8));
        BoxGeometryData cutter = new BoxGeometryData(new Vector3d(0.5, 0.5, 0.5), new Vector3d(0.5, 0.5, 0.5));
        DifferenceGeometryData difference = new DifferenceGeometryData(base, cutter);

        GeometryVoxelizationResult full = GeometryVoxelizer.voxelizeStrict(difference, true);
        assertTrue(full.success(), full.error());
        Set<BlockPos> fullPositions = PreviewApplyPlacementAssert.positionsOf(full.blocks());
        assertTrue(fullPositions.size() > 50);

        int previewCap = 20;
        PreviewSampling.BlockSample sample = PreviewSampling.sampleBlocks(full.blocks(), previewCap);
        PreviewApplyPlacementAssert.assertSampleSubsetOfFull(sample, fullPositions, previewCap);

        GeometryVoxelizationResult applyVoxels = GeometryVoxelizer.resolveBlocksStrict(
                null, difference, null, null, null, null, true);
        assertTrue(applyVoxels.success(), applyVoxels.error());
        PreviewApplyPlacementAssert.assertSamePositions(
                fullPositions,
                PreviewApplyPlacementAssert.positionsOf(applyVoxels.blocks()),
                "Apply resolveBlocksStrict must use full voxel set even when preview samples"
        );
        assertEquals(fullPositions.size(), applyVoxels.blocks().size());
        assertTrue(sample.sampled());
        assertTrue(sample.renderedCount() < applyVoxels.blocks().size());
    }

    @Test
    void previewNodePublishesVoxelSamplingOutputsForDifference() {
        BoxGeometryData base = new BoxGeometryData(new Vector3d(1.5, 1.5, 1.5), new Vector3d(1.5, 1.5, 1.5));
        BoxGeometryData cutter = new BoxGeometryData(new Vector3d(0.4, 0.4, 0.4), new Vector3d(0.4, 0.4, 0.4));
        DifferenceGeometryData difference = new DifferenceGeometryData(base, cutter);

        GeometryVoxelizationResult full = GeometryVoxelizer.voxelizeStrict(difference, true);
        assertTrue(full.success(), full.error());

        PreviewGeometryNode preview = new PreviewGeometryNode();
        preview.setInput("input_geometry", difference);
        preview.processNode(null);

        assertEquals(Boolean.TRUE, preview.getOutput("output_success"));
        assertEquals(PreviewGeometryNode.KIND_VOXEL_FULL, preview.getOutput("output_preview_kind"));
        assertEquals(full.blocks().size(), preview.getOutput("output_voxel_total_count"));
        assertEquals(full.blocks().size(), preview.getOutput("output_voxel_shown_count"));
        assertEquals(Boolean.FALSE, preview.getOutput("output_voxel_sampled"));
    }
}

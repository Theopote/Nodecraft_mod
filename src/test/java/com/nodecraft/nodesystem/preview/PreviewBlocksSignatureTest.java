package com.nodecraft.nodesystem.preview;

import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PreviewBlocksSignatureTest {

    @Test
    void swappedMaterialsAtSamePositionsChangeFingerprint() {
        PreviewBlock stoneAtA = new PreviewBlock(1, 2, 3, "minecraft:stone");
        PreviewBlock oakAtB = new PreviewBlock(4, 5, 6, "minecraft:oak_planks");
        PreviewBlock oakAtA = new PreviewBlock(1, 2, 3, "minecraft:oak_planks");
        PreviewBlock stoneAtB = new PreviewBlock(4, 5, 6, "minecraft:stone");

        long before = PreviewBlocksSignature.computeContentFingerprint(List.of(stoneAtA, oakAtB));
        long after = PreviewBlocksSignature.computeContentFingerprint(List.of(oakAtA, stoneAtB));
        assertNotEquals(before, after);
    }

    @Test
    void styleFingerprintChangesWhenTransparencyChanges() {
        long opaque = PreviewBlocksSignature.computeStyleFingerprint(0.5f, true, 30);
        long clearer = PreviewBlocksSignature.computeStyleFingerprint(0.9f, true, 30);
        assertNotEquals(opaque, clearer);
    }

    @Test
    void duplicatePositionsUseLastWinsBeforeFingerprint() {
        PreviewBlock first = new PreviewBlock(0, 0, 0, "minecraft:stone");
        PreviewBlock second = new PreviewBlock(0, 0, 0, "minecraft:gold_block");
        List<PreviewBlock> deduped = PreviewBlocksSignature.dedupeLastWinsByCell(List.of(first, second));
        assertEquals(1, deduped.size());
        assertEquals("minecraft:gold_block", deduped.getFirst().blockId());
    }

    @Test
    void distinctCoordinatesAreNotMergedByBitOverlap() {
        PreviewBlock a = new PreviewBlock(2_097_152, 0, 0, "minecraft:stone");
        PreviewBlock b = new PreviewBlock(0, 1, 0, "minecraft:oak_planks");
        List<PreviewBlock> deduped = PreviewBlocksSignature.dedupeLastWinsByCell(List.of(a, b));
        assertEquals(2, deduped.size());
    }

    @Test
    void negativeCoordinatesRemainDistinct() {
        PreviewBlock a = new PreviewBlock(-1, -64, -30000000, "minecraft:stone");
        PreviewBlock b = new PreviewBlock(-2, -64, -30000000, "minecraft:stone");
        List<PreviewBlock> deduped = PreviewBlocksSignature.dedupeLastWinsByCell(List.of(a, b));
        assertEquals(2, deduped.size());
    }

    @Test
    void horizontalBoundaryCoordinatesRemainDistinct() {
        PreviewBlock min = new PreviewBlock(-30_000_000, 64, 0, "minecraft:stone");
        PreviewBlock max = new PreviewBlock(30_000_000, 64, 0, "minecraft:stone");
        List<PreviewBlock> deduped = PreviewBlocksSignature.dedupeLastWinsByCell(List.of(min, max));
        assertEquals(2, deduped.size());
    }

    @Test
    void previewAndApplyDuplicatePositionsBothUseLastWins() {
        BlockPos pos = new BlockPos(4, 64, 8);
        List<BlockPlacementData> applyOrder = List.of(
                new BlockPlacementData(pos, "minecraft:stone"),
                new BlockPlacementData(pos, "minecraft:gold_block")
        );
        List<PreviewBlock> previewOrder = List.of(
                new PreviewBlock(4, 64, 8, "minecraft:stone"),
                new PreviewBlock(4, 64, 8, "minecraft:gold_block")
        );

        String previewWinner = PreviewBlocksSignature.dedupeLastWinsByCell(previewOrder).getFirst().blockId();
        String applyWinner = applyOrder.getLast().blockId();

        assertEquals(applyWinner, previewWinner);
        assertEquals("minecraft:gold_block", previewWinner);
    }

    @Test
    void flooredCoordinatesShareCellForPreviewDedupe() {
        PreviewBlock low = new PreviewBlock(1.2, 64.4, 8.6, "minecraft:stone");
        PreviewBlock high = new PreviewBlock(1.8, 64.6, 8.2, "minecraft:gold_block");
        List<PreviewBlock> deduped = PreviewBlocksSignature.dedupeLastWinsByCell(List.of(low, high));
        assertEquals(1, deduped.size());
        assertEquals("minecraft:gold_block", deduped.getFirst().blockId());
        assertEquals(new BlockPos(1, 64, 8), PreviewBlocksSignature.toCell(high));
    }

    @Test
    void fingerprintsAreSixtyFourBitLongs() {
        long content = PreviewBlocksSignature.computeContentFingerprint(
            List.of(new PreviewBlock(0, 0, 0, "minecraft:stone")));
        long style = PreviewBlocksSignature.computeStyleFingerprint(0.5f, true, 30);
        assertNotEquals(0L, content);
        assertNotEquals(0L, style);
    }
}

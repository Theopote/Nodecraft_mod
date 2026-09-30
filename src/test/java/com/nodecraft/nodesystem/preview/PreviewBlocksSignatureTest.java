package com.nodecraft.nodesystem.preview;

import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
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

        int before = PreviewBlocksSignature.computeContentFingerprint(List.of(stoneAtA, oakAtB));
        int after = PreviewBlocksSignature.computeContentFingerprint(List.of(oakAtA, stoneAtB));
        assertNotEquals(before, after);
    }

    @Test
    void styleFingerprintChangesWhenTransparencyChanges() {
        int opaque = PreviewBlocksSignature.computeStyleFingerprint(0.5f, true, 30);
        int clearer = PreviewBlocksSignature.computeStyleFingerprint(0.9f, true, 30);
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
}

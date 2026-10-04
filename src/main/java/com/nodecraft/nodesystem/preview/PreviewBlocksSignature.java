package com.nodecraft.nodesystem.preview;

import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Stable content/style fingerprints and cell helpers for {@link com.nodecraft.nodesystem.nodes.output.preview.PreviewBlocksNode}.
 */
public final class PreviewBlocksSignature {

    private PreviewBlocksSignature() {
    }

    /**
     * Floors preview coordinates to the integer block cell used by Apply Changes and bake.
     */
    public static BlockPos toCell(PreviewBlock block) {
        return BlockPos.ofFloored(block.x(), block.y(), block.z());
    }

    /**
     * Last entry wins for the same integer cell — matches Apply Changes duplicate-position semantics.
     */
    public static List<PreviewBlock> dedupeLastWinsByCell(List<PreviewBlock> blocks) {
        LinkedHashMap<BlockPos, PreviewBlock> byCell = new LinkedHashMap<>();
        for (PreviewBlock block : blocks) {
            byCell.put(toCell(block), block);
        }
        return List.copyOf(byCell.values());
    }

    public static long computeContentFingerprint(List<PreviewBlock> blocks) {
        return PreviewFingerprint.ofPreviewBlocks(blocks);
    }

    public static long computeStyleFingerprint(float transparency, boolean showOutline, int durationSeconds) {
        return PreviewFingerprint.ofGhostStyle(transparency, showOutline, durationSeconds);
    }
}

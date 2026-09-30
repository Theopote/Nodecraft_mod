package com.nodecraft.nodesystem.preview;

import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Stable content/style fingerprints for {@link com.nodecraft.nodesystem.nodes.output.preview.PreviewBlocksNode}.
 * Position and material are hashed as one bound record (not independent XOR aggregates).
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

    public static int computeContentFingerprint(List<PreviewBlock> blocks) {
        List<PreviewBlock> canonical = new ArrayList<>(blocks);
        canonical.sort(Comparator
                .comparingDouble(PreviewBlock::x)
                .thenComparingDouble(PreviewBlock::y)
                .thenComparingDouble(PreviewBlock::z));

        int hash = 17;
        hash = 31 * hash + canonical.size();
        for (PreviewBlock block : canonical) {
            hash = 31 * hash + boundBlockFingerprint(block);
        }
        return hash;
    }

    public static int computeStyleFingerprint(float transparency, boolean showOutline, int durationSeconds) {
        int hash = 17;
        hash = 31 * hash + Float.hashCode(transparency);
        hash = 31 * hash + Boolean.hashCode(showOutline);
        hash = 31 * hash + durationSeconds;
        return hash;
    }

    private static int boundBlockFingerprint(PreviewBlock block) {
        int hash = 17;
        hash = 31 * hash + Long.hashCode(Double.doubleToLongBits(block.x()));
        hash = 31 * hash + Long.hashCode(Double.doubleToLongBits(block.y()));
        hash = 31 * hash + Long.hashCode(Double.doubleToLongBits(block.z()));
        hash = 31 * hash + block.blockId().hashCode();
        if (block.stateData() != null) {
            hash = 31 * hash + block.stateData().hashCode();
        }
        return hash;
    }
}

package com.nodecraft.nodesystem.preview;

import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Shared 64-bit content/style fingerprints for preview cache keys.
 */
public final class PreviewFingerprint {

    private PreviewFingerprint() {
    }

    public static long ofPreviewBlocks(List<PreviewBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return 0L;
        }
        List<PreviewBlock> canonical = new ArrayList<>(blocks);
        canonical.sort(Comparator
            .comparingDouble(PreviewBlock::x)
            .thenComparingDouble(PreviewBlock::y)
            .thenComparingDouble(PreviewBlock::z)
            .thenComparing(PreviewBlock::blockId));

        long hash = 17L;
        hash = mix(hash, canonical.size());
        for (PreviewBlock block : canonical) {
            hash = mix(hash, Double.doubleToLongBits(block.x()));
            hash = mix(hash, Double.doubleToLongBits(block.y()));
            hash = mix(hash, Double.doubleToLongBits(block.z()));
            hash = mix(hash, block.blockId().hashCode());
            if (block.stateData() != null) {
                hash = mix(hash, Objects.requireNonNull(block.stateData()).hashCode());
            }
        }
        return hash;
    }

    public static long ofBlockPosList(@Nullable BlockPosList blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return 0L;
        }
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : blocks) {
            if (pos != null) {
                positions.add(pos.toImmutable());
            }
        }
        positions.sort(Comparator
            .comparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ));

        long hash = 17L;
        hash = mix(hash, positions.size());
        for (BlockPos pos : positions) {
            hash = mix(hash, pos.asLong());
        }
        return hash;
    }

    public static long ofGhostStyle(float transparency, boolean showOutline, int durationSeconds) {
        long hash = 17L;
        hash = mix(hash, Float.hashCode(transparency));
        hash = mix(hash, Boolean.hashCode(showOutline));
        hash = mix(hash, durationSeconds);
        return hash;
    }

    public static long mix(long hash, long value) {
        long mixed = hash * 31L + value;
        mixed ^= (mixed >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= (mixed >>> 33);
        return mixed;
    }
}

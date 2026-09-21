package com.nodecraft.nodesystem.preview.protocol;

import com.nodecraft.nodesystem.util.BlockStateData;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * One block cell in world space. Coordinates are world-space doubles; consumers snap to cells as needed.
 * Optional {@link BlockStateData} mirrors {@code BlockPlacementData} so Preview ≈ Apply Changes.
 */
public final class PreviewBlock {
    private final double x;
    private final double y;
    private final double z;
    private final String blockId;
    private final @Nullable BlockStateData stateData;

    public PreviewBlock(double x, double y, double z, String blockId) {
        this(x, y, z, blockId, null);
    }

    public PreviewBlock(double x, double y, double z, String blockId, @Nullable BlockStateData stateData) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = Objects.requireNonNull(blockId, "blockId");
        if (blockId.isEmpty()) {
            throw new IllegalArgumentException("blockId must be non-empty");
        }
        this.stateData = stateData != null && !stateData.isEmpty() ? stateData.copy() : null;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public String blockId() {
        return blockId;
    }

    public @Nullable BlockStateData stateData() {
        return stateData != null ? stateData.copy() : null;
    }
}

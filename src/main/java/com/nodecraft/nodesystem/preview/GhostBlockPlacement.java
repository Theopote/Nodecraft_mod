package com.nodecraft.nodesystem.preview;

import net.minecraft.util.math.Vec3d;

/**
 * Stable preview payload for ghost blocks. Kept as a top-level type so producers
 * ({@code GeometryViewerNode}, {@code PreviewBlocksNode}, …) and
 * {@link com.nodecraft.nodesystem.preview.elements.GhostBlockElement} share one {@code Class}
 * and {@code instanceof} cannot fail across class loaders for inner classes.
 */
public record GhostBlockPlacement(Vec3d position, String blockId, float opacity) {
    public GhostBlockPlacement(Vec3d position, String blockId) {
        this(position, blockId, 0.5f);
    }

    public GhostBlockPlacement {
        opacity = Math.max(0.0f, Math.min(1.0f, opacity));
    }
}

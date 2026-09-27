package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

/**
 * Strict geometry→voxel evaluation result.
 * Distinguishes legal empty ({@link VoxelizationStatus#SUCCESS} + empty blocks)
 * from evaluation failure (any non-SUCCESS status).
 */
public record GeometryVoxelizationResult(
    VoxelizationStatus status,
    BlockPosList blocks,
    String error
) {
    public GeometryVoxelizationResult {
        if (status == null) {
            throw new NullPointerException("status");
        }
        if (blocks == null) {
            blocks = new BlockPosList();
        }
        if (error == null) {
            error = "";
        }
    }

    public boolean success() {
        return status == VoxelizationStatus.SUCCESS;
    }

    public static GeometryVoxelizationResult ok(BlockPosList blocks) {
        return new GeometryVoxelizationResult(
            VoxelizationStatus.SUCCESS,
            blocks == null ? new BlockPosList() : blocks,
            ""
        );
    }

    public static GeometryVoxelizationResult fail(VoxelizationStatus status, @Nullable String error) {
        if (status == null || status == VoxelizationStatus.SUCCESS) {
            throw new IllegalArgumentException("fail status must be a non-SUCCESS failure kind");
        }
        return new GeometryVoxelizationResult(status, new BlockPosList(), error == null ? "" : error);
    }
}

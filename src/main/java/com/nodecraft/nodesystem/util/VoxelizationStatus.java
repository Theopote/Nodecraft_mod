package com.nodecraft.nodesystem.util;

/**
 * Outcome status for strict geometry→voxel evaluation.
 * {@link #SUCCESS} may still carry an empty block set (legal empty solid / boolean).
 */
public enum VoxelizationStatus {
    SUCCESS,
    UNSUPPORTED,
    OVER_BUDGET,
    INVALID_BOUNDS,
    CHILD_FAILURE,
    /** Non-finite procedural evaluation (e.g. SDF sample returned NaN). */
    EVALUATION_FAILURE
}

package com.nodecraft.nodesystem.preview;

/**
 * Preview backends for geometry visualization.
 * <p>
 * <b>Default:</b> {@link #GHOST} — pure visual overlay via {@code PreviewRenderer}.
 * Permanent world edits belong in {@code BakePlacementService}, not here.
 * <p>
 * <b>Compat / special:</b> {@link #TRACKED_WORLD} — temporary world mutation with
 * restore. Kept for tooling gaps Ghost cannot cover yet; do not use for new
 * default preview paths. See {@code docs/architecture/preview-world-boundary.md}.
 */
public enum PreviewBackend {
    /** Render-only preview (default). */
    GHOST,
    /**
     * Compatibility / special preview: mutates the world then restores.
     * Prefer {@link #GHOST} unless a specific tooling case requires real blocks.
     */
    TRACKED_WORLD
}

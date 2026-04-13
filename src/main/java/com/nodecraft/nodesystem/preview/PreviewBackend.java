package com.nodecraft.nodesystem.preview;

/**
 * Preview backends for geometry visualization.
 */
public enum PreviewBackend {
    GHOST,
    /**
     * World-mutating preview backend.
     * Use only for controlled tooling where render-only previews are insufficient.
     */
    TRACKED_WORLD
}

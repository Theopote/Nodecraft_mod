package com.nodecraft.nodesystem.preview.protocol;

/**
 * Explicit preview payload kinds (v1). Renderers branch on {@link PreviewPayload#getKind()} and concrete type.
 */
public enum PreviewKind {
    BLOCKS,
    POINTS,
    VECTORS,
    CURVES,
    REGIONS,
    PLANE,
    FRAME,
    LABELS,
    /**
     * Reserved/legacy preview kind. No concrete payload or renderer path is implemented yet.
     * Use GEOMETRY preview instead until surface-strip protocol is fully implemented.
     */
    @Deprecated
    SURFACE_STRIP,
    GEOMETRY
}

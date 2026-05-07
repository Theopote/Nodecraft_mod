package com.nodecraft.nodesystem.preview.protocol;

import java.util.Objects;

/**
 * v1: still carries the existing geometry graph object until geometry is fully protocolized.
 */
public final class PreviewGeometryPayload implements PreviewPayload {
    private final Object geometry;

    public PreviewGeometryPayload(Object geometry) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
    }

    @Override
    public PreviewKind getKind() {
        return PreviewKind.GEOMETRY;
    }

    public Object getGeometry() {
        return geometry;
    }
}

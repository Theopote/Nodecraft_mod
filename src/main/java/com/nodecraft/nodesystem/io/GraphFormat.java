package com.nodecraft.nodesystem.io;

/**
 * Version constants for the {@link SavedGraph} on-disk format.
 */
public final class GraphFormat {

    /** Pre-versioning files, or JSON that omitted {@link SavedGraph#formatVersion}. */
    public static final int LEGACY_UNSPECIFIED = 0;

    /** Current format: explicit version field and migration registry support. */
    public static final int CURRENT = 1;

    private GraphFormat() {
    }
}

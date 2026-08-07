package com.nodecraft.nodesystem.io;

/**
 * Canonical on-disk / embedded {@link SavedGraph} format version constants and policy helpers.
 * <p>
 * See {@code docs/architecture/graph-format-version.md}.
 */
public final class GraphFormatVersion {

    /** Pre-versioning files, or JSON that omitted {@link SavedGraph#formatVersion}. */
    public static final int LEGACY_UNSPECIFIED = 0;

    /**
     * Format v1: explicit {@code formatVersion} field and {@code GraphMigrationRegistry} support.
     */
    public static final int V1 = 1;

    /** Version written by current builds. */
    public static final int CURRENT = V1;

    private GraphFormatVersion() {
    }

    /**
     * Clamps negative / missing versions up to {@link #LEGACY_UNSPECIFIED}.
     */
    public static int normalize(int formatVersion) {
        return Math.max(formatVersion, LEGACY_UNSPECIFIED);
    }

    public static boolean isLegacy(int formatVersion) {
        return normalize(formatVersion) == LEGACY_UNSPECIFIED;
    }

    public static boolean needsMigration(int formatVersion) {
        return normalize(formatVersion) < CURRENT;
    }

    public static boolean isNewerThanCurrent(int formatVersion) {
        return formatVersion > CURRENT;
    }

    public static boolean isCurrent(int formatVersion) {
        return formatVersion == CURRENT;
    }
}

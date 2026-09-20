package com.nodecraft.nodesystem.io;

/**
 * Canonical on-disk / embedded {@link SavedGraph} format version constants.
 */
public final class GraphFormatVersion {

    /** Pre-versioning payloads and explicit V0 graphs. */
    public static final int V0 = 0;
    /** @deprecated Use {@link #V0}. */
    @Deprecated
    public static final int LEGACY_UNSPECIFIED = V0;

    public static final int V1 = 1;

    /**
     * Batch A language remediation: Integer Slider {@code value} → {@code output_value},
     * plus related numeric/angle language fixes that do not require further port remaps.
     */
    public static final int V2 = 2;

    /** Version written by current builds. */
    public static final int CURRENT = V2;

    private GraphFormatVersion() {
    }

    public static int normalize(int formatVersion) {
        return formatVersion <= 0 ? V0 : formatVersion;
    }

    public static boolean needsMigration(int formatVersion) {
        return normalize(formatVersion) < CURRENT;
    }

    public static boolean isLegacy(int formatVersion) {
        return normalize(formatVersion) <= V0;
    }

    public static boolean isNewerThanCurrent(int formatVersion) {
        return formatVersion > CURRENT;
    }

    public static boolean isCurrent(int formatVersion) {
        return formatVersion == CURRENT;
    }
}

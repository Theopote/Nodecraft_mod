package com.nodecraft.nodesystem.io;

/**
 * Graph save format version policy (development stage).
 * <p>
 * Only {@link #CURRENT} is meaningful. Historical step migrations are not maintained;
 * older payloads are stamped to {@link #CURRENT} without port/type remaps.
 * See project acceptance policy: current-code-only correctness, no legacy graph compatibility.
 */
public final class GraphFormatVersion {

    /** Unspecified / pre-versioning payloads. */
    public static final int UNSPECIFIED = 0;

    /**
     * Version written by current builds.
     * Reset to {@code 1} when dropping the historical V0–V135 migration chain.
     */
    public static final int CURRENT = 1;

    private GraphFormatVersion() {
    }

    public static int normalize(int formatVersion) {
        return formatVersion <= 0 ? UNSPECIFIED : formatVersion;
    }

    /** True when the payload is older than {@link #CURRENT} (stamp-only, no remaps). */
    public static boolean needsMigration(int formatVersion) {
        return normalize(formatVersion) < CURRENT;
    }

    /** Older than current — same as {@link #needsMigration} under the stamp-only policy. */
    public static boolean isLegacy(int formatVersion) {
        return needsMigration(formatVersion);
    }

    public static boolean isNewerThanCurrent(int formatVersion) {
        return formatVersion > CURRENT;
    }

    public static boolean isCurrent(int formatVersion) {
        return formatVersion == CURRENT;
    }
}

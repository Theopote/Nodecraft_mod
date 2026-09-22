package com.nodecraft.nodesystem.io;

/**
 * Canonical on-disk / embedded {@link SavedGraph} format version constants.
 * <p>
 * NodeCraft is still pre-release: prefer clean format bumps for in-repo assets over
 * behavior-preserving migrations for abandoned on-disk graphs.
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

    /**
     * Batch B language remediation: Coordinate Input → Block Position Input
     * ({@code reference.points.point_from_coordinates} → {@code reference.points.block_position}).
     */
    public static final int V3 = 3;

    /**
     * Batch 3 curves language: Points To Path / Path To Points canonical ids, and legacy
     * path triple ports ({@code input_curve} / {@code input_polyline} / path {@code input_line})
     * → {@code input_path}.
     */
    public static final int V4 = 4;

    /**
     * Batch 4 solids language: Extrude canonical id, Surface Strip To Lattice rename,
     * and related port remaps for demoted Prism / lattice conversion nodes.
     */
    public static final int V5 = 5;

    /**
     * Batch 5 geometry ops: Combine Geometry canonical id
     * ({@code geometry.boolean.union} → {@code geometry.combine.geometry}).
     */
    public static final int V6 = 6;

    /**
     * Batch 6 transform language: Rotate Vector angle units freeze to degrees.
     * Legacy {@code input_angle_rad} connections are dropped (pre-release: no
     * radians→degrees value preservation).
     */
    public static final int V7 = 7;

    /**
     * Batch 9 output language: Bake Geometry To Blocks → Voxelize Geometry (PURE).
     * {@code output.execute.bake_geometry_to_blocks} → {@code geometry.voxel.voxelize_geometry}.
     */
    public static final int V8 = 8;

    /** Version written by current builds. */
    public static final int CURRENT = V8;

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

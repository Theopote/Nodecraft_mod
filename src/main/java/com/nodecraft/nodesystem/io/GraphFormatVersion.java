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

    /**
     * Batch 10 math language: graph-facing trigonometry freezes to degrees.
     * Legacy {@code input_angle_rad} / {@code output_angle_rad} connections are dropped
     * (pre-release: no radians→degrees value preservation).
     */
    public static final int V9 = 9;

    /**
     * Batch 13 architectural language: Railing / Staircase {@code input_line} → {@code input_path}.
     */
    public static final int V10 = 10;

    /**
     * Spatial language P1: Closest Point no longer emits BLOCK_POS;
     * Deconstruct Coordinate → Deconstruct Block Position; legacy closest ports remapped.
     */
    public static final int V11 = 11;

    /**
     * Numeric domain language: directed Domain Input, Remap Source/Target, Random Number split,
     * Path Frames sampling mode, Curve Frame Along Path merged into Path Frames.
     */
    public static final int V12 = 12;

    /**
     * Curve path language: Resample Path canonical id, Evaluate Path consolidation,
     * sampling mode only on Resample Path, Offset Path simplified, legacy nodes removed.
     */
    public static final int V13 = 13;

    /**
     * Curve path P2: Tween PATH_LIST output, Blend PATH output, Join/Reverse/Split/Trim
     * path ops, Closest Point On Path, Fillet PATH output.
     */
    public static final int V14 = 14;

    /**
     * Path Length canonical id: {@code geometry.curves.polyline_length} → {@code geometry.curves.path_length}.
     */
    public static final int V15 = 15;

    /**
     * Closest Point On Path canonical id replaces Project Point To Polyline;
     * Fillet Path Corners drops legacy polyline output port.
     */
    public static final int V16 = 16;

    /**
     * Path language v1 closure: Path Parameter At Point → Closest Point On Path.
     */
    public static final int V17 = 17;

    /**
     * Frame/plane language v1 closure: producer/deconstruct split, Transform Frame no scale,
     * Transform Points by Frames uses FRAME_LIST, new Deconstruct Plane / Frame From Plane.
     */
    public static final int V18 = 18;

    /**
     * Point/vector language v1 closure: no position-as-VECTOR outputs, degrees-only angles,
     * POINT_LIST tightening, DELETE Block To Vector / Closest Point To Object,
     * ADD Construct Point / Translate Point / Vector Between Points.
     */
    public static final int V19 = 19;

    /**
     * Box/face point language + world.selection Point↔BlockPos typing:
     * Get Box Corner / Deconstruct Face Edge positions as POINT;
     * Get Face Edge slimmed; Snap Point ports typed POINT / POINT_LIST.
     */
    public static final int V20 = 20;

    /**
     * Vector producer/deconstruct closure: Construct Vector / Vector Input emit VECTOR only
     * (no X/Y/Z echo); use Deconstruct Vector for components.
     */
    public static final int V21 = 21;

    /**
     * List/collection v1: DOUBLE_LIST / BOOLEAN_LIST; numeric list producers;
     * Create List / Repeat Item semantics; delete Chunk/Combine/Zip/Transpose;
     * Group List → DATA_TREE; Map List → Map Numbers.
     */
    public static final int V22 = 22;

    /**
     * List typed boundary: asymmetric LIST↔typed connectability, list type-variable
     * preservation, STRING_LIST, Filter/Dispatch BOOLEAN_LIST masks, retire generic Sort/Reduce.
     */
    public static final int V23 = 23;

    /**
     * Data Tree v1: DataTree&lt;T&gt;, TREE_PATH / TREE_PATH_LIST, unique-path invariant,
     * Merge vs Entwine separation, fail-closed path/index language.
     */
    public static final int V24 = 24;

    /**
     * Scalar Math v1 schema cleanup: drop deleted Fraction {@code output_floor} wires and
     * Graph Mapper curve-parameter input ports ({@code input_exponent} /
     * {@code input_gaussian_center} / {@code input_gaussian_width}) now owned by properties.
     */
    public static final int V25 = 25;

    /** Version written by current builds. */
    public static final int CURRENT = V25;

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

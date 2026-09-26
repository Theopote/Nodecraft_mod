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

    /**
     * Trigonometry v1: delete deg↔rad converters; move Pi/E to {@code input.numeric};
     * drop orphan wires to removed nodes.
     */
    public static final int V26 = 26;

    /**
     * Compare v1: delete composite {@code math.compare.compare}; drop orphan wires.
     */
    public static final int V27 = 27;

    /**
     * Sequence v1: drop Number Series {@code output_sum} wires (use Sum Numbers instead).
     */
    public static final int V28 = 28;

    /**
     * Random v1: Random Vector becomes single VECTOR (drop Count + old ANY output wires);
     * drop Random List Item / Random Numbers wires incompatible with LIST&lt;T&gt; / DOUBLE_LIST.
     */
    public static final int V29 = 29;

    /**
     * Field v1: Scalar Field Sample Points {@code output_values} LIST → DOUBLE_LIST;
     * drop incompatible downstream wires.
     */
    public static final int V30 = 30;

    /**
     * Input Numeric v1: XY Slider drops {@code output_vector}, {@code output_uv} → DOUBLE_LIST;
     * Pi/E {@code output_value}; finite-value semantics; drop incompatible wires.
     */
    public static final int V31 = 31;

    /**
     * Input Context v1: Player Raycast POINT/DOUBLE + {@code output_valid}; Current Time ticks DOUBLE;
     * fail-closed context semantics; {@code player_look_direction} → {@code player_raycast}.
     */
    public static final int V32 = 32;

    /**
     * Type Selectors v1: Block Type {@code BLOCK_TYPE}, {@code output_valid}, preserve unknown ids;
     * remove Block State Selector; Build Block State gains properties text.
     */
    public static final int V33 = 33;

    /**
     * Input Values v1: remap {@code input.basic.*} value sources to {@code input.values.*};
     * Color channels DOUBLE + ColorData; Value List STRING_LIST; Gradient finite Valid / drop ramp;
     * File Path {@code output_valid}.
     */
    public static final int V34 = 34;

    /**
     * Block State v1: four PURE state-only nodes; apply merge semantics; Build Valid fix;
     * slab autofill moved to directional_mapping; remove geometry-voxelize block_state nodes.
     */
    public static final int V35 = 35;

    /**
     * Directional Mapping v1: blockId-only remap; surface slope; slab/stair adapt;
     * drop deconstruct outputs; no hidden vanilla material fallbacks.
     */
    public static final int V36 = 36;

    /**
     * Gradient Mapping v1: five PURE scalar→palette→blockId nodes; RandomOps noise;
     * DOUBLE_LIST diagnostics; Distance POINT reference; drop deconstruct outputs;
     * no hidden stone/origin/epsilon domain repair.
     */
    public static final int V37 = 37;

    /**
     * Pattern Mapping v1: four PURE role-based pattern nodes; Pattern Origin;
     * no hidden vanilla defaults; fail-closed integer params; drop deconstruct outputs.
     */
    public static final int V38 = 38;

    /**
     * Surface Aging v1: three PURE topology aging nodes; RandomOps masks;
     * placements-in; drop deconstruct outputs; no hidden vanilla defaults.
     */
    public static final int V39 = 39;

    /**
     * Basic Assignment v1: four PURE material entry nodes; BLOCK_PALETTE contract;
     * RandomOps weighted pick; drop deconstruct outputs; no hidden stone defaults.
     */
    public static final int V40 = 40;

    /**
     * Pattern Linear v1: four canonical linear pattern nodes with clean type ids;
     * POINT_LIST instancing; closed-path seam rules; no legacy block-array nodes.
     */
    public static final int V41 = 41;

    /**
     * Pattern Grid v1: five canonical grid nodes; geometry-first Grid Array;
     * layout producers emit POINT_LIST; typed spatial ports; Count semantics unified.
     */
    public static final int V42 = 42;

    /**
     * Pattern Radial v1: three canonical radial nodes; geometry-first Polar Array;
     * Spiral/Phyllotaxis layout producers emit POINT_LIST + VECTOR_LIST + FRAME_LIST.
     */
    public static final int V43 = 43;

    /**
     * Surface / Volume Distribution v1: six canonical scatter/sampling nodes;
     * continuous POINT_LIST outputs; deterministic seeds; no BLOCK_LIST mirrors.
     */
    public static final int V44 = 44;

    /**
     * Pattern Voronoi 3D v1: Lloyd Relax 3D with Corner A/B POINT ports,
     * strict validation, Iterations=0 passthrough, and fail-closed work budget.
     */
    public static final int V45 = 45;

    /**
     * Pattern L-System v1: Rule / Expand / Turtle 3D with typed LSYSTEM_RULE_LIST,
     * strict validation, PATH_LIST turtle segments, and fail-closed bracket limits.
     */
    public static final int V46 = 46;

    /**
     * Reference Frames v1: sphere_surface_frame rename, unified FrameUtils,
     * Construct strict parallel axes, Sphere X Hint, deconstruct orthonormal boundary.
     */
    public static final int V47 = 47;

    /**
     * Reference Planes v1: PLANE invariant, box_face_plane rename, World Plane Valid,
     * canonical validation at deconstruct/distance/offset boundaries, unified PlaneUtils.
     */
    public static final int V48 = 48;

    /**
     * Reference Points v1: strict INTEGER, strict POINT_LIST fail-closed, unified SpatialValueResolver,
     * typed topology (LINE_LIST/INTEGER_LIST), Get Box Face precedence, unique order 0-18.
     */
    public static final int V49 = 49;

    /**
     * Reference Vectors v1: Slerp geodesic fix, zero-vector validity, connected-vs-unconnected inputs,
     * null invalid VECTOR outputs, VectorUtils util, unique order 0-17.
     */
    public static final int V50 = 50;

    /**
     * Reference Vectors P1: VectorData layer, SpatialTolerance EPS/EPS_SQ, strict INTEGER_LIST,
     * Component Min/Max moved to math.vector.
     */
    public static final int V51 = 51;

    /**
     * Basic Transforms v1: continuous Geometry/Point/BoxFace transforms only; block-grid moved to
     * placement; Shear to deformations; transactional Composite transform/mirror; optional-drive;
     * strict POINT_LIST; unique order 0-8.
     */
    public static final int V52 = 52;

    /**
     * Deformations v1: strict POINT_LIST/VECTOR_LIST, OptionalPortDrive, exact INTEGER,
     * Length/Radius fail-closed, unique order 0-10, GenerationLimits budgets.
     */
    public static final int V53 = 53;

    /**
     * Placement v1: BlockPos cell-center→floor snap pipeline, Coordinate→Block Position rename,
     * strict FRAME_LIST, Frame XOR Frames, place-on-frames transactional + instance cap.
     */
    public static final int V54 = 54;

    /**
     * Assist Utilities v1: scalar passthrough T, ban unbound ANY→typed washout,
     * Reroute+Tag→Relay, Assert→Validate, Signal Merge→Coalesce.
     */
    public static final int V55 = 55;

    /**
     * FileIO v1: IMAGE/ImageData protocol, ImportAccessPolicy (no graph self-grant),
     * COLOR_LIST, GenerationLimits image/vox caps, VOX structure-only (no stone/placements).
     */
    public static final int V56 = 56;

    /**
     * Block Morphology v1: BLOCK_LIST strict payload, GenerationLimits workload cap,
     * exact INTEGER iterations, fail-closed morphology (no partial output).
     */
    public static final int V57 = 57;

    /**
     * Organization & Subgraph v1: three runtime nodes, SubgraphCallFrame IO,
     * typed subgraph interfaces, SavedGraph.subgraphDefinitions, Comment/Group metadata.
     */
    public static final int V58 = 58;

    /**
     * Variable Scope v1: CONTEXT_READ/WRITE effects, passthrough T on value ports,
     * connection-aware optional drives, Clear Variables Valid/Error, STRING_LIST names.
     */
    public static final int V59 = 59;

    /**
     * World Query v1: cell-center grid semantics, strict typed spatial ports,
     * PURE predicates, bounded world access, Valid/Error on query nodes.
     */
    public static final int V60 = 60;

    /**
     * World Read v1: strict BLOCK_POS, hard read caps, typed collections,
     * Valid/Complete/Error, entity-NBT entity-only, Get Block Positions PURE.
     */
    public static final int V61 = 61;

    /** Version written by current builds. */
    public static final int CURRENT = V61;

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

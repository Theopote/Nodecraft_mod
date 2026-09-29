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
     * Pattern Grid v1 (historical milestone): five canonical grid nodes; geometry-first Grid Array;
     * layout producers emit POINT_LIST; typed spatial ports; Count semantics unified.
     * Superseded by {@link #V80}.
     */
    public static final int V42 = 42;

    /**
     * Pattern Radial v1 (historical milestone): three canonical radial nodes; geometry-first Polar Array;
     * Spiral/Phyllotaxis layout producers emit POINT_LIST + VECTOR_LIST + FRAME_LIST.
     * Superseded by {@link #V81}.
     */
    public static final int V43 = 43;

    /**
     * Surface / Volume Distribution v1 (historical milestone): six canonical scatter/sampling nodes;
     * continuous POINT_LIST outputs; deterministic seeds; no BLOCK_LIST mirrors.
     * Superseded by {@link #V82}.
     */
    public static final int V44 = 44;

    /**
     * Pattern Voronoi 3D v1 (historical milestone): Lloyd Relax 3D with Corner A/B POINT ports,
     * strict validation, Iterations=0 passthrough, and fail-closed work budget.
     * Superseded by {@link #V83}.
     */
    public static final int V45 = 45;

    /**
     * Pattern L-System v1 (historical milestone): Rule / Expand / Turtle 3D with typed
     * LSYSTEM_RULE_LIST, strict validation, PATH_LIST turtle segments, and fail-closed bracket limits.
     * Superseded by {@link #V84}.
     */
    public static final int V46 = 46;

    /**
     * Reference Frames v1 (historical milestone): sphere_surface_frame rename, unified FrameUtils,
     * Construct strict parallel axes, Sphere X Hint, deconstruct orthonormal boundary.
     */
    public static final int V47 = 47;

    /**
     * Reference Planes v1 (historical milestone): PLANE invariant, box_face_plane rename, World Plane Valid,
     * canonical validation at deconstruct/distance/offset boundaries, unified PlaneUtils.
     */
    public static final int V48 = 48;

    /**
     * Reference Points v1 (historical milestone): strict INTEGER, strict POINT_LIST fail-closed,
     * unified SpatialValueResolver, typed topology (LINE_LIST/INTEGER_LIST), Get Box Face precedence,
     * unique order 0-18. Superseded by {@link #V87}.
     */
    public static final int V49 = 49;

    /**
     * Reference Vectors v1 (historical milestone): Slerp geodesic fix, zero-vector validity,
     * connected-vs-unconnected inputs, null invalid VECTOR outputs, VectorUtils util,
     * unique order 0-17. Superseded by {@link #V88}.
     */
    public static final int V50 = 50;

    /**
     * Reference Vectors P1 (historical milestone): VectorData layer, SpatialTolerance EPS/EPS_SQ,
     * strict INTEGER_LIST, Component Min/Max moved to math.vector.
     * Superseded by {@link #V88}.
     */
    public static final int V51 = 51;

    /**
     * Basic Transforms v1: continuous Geometry/Point/BoxFace transforms only; block-grid moved to
     * placement; Shear to deformations; transactional Composite transform/mirror; optional-drive;
     * strict POINT_LIST; unique order 0-8.
     */
    public static final int V52 = 52;

    /**
     * Deformations v1 (historical milestone): strict POINT_LIST/VECTOR_LIST, OptionalPortDrive,
     * exact INTEGER, Length/Radius fail-closed, unique order 0-10. Superseded by V78.
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
     * Superseded by {@link #V89}.
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

    /**
     * World Selection v1: cell-center snaps, PURE spatial nodes, CONTEXT_READ
     * selection sources, strict lists, delete Snap Vector To Block.
     */
    public static final int V62 = 62;

    /**
     * Terrain Field v1: cell-center raster, GenerationLimits terrain caps,
     * finite Field contract, explicit materializers, Valid/Error on all terrain nodes.
     */
    public static final int V63 = 63;

    /**
     * World Write v1: strict typed inputs, hard write caps, full BE-NBT transactions,
     * Trigger fail-closed, real entity teleport/remove, Valid/Error on all write nodes.
     */
    public static final int V64 = 64;

    /**
     * Flow Control v1: exec-first Branch/Do Once, typed passthrough T, strict BOOLEAN/INTEGER,
     * Do Once CONTEXT_WRITE with run-local gate (never SavedGraph).
     */
    public static final int V65 = 65;

    /**
     * Flow Loop v1: slim For Each + pure exec While, delete Accumulator,
     * ExecLoopNode completion policy, run-local While iteration counter.
     */
    public static final int V66 = 66;

    /**
     * Geometry Analysis v1: continuous BOUNDING_BOX vs discrete REGION/BLOCK_POS,
     * rename analysis nodes, GeometryBoundsResolver (no voxelizer).
     */
    public static final int V67 = 67;

    /**
     * Architectural Primitives v1 (PASSED / FROZEN): 18 canonical nodes (order 0–17),
     * strict inputs / enums / Column XOR, Valid+Error, architecture hard caps,
     * Roof Base vs Generator split, 0/1/N packGeometry (null members fail closed),
     * transactional face-array generation (no partial output).
     */
    public static final int V68 = 68;

    /**
     * Geometry Boolean v1 (PASSED / FROZEN): Difference/Intersection Valid+Error + orders 0–1,
     * strict GeometryVoxelizationResult (empty SUCCESS ≠ FAILURE), transactional
     * Composite/Diff/Inter voxelization, GenerationLimits.MAX_GEOMETRY_VOXELS,
     * Boolean Preview via voxelizeStrict.
     */
    public static final int V69 = 69;

    /**
     * Geometry Combine v1 (PASSED / FROZEN): Combine Geometry order 0, Valid+Error,
     * connection-aware transactional combine, canonical 0/1/N packGeometry, leaf Count,
     * CompositeGeometryData null rejection + nested flatten.
     */
    public static final int V70 = 70;

    /**
     * Geometry Curves / PATH Language v2 (PASSED / FROZEN): Valid+Error on all 28 nodes,
     * PATH canonical public language, strict INTEGER/finite DOUBLE, connection-aware drives,
     * normalized arc-length t, curve sampling budgets fail closed, orders 0–27.
     */
    public static final int V71 = 71;

    /**
     * Geometry Solids / Surface Modeling Language v1 (PASSED / FROZEN): Valid+Error on 22 nodes,
     * SURFACE_STRIP canonical surface type, typed list ports, PATH not POLYLINE, strict inputs/workloads,
     * delete extrude_profile + shell duplicates, orders 0–21.
     */
    public static final int V72 = 72;

    /**
     * Geometry Profiles / Polygon Profile Language v1 (PASSED / FROZEN): Valid+Error on 23 nodes,
     * POLYGON_PROFILE canonical invariant, PATH not POLYLINE, typed POLYGON_PROFILE_LIST ports,
     * strict inputs/workloads, Convex Hull 3D moved to geometry.analysis, orders 0–22.
     */
    public static final int V73 = 73;

    /**
     * Primitive Geometry Language v1 (PASSED / FROZEN): Valid+Error on 29 nodes, BOX_FACE_LIST,
     * PATH not LINE, Box continuous-only (no Blocks/Region), Capsule/Torus fail-closed, orders 0–28.
     */
    public static final int V74 = 74;

    /**
     * Basic Transforms Language v2 (PASSED / FROZEN): Valid+Error on 9 nodes,
     * Frames×Points / POINT_LIST budgets, BoxFaceValidator (exactly 4 corners),
     * Offset/Inset fail-closed, TRS Scale→RotateXYZ→Translate, orders 0–8.
     * V52 remains the historical Basic Transforms v1 milestone.
     */
    public static final int V75 = 75;

    /**
     * Placement Language v2 (PASSED / FROZEN): Valid+Error on 8 nodes, block port canonicalize,
     * sourceLeaves×frameCount budget, BLOCK_LIST cap, strict XOR inputs,
     * PlacementBlockUtils safe arithmetic/snap, Scale connection XOR (no useUniformScaling).
     */
    public static final int V76 = 76;

    /**
     * Orientation Language v1 (PASSED / FROZEN): Valid+Error on 6 nodes, PATH canonical projection,
     * strict POINT/VECTOR lists, DOUBLE_LIST distances, OptionalPortDrive on Rotate/Forward Hint,
     * Align equal cardinality, project_path_to_plane, orders 0–5.
     */
    public static final int V77 = 77;

    /**
     * Deformations Language v2 (PASSED / FROZEN): SDF-only Twist/Bend, Valid+Error on all 11 nodes,
     * bounded POINT_LIST, finite output validation, Path Attract workload cap, orders 0–10.
     */
    public static final int V78 = 78;

    /**
     * Pattern Linear Language v2 (PASSED / FROZEN): Valid+Error on 4 nodes, remove raw LIST geometry outputs,
     * OptionalPortDrive / fail-closed Count budgets, Instance Block Placements (BLOCK_LIST anchors),
     * transactional array copies, sourceLeaves×instances preflight, Path Frames degenerate tangent
     * fail-closed. V41 remains the historical v1 fence.
     */
    public static final int V79 = 79;

    /**
     * Pattern Grid Language v2 (PASSED / FROZEN): Valid+Error on 5 nodes, no raw LIST geometry outputs,
     * OptionalPortDrive / fail-closed grid products (no silent clamp), BoxFaceValidator on Facade,
     * sourceLeaves×gridCount budget, transactional copies, finite layout outputs.
     * V42 remains the historical Pattern Grid v1 fence.
     */
    public static final int V80 = 80;

    /**
     * Pattern Radial Language v2 (PASSED / FROZEN): Valid+Error on 3 nodes, drop Polar Array raw LIST
     * geometry output, OptionalPortDrive / fail-closed Count budgets (no silent clamp),
     * sourceLeaves×Count preflight, transactional Polar copies, aligned layout commit for
     * Spiral/Phyllotaxis, degenerate tangent fail-closed.
     * V43 remains the historical Pattern Radial v1 fence.
     */
    public static final int V81 = 81;

    /**
     * Surface / Volume Distribution Language v2 (PASSED / FROZEN): Valid+Error+Complete on 6 nodes,
     * OptionalPortDrive / fail-closed Count budgets (no silent clamp), contiguous orders 0–5,
     * continuous volume AABB (no GeometryVoxelizer/BlockPos), strict Image density protocol
     * (exact Width×Height, no FILE_PATH), under-target scatter Valid=true Complete=false,
     * min-distance selection workload preflight.
     * V44 remains the historical Surface / Volume Distribution v1 fence.
     */
    public static final int V82 = 82;

    /**
     * Pattern Voronoi 3D Language v2 (PASSED / FROZEN): Valid+Error on Lloyd Relax 3D, order 0,
     * OptionalPortDrive for Cells/Iterations (connected non-exact Integer fails),
     * transactional publish fence (cardinality / finite / in-bounds).
     * V45 remains the historical Pattern Voronoi 3D v1 fence.
     */
    public static final int V83 = 83;

    /**
     * Pattern L-System Language v2 (PASSED / FROZEN): Valid+Error on Rule/Expand/Turtle, orders 0–2,
     * OptionalPortDrive / connection-aware rules, axiom and rewrite workloads,
     * weighted-sum finite fence, Turtle hard-fail transactional geometry.
     * V46 remains the historical Pattern L-System v1 fence.
     */
    public static final int V84 = 84;

    /**
     * Reference Frames Language v2 (PASSED / FROZEN): Valid+Error on seven fallible nodes,
     * OptionalPortDrive on Construct/Transform optional ports, connected X Hint uses RequireHint
     * helpers (no fallback when connected), BoxFaceValidator on Face Center Frame,
     * Transform input canonicalization, Deconstruct Frames MAX_LIST_ELEMENTS cap.
     * V47 remains the historical Reference Frames v1 fence.
     */
    public static final int V85 = 85;

    /**
     * Reference Planes Language v2 (PASSED / FROZEN): Valid+Error on all seven nodes,
     * OptionalPortDrive on World Plane Origin and Offset Distance, BoxFaceValidator on Box Face To Plane,
     * strict DOUBLE distance semantics, canonical producer/consumer boundaries.
     * V48 remains the historical Reference Planes v1 fence.
     */
    public static final int V86 = 86;

    /**
     * Reference Points Language v2 (PASSED / FROZEN): Valid+Error on all 19 nodes, finite-result fences
     * on point arithmetic, bounded POINT_LIST, overflow-safe safeListCenter, BoxFaceValidator on topology
     * boundaries, query Valid+Found+Error semantics, StrictDoubleUtils for required DOUBLE inputs,
     * OptionalPortDrive on Block Position Input. V49 remains the historical Reference Points v1 fence.
     */
    public static final int V87 = 87;

    /**
     * Reference Vectors Language v2 (PASSED / FROZEN): Valid+Error on all 17 nodes, finite-result fences
     * on vector arithmetic, safeLength/safeNormalize/safeLerp, strict exact-Double inputs, unit-axis Project,
     * zero VECTOR valid. V50/V51 remain the historical Reference Vectors v1 fences.
     */
    public static final int V88 = 88;

    /**
     * Assist Utilities Language v2 (PASSED / FROZEN): String Format streaming workload caps,
     * incremental dynamic ports, Validate/Coalesce/String Format Error ports,
     * connection-aware optional inputs. V55 remains the historical Assist Utilities v1 fence.
     */
    public static final int V89 = 89;

    /**
     * Primitive Geometry Language v2 (PASSED / FROZEN): continuous deconstruct Bounding Box via
     * GeometryBoundsResolver, Region from block-space quantization, Deconstruct Torus + Capsule,
     * orders 0–30. V74 remains the historical Primitive Geometry v1 fence.
     */
    public static final int V90 = 90;

    /**
     * Planar Region Language v2 output layer (historical): PLANAR_REGION (outer + holes),
     * Profile Boolean/Offset emit regions, Annulus Region output, Sector sweep &lt; 360°,
     * Extrude Region, ProfileConstructionUtils fail-closed. V73 remains historical Profile v1.
     */
    public static final int V91 = 91;

    /**
     * Planar Region Modeling Language (PASSED / FROZEN): Profile To Region,
     * Region Boolean 2D / Region Offset (PLANAR_REGION in/out), Profile Boolean/Offset
     * as convenience wrappers (Profile → Region → Region op). Closes Region→Boolean/Offset
     * composability. V91 remains the historical region-output fence.
     */
    public static final int V92 = 92;

    /**
     * SDF Language v1 (PASSED / FROZEN): Valid+Error on all geometry.sdf nodes,
     * SdfInputUtils connection-aware resolvers, primitive contracts aligned with
     * geometry.primitives (ring torus, capsule axis, positive box extents),
     * typed Sample Points / Blend Mask lists, minuend-conservative Difference bounds.
     * Type IDs remain {@code geometry.boolean.sdf_*}.
     */
    public static final int V93 = 93;

    /**
     * Geometry Solids / Section Topology v2 (PASSED / FROZEN): Voxel Section/Contour
     * emit PLANAR_REGION with hole topology via containment hierarchy; connection-aware
     * Plane resolution (no XY washout); strict POINT_LIST on solids consumers (no silent drop).
     * Inventory remains 23 solids nodes. V72 remains the historical Solids v1 fence.
     */
    public static final int V94 = 94;

    /**
     * Geometry Voxel Language v1 (PASSED / FROZEN): Voxelize Geometry uses voxelizeStrict
     * with Valid+Error+Status; connection-aware Geometry Tree (transactional, no silent drop);
     * Boolean shell = solid CSG then extractShell; aggregate output budget on Composite/Tree;
     * SDF non-finite sample → EVALUATION_FAILURE. V69 remains historical strict voxelizer fence.
     */
    public static final int V95 = 95;

    /**
     * Geometry Analysis / Convex Hull 3D v2: TRIANGLE_MESH output (replaces raw LIST faces);
     * strict POINT_LIST input; dedupe-before-budget; MAX_CONVEX_HULL_3D_POINTS hard cap;
     * coplanar facet grouping with non-overlapping triangulation. Bounds nodes remain V67 frozen.
     */
    public static final int V96 = 96;

    /**
     * Architectural Topology & Path Join v2: roof PATH_LIST topology (Eaves/Ridges/Valleys);
     * type-correct primary ridge/eave paths; wall footprint join extrusion; railing joined offset path;
     * signed Offset on Wall/Beam/Railing; Join miter/bevel/butt. V68 input/budget foundations frozen.
     */
    public static final int V97 = 97;

    /**
     * Basic Transform / Oriented Box Consistency v2: GeometryTransform and GeometryMirror mark
     * BoxGeometryData as oriented when orientation is non-identity (or always after mirror),
     * so Bounds / Voxelize / Corners stay consistent. Additive runtime fix; no wire changes.
     */
    public static final int V98 = 98;

    /**
     * Orientation / Frame Handedness & Projected Path Validity v2: Align Y-Up frames keep
     * Local Y along +Normal (right-handed Z = tangent × up); Project Path fails closed on
     * degenerate projected segments and emits LINE for two-point paths; Rotate Vector emits
     * VectorData; Project Profile enforces MAX_LIST_ELEMENTS. Additive runtime fix.
     */
    public static final int V99 = 99;

    /**
     * Placement Semantics & Budgeting v3: Orient Geometry To Frame product language clarified
     * as Apply Frame Orientation (relative frame rotation; algorithm unchanged); bounded
     * FRAME_LIST resolve and allocation-free leaf counting for Place On Frames. No wire changes.
     */
    public static final int V100 = 100;

    /**
     * Deformation Bend Contract & SDF Bounds v3: Bend SDF fails closed when bend normal is
     * parallel to axis; output bounds from SdfSource.min/max with boundsSamples; strict
     * BentSdfData / TwistedSdfData constructors (no silent axis/normal/length repair). No wire changes.
     */
    public static final int V101 = 101;

    /**
     * Pattern Linear Frame & Budget Contract v3: connected Up ∥ tangent fail-closed;
     * closed-path frame roll correction; Linear/Curve countLeavesBounded; Instance Block
     * product preflight before allocate. No wire changes.
     */
    public static final int V102 = 102;

    /**
     * Pattern Grid Face Invariant & Budget v3: BOX_FACE ordered rectangular invariant;
     * Grid Array countLeavesBounded. No wire changes.
     */
    public static final int V103 = 103;

    /**
     * Pattern Radial Frame Continuity & Budget v3: Spiral/Phyllotaxis parallel-transport
     * frames; Count=1 tangent contract; Polar Array countLeavesBounded. No wire changes.
     */
    public static final int V104 = 104;

    /**
     * Surface Distribution Uniformity & Strict Topology v3: Cone/Torus area-uniform sampling;
     * Surface Strip strict quad validation; Poisson Max Attempts fail-closed. No wire changes.
     */
    public static final int V105 = 105;

    /**
     * Pattern L-System Turtle Input & Pose Strictness v3: Turtle Commands required;
     * quaternion normalization after rotations. No wire changes.
     */
    public static final int V106 = 106;

    /**
     * Pattern Voronoi 3D Sampling Capacity & Site Invariants v3: grid capacity invariant;
     * output distinctness fence; bounded Sites preflight. No wire changes.
     */
    public static final int V107 = 107;

    /**
     * Reference Frame Transform Semantics v3: world-axis Euler XYZ rotation contract;
     * Transform Frame saved-state non-finite fail-closed. No wire changes.
     */
    public static final int V108 = 108;

    /**
     * Reference Vector Signed-Angle Semantics v3 placeholder: Graph step reserved for
     * Angle Between Vectors projection-around-Reference remediation. No wire changes.
     */
    public static final int V109 = 109;

    /**
     * Input Values Optional Drive Strictness v2: Value List Index/Options and Gradient
     * T/X/Y use OptionalPortDrive fail-closed; Gradient exact finite Double. No wire changes.
     */
    public static final int V110 = 110;

    /**
     * Input Numeric Domain Finite Contract v2: Domain Input Valid/Error; finite directed
     * span; NumericRangeData.canonical. No wire changes.
     */
    public static final int V111 = 111;

    /**
     * Input Context Snapshot Data Contract v2: Player Raycast BLOCK_INFO/ENTITY_INFO
     * emit BlockInfoData/EntityInfoData; Position runtime capture is ExecutionContext-only.
     * No wire changes.
     */
    public static final int V112 = 112;

    /**
     * Type Selector Authoritative Registry Contract v2: biome UI fallback catalogs are
     * non-authoritative; Graph Valid requires live/static registry membership.
     * No wire changes.
     */
    public static final int V113 = 113;
    
    /** Version written by current builds. */
    public static final int CURRENT = V113;

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

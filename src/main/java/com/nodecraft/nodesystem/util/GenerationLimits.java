package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

/**
 * Shared hard limits for nodes that materialize collections from user-supplied counts.
 */
public final class GenerationLimits {

    /**
     * Maximum number of elements a single node may allocate into one output list.
     */
    public static final int MAX_LIST_ELEMENTS = 1_048_576;

    /**
     * Hard cap for GeometryData / CompositeGeometry instance arrays.
     * Much lower than {@link #MAX_LIST_ELEMENTS} because each instance is far heavier than a point or scalar.
     */
    public static final int MAX_GEOMETRY_INSTANCES = 16_384;

    /**
     * Hard cap for layout producers that emit POINT_LIST / VECTOR_LIST / FRAME_LIST together
     * (Spiral, Phyllotaxis, path-frame producers).
     */
    public static final int MAX_LAYOUT_INSTANCES = 16_384;

    /**
     * Candidate oversampling factor per target for surface/volume scatter preflight.
     */
    public static final int SCATTER_CANDIDATES_PER_TARGET = 32;

    /**
     * Hard cap on candidate-pool size for surface/volume scatter preflight
     * ({@code target × SCATTER_CANDIDATES_PER_TARGET}). This bounds the accepted
     * candidate list size, not every internal rejection attempt inside a single
     * sample (e.g. Torus/Hemisphere volume probes).
     */
    public static final long MAX_SCATTER_CANDIDATES = (long) MAX_LAYOUT_INSTANCES * SCATTER_CANDIDATES_PER_TARGET;

    /**
     * Hard cap on Poisson / min-distance pairwise distance tests
     * (approx {@code attempts × accepted} or selector selection work).
     */
    public static final long MAX_SCATTER_DISTANCE_TESTS = MAX_LIST_ELEMENTS;

    /**
     * Probe attempts per blue-noise selection step in {@link MinDistanceScatterSelector}.
     */
    public static final int SCATTER_BLUE_NOISE_PROBES = MinDistanceScatterSelector.BLUE_NOISE_PROBE_ATTEMPTS;

    /**
     * Fail-closed candidate budget for scatter oversampling.
     *
     * @return null when valid; otherwise an error message
     */
    public static @Nullable String validateScatterCandidateBudget(int targetCount, int candidatesPerTarget) {
        if (targetCount <= 0) {
            return "Target Count must be >= 1";
        }
        if (candidatesPerTarget <= 0) {
            return "Candidate factor must be >= 1";
        }
        long product;
        try {
            product = Math.multiplyExact((long) targetCount, (long) candidatesPerTarget);
        } catch (ArithmeticException overflow) {
            return "Scatter candidate budget overflows";
        }
        if (product > MAX_SCATTER_CANDIDATES) {
            return "Scatter candidate budget exceeds MAX_SCATTER_CANDIDATES";
        }
        return null;
    }

    /**
     * Fail-closed distance-test workload preflight for Poisson-style rejection.
     *
     * @return null when valid; otherwise an error message
     */
    public static @Nullable String validateScatterDistanceTests(long attempts, int targetCount) {
        if (attempts < 0L || targetCount < 0) {
            return "Scatter distance-test budget invalid";
        }
        long product;
        try {
            product = Math.multiplyExact(attempts, (long) Math.max(1, targetCount));
        } catch (ArithmeticException overflow) {
            return "Scatter distance-test budget overflows";
        }
        if (product > MAX_SCATTER_DISTANCE_TESTS) {
            return "Scatter distance-test budget exceeds MAX_SCATTER_DISTANCE_TESTS";
        }
        return null;
    }

    /**
     * Fail-closed selection workload before {@link MinDistanceScatterSelector#select}.
     * When {@code minDistance <= 0}, spacing work is skipped (shuffle/subList path).
     * <ul>
     *   <li>RANDOM: {@code candidateCount × targetCount}</li>
     *   <li>BLUE_NOISE_APPROX: {@code SCATTER_BLUE_NOISE_PROBES × targetCount²}</li>
     * </ul>
     *
     * @return null when valid; otherwise an error message
     */
    public static @Nullable String validateScatterSelectionWork(
        long candidateCount,
        int targetCount,
        MinDistanceScatterSelector.DistributionMode mode,
        double minDistance
    ) {
        if (!Double.isFinite(minDistance) || minDistance <= 0.0d) {
            return null;
        }
        if (candidateCount < 0L || targetCount < 0) {
            return "Scatter selection workload invalid";
        }
        if (targetCount == 0) {
            return null;
        }
        long work;
        try {
            if (mode == MinDistanceScatterSelector.DistributionMode.BLUE_NOISE_APPROX) {
                work = Math.multiplyExact(
                    Math.multiplyExact((long) SCATTER_BLUE_NOISE_PROBES, (long) targetCount),
                    (long) targetCount
                );
            } else {
                work = Math.multiplyExact(candidateCount, (long) targetCount);
            }
        } catch (ArithmeticException overflow) {
            return "Scatter selection workload overflows";
        }
        if (work > MAX_SCATTER_DISTANCE_TESTS) {
            return "Scatter selection workload exceeds MAX_SCATTER_DISTANCE_TESTS";
        }
        return null;
    }

    /**
     * Hard cap for Relax Point List input size (implementation safety budget).
     */
    public static final int MAX_RELAX_POINTS = 8192;

    /**
     * Hard cap for Relax Point List iteration count.
     */
    public static final int MAX_RELAX_ITERATIONS = 64;

    /**
     * Hard cap for Path Attract closest-segment work ({@code points × (pathVertices - 1)}).
     */
    public static final long MAX_DEFORMATION_PATH_WORK = MAX_LIST_ELEMENTS;

    /**
     * Hard cap for Twist/Bend Geometry voxelization of non-SDF geometry sources.
     * @deprecated V78 removes implicit geometry voxelization from deformation nodes; retained for historical migrations.
     */
    @Deprecated
    public static final int MAX_DEFORM_SOURCE_VOXELS = 32768;

    /**
     * Maximum repetitions per axis for 2D grid/array nodes before multiplying by source size.
     */
    public static final int MAX_GRID_AXIS = 1024;

    /**
     * Maximum iterations for flow-control loop nodes.
     */
    public static final int MAX_LOOP_ITERATIONS = 100_000;

    /**
     * Maximum segment/resolution count for most geometry-generation nodes.
     */
    public static final int MAX_SEGMENTS = 131_072;

    /**
     * Hard cap for architectural instance emitters (face arrays, grids, railings, stairs).
     * Same scale as {@link #MAX_GEOMETRY_INSTANCES}.
     */
    public static final int MAX_ARCHITECTURAL_INSTANCES = MAX_GEOMETRY_INSTANCES;

    /**
     * Hard cap for path-following architectural segment expansion
     * (Wall Along Path / Beam Along Path / railing path sampling).
     */
    public static final int MAX_ARCHITECTURAL_PATH_SEGMENTS = 4096;

    /**
     * Hard cap for architectural profile / arch sampling segments.
     */
    public static final int MAX_ARCHITECTURAL_PROFILE_SEGMENTS = 2048;

    /**
     * Looser segment cap for shapes that need higher resolution to stay smooth.
     */
    public static final int MAX_HEART_SEGMENTS = 524_288;

    /**
     * Maximum per-axis samples for cubic bounds-estimation grids.
     */
    public static final int MAX_BOUNDS_SAMPLES = 128;

    /** Minimum grid resolution per axis for 3D Lloyd relaxation. */
    public static final int MIN_VORONOI_LLOYD_CELLS_PER_AXIS = 4;

    /** Maximum grid resolution per axis for 3D Lloyd relaxation. */
    public static final int MAX_VORONOI_LLOYD_CELLS_PER_AXIS = 96;

    /** Maximum Lloyd relaxation iterations. */
    public static final int MAX_VORONOI_LLOYD_ITERATIONS = 32;

    /** Maximum site count accepted by 3D Lloyd relaxation. */
    public static final int MAX_VORONOI_LLOYD_SITES = 4096;

    /**
     * Hard cap on estimated nearest-site distance tests:
     * cells^3 * siteCount * iterations.
     */
    public static final long MAX_LLOYD_DISTANCE_TESTS = 100_000_000L;

    /** Maximum L-system rewrite iterations. */
    public static final int MAX_LSYSTEM_ITERATIONS = 16;

    /** Maximum expanded L-system command string length. */
    public static final int MAX_LSYSTEM_EXPANDED_LENGTH = 1_000_000;

    /** Maximum turtle command string length. */
    public static final int MAX_LSYSTEM_COMMAND_LENGTH = 1_000_000;

    /** Maximum draw segments emitted by L-system turtle interpretation. */
    public static final int MAX_LSYSTEM_TURTLE_SEGMENTS = 1_000_000;

    /** Maximum turtle bracket stack depth. */
    public static final int MAX_LSYSTEM_TURTLE_STACK_DEPTH = 4096;

    /** Maximum merged production rules accepted by L-System Expand. */
    public static final int MAX_LSYSTEM_RULES = 4096;

    /**
     * Hard cap on estimated rewrite match work:
     * stringLength × ruleCount × iterations (and per-round stringLength × ruleCount).
     */
    public static final long MAX_LSYSTEM_REWRITE_MATCH_TESTS = 100_000_000L;

    /** Maximum materializable image sample count (width × height after downsample). */
    public static final int MAX_IMAGE_PIXELS = 1_048_576;

    /** Maximum accepted raster image file size on disk. */
    public static final long MAX_IMAGE_FILE_BYTES = 64L * 1024 * 1024;

    /** Maximum accepted MagicaVoxel .vox file size on disk. */
    public static final long MAX_VOX_FILE_BYTES = 64L * 1024 * 1024;

    /** Maximum solid voxels accepted from a single .vox import. */
    public static final int MAX_IMPORTED_VOXELS = 262_144;

    /**
     * Hard cap for geometry→voxel bounding volume (blocks) used by
     * {@link GeometryVoxelizer} / deferred Difference & Intersection evaluation.
     */
    public static final long MAX_GEOMETRY_VOXELS = 262_144L;

    /** Maximum unique blocks for morphology input, intermediate, and output sets. */
    public static final int MAX_MORPHOLOGY_BLOCKS = 262_144;

    /** Maximum dilate/erode iterations per morphology operation. */
    public static final int MAX_MORPHOLOGY_ITERATIONS = 64;

    /** Maximum nested subgraph call depth (hard budget; not user-tunable). */
    public static final int MAX_SUBGRAPH_CALL_DEPTH = 8;

    /** Hard cap on neighbor cube-volume queries: {@code (2r+1)^3 - 1}. */
    public static final int MAX_NEIGHBOR_QUERY_BLOCKS = 262_144;

    /** Hard safety ceiling for flood-fill block budgets (user Max Blocks must not exceed). */
    public static final int MAX_FLOOD_FILL_BLOCKS = 262_144;

    /**
     * Hard upper bound for continuous world-query distances (Raycast / Get Entity Max Distance).
     * Values above this fail closed — never clamped.
     */
    public static final double MAX_WORLD_QUERY_DISTANCE = 8192.0d;

    /**
     * Hard upper bound for Raycast entity-hitbox expansion radius.
     * Distinct from {@link #MAX_WORLD_QUERY_DISTANCE} (ray length); values above fail closed.
     */
    public static final double MAX_ENTITY_QUERY_RADIUS = 256.0d;

    /**
     * Hard per-axis span (inclusive block count) for Get Entities In Region.
     * Regions exceeding any axis fail closed before {@code getOtherEntities}.
     */
    public static final int MAX_ENTITY_QUERY_REGION_AXIS = 2048;

    /** Hard safety ceiling for world.read Max Blocks / Max Points budgets. */
    public static final int MAX_WORLD_READ_BLOCKS = 262_144;

    /** Hard safety ceiling for world.read Max Columns (heightmap / surface) budgets. */
    public static final int MAX_WORLD_READ_COLUMNS = 65_536;

    /** Hard safety ceiling for world.read Max Results (Find Blocks) budgets. */
    public static final int MAX_WORLD_READ_RESULTS = 262_144;

    /** Hard cap for materialized terrain X/Z lattice cells (512×512). */
    public static final int MAX_TERRAIN_GRID_CELLS = 262_144;

    /**
     * Hard cap for terrain simulation work: gridCells × iterations
     * (Flow Accumulation and similar).
     */
    public static final long MAX_TERRAIN_SIMULATION_WORK = 16_777_216L;

    /** Hard safety ceiling for terrain Max Placements budgets. */
    public static final int MAX_TERRAIN_PLACEMENTS = 262_144;

    /** Hard safety ceiling for terrain Max Samples / Max Columns budgets. */
    public static final int MAX_TERRAIN_SAMPLES = 65_536;

    /** Hard safety ceiling for Plate Partition plate count. */
    public static final int MAX_TERRAIN_PLATES = 1_024;

    /** Hard upper bound for Flow Accumulation iterations (independent of work product). */
    public static final int MAX_TERRAIN_FLOW_ITERATIONS = 4_096;

    /** Hard safety ceiling for world.write Max Blocks / region volume budgets. */
    public static final int MAX_WORLD_WRITE_BLOCKS = 262_144;

    /** Hard safety ceiling for world.write Max Count entity mutate budgets. */
    public static final int MAX_WORLD_WRITE_ENTITIES = 4_096;

    /** Maximum sample points along a single curve/path resample or producer output. */
    public static final int MAX_CURVE_SAMPLES = MAX_SEGMENTS;

    /** Maximum paths emitted by multi-path curve nodes (Tween, Rainbow, Explode). */
    public static final int MAX_CURVE_OUTPUT_PATHS = MAX_GEOMETRY_INSTANCES;

    /** Maximum total sample workload: output paths × samples per path. */
    public static final long MAX_CURVE_TOTAL_SAMPLES = MAX_LIST_ELEMENTS;

    /** Maximum section count (U direction) for surface strip topology. */
    public static final int MAX_SURFACE_SECTIONS = MAX_SEGMENTS;

    /** Maximum points per section (V direction) for surface strip topology. */
    public static final int MAX_SURFACE_POINTS_PER_SECTION = MAX_SEGMENTS;

    /** Maximum total surface points: sections × points per section. */
    public static final long MAX_SURFACE_TOTAL_POINTS = MAX_LIST_ELEMENTS;

    /** Maximum unique vertices per polygon profile loop. */
    public static final int MAX_PROFILE_VERTICES = MAX_SEGMENTS;

    /** Maximum polygon profiles emitted by a single node. */
    public static final int MAX_PROFILE_OUTPUT_PROFILES = MAX_GEOMETRY_INSTANCES;

    /** Maximum total unique profile vertices across all outputs of one node. */
    public static final long MAX_PROFILE_TOTAL_VERTICES = MAX_LIST_ELEMENTS;

    /** Maximum combined input vertex count for profile boolean operations. */
    public static final int MAX_PROFILE_BOOLEAN_VERTICES = MAX_PROFILE_VERTICES;

    /** Maximum O(n²) triangulation workload for profile nodes (vertices²). */
    public static final long MAX_PROFILE_TRIANGULATION_WORK = MAX_LIST_ELEMENTS;

    /** Maximum shrinkwrap query points. */
    public static final int MAX_SURFACE_PROJECTION_QUERIES = MAX_GEOMETRY_INSTANCES;

    /** Maximum shrinkwrap workload: queries × triangles. */
    public static final long MAX_SURFACE_PROJECTION_WORK = MAX_LIST_ELEMENTS;

    /** Hard safety ceiling for world.write SNBT / NBT String input length. */
    public static final int MAX_WORLD_WRITE_SNBT_CHARS = 65_536;

    /** Hard safety ceiling for Execute Command string length (without leading slash). */
    public static final int MAX_WORLD_WRITE_COMMAND_CHARS = 1_024;

    private GenerationLimits() {
    }

    /**
     * Estimates inclusive cube volume {@code (2 * radius + 1)^3 - 1} excluding center.
     * Returns {@code -1} when the estimate overflows {@code long}.
     */
    public static long estimateCubeVolume(int radius) {
        if (radius < 0) {
            return -1L;
        }
        long edge = 2L * radius + 1L;
        if (edge > Integer.MAX_VALUE) {
            return -1L;
        }
        long volume = edge * edge * edge;
        if (volume <= 0L || volume == Long.MAX_VALUE) {
            return -1L;
        }
        return volume - 1L;
    }

    public static boolean exceedsLloydWorkBudget(int cellsPerAxis, int siteCount, int iterations) {
        if (iterations <= 0) {
            return false;
        }
        long cellsCubed = (long) cellsPerAxis * cellsPerAxis * cellsPerAxis;
        return cellsCubed * (long) siteCount * (long) iterations > MAX_LLOYD_DISTANCE_TESTS;
    }

    /**
     * Estimated L-system rewrite match tests for a full expansion request.
     * {@code iterations <= 0} never exceeds (passthrough has no rewrite work).
     */
    public static boolean exceedsLSystemRewriteMatchBudget(int stringLength, int ruleCount, int iterations) {
        if (iterations <= 0 || stringLength <= 0 || ruleCount <= 0) {
            return false;
        }
        long perRound = (long) stringLength * (long) ruleCount;
        if (perRound > MAX_LSYSTEM_REWRITE_MATCH_TESTS) {
            return true;
        }
        return perRound * (long) iterations > MAX_LSYSTEM_REWRITE_MATCH_TESTS;
    }

    /**
     * Effective turtle segment cap so {@code points = 2 × segments} never exceeds {@link #MAX_LIST_ELEMENTS}.
     */
    public static int maxLSystemTurtleSegments() {
        return Math.min(MAX_LSYSTEM_TURTLE_SEGMENTS, MAX_LIST_ELEMENTS / 2);
    }

    public static int clampNonNegativeCount(int count) {
        if (count <= 0) {
            return 0;
        }
        return Math.min(count, MAX_LIST_ELEMENTS);
    }

    public static int clampPositiveCount(int count) {
        return Math.max(1, Math.min(MAX_LIST_ELEMENTS, count));
    }

    /** Graph-facing validation: sample count within curve budget (fail closed when false). */
    public static boolean isWithinCurveSamples(int count) {
        return count >= 2 && count <= MAX_CURVE_SAMPLES;
    }

    /** Graph-facing validation: multi-path output count within curve budget. */
    public static boolean isWithinCurveOutputPaths(int count) {
        return count >= 1 && count <= MAX_CURVE_OUTPUT_PATHS;
    }

    /** Graph-facing validation: section count within surface budget. */
    public static boolean isWithinSurfaceSections(int sectionCount) {
        return sectionCount >= 2 && sectionCount <= MAX_SURFACE_SECTIONS;
    }

    /** Graph-facing validation: points per section within surface budget. */
    public static boolean isWithinSurfacePointsPerSection(int pointsPerSection) {
        return pointsPerSection >= 2 && pointsPerSection <= MAX_SURFACE_POINTS_PER_SECTION;
    }

    /** Graph-facing validation: total surface point workload (sections × points). */
    public static boolean isWithinSurfaceWorkload(int sectionCount, int pointsPerSection) {
        if (sectionCount < 2 || pointsPerSection < 2) {
            return false;
        }
        return (long) sectionCount * pointsPerSection <= MAX_SURFACE_TOTAL_POINTS;
    }

    /** Graph-facing validation: unique vertices per profile within budget (fail closed when false). */
    public static boolean isWithinProfileVertices(int vertexCount) {
        return vertexCount >= 3 && vertexCount <= MAX_PROFILE_VERTICES;
    }

    /** Graph-facing validation: profile output count within budget. */
    public static boolean isWithinProfileOutputCount(int count) {
        return count >= 0 && count <= MAX_PROFILE_OUTPUT_PROFILES;
    }

    /** Graph-facing validation: total profile vertex workload across outputs. */
    public static boolean isWithinProfileTotalVertices(long totalVertices) {
        return totalVertices >= 0L && totalVertices <= MAX_PROFILE_TOTAL_VERTICES;
    }

    /** Graph-facing validation: combined boolean input vertex count. */
    public static boolean isWithinProfileBooleanVertices(int totalVertices) {
        return totalVertices >= 0 && totalVertices <= MAX_PROFILE_BOOLEAN_VERTICES;
    }

    /** Graph-facing validation: O(n²) profile triangulation workload. */
    public static boolean isWithinProfileTriangulationWork(int vertexCount) {
        if (vertexCount < 3) {
            return false;
        }
        return (long) vertexCount * vertexCount <= MAX_PROFILE_TRIANGULATION_WORK;
    }

    /** Graph-facing validation: total curve sample workload (paths × samples). */
    public static boolean isWithinCurveWorkload(long pathCount, long samplesPerPath) {
        if (pathCount <= 0L || samplesPerPath <= 0L) {
            return false;
        }
        if (pathCount > MAX_CURVE_OUTPUT_PATHS) {
            return false;
        }
        if (samplesPerPath > MAX_CURVE_SAMPLES) {
            return false;
        }
        return pathCount * samplesPerPath <= MAX_CURVE_TOTAL_SAMPLES;
    }

    /** Graph-facing validation: Path Attract closest-segment workload (points × segments). */
    public static boolean isDeformationPathWorkWithinBudget(int pointCount, int pathVertexCount) {
        if (pointCount < 1 || pathVertexCount < 2) {
            return false;
        }
        return (long) pointCount * (pathVertexCount - 1L) <= MAX_DEFORMATION_PATH_WORK;
    }

    /**
     * Caps geometry array / placement instance counts to {@link #MAX_GEOMETRY_INSTANCES}.
     * Returns 0 when {@code count <= 0}.
     */
    public static int clampGeometryInstanceCount(int count) {
        if (count <= 0) {
            return 0;
        }
        return Math.min(count, MAX_GEOMETRY_INSTANCES);
    }

    /**
     * Caps layout producer instance counts to {@link #MAX_LAYOUT_INSTANCES}.
     * Returns 0 when {@code count <= 0}.
     */
    public static int clampLayoutInstanceCount(int count) {
        if (count <= 0) {
            return 0;
        }
        return Math.min(count, MAX_LAYOUT_INSTANCES);
    }

    /**
     * Caps positive geometry instance counts to at least one and at most {@link #MAX_GEOMETRY_INSTANCES}.
     */
    public static int clampPositiveGeometryInstanceCount(int count) {
        return Math.max(1, Math.min(MAX_GEOMETRY_INSTANCES, count));
    }

    public static int clampGridAxis(int count) {
        return clampNonNegativeCount(Math.min(count, MAX_GRID_AXIS));
    }

    /**
     * Caps grid repetition counts for nodes that iterate {@code 0..count} along each axis.
     * Scales axes down when {@code (x+1)(y+1)(z+1) * itemsPerCell} would exceed {@link #MAX_LIST_ELEMENTS}.
     */
    public static GridAxisCounts clampGridCounts(int xCount, int yCount, int zCount, int itemsPerCell) {
        int x = clampGridAxis(xCount);
        int y = clampGridAxis(yCount);
        int z = clampGridAxis(zCount);
        int perCell = Math.max(1, itemsPerCell);

        while (estimatedInclusiveGridOutputSize(x, y, z, perCell) > MAX_LIST_ELEMENTS) {
            if (z > 0 && z >= x && z >= y) {
                z--;
            } else if (y > 0 && y >= x) {
                y--;
            } else if (x > 0) {
                x--;
            } else {
                break;
            }
        }
        return new GridAxisCounts(x, y, z);
    }

    /**
     * Caps grid counts for nodes that iterate {@code 0..count-1} along each axis.
     */
    public static GridAxisCounts clampExclusiveGridCounts(int xCount, int yCount, int zCount, int itemsPerCell) {
        return clampExclusiveGridCounts(xCount, yCount, zCount, itemsPerCell, MAX_LIST_ELEMENTS);
    }

    /**
     * Caps exclusive grid counts so total cells stay within {@link #MAX_GEOMETRY_INSTANCES}.
     */
    public static GridAxisCounts clampExclusiveGeometryGridCounts(int xCount, int yCount, int zCount) {
        return clampExclusiveGridCounts(xCount, yCount, zCount, 1, MAX_GEOMETRY_INSTANCES);
    }

    /**
     * Fail-closed 2D grid product check (axes a×b).
     *
     * @return {@code null} when valid; otherwise an error message (never adjusts counts)
     */
    public static @Nullable String validateGridProduct(int a, int b, long limit) {
        return validateGridProduct(a, b, 1, limit);
    }

    /**
     * Fail-closed 3D grid product check (axes a×b×c).
     *
     * @return {@code null} when valid; otherwise an error message (never adjusts counts)
     */
    public static @Nullable String validateGridProduct(int a, int b, int c, long limit) {
        if (a <= 0 || b <= 0 || c <= 0) {
            return "Grid counts must be >= 1";
        }
        if (limit <= 0L) {
            return "Grid product exceeds budget";
        }
        long product;
        try {
            product = Math.multiplyExact(Math.multiplyExact((long) a, (long) b), (long) c);
        } catch (ArithmeticException overflow) {
            return "Grid product overflows";
        }
        if (product > limit) {
            return "Grid product exceeds budget";
        }
        return null;
    }

    private static GridAxisCounts clampExclusiveGridCounts(
        int xCount,
        int yCount,
        int zCount,
        int itemsPerCell,
        int maxElements
    ) {
        int x = clampPositiveGridAxis(xCount);
        int y = clampPositiveGridAxis(yCount);
        int z = clampPositiveGridAxis(zCount);
        int perCell = Math.max(1, itemsPerCell);
        int cap = Math.max(1, maxElements);

        while ((long) x * y * z * perCell > cap) {
            if (z > 1 && z >= x && z >= y) {
                z--;
            } else if (y > 1 && y >= x) {
                y--;
            } else if (x > 1) {
                x--;
            } else {
                break;
            }
        }
        return new GridAxisCounts(x, y, z);
    }

    private static int clampPositiveGridAxis(int count) {
        return Math.max(1, Math.min(MAX_GRID_AXIS, count));
    }

    private static long estimatedInclusiveGridOutputSize(int xCount, int yCount, int zCount, int itemsPerCell) {
        return (long) (xCount + 1) * (yCount + 1) * (zCount + 1) * itemsPerCell;
    }

    public record GridAxisCounts(int xCount, int yCount, int zCount) {
    }

    /**
     * Caps the number of instances produced by fixed-spacing sampling along a span.
     */
    public static int clampSpacingInstanceCount(double span, double spacing) {
        return clampSpacingInstanceCount(span, spacing, MAX_LIST_ELEMENTS);
    }

    /**
     * Caps spacing-based geometry instance counts to {@link #MAX_GEOMETRY_INSTANCES}.
     */
    public static int clampGeometrySpacingInstanceCount(double span, double spacing) {
        return clampSpacingInstanceCount(span, spacing, MAX_GEOMETRY_INSTANCES);
    }

    private static int clampSpacingInstanceCount(double span, double spacing, int maxElements) {
        if (!Double.isFinite(span) || !Double.isFinite(spacing) || spacing <= 0.0d) {
            return 1;
        }
        long raw = (long) Math.ceil(span / spacing) + 1L;
        return (int) Math.min(Math.max(1L, raw), Math.max(1, maxElements));
    }

    /**
     * Caps attempt budgets for rejection-style samplers.
     */
    public static int clampAttemptBudget(int attempts, int targetCount) {
        int perTarget = Math.max(1, targetCount);
        long scaled = (long) perTarget * 100L;
        long capped = Math.min(Math.max(100L, attempts), Math.max(scaled, 100L));
        capped = Math.min(capped, (long) MAX_LIST_ELEMENTS * 100L);
        return (int) capped;
    }

    /**
     * Caps repeat iterations so {@code count * itemsPerRepeat} does not exceed {@link #MAX_LIST_ELEMENTS}.
     */
    public static int clampRepeatCount(int count, int itemsPerRepeat) {
        if (count <= 0) {
            return 0;
        }
        int perRepeat = Math.max(1, itemsPerRepeat);
        long maxCount = MAX_LIST_ELEMENTS / (long) perRepeat;
        if (maxCount <= 0L) {
            return 0;
        }
        return (int) Math.min(count, maxCount);
    }

    /**
     * Caps loop iteration budgets to at least one and at most {@link #MAX_LOOP_ITERATIONS}.
     */
    public static int clampLoopIterations(int count) {
        return Math.max(1, Math.min(MAX_LOOP_ITERATIONS, count));
    }

    /**
     * Clamps curve/profile segment counts to {@code [min, MAX_SEGMENTS]}.
     */
    public static int clampSegments(int min, int requested) {
        return clampSegments(min, MAX_SEGMENTS, requested);
    }

    /**
     * Clamps segment counts to {@code [min, max]}.
     */
    public static int clampSegments(int min, int max, int requested) {
        return Math.max(min, Math.min(max, requested));
    }

    /**
     * Clamps heart-profile segment counts with a looser upper bound than {@link #clampSegments(int, int)}.
     */
    public static int clampHeartSegments(int requested) {
        return Math.max(24, Math.min(MAX_HEART_SEGMENTS, requested));
    }

    /**
     * Clamps per-unit resolution so {@code unitCount * resolution} does not exceed {@link #MAX_LIST_ELEMENTS}.
     */
    public static int clampResolutionPerUnit(int min, int requested, int unitCount) {
        int clamped = clampSegments(min, requested);
        int units = Math.max(1, unitCount);
        long maxPerUnit = MAX_LIST_ELEMENTS / (long) units;
        if (maxPerUnit < min) {
            return min;
        }
        return (int) Math.min(clamped, maxPerUnit);
    }

    /**
     * Clamps section counts so {@code profilePointCount * sectionCount} does not exceed {@link #MAX_LIST_ELEMENTS}.
     */
    public static int clampSectionCountForProfile(int min, int requested, int profilePointCount) {
        int clamped = clampSegments(min, requested);
        int profileSize = Math.max(1, profilePointCount);
        long maxSections = MAX_LIST_ELEMENTS / (long) profileSize;
        if (maxSections < min) {
            return min;
        }
        return (int) Math.min(clamped, maxSections);
    }

    /**
     * Clamps helix segments-per-turn so {@code turns * segmentsPerTurn} stays within {@link #MAX_LIST_ELEMENTS}.
     */
    public static int clampSegmentsPerTurn(int min, int requested, double turns) {
        int clamped = clampSegments(min, requested);
        if (!Double.isFinite(turns) || turns <= 0.0d) {
            return clamped;
        }
        long totalSegments = (long) Math.ceil(turns * clamped);
        if (totalSegments <= MAX_LIST_ELEMENTS) {
            return clamped;
        }
        long maxPerTurn = (long) Math.floor((double) MAX_LIST_ELEMENTS / Math.ceil(turns));
        return (int) Math.max(min, Math.min(clamped, maxPerTurn));
    }

    /**
     * Clamps per-axis bounds sampling for cubic grid estimators.
     */
    public static int clampBoundsSamples(int requested) {
        return Math.max(2, Math.min(MAX_BOUNDS_SAMPLES, requested));
    }
}

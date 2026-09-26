package com.nodecraft.nodesystem.util;

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
     * Hard cap for Relax Point List input size (implementation safety budget).
     */
    public static final int MAX_RELAX_POINTS = 8192;

    /**
     * Hard cap for Twist/Bend Geometry voxelization of non-SDF geometry sources.
     */
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

    /** Maximum materializable image sample count (width × height after downsample). */
    public static final int MAX_IMAGE_PIXELS = 1_048_576;

    /** Maximum accepted raster image file size on disk. */
    public static final long MAX_IMAGE_FILE_BYTES = 64L * 1024 * 1024;

    /** Maximum accepted MagicaVoxel .vox file size on disk. */
    public static final long MAX_VOX_FILE_BYTES = 64L * 1024 * 1024;

    /** Maximum solid voxels accepted from a single .vox import. */
    public static final int MAX_IMPORTED_VOXELS = 262_144;

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

    public static int clampNonNegativeCount(int count) {
        if (count <= 0) {
            return 0;
        }
        return Math.min(count, MAX_LIST_ELEMENTS);
    }

    public static int clampPositiveCount(int count) {
        return Math.max(1, Math.min(MAX_LIST_ELEMENTS, count));
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

package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    id = "pattern.surface_volume_distribution.scatter_geometry_surface",
    displayName = "Scatter On Geometry Surface",
    description = "Scatters points on voxelized geometry surfaces with random or blue-noise approximation and spacing fallback controls",
    category = "pattern.surface_volume_distribution",
    order = 4
)
public class ScatterOnGeometrySurfaceNode extends BaseNode {
    public enum DistributionMode {
        RANDOM,
        BLUE_NOISE_APPROX
    }

    public enum SpacingFallbackMode {
        STRICT,
        RELAXED
    }

    @NodeProperty(displayName = "Count", category = "Scatter", order = 1)
    private int count = 128;

    @NodeProperty(displayName = "Seed", category = "Scatter", order = 2)
    private int seed = 12345;

    @NodeProperty(displayName = "Min Spacing", category = "Scatter", order = 3)
    private double minSpacing = 0.0d;

    @NodeProperty(displayName = "Distribution", category = "Scatter", order = 4)
    private DistributionMode distributionMode = DistributionMode.BLUE_NOISE_APPROX;

    @NodeProperty(displayName = "Spacing Fallback", category = "Scatter", order = 5)
    private SpacingFallbackMode spacingFallbackMode = SpacingFallbackMode.RELAXED;

    @NodeProperty(displayName = "Unique Blocks Only", category = "Output", order = 6)
    private boolean uniqueBlocksOnly = true;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_MIN_SPACING_ID = "input_min_spacing";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_POINT_COUNT_ID = "output_point_count";
    private static final String OUTPUT_BLOCK_COUNT_ID = "output_block_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ScatterOnGeometrySurfaceNode() {
        super(UUID.randomUUID(), "pattern.surface_volume_distribution.scatter_geometry_surface");
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Optional scatter count override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional seed override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MIN_SPACING_ID, "Min Spacing", "Minimum Euclidean spacing between selected points in block units", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Scattered surface points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Scattered points snapped to block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POINT_COUNT_ID, "Point Count", "Number of scattered geometric points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_COUNT_ID, "Block Count", "Number of scattered snapped block coordinates", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when geometry input is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Scatters points on voxelized geometry surfaces with random or blue-noise approximation and spacing fallback controls";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPosList surfaceBlocks = GeometryVoxelizer.resolveBlocks(
            null,
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            false
        );
        if (surfaceBlocks.isEmpty()) {
            outputValues.put(OUTPUT_POINTS_ID, List.of());
            outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
            outputValues.put(OUTPUT_POINT_COUNT_ID, 0);
            outputValues.put(OUTPUT_BLOCK_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        int resolvedCount = Math.max(1, inputValues.get(INPUT_COUNT_ID) instanceof Number n ? n.intValue() : count);
        int resolvedSeed = inputValues.get(INPUT_SEED_ID) instanceof Number n ? n.intValue() : seed;
        double resolvedMinSpacing = Math.max(0.0d, inputValues.get(INPUT_MIN_SPACING_ID) instanceof Number n ? n.doubleValue() : minSpacing);
        Random random = new Random(resolvedSeed);

        List<BlockPos> source = new ArrayList<>(surfaceBlocks.getPositions());
        List<BlockPos> selected = selectPositions(source, resolvedCount, resolvedMinSpacing, random);

        List<Vector3d> points = new ArrayList<>(selected.size());
        BlockPosList blocks = new BlockPosList();
        Set<BlockPos> unique = uniqueBlocksOnly ? new LinkedHashSet<>() : null;
        for (BlockPos pos : selected) {
            points.add(toPoint(pos));
            if (unique != null) {
                if (unique.add(pos.toImmutable())) {
                    blocks.add(pos);
                }
            } else {
                blocks.add(pos);
            }
        }

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_POINT_COUNT_ID, points.size());
        outputValues.put(OUTPUT_BLOCK_COUNT_ID, blocks.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private List<BlockPos> selectPositions(List<BlockPos> source, int resolvedCount, double minSpacing, Random random) {
        if (source.isEmpty()) {
            return List.of();
        }

        double minSpacingSq = minSpacing * minSpacing;
        if (distributionMode == DistributionMode.RANDOM && minSpacingSq <= 0.0d) {
            return selectPureRandom(source, resolvedCount, random);
        }

        List<BlockPos> selected = distributionMode == DistributionMode.BLUE_NOISE_APPROX
            ? selectBlueNoiseApprox(source, resolvedCount, random, minSpacingSq)
            : selectRandomWithSpacing(source, resolvedCount, random, minSpacingSq);

        if (selected.size() >= resolvedCount || spacingFallbackMode == SpacingFallbackMode.STRICT) {
            return selected;
        }

        List<BlockPos> remaining = new ArrayList<>(source);
        remaining.removeAll(selected);
        Collections.shuffle(remaining, random);
        for (BlockPos pos : remaining) {
            if (selected.size() >= resolvedCount) {
                break;
            }
            selected.add(pos);
        }
        return selected;
    }

    private List<BlockPos> selectPureRandom(List<BlockPos> source, int resolvedCount, Random random) {
        List<BlockPos> pool = new ArrayList<>(source);
        Collections.shuffle(pool, random);
        if (pool.size() <= resolvedCount) {
            return pool;
        }
        return new ArrayList<>(pool.subList(0, resolvedCount));
    }

    private List<BlockPos> selectRandomWithSpacing(List<BlockPos> source, int resolvedCount, Random random, double minSpacingSq) {
        List<BlockPos> shuffled = new ArrayList<>(source);
        Collections.shuffle(shuffled, random);
        List<Vector3d> acceptedPoints = new ArrayList<>(resolvedCount);
        List<BlockPos> selected = new ArrayList<>(resolvedCount);
        for (BlockPos pos : shuffled) {
            if (selected.size() >= resolvedCount) {
                break;
            }
            if (minSpacingSq > 0.0d && !isFarEnough(pos, acceptedPoints, minSpacingSq)) {
                continue;
            }
            selected.add(pos);
            acceptedPoints.add(toPoint(pos));
        }
        return selected;
    }

    private List<BlockPos> selectBlueNoiseApprox(List<BlockPos> source, int resolvedCount, Random random, double minSpacingSq) {
        List<BlockPos> candidates = new ArrayList<>(source);
        Collections.shuffle(candidates, random);
        List<BlockPos> selected = new ArrayList<>(resolvedCount);
        if (candidates.isEmpty()) {
            return selected;
        }

        selected.add(candidates.remove(0));
        while (!candidates.isEmpty() && selected.size() < resolvedCount) {
            int bestIndex = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            int attempts = Math.min(24, candidates.size());
            for (int i = 0; i < attempts; i++) {
                int candidateIndex = random.nextInt(candidates.size());
                BlockPos candidate = candidates.get(candidateIndex);
                double nearestSq = nearestDistanceSq(candidate, selected);
                if (minSpacingSq > 0.0d && nearestSq < minSpacingSq) {
                    continue;
                }
                if (nearestSq > bestScore) {
                    bestScore = nearestSq;
                    bestIndex = candidateIndex;
                }
            }
            if (bestIndex < 0) {
                break;
            }
            selected.add(candidates.remove(bestIndex));
        }
        return selected;
    }

    private double nearestDistanceSq(BlockPos candidate, List<BlockPos> selected) {
        double minSq = Double.POSITIVE_INFINITY;
        double cx = candidate.getX() + 0.5d;
        double cy = candidate.getY() + 0.5d;
        double cz = candidate.getZ() + 0.5d;
        for (BlockPos pos : selected) {
            double dx = cx - (pos.getX() + 0.5d);
            double dy = cy - (pos.getY() + 0.5d);
            double dz = cz - (pos.getZ() + 0.5d);
            double distanceSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (distanceSq < minSq) {
                minSq = distanceSq;
            }
        }
        return minSq;
    }

    private boolean isFarEnough(BlockPos candidate, List<Vector3d> existingPoints, double minSpacingSq) {
        double cx = candidate.getX() + 0.5d;
        double cy = candidate.getY() + 0.5d;
        double cz = candidate.getZ() + 0.5d;
        for (Vector3d point : existingPoints) {
            double dx = cx - point.x;
            double dy = cy - point.y;
            double dz = cz - point.z;
            if ((dx * dx) + (dy * dy) + (dz * dz) < minSpacingSq) {
                return false;
            }
        }
        return true;
    }

    private Vector3d toPoint(BlockPos pos) {
        return new Vector3d(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d);
    }
}


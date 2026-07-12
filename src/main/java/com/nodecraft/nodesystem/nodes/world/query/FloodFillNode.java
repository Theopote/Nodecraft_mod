package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    id = "world.query.flood_fill",
    displayName = "Flood Fill",
    description = "Runs BFS flood fill from a seed block using 6 or 26-neighbor connectivity.",
    category = "world.query",
    order = 9
)
public class FloodFillNode extends BaseNode {

    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_TARGET_BLOCK_ID = "input_target_block";
    private static final String INPUT_MAX_DISTANCE_ID = "input_max_distance";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";
    private static final String INPUT_INCLUDE_DIAGONALS_ID = "input_include_diagonals";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_TARGET_BLOCK_ID = "output_target_block";
    private static final String OUTPUT_BOUNDARY_BLOCKS_ID = "output_boundary_blocks";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_STOPPED_REASON_ID = "output_stopped_reason";
    private static final String OUTPUT_VISITED_COUNT_ID = "output_visited_count";
    private static final String OUTPUT_SKIPPED_COUNT_ID = "output_skipped_count";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";

    private static final int[][] OFFSETS_6 = new int[][] {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private static final int[][] OFFSETS_26 = buildOffsets26();

    public FloodFillNode() {
        super(UUID.randomUUID(), "world.query.flood_fill");

        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Start block position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_TARGET_BLOCK_ID, "Target Block", "Optional target block id; defaults to seed block id", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_MAX_DISTANCE_ID, "Max Distance", "Maximum Chebyshev search radius from seed", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "Maximum number of connected blocks to return", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_INCLUDE_DIAGONALS_ID, "Include Diagonals", "Use 26-neighbor connectivity instead of 6-neighbor", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Connected block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Connected block count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TARGET_BLOCK_ID, "Target Block", "Resolved target block id", NodeDataType.BLOCK_TYPE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_BLOCKS_ID, "Boundary Blocks", "Subset of filled blocks touching non-target neighbors", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether flood fill was executed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_STOPPED_REASON_ID, "Stopped Reason", "completed, max_blocks, or invalid", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VISITED_COUNT_ID, "Visited Count", "Number of positions visited by the search", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SKIPPED_COUNT_ID, "Skipped Count", "Number of candidate neighbors skipped before enqueue", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "Whether Max Blocks stopped the search", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Runs BFS flood fill from a seed block using 6 or 26-neighbor connectivity.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null || context.getWorld() == null || !(inputValues.get(INPUT_SEED_ID) instanceof BlockPos seed)) {
            writeInvalid();
            return;
        }

        int maxDistance = inputValues.get(INPUT_MAX_DISTANCE_ID) instanceof Number n
            ? GenerationLimits.clampGridAxis(n.intValue())
            : 128;
        int maxBlocks = inputValues.get(INPUT_MAX_BLOCKS_ID) instanceof Number n
            ? GenerationLimits.clampPositiveCount(n.intValue())
            : 10000;
        boolean diagonals = inputValues.get(INPUT_INCLUDE_DIAGONALS_ID) instanceof Boolean b && b;
        int[][] offsets = diagonals ? OFFSETS_26 : OFFSETS_6;

        String targetBlockId;
        if (inputValues.get(INPUT_TARGET_BLOCK_ID) instanceof String blockId && !blockId.isBlank()) {
            targetBlockId = blockId;
        } else {
            BlockState seedState = context.getWorld().getBlockState(seed);
            targetBlockId = Registries.BLOCK.getId(seedState.getBlock()).toString();
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> filled = new ArrayList<>();
        int skippedCount = 0;

        BlockPos start = seed.toImmutable();
        if (!matchesTarget(context, start, targetBlockId)) {
            publish(new BlockPosList(), new BlockPosList(), targetBlockId, visited.size(), 1, false, "completed");
            return;
        }

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && filled.size() < maxBlocks) {
            BlockPos pos = queue.pollFirst();
            filled.add(pos);

            for (int[] d : offsets) {
                BlockPos next = pos.add(d[0], d[1], d[2]).toImmutable();
                if (visited.contains(next)) {
                    continue;
                }
                if (distanceChebyshev(seed, next) > maxDistance) {
                    continue;
                }
                if (!matchesTarget(context, next, targetBlockId)) {
                    skippedCount++;
                    continue;
                }
                visited.add(next);
                queue.addLast(next);
            }
        }

        boolean hitLimit = filled.size() >= maxBlocks && !queue.isEmpty();
        String stoppedReason = hitLimit ? "max_blocks" : "completed";
        BlockPosList blocks = new BlockPosList(filled);
        BlockPosList boundary = new BlockPosList(resolveBoundary(context, filled, targetBlockId, offsets));
        publish(blocks, boundary, targetBlockId, visited.size(), skippedCount, hitLimit, stoppedReason);
    }

    private void publish(BlockPosList blocks,
                         BlockPosList boundary,
                         String targetBlockId,
                         int visitedCount,
                         int skippedCount,
                         boolean hitLimit,
                         String stoppedReason) {
        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
        outputValues.put(OUTPUT_TARGET_BLOCK_ID, targetBlockId);
        outputValues.put(OUTPUT_BOUNDARY_BLOCKS_ID, boundary);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, stoppedReason);
        outputValues.put(OUTPUT_VISITED_COUNT_ID, visitedCount);
        outputValues.put(OUTPUT_SKIPPED_COUNT_ID, skippedCount);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
    }

    private boolean matchesTarget(ExecutionContext context, BlockPos pos, String targetBlockId) {
        BlockState state = context.getWorld().getBlockState(pos);
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        return targetBlockId.equals(blockId);
    }

    private List<BlockPos> resolveBoundary(ExecutionContext context, List<BlockPos> filled, String targetBlockId, int[][] offsets) {
        Set<BlockPos> filledSet = new HashSet<>(filled);
        List<BlockPos> boundary = new ArrayList<>();
        for (BlockPos pos : filled) {
            boolean isBoundary = false;
            for (int[] d : offsets) {
                BlockPos neighbor = pos.add(d[0], d[1], d[2]);
                if (!filledSet.contains(neighbor.toImmutable())) {
                    if (!matchesTarget(context, neighbor, targetBlockId)) {
                        isBoundary = true;
                        break;
                    }
                }
            }
            if (isBoundary) {
                boundary.add(pos.toImmutable());
            }
        }
        return boundary;
    }

    private int distanceChebyshev(BlockPos a, BlockPos b) {
        return Math.max(
            Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())),
            Math.abs(a.getZ() - b.getZ())
        );
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_TARGET_BLOCK_ID, "");
        outputValues.put(OUTPUT_BOUNDARY_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, "invalid");
        outputValues.put(OUTPUT_VISITED_COUNT_ID, 0);
        outputValues.put(OUTPUT_SKIPPED_COUNT_ID, 0);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, false);
    }

    private static int[][] buildOffsets26() {
        List<int[]> offsets = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    offsets.add(new int[] {x, y, z});
                }
            }
        }
        return offsets.toArray(new int[0][]);
    }
}

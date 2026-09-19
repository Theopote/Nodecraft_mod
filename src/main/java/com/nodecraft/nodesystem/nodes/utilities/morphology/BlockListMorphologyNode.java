package com.nodecraft.nodesystem.nodes.utilities.morphology;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Morphological dilate or erode on a block set using 6- or 26-connected voxel neighborhoods.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "utilities.morphology.block_list_morphology",
    displayName = "Block List Morphology",
    description = "Dilates or erodes a block list using 6- or 26-neighbor morphology iterations (Connectivity property)",
    category = "utilities.morphology",
    order = 0
)
public class BlockListMorphologyNode extends BaseNode {

    public enum MorphOp {
        DILATE,
        ERODE
    }

    public enum Connectivity {
        SIX,
        TWENTY_SIX
    }

    private static final int[][] D6 = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    private static final int[][] D26 = buildD26();

    @NodeProperty(displayName = "Operation", category = "Morphology", order = 1)
    private MorphOp operation = MorphOp.DILATE;

    @NodeProperty(displayName = "Connectivity", category = "Morphology", order = 2,
        description = "6-neighbor (Manhattan-1) or 26-neighbor (Chebyshev-1 cube) structuring element")
    private Connectivity connectivity = Connectivity.SIX;

    @NodeProperty(displayName = "Iterations", category = "Morphology", order = 3,
        description = "Number of dilate/erode iterations to apply")
    private int iterations = 1;

    private static int[][] buildD26() {
        java.util.List<int[]> list = new java.util.ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    list.add(new int[] {dx, dy, dz});
                }
            }
        }
        return list.toArray(new int[0][]);
    }

    private static final String INPUT_BLOCKS_ID = "input_blocks";
    private static final String INPUT_ITERATIONS_ID = "input_iterations";
    private static final String INPUT_MAX_OUTPUT_BLOCKS_ID = "input_max_output_blocks";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_INPUT_COUNT_ID = "output_input_count";
    private static final String OUTPUT_OUTPUT_COUNT_ID = "output_output_count";
    private static final String OUTPUT_DELTA_COUNT_ID = "output_delta_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_STOPPED_REASON_ID = "output_stopped_reason";

    @NodeProperty(displayName = "Max Output Blocks", category = "Morphology", order = 4,
        description = "Stops processing if dilation grows beyond this many blocks")
    private int maxOutputBlocks = 32768;

    public BlockListMorphologyNode() {
        super(UUID.randomUUID(), "utilities.morphology.block_list_morphology");

        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks",
            "Input block positions",
            NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_ITERATIONS_ID, "Iterations",
            "Optional iteration override (>= 1). When disconnected, the node property is used.",
            NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MAX_OUTPUT_BLOCKS_ID, "Max Output Blocks",
            "Optional safety limit for the result block count",
            NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks",
            "Morphology result",
            NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_INPUT_COUNT_ID, "Input Count",
            "Number of unique input blocks",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_OUTPUT_COUNT_ID, "Output Count",
            "Number of result blocks",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_DELTA_COUNT_ID, "Delta Count",
            "Output count minus input count",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when processing succeeded",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_STOPPED_REASON_ID, "Stopped Reason",
            "Reason processing stopped early or failed",
            NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Block List Morphology";
    }

    @Override
    public String getDescription() {
        return "Dilates or erodes a block list using 6- or 26-neighbor morphology iterations";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object blocksObj = inputValues.get(INPUT_BLOCKS_ID);
        if (!(blocksObj instanceof BlockPosList input) || input.isEmpty()) {
            writeOutputs(new BlockPosList(), 0, false, "Invalid or empty block list");
            return;
        }

        int iters = iterations;
        Object iterObj = inputValues.get(INPUT_ITERATIONS_ID);
        if (iterObj instanceof Number n) {
            iters = n.intValue();
        }
        if (iters < 1) {
            iters = 1;
        }
        if (iters > 64) {
            iters = 64;
        }

        int outputLimit = maxOutputBlocks;
        Object maxObj = inputValues.get(INPUT_MAX_OUTPUT_BLOCKS_ID);
        if (maxObj instanceof Number number) {
            outputLimit = Math.max(1, number.intValue());
        }

        Set<BlockPos> current = new LinkedHashSet<>();
        for (BlockPos p : input) {
            current.add(p.toImmutable());
        }
        int inputCount = current.size();

        int[][] offsets = connectivity == Connectivity.TWENTY_SIX ? D26 : D6;

        for (int i = 0; i < iters; i++) {
            if (operation == MorphOp.DILATE) {
                current = dilateOnce(current, offsets);
            } else {
                current = erodeOnce(current, offsets);
            }
            if (current.size() > outputLimit) {
                BlockPosList partial = new BlockPosList();
                int count = 0;
                for (BlockPos pos : current) {
                    if (count++ >= outputLimit) {
                        break;
                    }
                    partial.add(pos);
                }
                writeOutputs(partial, inputCount, false, "Output exceeds max output blocks " + outputLimit);
                return;
            }
        }

        BlockPosList out = new BlockPosList();
        out.addAll(current);
        writeOutputs(out, inputCount, true, "");
    }

    private void writeOutputs(BlockPosList out, int inputCount, boolean valid, String stoppedReason) {
        int outputCount = out == null ? 0 : out.size();
        outputValues.put(OUTPUT_BLOCKS_ID, out == null ? new BlockPosList() : out);
        outputValues.put(OUTPUT_INPUT_COUNT_ID, inputCount);
        outputValues.put(OUTPUT_OUTPUT_COUNT_ID, outputCount);
        outputValues.put(OUTPUT_DELTA_COUNT_ID, outputCount - inputCount);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, stoppedReason == null ? "" : stoppedReason);
    }

    private static Set<BlockPos> dilateOnce(Set<BlockPos> input, int[][] offsets) {
        Set<BlockPos> out = new LinkedHashSet<>(input);
        for (BlockPos p : input) {
            for (int[] d : offsets) {
                out.add(new BlockPos(p.getX() + d[0], p.getY() + d[1], p.getZ() + d[2]).toImmutable());
            }
        }
        return out;
    }

    private static Set<BlockPos> erodeOnce(Set<BlockPos> input, int[][] offsets) {
        Set<BlockPos> set = new HashSet<>(input);
        Set<BlockPos> out = new LinkedHashSet<>();
        for (BlockPos p : input) {
            boolean keep = true;
            for (int[] d : offsets) {
                BlockPos q = new BlockPos(p.getX() + d[0], p.getY() + d[1], p.getZ() + d[2]).toImmutable();
                if (!set.contains(q)) {
                    keep = false;
                    break;
                }
            }
            if (keep) {
                out.add(p.toImmutable());
            }
        }
        return out;
    }

    public MorphOp getOperation() {
        return operation;
    }

    public void setOperation(MorphOp operation) {
        this.operation = operation == null ? MorphOp.DILATE : operation;
        markDirty();
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
        markDirty();
    }

    public Connectivity getConnectivity() {
        return connectivity;
    }

    public void setConnectivity(Connectivity connectivity) {
        this.connectivity = connectivity == null ? Connectivity.SIX : connectivity;
        markDirty();
    }

    public int getMaxOutputBlocks() {
        return maxOutputBlocks;
    }

    public void setMaxOutputBlocks(int maxOutputBlocks) {
        this.maxOutputBlocks = Math.max(1, maxOutputBlocks);
        markDirty();
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of(
            "operation", operation.name(),
            "connectivity", connectivity.name(),
            "iterations", iterations,
            "maxOutputBlocks", maxOutputBlocks
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map<?, ?> map) {
            Object op = map.get("operation");
            if (op instanceof String s) {
                try {
                    setOperation(MorphOp.valueOf(s));
                } catch (IllegalArgumentException ignored) {
                    // keep default
                }
            }
            Object conn = map.get("connectivity");
            if (conn instanceof String cs) {
                try {
                    setConnectivity(Connectivity.valueOf(cs));
                } catch (IllegalArgumentException ignored) {
                    // keep default
                }
            }
            Object it = map.get("iterations");
            if (it instanceof Number n) {
                setIterations(n.intValue());
            }
            Object max = map.get("maxOutputBlocks");
            if (max instanceof Number n) {
                setMaxOutputBlocks(n.intValue());
            }
        }
    }
}

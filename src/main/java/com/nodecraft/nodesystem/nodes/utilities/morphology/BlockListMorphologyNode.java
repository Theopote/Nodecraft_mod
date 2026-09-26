package com.nodecraft.nodesystem.nodes.utilities.morphology;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockListUtils;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Morphological dilate or erode on a block set using 6- or 26-connected voxel neighborhoods.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "utilities.morphology.block_list_morphology",
    displayName = "Block Morphology",
    description = "Dilate or erode a block set.",
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

    private enum StepResult {
        OK,
        OVERFLOW,
        COORD_OVERFLOW
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

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_INPUT_COUNT_ID = "output_input_count";
    private static final String OUTPUT_OUTPUT_COUNT_ID = "output_output_count";
    private static final String OUTPUT_DELTA_COUNT_ID = "output_delta_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public BlockListMorphologyNode() {
        super(UUID.randomUUID(), "utilities.morphology.block_list_morphology");

        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks",
            "Input block positions (duplicate positions are ignored)",
            NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_ITERATIONS_ID, "Iterations",
            "Optional iteration override (1..64). When disconnected, the node property is used.",
            NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks",
            "Morphology result (deterministic order, set semantics)",
            NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_INPUT_COUNT_ID, "Input Count",
            "Number of unique input blocks",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_OUTPUT_COUNT_ID, "Output Count",
            "Number of result blocks",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_DELTA_COUNT_ID, "Delta Count",
            "Output count minus input count (0 when Valid is false)",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when processing succeeded",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Why morphology failed",
            NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Block Morphology";
    }

    @Override
    public String getDescription() {
        return "Dilate or erode a block set.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        LinkedHashSet<BlockPos> current = BlockListUtils.resolveStrictBlockSet(inputValues.get(INPUT_BLOCKS_ID));
        if (current == null) {
            writeFailure(0, "Invalid block list");
            return;
        }

        if (current.isEmpty()) {
            writeSuccess(new BlockPosList(), 0, 0);
            return;
        }

        int inputCount = current.size();
        if (inputCount > GenerationLimits.MAX_MORPHOLOGY_BLOCKS) {
            writeFailure(inputCount, "Input exceeds max morphology blocks " + GenerationLimits.MAX_MORPHOLOGY_BLOCKS);
            return;
        }

        Integer resolvedIterations = OptionalPortDrive.resolveOptionalInteger(
            this,
            INPUT_ITERATIONS_ID,
            iterations
        );
        if (resolvedIterations == null
                || resolvedIterations < 1
                || resolvedIterations > GenerationLimits.MAX_MORPHOLOGY_ITERATIONS) {
            writeFailure(inputCount, "Iterations must be an exact Integer between 1 and "
                + GenerationLimits.MAX_MORPHOLOGY_ITERATIONS);
            return;
        }

        int[][] offsets = connectivity == Connectivity.TWENTY_SIX ? D26 : D6;
        MorphOp resolvedOperation = operation == null ? MorphOp.DILATE : operation;

        for (int i = 0; i < resolvedIterations; i++) {
            StepResult stepResult;
            if (resolvedOperation == MorphOp.DILATE) {
                stepResult = dilateOnce(current, offsets);
            } else {
                stepResult = erodeOnce(current, offsets);
            }
            if (stepResult != StepResult.OK) {
                String error = stepResult == StepResult.COORD_OVERFLOW
                    ? "Block coordinate overflow during morphology"
                    : "Morphology exceeds max blocks " + GenerationLimits.MAX_MORPHOLOGY_BLOCKS;
                writeFailure(inputCount, error);
                return;
            }
        }

        BlockPosList out = new BlockPosList();
        out.addAll(current);
        writeSuccess(out, inputCount, out.size());
    }

    private void writeSuccess(BlockPosList out, int inputCount, int outputCount) {
        outputValues.put(OUTPUT_BLOCKS_ID, out);
        outputValues.put(OUTPUT_INPUT_COUNT_ID, inputCount);
        outputValues.put(OUTPUT_OUTPUT_COUNT_ID, outputCount);
        outputValues.put(OUTPUT_DELTA_COUNT_ID, outputCount - inputCount);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeFailure(int inputCount, String error) {
        outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_INPUT_COUNT_ID, inputCount);
        outputValues.put(OUTPUT_OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_DELTA_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private static StepResult dilateOnce(LinkedHashSet<BlockPos> input, int[][] offsets) {
        LinkedHashSet<BlockPos> out = new LinkedHashSet<>(input);
        for (BlockPos p : input) {
            for (int[] d : offsets) {
                BlockPos candidate = offsetPos(p, d);
                if (candidate == null) {
                    return StepResult.COORD_OVERFLOW;
                }
                if (out.add(candidate) && out.size() > GenerationLimits.MAX_MORPHOLOGY_BLOCKS) {
                    return StepResult.OVERFLOW;
                }
            }
        }
        input.clear();
        input.addAll(out);
        return StepResult.OK;
    }

    private static StepResult erodeOnce(LinkedHashSet<BlockPos> input, int[][] offsets) {
        Set<BlockPos> membership = new HashSet<>(input);
        LinkedHashSet<BlockPos> out = new LinkedHashSet<>();
        for (BlockPos p : input) {
            boolean keep = true;
            for (int[] d : offsets) {
                BlockPos neighbor = offsetPos(p, d);
                if (neighbor == null) {
                    return StepResult.COORD_OVERFLOW;
                }
                if (!membership.contains(neighbor)) {
                    keep = false;
                    break;
                }
            }
            if (keep) {
                out.add(p.toImmutable());
            }
        }
        input.clear();
        input.addAll(out);
        return StepResult.OK;
    }

    private static @Nullable BlockPos offsetPos(BlockPos origin, int[] delta) {
        try {
            return new BlockPos(
                Math.addExact(origin.getX(), delta[0]),
                Math.addExact(origin.getY(), delta[1]),
                Math.addExact(origin.getZ(), delta[2])
            ).toImmutable();
        } catch (ArithmeticException ignored) {
            return null;
        }
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

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("operation", getOperation().name());
        state.put("connectivity", getConnectivity().name());
        state.put("iterations", iterations);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("operation") instanceof String value) {
            try {
                setOperation(MorphOp.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                // keep default
            }
        }
        if (map.get("connectivity") instanceof String value) {
            try {
                setConnectivity(Connectivity.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                // keep default
            }
        }
        if (map.get("iterations") instanceof Number value) {
            setIterations(value.intValue());
        }
    }
}

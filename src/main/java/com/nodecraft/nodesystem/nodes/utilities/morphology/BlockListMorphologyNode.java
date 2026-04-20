package com.nodecraft.nodesystem.nodes.utilities.morphology;

import com.nodecraft.nodesystem.api.NodeDataType;
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

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public BlockListMorphologyNode() {
        super(UUID.randomUUID(), "utilities.morphology.block_list_morphology");

        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks",
            "Input block positions",
            NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_ITERATIONS_ID, "Iterations",
            "Optional iteration override (>= 1). When disconnected, the node property is used.",
            NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks",
            "Morphology result",
            NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when processing succeeded",
            NodeDataType.BOOLEAN, this));
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
            outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
            outputValues.put(OUTPUT_VALID_ID, false);
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

        Set<BlockPos> current = new LinkedHashSet<>();
        for (BlockPos p : input) {
            current.add(p.toImmutable());
        }

        int[][] offsets = connectivity == Connectivity.TWENTY_SIX ? D26 : D6;

        for (int i = 0; i < iters; i++) {
            if (operation == MorphOp.DILATE) {
                current = dilateOnce(current, offsets);
            } else {
                current = erodeOnce(current, offsets);
            }
        }

        BlockPosList out = new BlockPosList();
        out.addAll(current);
        outputValues.put(OUTPUT_BLOCKS_ID, out);
        outputValues.put(OUTPUT_VALID_ID, true);
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

    @Override
    public Object getNodeState() {
        return java.util.Map.of(
            "operation", operation.name(),
            "connectivity", connectivity.name(),
            "iterations", iterations
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
        }
    }
}

package com.nodecraft.nodesystem.nodes.spatial.voxel;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Legacy compatibility voxel helper for set difference over block-coordinate lists.
 */
@NodeInfo(
    id = "spatial.voxel.difference_coords",
    displayName = "Difference Coords",
    description = "Subtracts one block-coordinate list from another.",
    category = "spatial.voxel"
)
public class DifferenceCoordsNode extends BaseNode {

    private static final String INPUT_COORDS_A_ID = "input_coords_a";
    private static final String INPUT_COORDS_B_ID = "input_coords_b";

    private static final String OUTPUT_DIFFERENCE_ID = "output_difference";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_REMOVED_COUNT_ID = "output_removed_count";

    public DifferenceCoordsNode() {
        super(UUID.randomUUID(), "spatial.voxel.difference_coords");

        addInputPort(new BasePort(
            INPUT_COORDS_A_ID,
            "Coordinates A",
            "Base block-coordinate list",
            NodeDataType.BLOCK_LIST,
            this
        ));
        addInputPort(new BasePort(
            INPUT_COORDS_B_ID,
            "Coordinates B",
            "Block-coordinate list to subtract",
            NodeDataType.BLOCK_LIST,
            this
        ));

        addOutputPort(new BasePort(
            OUTPUT_DIFFERENCE_ID,
            "Difference",
            "Remaining block-coordinate list after subtraction",
            NodeDataType.BLOCK_LIST,
            this
        ));
        addOutputPort(new BasePort(
            OUTPUT_COUNT_ID,
            "Count",
            "Number of coordinates remaining after subtraction",
            NodeDataType.INTEGER,
            this
        ));
        addOutputPort(new BasePort(
            OUTPUT_REMOVED_COUNT_ID,
            "Removed Count",
            "Number of coordinates removed from Coordinates A",
            NodeDataType.INTEGER,
            this
        ));
    }

    @Override
    public String getDescription() {
        return "Removes the coordinates from B out of A.";
    }

    @Override
    public String getDisplayName() {
        return "Difference (Coords)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordsAObj = inputValues.get(INPUT_COORDS_A_ID);
        Object coordsBObj = inputValues.get(INPUT_COORDS_B_ID);

        BlockPosList resultList = new BlockPosList();
        int removedCount = 0;

        if (coordsAObj instanceof BlockPosList coordsA) {
            if (!(coordsBObj instanceof BlockPosList coordsB)) {
                resultList.addAll(coordsA.getPositions());
            } else {
                Set<BlockPos> subtractSet = new HashSet<>();
                for (BlockPos pos : coordsB) {
                    subtractSet.add(pos.toImmutable());
                }

                int originalSize = coordsA.size();
                for (BlockPos pos : coordsA) {
                    if (!subtractSet.contains(pos)) {
                        resultList.add(pos);
                    }
                }
                removedCount = originalSize - resultList.size();
            }
        }

        outputValues.put(OUTPUT_DIFFERENCE_ID, resultList);
        outputValues.put(OUTPUT_COUNT_ID, resultList.size());
        outputValues.put(OUTPUT_REMOVED_COUNT_ID, removedCount);
    }
}

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
 * Legacy compatibility voxel helper for set union over block-coordinate lists.
 */
@NodeInfo(
    id = "spatial.voxel.union_coords",
    displayName = "Union Coords",
    description = "Merges two block-coordinate lists and removes duplicates.",
    category = "spatial.voxel"
)
public class UnionCoordsNode extends BaseNode {

    private static final String INPUT_COORDS_A_ID = "input_coords_a";
    private static final String INPUT_COORDS_B_ID = "input_coords_b";

    private static final String OUTPUT_UNION_ID = "output_union";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public UnionCoordsNode() {
        super(UUID.randomUUID(), "spatial.voxel.union_coords");

        addInputPort(new BasePort(
            INPUT_COORDS_A_ID,
            "Coordinates A",
            "First block-coordinate list",
            NodeDataType.BLOCK_LIST,
            this
        ));
        addInputPort(new BasePort(
            INPUT_COORDS_B_ID,
            "Coordinates B",
            "Second block-coordinate list",
            NodeDataType.BLOCK_LIST,
            this
        ));

        addOutputPort(new BasePort(
            OUTPUT_UNION_ID,
            "Union",
            "Merged block-coordinate list",
            NodeDataType.BLOCK_LIST,
            this
        ));
        addOutputPort(new BasePort(
            OUTPUT_COUNT_ID,
            "Count",
            "Number of unique coordinates in the merged list",
            NodeDataType.INTEGER,
            this
        ));
    }

    @Override
    public String getDescription() {
        return "Merges two block-coordinate lists.";
    }

    @Override
    public String getDisplayName() {
        return "Union (Coords)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordsAObj = inputValues.get(INPUT_COORDS_A_ID);
        Object coordsBObj = inputValues.get(INPUT_COORDS_B_ID);

        BlockPosList resultList = new BlockPosList();

        if (coordsAObj instanceof BlockPosList || coordsBObj instanceof BlockPosList) {
            Set<BlockPos> unionSet = new HashSet<>();

            if (coordsAObj instanceof BlockPosList coordsA) {
                for (BlockPos pos : coordsA) {
                    unionSet.add(pos.toImmutable());
                }
            }

            if (coordsBObj instanceof BlockPosList coordsB) {
                for (BlockPos pos : coordsB) {
                    unionSet.add(pos.toImmutable());
                }
            }

            for (BlockPos pos : unionSet) {
                resultList.add(pos);
            }
        }

        outputValues.put(OUTPUT_UNION_ID, resultList);
        outputValues.put(OUTPUT_COUNT_ID, resultList.size());
    }
}

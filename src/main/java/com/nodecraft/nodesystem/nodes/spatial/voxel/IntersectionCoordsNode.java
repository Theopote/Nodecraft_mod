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
 * Legacy compatibility voxel helper for set intersection over block-coordinate lists.
 */
@NodeInfo(
    id = "spatial.voxel.intersection_coords",
    displayName = "Intersection Coords",
    description = "Computes the overlap between two block-coordinate lists.",
    category = "spatial.voxel"
)
public class IntersectionCoordsNode extends BaseNode {

    private static final String INPUT_COORDS_A_ID = "input_coords_a";
    private static final String INPUT_COORDS_B_ID = "input_coords_b";

    private static final String OUTPUT_INTERSECTION_ID = "output_intersection";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_OVERLAP_RATIO_ID = "output_overlap_ratio";

    public IntersectionCoordsNode() {
        super(UUID.randomUUID(), "spatial.voxel.intersection_coords");

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
            OUTPUT_INTERSECTION_ID,
            "Intersection",
            "Overlapping block-coordinate list",
            NodeDataType.BLOCK_LIST,
            this
        ));
        addOutputPort(new BasePort(
            OUTPUT_COUNT_ID,
            "Count",
            "Number of overlapping coordinates",
            NodeDataType.INTEGER,
            this
        ));
        addOutputPort(new BasePort(
            OUTPUT_OVERLAP_RATIO_ID,
            "Overlap Ratio",
            "Overlap ratio relative to Coordinates A",
            NodeDataType.DOUBLE,
            this
        ));
    }

    @Override
    public String getDescription() {
        return "Gets the overlap between two block-coordinate lists.";
    }

    @Override
    public String getDisplayName() {
        return "Intersection (Coords)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordsAObj = inputValues.get(INPUT_COORDS_A_ID);
        Object coordsBObj = inputValues.get(INPUT_COORDS_B_ID);

        BlockPosList resultList = new BlockPosList();
        double overlapRatio = 0.0d;

        if (coordsAObj instanceof BlockPosList coordsA && coordsBObj instanceof BlockPosList coordsB) {
            if (!coordsA.isEmpty() && !coordsB.isEmpty()) {
                BlockPosList smallerList;
                BlockPosList largerList;
                if (coordsA.size() <= coordsB.size()) {
                    smallerList = coordsA;
                    largerList = coordsB;
                } else {
                    smallerList = coordsB;
                    largerList = coordsA;
                }

                Set<BlockPos> largerSet = new HashSet<>();
                for (BlockPos pos : largerList) {
                    largerSet.add(pos.toImmutable());
                }

                for (BlockPos pos : smallerList) {
                    if (largerSet.contains(pos)) {
                        resultList.add(pos);
                    }
                }

                overlapRatio = (double) resultList.size() / coordsA.size();
            }
        }

        outputValues.put(OUTPUT_INTERSECTION_ID, resultList);
        outputValues.put(OUTPUT_COUNT_ID, resultList.size());
        outputValues.put(OUTPUT_OVERLAP_RATIO_ID, overlapRatio);
    }
}

package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GridArrayNodeTest {

    @Test
    void hugeGridCountsAreClampedBeforeGeneration() {
        GridArrayNode node = new GridArrayNode();
        node.setGridType(GridArrayNode.GridType.GRID_3D);

        BlockPosList source = new BlockPosList();
        source.add(new BlockPos(0, 0, 0));

        Map<String, Object> outputs = node.compute(Map.of(
            "input_coordinates", source,
            "input_x_count", Integer.MAX_VALUE,
            "input_y_count", Integer.MAX_VALUE,
            "input_z_count", Integer.MAX_VALUE
        ));

        BlockPosList result = (BlockPosList) outputs.get("output_grid_coordinates");
        assertTrue(result.size() <= GenerationLimits.MAX_LIST_ELEMENTS);
    }
}

package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetBlockPositionsInRegionNodeTest {

    @Test
    void unconnectedMaxPointsUsesDefaultBudgetForOversizedRegion() {
        GetBlockPositionsInRegionNode node = new GetBlockPositionsInRegionNode();
        RegionData region = new RegionData(new BlockPos(0, 0, 0), new BlockPos(1023, 1, 1023));

        Map<String, Object> outputs = node.compute(Map.of(
            "input_region", region
        ));

        assertTrue((Boolean) outputs.get("output_valid"));
        int count = (Integer) outputs.get("output_count");
        assertTrue(count <= WorldReadUtils.DEFAULT_MAX_BLOCKS);
        assertTrue(count < 2_097_152);
        assertTrue((Boolean) outputs.get("output_sampled"));
        assertTrue((Boolean) outputs.get("output_hit_limit"));
        assertFalse((Boolean) outputs.get("output_complete"));
    }

    @Test
    void zeroMaxPointsFailsClosed() {
        GetBlockPositionsInRegionNode node = new GetBlockPositionsInRegionNode();
        RegionData region = new RegionData(new BlockPos(0, 0, 0), new BlockPos(10, 10, 10));

        Map<String, Object> outputs = node.compute(Map.of(
            "input_region", region,
            "input_max_points", 0
        ));

        assertFalse((Boolean) outputs.get("output_valid"));
    }

    @Test
    void overHardCapFailsClosed() {
        GetBlockPositionsInRegionNode node = new GetBlockPositionsInRegionNode();
        RegionData region = new RegionData(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1));

        Map<String, Object> outputs = node.compute(Map.of(
            "input_region", region,
            "input_max_points", GenerationLimits.MAX_WORLD_READ_BLOCKS + 1
        ));

        assertFalse((Boolean) outputs.get("output_valid"));
    }

    @Test
    void underBudgetReturnsFullRegionWithoutSampling() {
        GetBlockPositionsInRegionNode node = new GetBlockPositionsInRegionNode();
        RegionData region = new RegionData(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1));

        Map<String, Object> outputs = node.compute(Map.of(
            "input_region", region,
            "input_max_points", 100
        ));

        assertEquals(8, outputs.get("output_count"));
        assertFalse((Boolean) outputs.get("output_sampled"));
        assertFalse((Boolean) outputs.get("output_hit_limit"));
        assertTrue((Boolean) outputs.get("output_complete"));
    }

    @Test
    void explicitMaxPointsTriggersUniformSampling() {
        GetBlockPositionsInRegionNode node = new GetBlockPositionsInRegionNode();
        RegionData region = new RegionData(new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));

        Map<String, Object> outputs = node.compute(Map.of(
            "input_region", region,
            "input_max_points", 8
        ));

        assertEquals(8, outputs.get("output_count"));
        assertTrue((Boolean) outputs.get("output_sampled"));
        assertTrue((Boolean) outputs.get("output_hit_limit"));
        assertFalse((Boolean) outputs.get("output_complete"));
    }

    @Test
    void filterModeCapsCoordinateListWithoutSampling() {
        GetBlockPositionsInRegionNode node = new GetBlockPositionsInRegionNode();
        node.setFilterFromCoordinates(true);
        RegionData region = new RegionData(new BlockPos(0, 0, 0), new BlockPos(9, 0, 0));
        BlockPosList coordinates = new BlockPosList();
        for (int x = 0; x <= 9; x++) {
            coordinates.add(new BlockPos(x, 0, 0));
        }

        Map<String, Object> outputs = node.compute(Map.of(
            "input_region", region,
            "input_coordinates", coordinates,
            "input_max_points", 3,
            "input_use_input_coordinates", true
        ));

        assertEquals(3, outputs.get("output_count"));
        assertTrue((Boolean) outputs.get("output_hit_limit"));
        assertFalse((Boolean) outputs.get("output_sampled"));
        assertFalse((Boolean) outputs.get("output_complete"));
    }
}

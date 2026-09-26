package com.nodecraft.nodesystem.flow;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.nodes.pattern.linear.InstanceOnPointsNode;
import com.nodecraft.nodesystem.nodes.world.query.FilterPointsByRuleNode;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowAndInstanceNodeTest {

    @Test
    void instanceOnPointsCopiesTemplateAtEveryAnchor() {
        InstanceOnPointsNode node = new InstanceOnPointsNode();
        List<PointData> anchors = List.of(new PointData(10, 64, 10), new PointData(20, 64, 20));
        List<BlockPlacementData> template = List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_fence"),
                new BlockPlacementData(new BlockPos(0, 1, 0), "minecraft:lantern")
        );

        Map<String, Object> outputs = node.compute(Map.of(
                "input_points", anchors,
                "input_template_placements", template
        ));

        assertEquals(true, outputs.get("output_valid"));
        assertEquals(2, outputs.get("output_instance_count"));
        assertEquals(4, outputs.get("output_placement_count"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> placements = assertInstanceOf(List.class, outputs.get("output_placements"));
        assertTrue(placements.stream().anyMatch(p -> p.pos().equals(new BlockPos(20, 65, 20))));
    }

    @Test
    void filterPointsByRuleCanKeepHighSteepPoints() {
        FilterPointsByRuleNode node = new FilterPointsByRuleNode();

        Map<String, Object> outputs = node.compute(Map.of(
                "input_points", List.of(
                        new PointData(0.0d, 80.0d, 0.0d),
                        new PointData(0.0d, 120.0d, 0.0d)
                ),
                "input_normals", List.of(
                        new VectorData(0.0d, 1.0d, 0.0d),
                        new VectorData(1.0d, 1.0d, 0.0d)
                ),
                "input_min_height", 100.0d,
                "input_min_slope", 40.0d
        ));

        assertEquals(true, outputs.get("output_valid"));
        assertEquals(1, outputs.get("output_count"));
        assertEquals(List.of(false, true), outputs.get("output_mask"));
    }
}

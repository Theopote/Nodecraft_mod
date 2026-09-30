package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.AssignBlockTypeNode;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.BasicAssignmentUtils;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.CreateBlockPaletteNode;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.WeightedBlockPaletteNode;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basic Assignment Strict Source & Weight Contract v2 (Graph V114).
 */
class BasicAssignmentLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV114() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void overflowTotalWeightIsInvalidAndNeverInfinityWhenValid() {
        assertFalse(BasicAssignmentUtils.validateWeights(
            List.of(Double.MAX_VALUE, Double.MAX_VALUE), 2).valid());

        CreateBlockPaletteNode create = new CreateBlockPaletteNode();
        create.setInput("input_block_ids", List.of("minecraft:stone", "minecraft:dirt"));
        create.setInput("input_weights", List.of(Double.MAX_VALUE, Double.MAX_VALUE));
        create.processNode(null);
        assertFalse((Boolean) create.getOutput("output_valid"));

        BlockPaletteData palette = BlockPaletteData.ofBlockIds(
            List.of("minecraft:stone", "minecraft:dirt"));
        WeightedBlockPaletteNode weighted = new WeightedBlockPaletteNode();
        weighted.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        weighted.setInput("input_palette", palette);
        weighted.setInput("input_weights", List.of(Double.MAX_VALUE, Double.MAX_VALUE));
        weighted.processNode(null);
        assertFalse((Boolean) weighted.getOutput("output_valid"));
        Object total = weighted.getOutput("output_total_weight");
        assertFalse(total instanceof Double d && Double.isInfinite(d));
    }

    @Test
    void parseDoubleListRejectsNonExactDoubles() {
        assertFalse(BasicAssignmentUtils.parseDoubleList(List.of(1, 2), "Weights").valid());
        assertFalse(BasicAssignmentUtils.parseDoubleList(List.of(1.0f, 2.0f), "Weights").valid());
        assertTrue(BasicAssignmentUtils.parseDoubleList(List.of(1.0d, 2.0d), "Weights").valid());

        BlockPaletteData palette = BlockPaletteData.ofBlockIds(
            List.of("minecraft:stone", "minecraft:dirt"));
        WeightedProbe probe = new WeightedProbe();
        probe.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        probe.putInput("input_palette", palette);
        probe.putInput("input_weights", List.of(1, 2));
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
    }

    @Test
    void mixedPlacementListFailsClosed() {
        AssignProbe probe = new AssignProbe();
        List<Object> mixed = new ArrayList<>();
        mixed.add(new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null));
        mixed.add("bad");
        mixed.add(null);
        probe.putInput("input_placements", mixed);
        probe.putInput("input_block_type", "minecraft:stone");
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertInstanceOf(List.class, probe.getOutput("output_placements"));
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
        assertFalse(((String) probe.getOutput("output_error")).isBlank());
    }

    @Test
    void treeWithForeignItemsFailsClosed() {
        AssignProbe probe = new AssignProbe();
        DataTreeData tree = new DataTreeData(List.of(
            new DataTreeData.Branch(List.of(0), List.of(
                new BlockPos(0, 0, 0),
                "junk",
                42,
                new BlockPos(1, 0, 0)
            ))
        ));
        probe.putInput("input_blocks_tree", tree);
        probe.putInput("input_block_type", "minecraft:stone");
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertFalse(((String) probe.getOutput("output_error")).isBlank());
    }

    @Test
    void invalidPlacementsDoNotFallThroughToGeometry() {
        AssignProbe probe = new AssignProbe();
        List<Object> invalidOnly = new ArrayList<>();
        invalidOnly.add("not-a-placement");
        probe.putInput("input_placements", invalidOnly);
        probe.putInput("input_geometry", new BoxGeometryData(
            new Vector3d(0.5d, 0.5d, 0.5d),
            new Vector3d(0.5d, 0.5d, 0.5d)
        ));
        probe.putInput("input_block_type", "minecraft:stone");
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
        String error = (String) probe.getOutput("output_error");
        assertTrue(error.contains("Block Placements") || error.contains("placement"), error);
    }

    @Test
    void oversizedWeightsOverrideFails() {
        BlockPaletteData palette = BlockPaletteData.ofBlockIds(
            List.of("minecraft:stone", "minecraft:dirt"));
        WeightedBlockPaletteNode node = new WeightedBlockPaletteNode();
        node.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        node.setInput("input_palette", palette);
        node.setInput("input_weights", List.of(1.0d, 1.0d, 1.0d));
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
    }

    private static final class AssignProbe extends AssignBlockTypeNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }

    private static final class WeightedProbe extends WeightedBlockPaletteNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }
}

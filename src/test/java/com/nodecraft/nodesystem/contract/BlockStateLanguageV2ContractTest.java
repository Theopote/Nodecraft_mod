package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.material.block_state.ApplyBlockStateNode;
import com.nodecraft.nodesystem.nodes.material.block_state.BuildBlockStateNode;
import com.nodecraft.nodesystem.nodes.material.block_state.OrientBlockStateNode;
import com.nodecraft.nodesystem.nodes.material.block_state.StairShapeNode;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockStateData;
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
 * Block State Strict Apply & Stair Input Contract v2 (Graph V115).
 */
class BlockStateLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV115() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void applyMixedPlacementsFailsClosed() {
        ApplyProbe probe = new ApplyProbe();
        List<Object> mixed = new ArrayList<>();
        mixed.add(new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_stairs", null));
        mixed.add("bad");
        mixed.add(null);
        probe.putInput("input_placements", mixed);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
        assertFalse(((String) probe.getOutput("output_error")).isBlank());
    }

    @Test
    void applyBlankBlockIdFailsClosed() {
        ApplyBlockStateNode node = new ApplyBlockStateNode();
        node.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "  ", null)
        ));
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertTrue(((List<?>) node.getOutput("output_placements")).isEmpty());
    }

    @Test
    void applyExposesValidAndErrorPorts() {
        ApplyBlockStateNode node = new ApplyBlockStateNode();
        assertTrue(node.getOutputPorts().stream().anyMatch(p -> "output_valid".equals(p.getId())));
        assertTrue(node.getOutputPorts().stream().anyMatch(p -> "output_error".equals(p.getId())));
    }

    @Test
    void stairDirectionConnectedZeroFailsClosed() {
        StairProbe probe = new StairProbe();
        BlockStateData state = new BlockStateData();
        state.setProperty("facing", "north");
        probe.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_stairs", state)
        ));
        probe.putInput("input_direction", new Vector3d(0, 0, 0));
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
    }

    @Test
    void stairHalfConnectedBananaFailsClosed() {
        StairProbe probe = new StairProbe();
        BlockStateData state = new BlockStateData();
        state.setProperty("facing", "north");
        probe.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_stairs", state)
        ));
        probe.putInput("input_half", "banana");
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
    }

    @Test
    void stairDirectionUndrivenWithStateFacingSucceeds() {
        StairShapeNode node = new StairShapeNode();
        BlockStateData state = new BlockStateData();
        state.setProperty("facing", "east");
        state.setProperty("half", "bottom");
        node.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_stairs", state)
        ));
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals(1, out.size());
        assertEquals("minecraft:oak_stairs", out.getFirst().blockId());
    }

    @Test
    void buildPropertyConnectedValueNullFails() {
        BuildProbe probe = new BuildProbe();
        probe.putInput("input_block_type", "minecraft:oak_stairs");
        probe.putInput("input_property_name", "facing");
        probe.putInput("input_property_value", null);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertFalse(((String) probe.getOutput("output_error")).isBlank());
    }

    @Test
    void orientAcceptsVectorData() {
        OrientBlockStateNode node = new OrientBlockStateNode();
        node.setInput("input_block_type", "minecraft:oak_stairs");
        node.setInput("input_vector", VectorData.canonical(new Vector3d(1, 0, 0)));
        node.setInput("input_mode", "facing");
        node.processNode(null);
        // Registry may or may not accept facing for oak_stairs in test bootstrap;
        // VECTOR resolve must not reject VectorData as "null vector".
        String error = (String) node.getOutput("output_error");
        assertFalse("Vector required".equals(error), error);
        assertFalse("Vector must be finite".equals(error), error);
    }

    @Test
    void stairAcceptsVectorDataDirection() {
        StairProbe probe = new StairProbe();
        BlockStateData state = new BlockStateData();
        // Intentionally omit facing so Direction fallback is exercised when stairs are detected.
        probe.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_stairs", state)
        ));
        probe.putInput("input_direction", VectorData.canonical(new Vector3d(0, 0, 1)));
        probe.processNode(null);
        String error = (String) probe.getOutput("output_error");
        assertFalse("Direction connected but invalid or zero".equals(error), error);
    }

    @Test
    void stairLeavesNonStairPlacementsUnchanged() {
        StairShapeNode node = new StairShapeNode();
        BlockPlacementData stone = new BlockPlacementData(new BlockPos(1, 64, 1), "minecraft:stone", null);
        node.setInput("input_placements", List.of(stone));
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals(1, out.size());
        assertEquals("minecraft:stone", out.getFirst().blockId());
    }

    private static final class ApplyProbe extends ApplyBlockStateNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }

    private static final class StairProbe extends StairShapeNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }

    private static final class BuildProbe extends BuildBlockStateNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }
}

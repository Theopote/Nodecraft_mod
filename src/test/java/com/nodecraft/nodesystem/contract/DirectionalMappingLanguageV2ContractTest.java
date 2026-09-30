package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.material.directional_mapping.SlabStairAutofillNode;
import com.nodecraft.nodesystem.nodes.material.directional_mapping.SlopeMapNode;
import com.nodecraft.nodesystem.nodes.material.directional_mapping.TopSideBottomMapNode;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Directional Mapping Strict Sources & Normals v2 (Graph V116).
 */
class DirectionalMappingLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV116() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void mixedPlacementListFailsClosed() {
        ColumnProbe probe = new ColumnProbe();
        List<Object> mixed = new ArrayList<>();
        mixed.add(new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null));
        mixed.add("bad");
        mixed.add(null);
        probe.putInput("input_placements", mixed);
        probe.putInput("input_top", "minecraft:grass_block");
        probe.processNode(null);

        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
        assertFalse(((String) probe.getOutput("output_error")).isBlank());
    }

    @Test
    void invalidPlacementsDoNotFallThroughToGeometry() {
        ColumnProbe probe = new ColumnProbe();
        List<Object> invalidOnly = new ArrayList<>();
        invalidOnly.add("not-a-placement");
        probe.putInput("input_placements", invalidOnly);
        probe.putInput("input_geometry", new BoxGeometryData(
            new Vector3d(0.5d, 0.5d, 0.5d),
            new Vector3d(0.5d, 0.5d, 0.5d)
        ));
        probe.putInput("input_top", "minecraft:stone");
        probe.processNode(null);

        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
        String error = (String) probe.getOutput("output_error");
        assertTrue(error.contains("Block Placements") || error.contains("placement"), error);
    }

    @Test
    void zeroNormalFailsClosed() {
        SlabProbe probe = new SlabProbe();
        probe.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone", null)
        ));
        probe.putInput("input_normals", List.of(new Vector3d(0.0d, 0.0d, 0.0d)));
        probe.processNode(null);

        assertFalse((Boolean) probe.getOutput("output_valid"));
        String error = ((String) probe.getOutput("output_error")).toLowerCase(Locale.ROOT);
        assertTrue(error.contains("normal") && error.contains("non-zero"), error);
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
    }

    @Test
    void unknownOrBlankMappedBlockTypeFailsClosed() {
        ColumnProbe blank = new ColumnProbe();
        blank.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:dirt", null)
        ));
        blank.putInput("input_top", "   ");
        blank.processNode(null);
        assertFalse((Boolean) blank.getOutput("output_valid"));

        ColumnProbe nonString = new ColumnProbe();
        nonString.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:dirt", null)
        ));
        nonString.putInput("input_top", 42);
        nonString.processNode(null);
        assertFalse((Boolean) nonString.getOutput("output_valid"));

        ColumnProbe invalidId = new ColumnProbe();
        invalidId.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:dirt", null)
        ));
        invalidId.putInput("input_top", "!!!");
        invalidId.processNode(null);
        assertFalse((Boolean) invalidId.getOutput("output_valid"));
        assertTrue(((String) invalidId.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("unknown") || ((String) invalidId.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("block"), (String) invalidId.getOutput("output_error"));

        boolean registryLive = false;
        try {
            registryLive = !Registries.BLOCK.getIds().isEmpty();
        } catch (Throwable ignored) {
            registryLive = false;
        }
        Assumptions.assumeTrue(registryLive, "Live BLOCK registry required for unknown-id membership");
        assertFalse(MaterialMappingSupport.isKnownBlockId("minecraft:this_block_does_not_exist_v116"));

        ColumnProbe unknown = new ColumnProbe();
        unknown.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:dirt", null)
        ));
        unknown.putInput("input_top", "minecraft:this_block_does_not_exist_v116");
        unknown.processNode(null);
        assertFalse((Boolean) unknown.getOutput("output_valid"));
        assertTrue(((String) unknown.getOutput("output_error")).contains("Unknown block"));
    }

    @Test
    void nanAndUnorderedAnglesFailClosed() {
        SlabProbe nan = new SlabProbe();
        nan.forceAnglesForTest(Double.NaN, 35.0d);
        nan.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone", null)
        ));
        nan.putInput("input_normals", List.of(new Vector3d(0.0d, 1.0d, 0.0d)));
        nan.processNode(null);
        assertFalse((Boolean) nan.getOutput("output_valid"));
        assertTrue(((String) nan.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("angle"));

        SlabProbe unordered = new SlabProbe();
        unordered.forceAnglesForTest(40.0d, 20.0d);
        unordered.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone", null)
        ));
        unordered.putInput("input_normals", List.of(new Vector3d(0.0d, 1.0d, 0.0d)));
        unordered.processNode(null);
        assertFalse((Boolean) unordered.getOutput("output_valid"));
        String err = ((String) unordered.getOutput("output_error")).toLowerCase(Locale.ROOT);
        assertTrue(err.contains("stair") && err.contains("slab"), err);
    }

    @Test
    void columnSingleCellTopWinsSmoke() {
        TopSideBottomMapNode node = new TopSideBottomMapNode();
        BlockPlacementData cell = new BlockPlacementData(new BlockPos(1, 5, 1), "minecraft:dirt", null);
        node.setInput("input_placements", List.of(cell));
        node.setInput("input_top", "minecraft:grass_block");
        node.setInput("input_bottom", "minecraft:stone");
        node.processNode(null);

        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals(1, out.size());
        assertEquals("minecraft:grass_block", out.getFirst().blockId());
    }

    @Test
    void slopeSurfaceOnlySmoke() {
        SlopeMapNode node = new SlopeMapNode();
        BlockStateData state = new BlockStateData();
        state.setProperty("axis", "y");

        BlockPlacementData surface = new BlockPlacementData(new BlockPos(0, 5, 0), "minecraft:oak_log", state);
        BlockPlacementData interior = new BlockPlacementData(new BlockPos(0, 4, 0), "minecraft:oak_log", state);
        BlockPlacementData neighbor = new BlockPlacementData(new BlockPos(-1, 7, 0), "minecraft:oak_log", null);

        node.setInput("input_placements", List.of(surface, interior, neighbor));
        node.setInput("input_flat", "minecraft:grass_block");
        node.setInput("input_slope", "minecraft:dirt");
        node.setInput("input_steep", "minecraft:stone");
        node.processNode(null);

        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));

        BlockPlacementData remappedSurface = findAt(out, 0, 5, 0);
        assertEquals("minecraft:stone", remappedSurface.blockId());
        assertEquals("y", remappedSurface.stateData().get("axis"));

        BlockPlacementData remappedInterior = findAt(out, 0, 4, 0);
        assertEquals("minecraft:oak_log", remappedInterior.blockId());
    }

    private static BlockPlacementData findAt(List<BlockPlacementData> placements, int x, int y, int z) {
        return placements.stream()
            .filter(p -> p.pos() != null && p.pos().getX() == x && p.pos().getY() == y && p.pos().getZ() == z)
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing placement at " + x + "," + y + "," + z));
    }

    private static final class ColumnProbe extends TopSideBottomMapNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }

    private static final class SlabProbe extends SlabStairAutofillNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }
}

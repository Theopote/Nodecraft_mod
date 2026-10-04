package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.material.surface_aging.CrackPatternNode;
import com.nodecraft.nodesystem.nodes.material.surface_aging.MossGrowthNode;
import com.nodecraft.nodesystem.nodes.material.surface_aging.WeatheringNode;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Surface Aging Strict Sources & Spatial Sampling v2 (current graph format).
 */
class SurfaceAgingLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsCurrent() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void mixedPlacementListFailsClosed() {
        WeatheringProbe probe = new WeatheringProbe();
        List<Object> mixed = new ArrayList<>();
        mixed.add(new BlockPlacementData(new net.minecraft.util.math.BlockPos(0, 0, 0), "minecraft:oak_planks", null));
        mixed.add("bad");
        mixed.add(null);
        probe.putInput("input_placements", mixed);
        probe.putInput("input_aged_block", "minecraft:cobblestone");
        probe.processNode(null);

        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
        assertFalse(((String) probe.getOutput("output_error")).isBlank());
    }

    @Test
    void invalidPlacementsDoNotFallThroughToGeometry() {
        MossProbe probe = new MossProbe();
        List<Object> invalidOnly = new ArrayList<>();
        invalidOnly.add("not-a-placement");
        probe.putInput("input_placements", invalidOnly);
        probe.putInput("input_geometry", new BoxGeometryData(
            new Vector3d(0.5d, 0.5d, 0.5d),
            new Vector3d(0.5d, 0.5d, 0.5d)
        ));
        probe.putInput("input_base", "minecraft:stone");
        probe.putInput("input_moss", "minecraft:moss_block");
        probe.processNode(null);

        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
        String error = (String) probe.getOutput("output_error");
        assertTrue(error.contains("Block Placements") || error.contains("placement"), error);
    }

    @Test
    void duplicatePositionsFailClosed() {
        WeatheringNode node = new WeatheringNode();
        node.setInput("input_placements", List.of(
            new BlockPlacementData(new net.minecraft.util.math.BlockPos(0, 0, 0), "minecraft:oak_planks", null),
            new BlockPlacementData(new net.minecraft.util.math.BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        node.setInput("input_aged_block", "minecraft:cobblestone");
        node.setInput("input_amount", 1.0d);
        node.processNode(null);

        assertFalse((Boolean) node.getOutput("output_valid"));
        assertTrue(((String) node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("duplicate"));
    }

    @Test
    void extremeRelativeCoordsDoNotWrapIntArithmetic() {
        net.minecraft.util.math.BlockPos origin = new net.minecraft.util.math.BlockPos(-2_000_000_000, 0, -2_000_000_000);
        net.minecraft.util.math.BlockPos low = new net.minecraft.util.math.BlockPos(-2_000_000_000, 0, -2_000_000_000);
        net.minecraft.util.math.BlockPos high = new net.minecraft.util.math.BlockPos(2_000_000_000, 0, 2_000_000_000);

        WeatheringNode weathering = new WeatheringNode();
        weathering.setInput("input_placements", List.of(
            new BlockPlacementData(low, "minecraft:oak_planks", null),
            new BlockPlacementData(high, "minecraft:oak_planks", null)
        ));
        weathering.setInput("input_aged_block", "minecraft:cobblestone");
        weathering.setInput("input_amount", 1.0d);
        weathering.setInput("input_seed", 0);
        weathering.setInput("input_aging_origin", origin);
        weathering.processNode(null);
        assertTrue((Boolean) weathering.getOutput("output_valid"));
        assertEquals(2, (Integer) weathering.getOutput("output_affected_count"));

        MossGrowthNode moss = new MossGrowthNode();
        moss.setInput("input_placements", List.of(
            new BlockPlacementData(low, "minecraft:oak_planks", null),
            new BlockPlacementData(high, "minecraft:oak_planks", null)
        ));
        moss.setInput("input_moss", "minecraft:moss_block");
        moss.setInput("input_amount", 1.0d);
        moss.setInput("input_seed", 0);
        moss.setInput("input_aging_origin", origin);
        moss.processNode(null);
        assertTrue((Boolean) moss.getOutput("output_valid"));

        CrackPatternNode crack = new CrackPatternNode();
        crack.setInput("input_placements", List.of(
            new BlockPlacementData(low, "minecraft:oak_planks", null),
            new BlockPlacementData(high, "minecraft:oak_planks", null)
        ));
        crack.setInput("input_crack", "minecraft:cracked_stone_bricks");
        crack.setInput("input_amount", 1.0d);
        crack.setInput("input_seed", 0);
        crack.setInput("input_aging_origin", origin);
        crack.processNode(null);
        assertTrue((Boolean) crack.getOutput("output_valid"));
        assertEquals(2, (Integer) crack.getOutput("output_affected_count"));
    }

    @Test
    void drivenInvalidAmountFailsClosed() {
        MossProbe invalidType = new MossProbe();
        invalidType.putInput("input_placements", List.of(
            new BlockPlacementData(new net.minecraft.util.math.BlockPos(0, 1, 0), "minecraft:oak_planks", null)
        ));
        invalidType.putInput("input_moss", "minecraft:moss_block");
        invalidType.putInput("input_amount", "not-a-number");
        invalidType.processNode(null);
        assertFalse((Boolean) invalidType.getOutput("output_valid"));
        assertEquals(0, (Integer) invalidType.getOutput("output_affected_count"));

        MossProbe nanAmount = new MossProbe();
        nanAmount.putInput("input_placements", List.of(
            new BlockPlacementData(new net.minecraft.util.math.BlockPos(0, 1, 0), "minecraft:oak_planks", null)
        ));
        nanAmount.putInput("input_moss", "minecraft:moss_block");
        nanAmount.putInput("input_amount", Double.NaN);
        nanAmount.processNode(null);
        assertFalse((Boolean) nanAmount.getOutput("output_valid"));
        assertEquals(0, (Integer) nanAmount.getOutput("output_affected_count"));
    }

    @Test
    void drivenInvalidOriginFailsClosed() {
        WeatheringProbe pointOrigin = new WeatheringProbe();
        pointOrigin.putInput("input_placements", List.of(
            new BlockPlacementData(new net.minecraft.util.math.BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        pointOrigin.putInput("input_aged_block", "minecraft:cobblestone");
        pointOrigin.putInput("input_aging_origin", new PointData(0, 0, 0));
        pointOrigin.processNode(null);
        assertFalse((Boolean) pointOrigin.getOutput("output_valid"));
        assertTrue(((String) pointOrigin.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("block_pos"));

        WeatheringProbe vectorOrigin = new WeatheringProbe();
        vectorOrigin.putInput("input_placements", List.of(
            new BlockPlacementData(new net.minecraft.util.math.BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        vectorOrigin.putInput("input_aged_block", "minecraft:cobblestone");
        vectorOrigin.putInput("input_aging_origin", new Vector3d(0, 0, 0));
        vectorOrigin.processNode(null);
        assertFalse((Boolean) vectorOrigin.getOutput("output_valid"));
    }

    @Test
    void drivenInvalidBlockTypeFailsClosed() {
        MossProbe blank = new MossProbe();
        blank.putInput("input_placements", List.of(
            new BlockPlacementData(new net.minecraft.util.math.BlockPos(0, 1, 0), "minecraft:oak_planks", null)
        ));
        blank.putInput("input_moss", "   ");
        blank.processNode(null);
        assertFalse((Boolean) blank.getOutput("output_valid"));
        assertTrue(((String) blank.getOutput("output_error")).contains("Block Type"));

        boolean registryLive = false;
        try {
            registryLive = !Registries.BLOCK.getIds().isEmpty();
        } catch (Throwable ignored) {
            registryLive = false;
        }
        Assumptions.assumeTrue(registryLive, "Live BLOCK registry required for unknown-id membership");
        assertFalse(MaterialMappingSupport.isKnownBlockId("minecraft:this_block_does_not_exist_v119"));

        MossProbe unknown = new MossProbe();
        unknown.putInput("input_placements", List.of(
            new BlockPlacementData(new net.minecraft.util.math.BlockPos(0, 1, 0), "minecraft:oak_planks", null)
        ));
        unknown.putInput("input_moss", "minecraft:this_block_does_not_exist_v119");
        unknown.processNode(null);
        assertFalse((Boolean) unknown.getOutput("output_valid"));
        assertTrue(((String) unknown.getOutput("output_error")).contains("Unknown block"));
    }

    @Test
    void topologyNotCorruptedByStrictSources() {
        List<BlockPlacementData> cube = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    cube.add(new BlockPlacementData(
                        new net.minecraft.util.math.BlockPos(x, y, z), "minecraft:oak_planks", null));
                }
            }
        }

        WeatheringNode node = new WeatheringNode();
        node.setInput("input_placements", cube);
        node.setInput("input_aged_block", "minecraft:cobblestone");
        node.setInput("input_amount", 1.0d);
        node.setInput("input_seed", 0);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals("minecraft:oak_planks", findAt(out, 1, 1, 1).blockId());
        assertEquals("minecraft:cobblestone", findAt(out, 0, 1, 1).blockId());
        assertEquals(26, (Integer) node.getOutput("output_affected_count"));
    }

    @Test
    void remappedIncompatibleStateFailsClosedWhenRegistryPopulated() {
        Assumptions.assumeTrue(
            MaterialMappingSupport.isBlockRegistryPopulated(),
            "Live BLOCK registry required for state compatibility"
        );
        BlockStateData state = new BlockStateData().withProperty("axis", "y");
        WeatheringNode node = new WeatheringNode();
        node.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_log", state)
        ));
        node.setInput("input_aged_block", "minecraft:stone");
        node.setInput("input_amount", 1.0d);
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertTrue(((List<?>) node.getOutput("output_placements")).isEmpty());
        assertEquals(0, (Integer) node.getOutput("output_affected_count"));
    }

    @Test
    void compatibleStairsRemapPreservesFacing() {
        BlockStateData state = new BlockStateData()
            .withProperty("facing", "east")
            .withProperty("half", "top");
        WeatheringNode node = new WeatheringNode();
        node.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_stairs", state)
        ));
        node.setInput("input_aged_block", "minecraft:cobblestone_stairs");
        node.setInput("input_amount", 1.0d);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals("minecraft:cobblestone_stairs", out.getFirst().blockId());
        assertEquals("east", out.getFirst().stateData().get("facing"));
        assertEquals("top", out.getFirst().stateData().get("half"));
    }

    @Test
    void maxIntEastNeighborDoesNotWrapToMinInt() {
        BlockPos atMax = new BlockPos(Integer.MAX_VALUE, 0, 0);
        BlockPos wrapEast = new BlockPos(Integer.MIN_VALUE, 0, 0);
        WeatheringNode node = new WeatheringNode();
        node.setInput("input_placements", List.of(
            new BlockPlacementData(atMax, "minecraft:oak_planks", null),
            new BlockPlacementData(atMax.up(), "minecraft:oak_planks", null),
            new BlockPlacementData(atMax.down(), "minecraft:oak_planks", null),
            new BlockPlacementData(atMax.north(), "minecraft:oak_planks", null),
            new BlockPlacementData(atMax.south(), "minecraft:oak_planks", null),
            new BlockPlacementData(atMax.west(), "minecraft:oak_planks", null),
            new BlockPlacementData(wrapEast, "minecraft:oak_planks", null)
        ));
        node.setInput("input_aged_block", "minecraft:cobblestone");
        node.setInput("input_amount", 1.0d);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals("minecraft:cobblestone", findAt(out, Integer.MAX_VALUE, 0, 0).blockId());
    }

    @Test
    void drivenInvalidSeedFailsClosed() {
        WeatheringProbe banana = new WeatheringProbe();
        banana.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        banana.putInput("input_aged_block", "minecraft:cobblestone");
        banana.putInput("input_seed", "banana");
        banana.processNode(null);
        assertFalse((Boolean) banana.getOutput("output_valid"));
        assertTrue(((List<?>) banana.getOutput("output_placements")).isEmpty());
        assertEquals(0, (Integer) banana.getOutput("output_affected_count"));

        WeatheringProbe decimal = new WeatheringProbe();
        decimal.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        decimal.putInput("input_aged_block", "minecraft:cobblestone");
        decimal.putInput("input_seed", 42.0d);
        decimal.processNode(null);
        assertFalse((Boolean) decimal.getOutput("output_valid"));
        assertEquals(0, (Integer) decimal.getOutput("output_affected_count"));
    }

    @Test
    void perNodeSeedSaltDecorrelatesFamiliesAtSeedZero() {
        List<BlockPlacementData> slab = new ArrayList<>();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                slab.add(new BlockPlacementData(new BlockPos(x, 0, z), "minecraft:oak_planks", null));
            }
        }
        WeatheringNode weathering = new WeatheringNode();
        weathering.setInput("input_placements", slab);
        weathering.setInput("input_aged_block", "minecraft:cobblestone");
        weathering.setInput("input_amount", 0.5d);
        weathering.setInput("input_seed", 0);
        weathering.processNode(null);
        CrackPatternNode crack = new CrackPatternNode();
        crack.setInput("input_placements", slab);
        crack.setInput("input_crack", "minecraft:cobblestone");
        crack.setInput("input_amount", 0.5d);
        crack.setInput("input_seed", 0);
        crack.processNode(null);
        assertTrue((Boolean) weathering.getOutput("output_valid"));
        assertTrue((Boolean) crack.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> weatherOut = assertInstanceOf(List.class, weathering.getOutput("output_placements"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> crackOut = assertInstanceOf(List.class, crack.getOutput("output_placements"));
        assertNotEquals(
            weatherOut.stream().map(BlockPlacementData::blockId).toList(),
            crackOut.stream().map(BlockPlacementData::blockId).toList()
        );
    }

    private static BlockPlacementData findAt(List<BlockPlacementData> placements, int x, int y, int z) {
        return placements.stream()
            .filter(p -> p.pos() != null && p.pos().getX() == x && p.pos().getY() == y && p.pos().getZ() == z)
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing placement at " + x + "," + y + "," + z));
    }

    private static final class WeatheringProbe extends WeatheringNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }

    private static final class MossProbe extends MossGrowthNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }
}

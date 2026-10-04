package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.material.pattern_mapping.CheckerPatternMapNode;
import com.nodecraft.nodesystem.nodes.material.pattern_mapping.GridPatternMapNode;
import com.nodecraft.nodesystem.nodes.material.pattern_mapping.StripePatternMapNode;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.BrickPatternMapping;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pattern Mapping Strict Coordinates & Source Contract v2 (current graph format).
 */
class PatternMappingLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsCurrent() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void mixedPlacementListFailsClosed() {
        CheckerProbe probe = new CheckerProbe();
        List<Object> mixed = new ArrayList<>();
        mixed.add(new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null));
        mixed.add("bad");
        mixed.add(null);
        probe.putInput("input_placements", mixed);
        probe.putInput("input_primary", "minecraft:dirt");
        probe.putInput("input_secondary", "minecraft:cobblestone");
        probe.processNode(null);

        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
        assertFalse(((String) probe.getOutput("output_error")).isBlank());
    }

    @Test
    void invalidPlacementsDoNotFallThroughToGeometry() {
        CheckerProbe probe = new CheckerProbe();
        List<Object> invalidOnly = new ArrayList<>();
        invalidOnly.add("not-a-placement");
        probe.putInput("input_placements", invalidOnly);
        probe.putInput("input_geometry", new BoxGeometryData(
            new Vector3d(0.5d, 0.5d, 0.5d),
            new Vector3d(0.5d, 0.5d, 0.5d)
        ));
        probe.putInput("input_primary", "minecraft:stone");
        probe.putInput("input_secondary", "minecraft:dirt");
        probe.processNode(null);

        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
        String error = (String) probe.getOutput("output_error");
        assertTrue(error.contains("Block Placements") || error.contains("placement"), error);
    }

    @Test
    void extremeRelativeCoordsDoNotWrapIntArithmetic() {
        // Origin at -2e9, positions at ±2e9 → relative spans beyond int range
        BlockPos origin = new BlockPos(-2_000_000_000, 0, -2_000_000_000);
        BlockPos low = new BlockPos(-2_000_000_000, 0, -2_000_000_000);
        BlockPos high = new BlockPos(2_000_000_000, 0, 2_000_000_000);

        CheckerPatternMapNode checker = new CheckerPatternMapNode();
        checker.setInput("input_placements", List.of(
            new BlockPlacementData(low, "minecraft:oak_planks", null),
            new BlockPlacementData(high, "minecraft:oak_planks", null)
        ));
        checker.setInput("input_primary", "minecraft:dirt");
        checker.setInput("input_secondary", "minecraft:cobblestone");
        checker.setInput("input_pattern_origin", origin);
        checker.processNode(null);
        assertTrue((Boolean) checker.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> checkerOut = assertInstanceOf(List.class, checker.getOutput("output_placements"));
        // Relative (0,0,0) even → primary; relative (4e9,0,4e9) even XOR → primary
        assertEquals("minecraft:dirt", findAt(checkerOut, -2_000_000_000, 0, -2_000_000_000).blockId());
        assertEquals("minecraft:dirt", findAt(checkerOut, 2_000_000_000, 0, 2_000_000_000).blockId());

        StripePatternMapNode stripe = new StripePatternMapNode();
        stripe.setStripeWidth(2);
        stripe.setAxis(StripePatternMapNode.StripeAxis.X);
        stripe.setInput("input_placements", List.of(
            new BlockPlacementData(low, "minecraft:oak_planks", null),
            new BlockPlacementData(high, "minecraft:oak_planks", null)
        ));
        stripe.setInput("input_primary", "minecraft:dirt");
        stripe.setInput("input_secondary", "minecraft:cobblestone");
        stripe.setInput("input_pattern_origin", origin);
        stripe.processNode(null);
        assertTrue((Boolean) stripe.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> stripeOut = assertInstanceOf(List.class, stripe.getOutput("output_placements"));
        assertEquals(2, stripeOut.size());
        // floorDiv(0,2)%2==0 primary; floorDiv(4e9,2)%2==0 primary
        assertEquals("minecraft:dirt", findAt(stripeOut, -2_000_000_000, 0, -2_000_000_000).blockId());
        assertEquals("minecraft:dirt", findAt(stripeOut, 2_000_000_000, 0, 2_000_000_000).blockId());

        GridPatternMapNode grid = new GridPatternMapNode();
        grid.setGridSize(4);
        grid.setLineWidth(1);
        grid.setInput("input_placements", List.of(
            new BlockPlacementData(low, "minecraft:oak_planks", null),
            new BlockPlacementData(new BlockPos(-2_000_000_000 + 1, 0, -2_000_000_000 + 1), "minecraft:oak_planks", null)
        ));
        grid.setInput("input_frame", "minecraft:stone");
        grid.setInput("input_fill", "minecraft:dirt");
        grid.setInput("input_pattern_origin", origin);
        grid.processNode(null);
        assertTrue((Boolean) grid.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> gridOut = assertInstanceOf(List.class, grid.getOutput("output_placements"));
        assertEquals("minecraft:stone", findAt(gridOut, -2_000_000_000, 0, -2_000_000_000).blockId());
        assertEquals("minecraft:dirt", findAt(gridOut, -2_000_000_000 + 1, 0, -2_000_000_000 + 1).blockId());
    }

    @Test
    void brickLongSafeAutoAndStagger() {
        assertEquals(
            BrickPatternMapping.Axis.Z,
            BrickPatternMapping.resolveAxisFromSpans(0L, 1L, -2_000_000_000L, 2_000_000_000L)
        );
        long along = Integer.MAX_VALUE;
        int index = BrickPatternMapping.brickIndex(along, 1, 0, 4, 1, BrickPatternMapping.Axis.X);
        assertEquals((int) Math.floorDiv(along + 2L, 4L), index);
        assertNotEquals(
            BrickPatternMapping.brickIndex(along, 0, 0, 4, 1, BrickPatternMapping.Axis.X),
            index
        );
    }

    @Test
    void drivenInvalidOriginFailsClosed() {
        CheckerProbe nullOrigin = new CheckerProbe();
        nullOrigin.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        nullOrigin.putInput("input_primary", "minecraft:dirt");
        nullOrigin.putInput("input_secondary", "minecraft:cobblestone");
        // Mark driven with wrong type (null alone is undriven)
        nullOrigin.putInput("input_pattern_origin", new PointData(0, 0, 0));
        nullOrigin.processNode(null);
        assertFalse((Boolean) nullOrigin.getOutput("output_valid"));
        assertTrue(((String) nullOrigin.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("block_pos"));
    }

    @Test
    void undrivenOriginDefaultsToWorldOriginSmoke() {
        CheckerPatternMapNode missing = new CheckerPatternMapNode();
        missing.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        missing.setInput("input_primary", "minecraft:dirt");
        missing.setInput("input_secondary", "minecraft:cobblestone");
        missing.processNode(null);
        assertTrue((Boolean) missing.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, missing.getOutput("output_placements"));
        assertEquals("minecraft:dirt", out.getFirst().blockId());
    }

    @Test
    void checkerIs3dAndGridIgnoresYSmoke() {
        CheckerPatternMapNode checker = new CheckerPatternMapNode();
        checker.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null),
            new BlockPlacementData(new BlockPos(0, 1, 0), "minecraft:oak_planks", null)
        ));
        checker.setInput("input_primary", "minecraft:dirt");
        checker.setInput("input_secondary", "minecraft:cobblestone");
        checker.processNode(null);
        assertTrue((Boolean) checker.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> checkerOut = assertInstanceOf(List.class, checker.getOutput("output_placements"));
        assertNotEquals(
            findAt(checkerOut, 0, 0, 0).blockId(),
            findAt(checkerOut, 0, 1, 0).blockId()
        );

        GridPatternMapNode grid = new GridPatternMapNode();
        grid.setGridSize(4);
        grid.setLineWidth(1);
        grid.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null),
            new BlockPlacementData(new BlockPos(0, 5, 0), "minecraft:oak_planks", null)
        ));
        grid.setInput("input_frame", "minecraft:stone");
        grid.setInput("input_fill", "minecraft:dirt");
        grid.processNode(null);
        assertTrue((Boolean) grid.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> gridOut = assertInstanceOf(List.class, grid.getOutput("output_placements"));
        assertEquals(findAt(gridOut, 0, 0, 0).blockId(), findAt(gridOut, 0, 5, 0).blockId());
    }

    @Test
    void connectedInvalidPrimaryFailsClosedUnconnectedPreservesSource() {
        List<BlockPlacementData> source = List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        );

        CheckerProbe bang = new CheckerProbe();
        bang.putInput("input_placements", source);
        bang.putInput("input_primary", "!!!");
        bang.putInput("input_secondary", "minecraft:cobblestone");
        bang.processNode(null);
        assertFalse((Boolean) bang.getOutput("output_valid"));
        assertTrue(((List<?>) bang.getOutput("output_placements")).isEmpty());

        CheckerProbe wrongType = new CheckerProbe();
        wrongType.putInput("input_placements", source);
        wrongType.putInput("input_primary", 42);
        wrongType.putInput("input_secondary", "minecraft:cobblestone");
        wrongType.processNode(null);
        assertFalse((Boolean) wrongType.getOutput("output_valid"));
        assertTrue(((List<?>) wrongType.getOutput("output_placements")).isEmpty());

        CheckerPatternMapNode unconnected = new CheckerPatternMapNode();
        unconnected.setInput("input_placements", source);
        unconnected.setInput("input_secondary", "minecraft:cobblestone");
        unconnected.processNode(null);
        assertTrue((Boolean) unconnected.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, unconnected.getOutput("output_placements"));
        assertEquals("minecraft:oak_planks", out.getFirst().blockId());
    }

    @Test
    void remappedIncompatibleStateFailsClosedWhenRegistryPopulated() {
        Assumptions.assumeTrue(
            MaterialMappingSupport.isBlockRegistryPopulated(),
            "Live BLOCK registry required for state compatibility"
        );
        BlockStateData state = new BlockStateData().withProperty("axis", "y");
        CheckerPatternMapNode node = new CheckerPatternMapNode();
        node.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_log", state)
        ));
        node.setInput("input_primary", "minecraft:stone");
        node.setInput("input_secondary", "minecraft:stone");
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertTrue(((List<?>) node.getOutput("output_placements")).isEmpty());
    }

    @Test
    void compatibleStairsRemapPreservesFacing() {
        BlockStateData state = new BlockStateData()
            .withProperty("facing", "east")
            .withProperty("half", "top");
        CheckerPatternMapNode node = new CheckerPatternMapNode();
        node.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_stairs", state)
        ));
        node.setInput("input_primary", "minecraft:cobblestone_stairs");
        node.setInput("input_secondary", "minecraft:cobblestone_stairs");
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals("minecraft:cobblestone_stairs", out.getFirst().blockId());
        assertEquals("east", out.getFirst().stateData().get("facing"));
        assertEquals("top", out.getFirst().stateData().get("half"));
    }

    @Test
    void doublePropertyRestoreIsIgnoredIntegerZeroFailsAtProcess() {
        StripePatternMapNode stripe = new StripePatternMapNode();
        stripe.setNodeState(java.util.Map.of("stripeWidth", 1.9d));
        assertEquals(2, stripe.getStripeWidth());
        stripe.setNodeState(java.util.Map.of("stripeWidth", 0));
        stripe.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        stripe.setInput("input_primary", "minecraft:dirt");
        stripe.processNode(null);
        assertFalse((Boolean) stripe.getOutput("output_valid"));
        assertTrue(((String) stripe.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("width"));
    }

    private static BlockPlacementData findAt(List<BlockPlacementData> placements, int x, int y, int z) {
        return placements.stream()
            .filter(p -> p.pos() != null && p.pos().getX() == x && p.pos().getY() == y && p.pos().getZ() == z)
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing placement at " + x + "," + y + "," + z));
    }

    private static final class CheckerProbe extends CheckerPatternMapNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }
}

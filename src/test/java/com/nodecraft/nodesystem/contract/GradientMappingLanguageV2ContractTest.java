package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.DistanceBasedMaterialNode;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.GradientRampMapNode;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.HeightGradientMapNode;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.NoiseMaterialNode;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gradient Mapping Strict Source & Finite Domain v2 (Graph V117).
 */
class GradientMappingLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV117() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void mixedPlacementListFailsClosed() {
        HeightProbe probe = new HeightProbe();
        List<Object> mixed = new ArrayList<>();
        mixed.add(new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null));
        mixed.add("bad");
        mixed.add(null);
        probe.putInput("input_placements", mixed);
        probe.putInput("input_bottom", "minecraft:dirt");
        probe.processNode(null);

        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
        assertFalse(((String) probe.getOutput("output_error")).isBlank());
    }

    @Test
    void invalidPlacementsDoNotFallThroughToGeometry() {
        HeightProbe probe = new HeightProbe();
        List<Object> invalidOnly = new ArrayList<>();
        invalidOnly.add("not-a-placement");
        probe.putInput("input_placements", invalidOnly);
        probe.putInput("input_geometry", new BoxGeometryData(
            new Vector3d(0.5d, 0.5d, 0.5d),
            new Vector3d(0.5d, 0.5d, 0.5d)
        ));
        probe.putInput("input_bottom", "minecraft:stone");
        probe.processNode(null);

        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_placements")).isEmpty());
        String error = (String) probe.getOutput("output_error");
        assertTrue(error.contains("Block Placements") || error.contains("placement"), error);
    }

    @Test
    void extremeHeightSpanDoesNotInvertFromIntOverflow() {
        HeightGradientMapNode node = new HeightGradientMapNode();
        // Defaults: lower=0.30, middle=0.70, upper=0.90 — so t≈0 → Bottom, t≈1 → Peak
        node.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, -2_000_000_000, 0), "minecraft:oak_planks", null),
            new BlockPlacementData(new BlockPos(0, 2_000_000_000, 0), "minecraft:oak_planks", null)
        ));
        node.setInput("input_bottom", "minecraft:dirt");
        node.setInput("input_peak", "minecraft:snow_block");
        node.processNode(null);

        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals("minecraft:dirt", findAt(out, 0, -2_000_000_000, 0).blockId());
        assertEquals("minecraft:snow_block", findAt(out, 0, 2_000_000_000, 0).blockId());

        GradientRampMapNode ramp = new GradientRampMapNode();
        ramp.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(1, -2_000_000_000, 1), "minecraft:oak_planks", null),
            new BlockPlacementData(new BlockPos(1, 2_000_000_000, 1), "minecraft:oak_planks", null)
        ));
        ramp.setInput("input_palette", BlockPaletteData.ofBlockIds(List.of(
            "minecraft:dirt",
            "minecraft:stone",
            "minecraft:snow_block"
        )));
        ramp.processNode(null);
        assertTrue((Boolean) ramp.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> rampOut = assertInstanceOf(List.class, ramp.getOutput("output_placements"));
        assertEquals("minecraft:dirt", findAt(rampOut, 1, -2_000_000_000, 1).blockId());
        assertEquals("minecraft:snow_block", findAt(rampOut, 1, 2_000_000_000, 1).blockId());
    }

    @Test
    void distanceDomainOverflowFailsClosed() {
        DistanceProbe probe = new DistanceProbe();
        probe.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone", null)
        ));
        probe.putInput("input_min_distance", -Double.MAX_VALUE);
        probe.putInput("input_max_distance", Double.MAX_VALUE);
        probe.putInput("input_reference_point", new PointData(0, 0, 0));
        probe.putInput("input_palette", BlockPaletteData.ofBlockIds(List.of("minecraft:dirt", "minecraft:stone")));
        probe.processNode(null);

        assertFalse((Boolean) probe.getOutput("output_valid"));
        String error = ((String) probe.getOutput("output_error")).toLowerCase(Locale.ROOT);
        assertTrue(error.contains("finite") || error.contains("width") || error.contains("domain"), error);
    }

    @Test
    void drivenNonDoubleOptionalPortsFailClosed() {
        NoiseProbe noise = new NoiseProbe();
        noise.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone", null)
        ));
        noise.putInput("input_threshold_low", 0);
        noise.processNode(null);
        assertFalse((Boolean) noise.getOutput("output_valid"));

        DistanceProbe distance = new DistanceProbe();
        distance.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone", null)
        ));
        distance.putInput("input_min_distance", 0);
        distance.putInput("input_max_distance", 16.0d);
        distance.putInput("input_reference_point", new PointData(0, 0, 0));
        distance.processNode(null);
        assertFalse((Boolean) distance.getOutput("output_valid"));
    }

    @Test
    void heightSingleYUsesBottomSmoke() {
        HeightGradientMapNode single = new HeightGradientMapNode();
        single.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(1, 5, 1), "minecraft:stone", null)
        ));
        single.setInput("input_bottom", "minecraft:dirt");
        single.setInput("input_peak", "minecraft:snow_block");
        single.processNode(null);
        assertTrue((Boolean) single.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, single.getOutput("output_placements"));
        assertEquals("minecraft:dirt", out.getFirst().blockId());
    }

    @Test
    void heightPaletteEmptyPreservesPlacementsSmoke() {
        GradientRampMapNode node = new GradientRampMapNode();
        BlockPlacementData placement = new BlockPlacementData(new BlockPos(2, 4, 2), "minecraft:oak_planks", null);
        node.setInput("input_placements", List.of(placement));
        node.setInput("input_palette", BlockPaletteData.empty());
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals("minecraft:oak_planks", out.getFirst().blockId());
    }

    private static BlockPlacementData findAt(List<BlockPlacementData> placements, int x, int y, int z) {
        return placements.stream()
            .filter(p -> p.pos() != null && p.pos().getX() == x && p.pos().getY() == y && p.pos().getZ() == z)
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing placement at " + x + "," + y + "," + z));
    }

    private static final class HeightProbe extends HeightGradientMapNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }

    private static final class NoiseProbe extends NoiseMaterialNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }

    private static final class DistanceProbe extends DistanceBasedMaterialNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }
}

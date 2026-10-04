package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.DistanceBasedMaterialNode;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.GradientMaterialUtils;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.GradientRampMapNode;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.HeightGradientMapNode;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.NoiseMaterialNode;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.SdfDrivenMaterialNode;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gradient Mapping Strict Source & Finite Domain v2.
 */
class GradientMappingLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsCurrent() {
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
        assertTrue(error.contains("finite") || error.contains("width") || error.contains("domain")
            || error.contains("min"), error);
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

    @Test
    void connectedInvalidPaletteFailsClosedUnconnectedPreserves() {
        RampProbe connectedInvalid = new RampProbe();
        connectedInvalid.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        connectedInvalid.putInput("input_palette", "not-a-palette");
        connectedInvalid.processNode(null);
        assertFalse((Boolean) connectedInvalid.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> invalidOut = assertInstanceOf(List.class, connectedInvalid.getOutput("output_placements"));
        assertTrue(invalidOut.isEmpty());

        GradientRampMapNode unconnected = new GradientRampMapNode();
        unconnected.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        unconnected.processNode(null);
        assertTrue((Boolean) unconnected.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> preserved = assertInstanceOf(List.class, unconnected.getOutput("output_placements"));
        assertEquals("minecraft:oak_planks", preserved.getFirst().blockId());
    }

    @Test
    void heightBandAndFallbackConnectedInvalidFailClosed() {
        HeightProbe height = new HeightProbe();
        height.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone", null)
        ));
        height.putInput("input_bottom", 0);
        height.processNode(null);
        assertFalse((Boolean) height.getOutput("output_valid"));

        NoiseProbe noise = new NoiseProbe();
        noise.putInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone", null)
        ));
        noise.putInput("input_fallback_block", 0);
        noise.processNode(null);
        assertFalse((Boolean) noise.getOutput("output_valid"));
    }

    @Test
    void pickByNormalizedRejectsNaN() {
        GradientMaterialUtils.PickResult nan = GradientMaterialUtils.pickByNormalized(
            BlockPaletteData.ofBlockIds(List.of("minecraft:stone", "minecraft:dirt")),
            Double.NaN,
            "minecraft:oak_planks"
        );
        assertFalse(nan.valid());
        GradientMaterialUtils.PickResult inf = GradientMaterialUtils.pickByNormalized(
            BlockPaletteData.empty(),
            Double.POSITIVE_INFINITY,
            "minecraft:oak_planks"
        );
        assertFalse(inf.valid());
        GradientMaterialUtils.PickResult emptyFinite = GradientMaterialUtils.pickByNormalized(
            BlockPaletteData.empty(),
            0.5d,
            "minecraft:oak_planks"
        );
        assertTrue(emptyFinite.valid());
        assertEquals("minecraft:oak_planks", emptyFinite.blockId());
    }

    @Test
    void distanceNegativeMinFailsClosed() {
        DistanceBasedMaterialNode node = new DistanceBasedMaterialNode();
        node.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone", null)
        ));
        node.setInput("input_min_distance", -1.0d);
        node.setInput("input_max_distance", 10.0d);
        node.setInput("input_reference_point", new PointData(0, 0, 0));
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertTrue(((String) node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("min"));
    }

    @Test
    void sdfArithmeticOverflowFailsClosed() {
        SdfDrivenMaterialNode node = new SdfDrivenMaterialNode();
        node.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone", null)
        ));
        node.setInput("input_sdf", (com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData) point -> 1.0e308d);
        node.setInput("input_center", -1.0e308d);
        node.setInput("input_half_width", 1.0d);
        node.setInput("input_palette", BlockPaletteData.ofBlockIds(List.of("minecraft:dirt", "minecraft:stone")));
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        List<?> distances = assertInstanceOf(List.class, node.getOutput("output_distances"));
        List<?> weights = assertInstanceOf(List.class, node.getOutput("output_weights"));
        assertTrue(distances.isEmpty());
        assertTrue(weights.isEmpty());
    }

    @Test
    void noiseOctaveRestoreAndWorkBudgetFailClosed() {
        NoiseMaterialNode restored = new NoiseMaterialNode();
        restored.setInput("input_placements", List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone", null)
        ));
        restored.setNodeState(java.util.Map.of("octaves", GenerationLimits.MAX_MATERIAL_NOISE_OCTAVES + 1));
        restored.processNode(null);
        assertFalse((Boolean) restored.getOutput("output_valid"));
        assertTrue(((String) restored.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("octave"));

        NoiseMaterialNode work = new NoiseMaterialNode();
        work.setOctaves(GenerationLimits.MAX_MATERIAL_NOISE_OCTAVES);
        int count = (int) (GenerationLimits.MAX_MATERIAL_SAMPLE_WORK / GenerationLimits.MAX_MATERIAL_NOISE_OCTAVES) + 1;
        List<BlockPlacementData> many = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            many.add(new BlockPlacementData(new BlockPos(i, 0, 0), "minecraft:stone", null));
        }
        work.setInput("input_placements", many);
        work.processNode(null);
        assertFalse((Boolean) work.getOutput("output_valid"));
        assertTrue(((String) work.getOutput("output_error")).contains("MAX_MATERIAL_SAMPLE_WORK"));
    }

    private static BlockPlacementData findAt(List<BlockPlacementData> placements, int x, int y, int z) {
        return placements.stream()
            .filter(p -> p.pos() != null && Objects.requireNonNull(p.pos()).getX() == x && Objects.requireNonNull(p.pos()).getY() == y && Objects.requireNonNull(p.pos()).getZ() == z)
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing placement at " + x + "," + y + "," + z));
    }

    private static final class RampProbe extends GradientRampMapNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
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

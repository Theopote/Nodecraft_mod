package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementSubmitPlanTest {

    private static void assumeBlockRegistryLive() {
        boolean registryLive = false;
        try {
            registryLive = !Registries.BLOCK.getIds().isEmpty()
                    && BlockStateResolver.resolveDefault("minecraft:stone") != null;
        } catch (Throwable ignored) {
            registryLive = false;
        }
        Assumptions.assumeTrue(registryLive, "Live BLOCK registry required for PlacementSubmitPlan");
    }

    @Test
    void geometryOnlyProducesUniformVoxelPlan() {
        assumeBlockRegistryLive();
        BoxGeometryData box = new BoxGeometryData(new Vector3d(0.5, 0.5, 0.5), new Vector3d(0.5, 0.5, 0.5));
        PlacementSubmitPlan.Plan plan = PlacementSubmitPlan.resolve(
                null, null, null, box, null, null, null, null, "minecraft:stone", true);

        assertTrue(plan.valid(), plan.error());
        assertEquals(PlacementSubmitPlan.Source.GEOMETRY_VOXELS, plan.source());
        assertFalse(plan.geometryIgnoredDueToPlacements());
        assertFalse(plan.entries().isEmpty());
        for (PlacementSubmitPlan.Entry entry : plan.entries()) {
            assertEquals("minecraft:stone", Registries.BLOCK.getId(entry.state().getBlock()).toString());
        }
    }

    @Test
    void placementsOnlyIgnoresEmptyGeometry() {
        assumeBlockRegistryLive();
        BlockPlacementData placement = new BlockPlacementData(new BlockPos(1, 2, 3), "minecraft:oak_planks");
        PlacementSubmitPlan.Plan plan = PlacementSubmitPlan.resolve(
                List.of(placement), null, null, null, null, null, null, null, "minecraft:stone", true);

        assertTrue(plan.valid(), plan.error());
        assertEquals(PlacementSubmitPlan.Source.PLACEMENTS, plan.source());
        assertEquals(1, plan.size());
        assertEquals(new BlockPos(1, 2, 3), plan.entries().getFirst().pos());
        assertFalse(plan.geometryIgnoredDueToPlacements());
    }

    @Test
    void placementsTakePriorityOverGeometry() {
        assumeBlockRegistryLive();
        BoxGeometryData box = new BoxGeometryData(new Vector3d(2, 2, 2), new Vector3d(1, 1, 1));
        BlockPlacementData placement = new BlockPlacementData(new BlockPos(9, 9, 9), "minecraft:oak_planks");

        PlacementSubmitPlan.Plan plan = PlacementSubmitPlan.resolve(
                List.of(placement), null, null, box, null, null, null, null, "minecraft:stone", true);

        assertTrue(plan.valid(), plan.error());
        assertEquals(PlacementSubmitPlan.Source.PLACEMENTS, plan.source());
        assertTrue(plan.geometryIgnoredDueToPlacements());
        assertEquals(1, plan.size());
        assertEquals(new BlockPos(9, 9, 9), plan.entries().getFirst().pos());
    }

    @Test
    void duplicatePositionsKeepOrderLastWriteWinsInMap() {
        assumeBlockRegistryLive();
        BlockPlacementData first = new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone");
        BlockPlacementData second = new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks");

        PlacementSubmitPlan.Plan plan = PlacementSubmitPlan.resolve(
                List.of(first, second), null, null, null, null, null, null, null, null, true);

        assertTrue(plan.valid(), plan.error());
        assertEquals(2, plan.size(), "queue retains both entries for Bake order");
        Map<BlockPos, net.minecraft.block.BlockState> map = plan.toPositionMap();
        assertEquals(1, map.size());
        assertEquals(
                Registries.BLOCK.get(net.minecraft.util.Identifier.of("minecraft:oak_planks")).getDefaultState(),
                map.get(new BlockPos(0, 0, 0))
        );
    }

    @Test
    void invalidPlacementFailsWholePlan() {
        PlacementSubmitPlan.Plan plan = PlacementSubmitPlan.resolve(
                List.of("not-a-placement"), null, null, null, null, null, null, null, null, true);
        assertFalse(plan.valid());
        assertEquals(PlacementSubmitPlan.Source.INVALID, plan.source());
    }

    @Test
    void emptyInputsYieldEmptyPlan() {
        PlacementSubmitPlan.Plan plan = PlacementSubmitPlan.resolve(
                null, null, null, null, null, null, null, null, "minecraft:stone", true);
        assertTrue(plan.valid());
        assertEquals(PlacementSubmitPlan.Source.EMPTY, plan.source());
        assertTrue(plan.entries().isEmpty());
    }
}

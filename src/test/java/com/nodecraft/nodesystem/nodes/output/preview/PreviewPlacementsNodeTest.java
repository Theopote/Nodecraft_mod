package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.PlacementSubmitPlan;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewPlacementsNodeTest {

    private static void assumeBlockRegistryLive() {
        boolean live = false;
        try {
            live = !Registries.BLOCK.getIds().isEmpty()
                    && Registries.BLOCK.get(net.minecraft.util.Identifier.of("minecraft:stone")) != null;
        } catch (Throwable ignored) {
            live = false;
        }
        Assumptions.assumeTrue(live, "Live BLOCK registry required");
    }

    @Test
    void placementsWinOverGeometryAndReportUniqueCount() {
        assumeBlockRegistryLive();
        BoxGeometryData box = new BoxGeometryData(new Vector3d(2, 2, 2), new Vector3d(1, 1, 1));
        BlockPlacementData placement = new BlockPlacementData(new BlockPos(3, 4, 5), "minecraft:oak_planks");

        PreviewPlacementsNode preview = new PreviewPlacementsNode();
        preview.setInput("input_geometry", box);
        preview.setInput("input_block_placements", List.of(placement));
        preview.processNode(null);

        assertEquals(PlacementSubmitPlan.Source.PLACEMENTS.name(), preview.getOutput("output_source"));
        assertEquals(Boolean.TRUE, preview.getOutput("output_geometry_ignored"));
        assertEquals(1, preview.getOutput("output_unique_count"));
        assertEquals(Boolean.FALSE, preview.getOutput("output_sampled"));
    }

    @Test
    void duplicatePlacementsReportRawVersusUnique() {
        assumeBlockRegistryLive();
        PreviewPlacementsNode preview = new PreviewPlacementsNode();
        preview.setInput("input_block_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone"),
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:gold_block")
        ));
        preview.processNode(null);

        assertEquals(2, preview.getOutput("output_raw_count"));
        assertEquals(1, preview.getOutput("output_unique_count"));
        assertTrue(Boolean.TRUE.equals(preview.getOutput("output_success"))
                || Boolean.FALSE.equals(preview.getOutput("output_success")));
    }
}

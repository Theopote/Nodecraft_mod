package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockStateResolver;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PlacementSubmitPlan;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6E: GHOST lifecycle — refresh, disable, ownership cleanup, sampling uniqueness.
 * Does not claim GPU ORIGINAL visual parity (client manual).
 */
class PreviewGhostLifecycleContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @AfterEach
    void tearDown() {
        PreviewManager.clearEmptyInputGrace();
    }

    @Test
    void disablePreviewClearsOutputsAndBookkeeping() {
        PreviewPlacementsNode preview = newPreviewNode();
        String owner = preview.getId().toString();
        preview.setInput("input_block_placements", List.of(
                new BlockPlacementData(new BlockPos(1, 2, 3), "minecraft:stone")));
        preview.processNode(null);

        PreviewManager.touchNonEmptyForTests(owner, "ghost_block");
        assertTrue(PreviewManager.withinEmptyGraceForTests(owner, "ghost_block"));

        preview.setNodeState(Map.of("previewEnabled", false));
        preview.processNode(null);

        assertEquals(Boolean.FALSE, preview.getOutput("output_success"));
        assertNull(preview.getOutput("output_preview_id"));
        assertEquals("Preview disabled", preview.getOutput("output_error"));
        assertFalse(PreviewManager.withinEmptyGraceForTests(owner, "ghost_block"));
    }

    @Test
    void repeatedRefreshWithChangedPlacementsUpdatesCounts() {
        Assumptions.assumeTrue(BlockStateResolver.isRegistryReady(), "Live BLOCK registry required");

        PreviewPlacementsNode preview = newPreviewNode();
        preview.setInput("input_block_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone"),
                new BlockPlacementData(new BlockPos(1, 0, 0), "minecraft:stone")));
        preview.processNode(null);
        assertEquals(2, preview.getOutput("output_unique_count"));
        assertEquals(2, preview.getOutput("output_shown_count"));

        preview.setInput("input_block_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks"),
                new BlockPlacementData(new BlockPos(1, 0, 0), "minecraft:oak_planks"),
                new BlockPlacementData(new BlockPos(2, 0, 0), "minecraft:oak_planks"),
                new BlockPlacementData(new BlockPos(3, 0, 0), "minecraft:oak_planks")));
        preview.processNode(null);
        assertEquals(4, preview.getOutput("output_unique_count"));
        assertEquals(4, preview.getOutput("output_shown_count"));
        assertEquals(Boolean.FALSE, preview.getOutput("output_sampled"));
    }

    @Test
    void identicalRefreshKeepsStableSamplePayload() {
        Assumptions.assumeTrue(BlockStateResolver.isRegistryReady(), "Live BLOCK registry required");

        List<BlockPlacementData> placements = List.of(
                new BlockPlacementData(new BlockPos(5, 1, 5), "minecraft:stone_bricks"),
                new BlockPlacementData(new BlockPos(6, 1, 5), "minecraft:stone_bricks"),
                new BlockPlacementData(new BlockPos(7, 1, 5), "minecraft:stone_bricks"));
        PlacementSubmitPlan.Plan plan = PlacementSubmitPlan.resolve(
                placements, null, null, null, null, null, null, null, null, true);
        assertTrue(plan.valid(), plan.error());

        PreviewPlacementsNode.GhostSample first =
                PreviewPlacementsNode.sampleGhosts(plan.entries(), GenerationLimits.MAX_PREVIEW_BLOCKS);
        PreviewPlacementsNode.GhostSample second =
                PreviewPlacementsNode.sampleGhosts(plan.entries(), GenerationLimits.MAX_PREVIEW_BLOCKS);
        assertEquals(first.sampled(), second.sampled());
        assertEquals(first.ghosts().size(), second.ghosts().size());
        for (int i = 0; i < first.ghosts().size(); i++) {
            PreviewBlock a = first.ghosts().get(i);
            PreviewBlock b = second.ghosts().get(i);
            assertEquals(a.x(), b.x(), 0.0);
            assertEquals(a.y(), b.y(), 0.0);
            assertEquals(a.z(), b.z(), 0.0);
            assertEquals(a.blockId(), b.blockId());
        }
    }

    @Test
    void largeSampleHasUniquePositionsStrictSubset() {
        Assumptions.assumeTrue(BlockStateResolver.isRegistryReady(), "Live BLOCK registry required");

        List<BlockPlacementData> placements = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            placements.add(new BlockPlacementData(new BlockPos(i, 10, 0), "minecraft:stone"));
        }
        PlacementSubmitPlan.Plan plan = PlacementSubmitPlan.resolve(
                placements, null, null, null, null, null, null, null, null, true);
        assertTrue(plan.valid(), plan.error());
        assertEquals(40, plan.uniqueCount());

        PreviewPlacementsNode.GhostSample sample =
                PreviewPlacementsNode.sampleGhosts(plan.entries(), 12);
        assertTrue(sample.sampled());
        assertEquals(12, sample.ghosts().size());

        Set<BlockPos> shown = new HashSet<>();
        Map<BlockPos, ?> planMap = plan.toPositionMap();
        for (PreviewBlock ghost : sample.ghosts()) {
            BlockPos pos = new BlockPos((int) ghost.x(), (int) ghost.y(), (int) ghost.z());
            assertTrue(shown.add(pos), "sampled ghost positions must be unique: " + pos);
            assertTrue(planMap.containsKey(pos), "sampled cell missing from Apply plan: " + pos);
        }
        assertEquals(12, shown.size());
        assertTrue(planMap.keySet().containsAll(shown));
        assertTrue(shown.size() < planMap.size());
    }

    @Test
    void hidePreviewsNotOwnedByDropsDeletedOwnerGrace() {
        String deleted = "ghost-lifecycle-deleted-" + UUID.randomUUID();
        String live = "ghost-lifecycle-live-" + UUID.randomUUID();
        PreviewManager.touchNonEmptyForTests(deleted, "ghost_block");
        PreviewManager.touchNonEmptyForTests(live, "ghost_block");

        PreviewManager.hidePreviewsNotOwnedBy(Set.of(live));

        assertFalse(PreviewManager.withinEmptyGraceForTests(deleted, "ghost_block"));
        assertTrue(PreviewManager.withinEmptyGraceForTests(live, "ghost_block"));
        assertFalse(PreviewRenderer.getInstance().ownerNodeIds().contains(deleted));
    }

    @Test
    void emptyPlacementsHideAndDoNotLeaveStaleCounts() {
        PreviewPlacementsNode preview = newPreviewNode();
        preview.setInput("input_block_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone")));
        preview.processNode(null);

        preview.setInput("input_block_placements", List.of());
        preview.processNode(null);

        assertEquals(Boolean.FALSE, preview.getOutput("output_success"));
        assertNull(preview.getOutput("output_preview_id"));
        assertNotEquals("", String.valueOf(preview.getOutput("output_error")));
        Object shown = preview.getOutput("output_shown_count");
        assertTrue(shown == null || ((Number) shown).intValue() == 0);
    }

    private static PreviewPlacementsNode newPreviewNode() {
        return assertInstanceOf(
                PreviewPlacementsNode.class,
                registry.createNodeInstance("output.preview.preview_placements"));
    }
}

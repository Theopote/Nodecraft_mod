package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.BlockStateResolver;
import com.nodecraft.nodesystem.util.PlacementSubmitPlan;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewPlacementsNodeTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    private static void assumeBlockRegistryLive() {
        Assumptions.assumeTrue(BlockStateResolver.isRegistryReady(), "Live BLOCK registry required");
    }

    @Test
    void registryCanCreatePreviewPlacementsNode() {
        assertTrue(registry.getAllNodeIds().stream()
                .anyMatch(id -> id.equalsIgnoreCase("output.preview.preview_placements")));
        assertInstanceOf(PreviewPlacementsNode.class,
                registry.createNodeInstance("output.preview.preview_placements"));
    }

    @Test
    void inputPortsMatchApplyGeometrySurface() {
        PreviewPlacementsNode preview = new PreviewPlacementsNode();
        Set<String> ids = new HashSet<>();
        for (IPort port : preview.getInputPorts()) {
            ids.add(port.getId());
        }
        assertTrue(ids.contains("input_block_placements"));
        assertTrue(ids.contains("input_block_placements_tree"));
        assertTrue(ids.contains("input_blocks"));
        assertTrue(ids.contains("input_geometry"));
        assertTrue(ids.contains("input_box_geometry"));
        assertTrue(ids.contains("input_cylinder_geometry"));
        assertTrue(ids.contains("input_sphere_geometry"));
        assertTrue(ids.contains("input_torus_geometry"));
        assertTrue(ids.contains("input_block_type"));
    }

    @Test
    void sampleGhostsEmptyWhenNoEntries() {
        PreviewPlacementsNode.GhostSample empty = PreviewPlacementsNode.sampleGhosts(List.of(), 10);
        assertTrue(empty.ghosts().isEmpty());
        assertFalse(empty.sampled());
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
    void ghostPayloadMatchesPlanBlockStatesForOrientedBlocks() {
        assumeBlockRegistryLive();

        List<BlockPlacementData> placements = List.of(
                placement(new BlockPos(0, 64, 0), "minecraft:stone", null),
                placement(new BlockPos(1, 64, 0), "minecraft:oak_planks", null),
                placement(new BlockPos(2, 64, 0), "minecraft:oak_stairs",
                        state("facing", "east", "half", "top", "shape", "straight")),
                placement(new BlockPos(3, 64, 0), "minecraft:oak_slab", state("type", "top")),
                placement(new BlockPos(4, 64, 0), "minecraft:oak_trapdoor",
                        state("facing", "south", "half", "top", "open", "true"))
        );

        PlacementSubmitPlan.Plan plan = PlacementSubmitPlan.resolve(
                placements, null, null, null, null, null, null, null, null, true);
        assertTrue(plan.valid(), plan.error());
        assertEquals(5, plan.uniqueCount());

        PreviewPlacementsNode.GhostSample sample =
                PreviewPlacementsNode.sampleGhosts(plan.entries(), Integer.MAX_VALUE);
        assertFalse(sample.sampled());
        assertEquals(plan.uniqueCount(), sample.ghosts().size());
        assertGhostsMatchPlan(plan, sample.ghosts());

        PreviewBlock stairs = sample.ghosts().get(2);
        assertEquals("minecraft:oak_stairs", stairs.blockId());
        assertNotNull(stairs.stateData());
        assertEquals("east", stairs.stateData().get("facing"));
        assertEquals("top", stairs.stateData().get("half"));

        PreviewBlock slab = sample.ghosts().get(3);
        assertEquals("top", slab.stateData().get("type"));

        PreviewBlock trapdoor = sample.ghosts().get(4);
        assertEquals("south", trapdoor.stateData().get("facing"));
        assertEquals("true", trapdoor.stateData().get("open"));
    }

    @Test
    void lastWriteWinsPreservesOrderAndFinalOrientedState() {
        assumeBlockRegistryLive();

        BlockPos p = new BlockPos(0, 70, 0);
        BlockPos q = new BlockPos(1, 70, 0);
        List<BlockPlacementData> placements = List.of(
                placement(p, "minecraft:stone", null),
                placement(q, "minecraft:oak_planks", null),
                placement(p, "minecraft:oak_stairs",
                        state("facing", "west", "half", "bottom", "shape", "straight"))
        );

        PlacementSubmitPlan.Plan plan = PlacementSubmitPlan.resolve(
                placements, null, null, null, null, null, null, null, null, true);
        assertTrue(plan.valid(), plan.error());
        assertEquals(3, plan.rawEntryCount());
        assertEquals(2, plan.uniqueCount());
        assertTrue(plan.hadDuplicates());

        PreviewPlacementsNode.GhostSample sample =
                PreviewPlacementsNode.sampleGhosts(plan.entries(), Integer.MAX_VALUE);
        assertEquals(2, sample.ghosts().size());
        assertEquals(p.getX(), sample.ghosts().get(0).x(), 0.001);
        assertEquals("minecraft:oak_stairs", sample.ghosts().get(0).blockId());
        assertEquals("west", sample.ghosts().get(0).stateData().get("facing"));
        assertEquals(q.getX(), sample.ghosts().get(1).x(), 0.001);
        assertEquals("minecraft:oak_planks", sample.ghosts().get(1).blockId());
        assertGhostsMatchPlan(plan, sample.ghosts());
    }

    @Test
    void sampledGhostsAreStrictSubsetWithUnchangedStates() {
        assumeBlockRegistryLive();

        List<BlockPlacementData> placements = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            placements.add(placement(
                    new BlockPos(i, 80, 0),
                    "minecraft:oak_stairs",
                    state("facing", i % 2 == 0 ? "east" : "west", "half", "bottom", "shape", "straight")));
        }
        PlacementSubmitPlan.Plan plan = PlacementSubmitPlan.resolve(
                placements, null, null, null, null, null, null, null, null, true);
        assertTrue(plan.valid(), plan.error());
        assertEquals(6, plan.uniqueCount());

        PreviewPlacementsNode.GhostSample sample = PreviewPlacementsNode.sampleGhosts(plan.entries(), 3);
        assertTrue(sample.sampled());
        assertEquals(3, sample.ghosts().size());

        Map<BlockPos, BlockState> planMap = plan.toPositionMap();
        for (PreviewBlock ghost : sample.ghosts()) {
            BlockPos pos = new BlockPos((int) ghost.x(), (int) ghost.y(), (int) ghost.z());
            assertTrue(planMap.containsKey(pos), "sampled cell missing from plan: " + pos);
            BlockState resolved = BlockStateResolver.resolve(ghost.blockId(), ghost.stateData());
            assertEquals(planMap.get(pos), resolved);
        }
    }

    @Test
    void disabledPreviewClearsOutputsWithoutWorldWrite() {
        PreviewPlacementsNode preview = new PreviewPlacementsNode();
        preview.setNodeState(Map.of("previewEnabled", false));
        preview.setInput("input_block_placements",
                List.of(new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone")));
        preview.processNode(null);

        assertEquals(Boolean.FALSE, preview.getOutput("output_success"));
        assertNull(preview.getOutput("output_preview_id"));
        assertEquals("Preview disabled", preview.getOutput("output_error"));
    }

    private static void assertGhostsMatchPlan(PlacementSubmitPlan.Plan plan, List<PreviewBlock> ghosts) {
        assertEquals(plan.entries().size(), ghosts.size());
        for (int i = 0; i < ghosts.size(); i++) {
            PlacementSubmitPlan.Entry entry = plan.entries().get(i);
            PreviewBlock ghost = ghosts.get(i);
            assertEquals(entry.pos().getX(), ghost.x(), 0.001);
            assertEquals(entry.pos().getY(), ghost.y(), 0.001);
            assertEquals(entry.pos().getZ(), ghost.z(), 0.001);
            BlockState resolved = BlockStateResolver.resolve(ghost.blockId(), ghost.stateData());
            assertNotNull(resolved, "ghost failed to resolve: " + ghost.blockId());
            assertEquals(entry.state(), resolved, "ghost BlockState mismatch at " + entry.pos());
        }
    }

    private static BlockPlacementData placement(BlockPos pos, String blockId, @org.jetbrains.annotations.Nullable BlockStateData state) {
        return new BlockPlacementData(pos, blockId, state);
    }

    private static BlockStateData state(String... keyValues) {
        BlockStateData data = new BlockStateData();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            data = data.withProperty(keyValues[i], keyValues[i + 1]);
        }
        return data;
    }
}

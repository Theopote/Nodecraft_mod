package com.nodecraft.nodesystem.bake;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests asynchronous undo/redo behavior and history stack routing.
 *
 * This test ensures that:
 * - APPLY operations push to undoStack and clear redoStack
 * - UNDO operations push inverse records to redoStack
 * - REDO operations push inverse records to undoStack (preserving redoStack)
 * - Multi-transaction undo/redo sequences maintain correct order
 */
public class BakeAsyncUndoRedoTest {

    /**
     * Basic undo/redo cycle:
     * Build → Undo → Redo
     *
     * Verifies history stack sizes at each step.
     */
    @GameTest(templateName = "empty_5x5x5")
    public void testBasicUndoRedoCycle(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId);

        // Clear any existing history
        history.clear();

        // Step 1: Build transaction A
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        List<BakeTask.Placement> placements = List.of(
            new BakeTask.Placement(pos, Blocks.STONE.getDefaultState())
        );

        UUID buildTaskId = service.enqueuePlacements(
            world,
            placements,
            PlacementMode.NORMAL,
            true,  // recordUndo
            BakeOperationKind.APPLY,
            1000,
            1_000_000L,
            actorId,
            null
        );

        // Process until build completes
        processTasks(service, ctx);

        // Verify: history.size() == 1, redoSize() == 0
        assertEquals(1, history.size(), "After build: undo stack should have 1 entry");
        assertEquals(0, history.redoSize(), "After build: redo stack should be empty");
        assertEquals(Blocks.STONE, world.getBlockState(pos).getBlock(), "Block should be placed");

        // Step 2: Async undo
        UUID undoTaskId = service.undoLastAsync(actorId, world, 1000, 1_000_000L);
        assertNotNull(undoTaskId, "Undo should return a task ID");

        // Process until undo completes
        processTasks(service, ctx);

        // Verify: history.size() == 0, redoSize() == 1
        assertEquals(0, history.size(), "After undo: undo stack should be empty");
        assertEquals(1, history.redoSize(), "After undo: redo stack should have 1 entry");
        assertEquals(Blocks.AIR, world.getBlockState(pos).getBlock(), "Block should be removed");

        // Step 3: Async redo
        UUID redoTaskId = service.redoLastAsync(actorId, world, 1000, 1_000_000L);
        assertNotNull(redoTaskId, "Redo should return a task ID");

        // Process until redo completes
        processTasks(service, ctx);

        // Verify: history.size() == 1, redoSize() == 0
        assertEquals(1, history.size(), "After redo: undo stack should have 1 entry");
        assertEquals(0, history.redoSize(), "After redo: redo stack should be empty");
        assertEquals(Blocks.STONE, world.getBlockState(pos).getBlock(), "Block should be re-placed");

        ctx.complete();
    }

    /**
     * Multi-transaction undo/redo:
     * Build A → Build B → Undo B → Undo A → Redo A → Redo B
     *
     * Verifies correct LIFO order and stack routing.
     */
    @GameTest(templateName = "empty_5x5x5")
    public void testMultiTransactionUndoRedo(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId);

        history.clear();

        BlockPos posA = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos posB = ctx.getAbsolutePos(new BlockPos(2, 1, 1));

        // Build A (stone)
        service.enqueuePlacements(
            world,
            List.of(new BakeTask.Placement(posA, Blocks.STONE.getDefaultState())),
            PlacementMode.NORMAL,
            true,
            BakeOperationKind.APPLY,
            1000, 1_000_000L, actorId, null
        );
        processTasks(service, ctx);

        // Build B (dirt)
        service.enqueuePlacements(
            world,
            List.of(new BakeTask.Placement(posB, Blocks.DIRT.getDefaultState())),
            PlacementMode.NORMAL,
            true,
            BakeOperationKind.APPLY,
            1000, 1_000_000L, actorId, null
        );
        processTasks(service, ctx);

        // State: [A, B] in undo, [] in redo
        assertEquals(2, history.size(), "After 2 builds: undo stack should have 2 entries");
        assertEquals(0, history.redoSize(), "After 2 builds: redo stack should be empty");
        assertEquals(Blocks.STONE, world.getBlockState(posA).getBlock());
        assertEquals(Blocks.DIRT, world.getBlockState(posB).getBlock());

        // Undo B
        service.undoLastAsync(actorId, world, 1000, 1_000_000L);
        processTasks(service, ctx);

        // State: [A] in undo, [B] in redo
        assertEquals(1, history.size(), "After undo B: undo stack should have 1 entry");
        assertEquals(1, history.redoSize(), "After undo B: redo stack should have 1 entry");
        assertEquals(Blocks.STONE, world.getBlockState(posA).getBlock());
        assertEquals(Blocks.AIR, world.getBlockState(posB).getBlock());

        // Undo A
        service.undoLastAsync(actorId, world, 1000, 1_000_000L);
        processTasks(service, ctx);

        // State: [] in undo, [B, A] in redo
        assertEquals(0, history.size(), "After undo A: undo stack should be empty");
        assertEquals(2, history.redoSize(), "After undo A: redo stack should have 2 entries");
        assertEquals(Blocks.AIR, world.getBlockState(posA).getBlock());
        assertEquals(Blocks.AIR, world.getBlockState(posB).getBlock());

        // Redo A
        service.redoLastAsync(actorId, world, 1000, 1_000_000L);
        processTasks(service, ctx);

        // State: [A] in undo, [B] in redo
        assertEquals(1, history.size(), "After redo A: undo stack should have 1 entry");
        assertEquals(1, history.redoSize(), "After redo A: redo stack should have 1 entry");
        assertEquals(Blocks.STONE, world.getBlockState(posA).getBlock());
        assertEquals(Blocks.AIR, world.getBlockState(posB).getBlock());

        // Redo B
        service.redoLastAsync(actorId, world, 1000, 1_000_000L);
        processTasks(service, ctx);

        // State: [A, B] in undo, [] in redo
        assertEquals(2, history.size(), "After redo B: undo stack should have 2 entries");
        assertEquals(0, history.redoSize(), "After redo B: redo stack should be empty");
        assertEquals(Blocks.STONE, world.getBlockState(posA).getBlock());
        assertEquals(Blocks.DIRT, world.getBlockState(posB).getBlock());

        ctx.complete();
    }

    /**
     * Verify that new APPLY operation clears redo stack.
     * Build A → Undo A → Build B → (redo stack should be cleared)
     */
    @GameTest(templateName = "empty_5x5x5")
    public void testNewApplyClearsRedoStack(TestContext ctx) {
        World world = ctx.getWorld();
        BakePlacementService service = BakePlacementService.getInstance();
        UUID actorId = UUID.randomUUID();
        BakeHistory history = service.getHistory(actorId);

        history.clear();

        BlockPos posA = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos posB = ctx.getAbsolutePos(new BlockPos(2, 1, 1));

        // Build A
        service.enqueuePlacements(
            world,
            List.of(new BakeTask.Placement(posA, Blocks.STONE.getDefaultState())),
            PlacementMode.NORMAL,
            true,
            BakeOperationKind.APPLY,
            1000, 1_000_000L, actorId, null
        );
        processTasks(service, ctx);

        // Undo A
        service.undoLastAsync(actorId, world, 1000, 1_000_000L);
        processTasks(service, ctx);

        assertEquals(0, history.size());
        assertEquals(1, history.redoSize(), "After undo: redo stack should have 1 entry");

        // Build B (new APPLY should clear redo)
        service.enqueuePlacements(
            world,
            List.of(new BakeTask.Placement(posB, Blocks.DIRT.getDefaultState())),
            PlacementMode.NORMAL,
            true,
            BakeOperationKind.APPLY,
            1000, 1_000_000L, actorId, null
        );
        processTasks(service, ctx);

        assertEquals(1, history.size(), "After new build: undo stack should have 1 entry");
        assertEquals(0, history.redoSize(), "After new build: redo stack should be cleared");

        ctx.complete();
    }

    /**
     * Helper: Process all queued tasks until completion.
     */
    private void processTasks(BakePlacementService service, TestContext ctx) {
        int maxTicks = 100;
        int tickCount = 0;

        while (tickCount < maxTicks) {
            service.processTick();
            tickCount++;

            // Small delay to allow async operations
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}

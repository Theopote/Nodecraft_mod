package com.nodecraft.nodesystem.nodes.material.basic_assignment;

import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPaletteEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateBlockPaletteWeightEditTransactionTest {

    @Test
    void weightEditCapturesBaselineBeforePreviewMutations() throws Exception {
        CreateBlockPaletteNode node = new CreateBlockPaletteNode();
        node.setNodeState(Map.of(
            "draftBlockIds", List.of("minecraft:stone"),
            "draftWeights", List.of(1.0d)));
        Object originalState = node.getNodeState();

        invokeBeginWeightEdit(node);
        assertEquals(originalState, node.pendingWeightEditBaseline());

        invokePreview(node, List.of(new BlockPaletteEntry("minecraft:stone", 2.5d)));
        assertEquals(originalState, node.pendingWeightEditBaseline(),
            "Preview must not replace the undo baseline");

        assertEquals(2.5d, draftWeight(node));

        invokeCommitWeightEdit(node);
        assertNull(node.pendingWeightEditBaseline());
        assertNotEquals(originalState, node.getNodeState());

        node.setNodeState(originalState);
        assertEquals(1.0d, draftWeight(node));
    }

    @Test
    void weightEditWithNoEffectiveChangeSkipsHistoryBaseline() throws Exception {
        CreateBlockPaletteNode node = new CreateBlockPaletteNode();
        node.setNodeState(Map.of(
            "draftBlockIds", List.of("minecraft:stone"),
            "draftWeights", List.of(1.0d)));
        Object originalState = node.getNodeState();

        invokeBeginWeightEdit(node);
        invokePreview(node, List.of(new BlockPaletteEntry("minecraft:stone", 1.0d)));
        invokeCommitWeightEdit(node);

        assertEquals(originalState, node.getNodeState());
    }

    @Test
    void immediateCommitUsesCurrentStateNotPreviewBaseline() throws Exception {
        CreateBlockPaletteNode node = new CreateBlockPaletteNode();
        node.setNodeState(Map.of(
            "draftBlockIds", List.of("minecraft:stone"),
            "draftWeights", List.of(1.0d)));

        invokeBeginWeightEdit(node);
        invokePreview(node, List.of(new BlockPaletteEntry("minecraft:stone", 2.0d)));

        invokeApplyDraft(node, List.of(
            new BlockPaletteEntry("minecraft:stone", 2.0d),
            new BlockPaletteEntry("minecraft:dirt", 1.0d)));

        assertNull(node.pendingWeightEditBaseline());
        node.processNode(null);
        BlockPaletteData palette = assertInstanceOf(BlockPaletteData.class, node.getOutput("output_palette"));
        assertEquals(2, palette.size());
    }

    @Test
    void previewUpdatesExecutionOutput() throws Exception {
        CreateBlockPaletteNode node = new CreateBlockPaletteNode();
        node.setNodeState(Map.of(
            "draftBlockIds", List.of("minecraft:stone"),
            "draftWeights", List.of(1.0d)));

        invokeBeginWeightEdit(node);
        invokePreview(node, List.of(new BlockPaletteEntry("minecraft:stone", 2.5d)));
        node.processNode(null);

        BlockPaletteData palette = assertInstanceOf(BlockPaletteData.class, node.getOutput("output_palette"));
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals(List.of(2.5d), palette.weights());
    }

    private static void invokeBeginWeightEdit(CreateBlockPaletteNode node) throws Exception {
        var method = CreateBlockPaletteNode.class.getDeclaredMethod("beginWeightEdit");
        method.setAccessible(true);
        method.invoke(node);
    }

    private static void invokeCommitWeightEdit(CreateBlockPaletteNode node) throws Exception {
        var method = CreateBlockPaletteNode.class.getDeclaredMethod("commitWeightEdit");
        method.setAccessible(true);
        method.invoke(node);
    }

    private static void invokePreview(CreateBlockPaletteNode node, List<BlockPaletteEntry> entries) throws Exception {
        var method = CreateBlockPaletteNode.class.getDeclaredMethod("previewDraftEntries", List.class);
        method.setAccessible(true);
        method.invoke(node, entries);
    }

    private static void invokeApplyDraft(CreateBlockPaletteNode node, List<BlockPaletteEntry> entries) throws Exception {
        var method = CreateBlockPaletteNode.class.getDeclaredMethod(
            "applyDraftEntries", List.class, String.class);
        method.setAccessible(true);
        method.invoke(node, entries, "Edit draft palette");
    }

    @SuppressWarnings("unchecked")
    private static double draftWeight(CreateBlockPaletteNode node) {
        Map<String, Object> state = (Map<String, Object>) node.getNodeState();
        List<Double> weights = (List<Double>) state.get("draftWeights");
        return weights.getFirst();
    }
}

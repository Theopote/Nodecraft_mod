package com.nodecraft.gui.editor.impl;

import com.nodecraft.gui.components.customui.CurvePlotWidget.BezierHandle;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.CreateBlockPaletteNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.GraphMapperNode;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end Undo/Redo through {@link ImGuiNodeHistory} for custom UI node-state edits.
 */
class CustomUINodeStateHistoryIntegrationTest {

    @Test
    void graphMapperBezierDragRecordsSingleUndoRestoringBothCoordinates() throws Exception {
        NodeGraph graph = new NodeGraph("graph-mapper-bezier-history");
        GraphMapperNode node = new GraphMapperNode();
        node.setCurveType(GraphMapperNode.CurveType.BEZIER);
        graph.addNode(node);

        double originalSample = node.sampleNormalizedCurve(0.5d);

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        editor.setCurrentGraph(graph);
        editor.getHistory().clear();

        node.applyBezierHandleDrag(BezierHandle.P1, 0.1d, 0.9d);

        assertEquals(1, editor.getHistory().undoStackSize());
        assertTrue(editor.getHistory().canUndo());
        assertEquals(ImGuiNodeHistory.ActionType.NODE_STATE_CHANGE, editor.getHistory().getUndoTopActionType());
        assertEquals(0.1d, node.getBezierX1(), 1.0e-9);
        assertEquals(0.9d, node.getBezierY1(), 1.0e-9);
        assertTrue(Math.abs(node.sampleNormalizedCurve(0.5d) - originalSample) > 1.0e-6d);

        assertTrue(editor.undo());
        assertEquals(0.25d, node.getBezierX1(), 1.0e-9);
        assertEquals(0.0d, node.getBezierY1(), 1.0e-9);
        assertEquals(originalSample, node.sampleNormalizedCurve(0.5d), 1.0e-9);

        assertTrue(editor.getHistory().canRedo());
        assertTrue(editor.redo());
        assertEquals(0.1d, node.getBezierX1(), 1.0e-9);
        assertEquals(0.9d, node.getBezierY1(), 1.0e-9);
        assertTrue(Math.abs(node.sampleNormalizedCurve(0.5d) - originalSample) > 1.0e-6d);
    }

    @Test
    void createBlockPaletteWeightEditRecordsSingleUndoThroughHistory() throws Exception {
        NodeGraph graph = new NodeGraph("palette-weight-history");
        CreateBlockPaletteNode node = new CreateBlockPaletteNode();
        node.setNodeState(Map.of(
            "draftBlockIds", List.of("minecraft:stone"),
            "draftWeights", List.of(1.0d)));
        graph.addNode(node);

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        editor.setCurrentGraph(graph);
        editor.getHistory().clear();

        node.applyDraftWeightEdit(0, 2.5d);

        assertEquals(1, editor.getHistory().undoStackSize());
        assertTrue(editor.getHistory().canUndo());
        assertEquals(ImGuiNodeHistory.ActionType.NODE_STATE_CHANGE, editor.getHistory().getUndoTopActionType());

        node.processNode(null);
        BlockPaletteData afterEdit = (BlockPaletteData) node.getOutput("output_palette");
        assertEquals(List.of(2.5d), afterEdit.weights());

        assertTrue(editor.undo());
        assertEquals(1.0d, draftWeight(node), 1.0e-9);
        node.processNode(null);
        BlockPaletteData afterUndo = (BlockPaletteData) node.getOutput("output_palette");
        assertEquals(List.of(1.0d), afterUndo.weights());

        assertTrue(editor.redo());
        assertEquals(2.5d, draftWeight(node), 1.0e-9);
        node.processNode(null);
        BlockPaletteData afterRedo = (BlockPaletteData) node.getOutput("output_palette");
        assertEquals(List.of(2.5d), afterRedo.weights());
    }

    @SuppressWarnings("unchecked")
    private static double draftWeight(CreateBlockPaletteNode node) {
        Map<String, Object> state = (Map<String, Object>) node.getNodeState();
        List<Double> weights = (List<Double>) state.get("draftWeights");
        return weights.getFirst();
    }
}

package com.nodecraft.gui.editor.impl;

import com.google.gson.Gson;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import imgui.ImVec2;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImGuiNodeIOMigrationTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        com.nodecraft.nodesystem.registry.NodeRegistry.getInstance().clear();
    }

    @Test
    void saveGraphWritesCanonicalTypeIdsForLegacyNodes() throws Exception {
        TestCanvasEditor editor = new TestCanvasEditor();
        NodeGraph graph = new NodeGraph("Save Legacy Graph");
        LegacyTestNode legacyNode = new LegacyTestNode();
        graph.addNode(legacyNode);

        Map<UUID, NodePosition> positions = new HashMap<>();
        positions.put(legacyNode.getId(), new NodePosition(12.0f, 34.0f));
        editor.setCurrentGraph(graph);
        editor.setNodePositions(positions);

        ImGuiNodeIO io = new ImGuiNodeIO(editor);
        Path file = tempDir.resolve("save-legacy.json");

        assertTrue(io.saveGraph(file));

        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"typeId\": \"output.preview.geometry_viewer\""));
        assertFalse(json.contains("visualization.preview.geometry_viewer"));
    }

    @Test
    void loadGraphResolvesLegacyTypeIdsToCanonicalNodes() throws Exception {
        com.nodecraft.nodesystem.registry.NodeRegistry registry = com.nodecraft.nodesystem.registry.NodeRegistry.getInstance();
        registry.clear();
        registry.initialize();

        SavedGraph savedGraph = new SavedGraph();
        savedGraph.graphName = "Legacy Load Graph";
        savedGraph.nodes = new ArrayList<>();
        savedGraph.connections = new ArrayList<>();
        savedGraph.nodePositions = new HashMap<>();

        SavedNode previewNode = new SavedNode();
        previewNode.nodeId = UUID.randomUUID().toString();
        previewNode.typeId = "visualization.preview.geometry_viewer";
        savedGraph.nodes.add(previewNode);
        savedGraph.nodePositions.put(previewNode.nodeId, new com.nodecraft.nodesystem.io.SavedPosition(10.0f, 20.0f));

        SavedNode branchNode = new SavedNode();
        branchNode.nodeId = UUID.randomUUID().toString();
        branchNode.typeId = "logic.if";
        savedGraph.nodes.add(branchNode);
        savedGraph.nodePositions.put(branchNode.nodeId, new com.nodecraft.nodesystem.io.SavedPosition(30.0f, 40.0f));

        Path file = tempDir.resolve("load-legacy.json");
        Files.writeString(file, new Gson().toJson(savedGraph), StandardCharsets.UTF_8);

        TestCanvasEditor editor = new TestCanvasEditor();
        ImGuiNodeIO io = new ImGuiNodeIO(editor);

        assertTrue(io.loadGraph(file));
        assertNotNull(editor.getCurrentGraph());
        assertEquals("Legacy Load Graph", editor.getCurrentGraph().getName());
        assertEquals(2, editor.getCurrentGraph().getNodes().size());

        List<String> loadedTypeIds = editor.getCurrentGraph().getNodes().stream()
            .map(INode::getTypeId)
            .toList();
        assertTrue(loadedTypeIds.contains("output.preview.geometry_viewer"));
        assertTrue(loadedTypeIds.contains("math.logic.if"));

        assertEquals(2, editor.getNodePositions().size());
        assertTrue(editor.getNodePositions().values().stream().anyMatch(pos -> pos.x == 10.0f && pos.y == 20.0f));
        assertTrue(editor.getNodePositions().values().stream().anyMatch(pos -> pos.x == 30.0f && pos.y == 40.0f));
    }

    private static final class LegacyTestNode extends BaseNode {
        private LegacyTestNode() {
            super(UUID.randomUUID(), "visualization.preview.geometry_viewer");
        }

        @Override
        public String getDescription() {
            return "Legacy test node";
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            // No-op test node.
        }
    }

    private static final class TestCanvasEditor implements ICanvasEditor {
        private NodeGraph currentGraph;
        private Map<UUID, NodePosition> nodePositions = new HashMap<>();
        private final Set<UUID> selectedNodeIds = new HashSet<>();

        @Override
        public float getCanvasZoom() {
            return 1.0f;
        }

        @Override
        public float getCanvasOffsetX() {
            return 0;
        }

        @Override
        public float getCanvasOffsetY() {
            return 0;
        }

        @Override
        public NodeGraph getCurrentGraph() {
            return currentGraph;
        }

        @Override
        public UUID getSelectedNodeId() {
            return selectedNodeIds.stream().findFirst().orElse(null);
        }

        @Override
        public void setSelectedNodeId(UUID nodeId) {
            selectedNodeIds.clear();
            if (nodeId != null) {
                selectedNodeIds.add(nodeId);
            }
        }

        @Override
        public boolean connectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            return currentGraph != null && currentGraph.connect(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        }

        @Override
        public boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            return currentGraph != null && currentGraph.disconnectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        }

        @Override
        public Map<UUID, NodePosition> getNodePositions() {
            return nodePositions;
        }

        @Override
        public NodePosition getNodePosition(UUID nodeId) {
            return nodePositions.get(nodeId);
        }

        @Override
        public void setCurrentGraph(NodeGraph graph) {
            this.currentGraph = graph;
        }

        @Override
        public void setNodePositions(Map<UUID, NodePosition> positions) {
            this.nodePositions = positions;
        }

        @Override
        public boolean isShowGrid() {
            return false;
        }

        @Override
        public void setShowGrid(boolean showGrid) {
        }

        @Override
        public void setCanvasZoom(float zoom) {
        }

        @Override
        public void setCanvasOffset(float x, float y) {
        }

        @Override
        public void clearNodePositions() {
            nodePositions.clear();
        }

        @Override
        public void clearSelectedNodes() {
            selectedNodeIds.clear();
        }

        @Override
        public void removeSelectedNode(UUID nodeId) {
            selectedNodeIds.remove(nodeId);
        }

        @Override
        public void removeNodePosition(UUID nodeId) {
            nodePositions.remove(nodeId);
        }

        @Override
        public UUID getNodeIdUnderMouse(float mouseX, float mouseY) {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public INode addNode(String nodeTypeId, float x, float y) {
            throw new UnsupportedOperationException();
        }

        @Override
        public INode addNodeWithState(String nodeTypeId, @Nullable UUID oldNodeId, float x, float y, @Nullable Object nodeState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<UUID> getSelectedNodeIds() {
            return selectedNodeIds;
        }

        @Override
        public void setCanvasView(float zoom, float offsetX, float offsetY) {
        }

        @Override
        public void pasteNodesAtPosition(float x, float y) {
        }

        @Override
        public ImGuiNodeInteraction getInteraction() {
            return null;
        }

        @Override
        public Map<UUID, Map<String, ImVec2>> getPortScreenPositions() {
            return Map.of();
        }

        @Override
        public ImGuiNodeIO getNodeIO() {
            return null;
        }

        @Override
        public ImGuiNodeHistory getHistory() {
            return null;
        }

        @Override
        public ImGuiNodeClipboard getClipboard() {
            return null;
        }

        @Override
        public boolean undo() {
            return false;
        }

        @Override
        public boolean redo() {
            return false;
        }

        @Override
        public boolean copySelectedNodes() {
            return false;
        }

        @Override
        public boolean cutSelectedNodes() {
            return false;
        }

        @Override
        public boolean pasteNodesAt(float x, float y) {
            return false;
        }

        @Override
        public boolean deleteSelectedNodes() {
            return false;
        }

        @Override
        public boolean hasUnsavedChanges() {
            return false;
        }

        @Override
        public boolean duplicateSelectedNode() {
            return false;
        }

        @Override
        public void setNodeCustomColor(UUID nodeId, int color) {
        }

        @Override
        public Integer getNodeCustomColor(UUID nodeId) {
            return null;
        }

        @Override
        public void removeNodeCustomColor(UUID nodeId) {
        }

        @Override
        public boolean hasNodeCustomColor(UUID nodeId) {
            return false;
        }

        @Override
        public boolean toggleNodeDisabled(UUID nodeId) {
            return false;
        }

        @Override
        public void setNodeDisabled(UUID nodeId, boolean disabled) {
        }

        @Override
        public boolean isNodeDisabled(UUID nodeId) {
            return false;
        }

        @Override
        public boolean toggleNodeVisible(UUID nodeId) {
            return false;
        }

        @Override
        public void setNodeVisible(UUID nodeId, boolean visible) {
        }

        @Override
        public boolean isNodeVisible(UUID nodeId) {
            return true;
        }
    }
}

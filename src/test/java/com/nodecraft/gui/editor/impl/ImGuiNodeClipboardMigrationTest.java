package com.nodecraft.gui.editor.impl;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import imgui.ImVec2;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImGuiNodeClipboardMigrationTest {

    @AfterEach
    void tearDown() {
        NodeRegistry.getInstance().clear();
    }

    @Test
    void copySelectedNodesWritesCanonicalTypeIds() throws Exception {
        TestCanvasEditor editor = new TestCanvasEditor();
        NodeGraph graph = new NodeGraph("Clipboard Copy Graph");
        LegacyPreviewNode legacyNode = new LegacyPreviewNode();
        graph.addNode(legacyNode);
        editor.setCurrentGraph(graph);
        editor.getNodePositions().put(legacyNode.getId(), new NodePosition(10.0f, 20.0f));
        editor.getSelectedNodeIds().add(legacyNode.getId());

        ImGuiNodeClipboard clipboard = new ImGuiNodeClipboard(editor);

        assertTrue(clipboard.copySelectedNodes());

        String json = getInternalClipboardContent(clipboard);
        assertNotNull(json);
        assertTrue(json.contains("\"typeId\": \"output.preview.geometry_viewer\""));
        assertFalse(json.contains("visualization.preview.geometry_viewer"));
    }

    @Test
    void pasteNodesAcceptsLegacyTypeIdsThroughRegistryAliases() throws Exception {
        NodeRegistry registry = NodeRegistry.getInstance();
        registry.clear();
        registry.initialize();

        TestCanvasEditor editor = new TestCanvasEditor();
        editor.setCurrentGraph(new NodeGraph("Clipboard Paste Graph"));

        ImGuiNodeClipboard clipboard = new ImGuiNodeClipboard(editor);
        String legacyClipboardJson = """
            {
              "format": "application/nodecraft-nodes+json",
              "nodes": [
                { "id": "a", "typeId": "visualization.preview.geometry_viewer", "x": 10.0, "y": 20.0 },
                { "id": "b", "typeId": "logic.if", "x": 40.0, "y": 60.0 }
              ],
              "connections": []
            }
            """;
        setInternalClipboardContent(clipboard, legacyClipboardJson);

        assertTrue(clipboard.pasteNodes(100.0f, 200.0f));
        assertEquals(2, editor.getCurrentGraph().getNodes().size());

        Set<String> typeIds = new HashSet<>();
        for (INode node : editor.getCurrentGraph().getNodes()) {
            typeIds.add(node.getTypeId());
        }

        assertTrue(typeIds.contains("output.preview.geometry_viewer"));
        assertTrue(typeIds.contains("math.logic.if"));
        assertEquals(2, editor.getNodePositions().size());
    }

    private static String getInternalClipboardContent(ImGuiNodeClipboard clipboard) throws Exception {
        Field field = ImGuiNodeClipboard.class.getDeclaredField("internalClipboardContent");
        field.setAccessible(true);
        return (String) field.get(clipboard);
    }

    private static void setInternalClipboardContent(ImGuiNodeClipboard clipboard, String content) throws Exception {
        Field field = ImGuiNodeClipboard.class.getDeclaredField("internalClipboardContent");
        field.setAccessible(true);
        field.set(clipboard, content);
    }

    private static final class LegacyPreviewNode extends BaseNode {
        private LegacyPreviewNode() {
            super(UUID.randomUUID(), "visualization.preview.geometry_viewer");
        }

        @Override
        public String getDescription() {
            return "Legacy preview test node";
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            // No-op test node.
        }
    }

    private static final class TestCanvasEditor implements ICanvasEditor {
        private NodeGraph currentGraph;
        private final Map<UUID, NodePosition> nodePositions = new HashMap<>();
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
            nodePositions.clear();
            nodePositions.putAll(positions);
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
            return addNodeWithState(nodeTypeId, null, x, y, null);
        }

        @Override
        public INode addNodeWithState(String nodeTypeId, @Nullable UUID oldNodeId, float x, float y, @Nullable Object nodeState) {
            INode node = NodeRegistry.getInstance().createNodeInstance(nodeTypeId);
            currentGraph.addNode(node);
            nodePositions.put(node.getId(), new NodePosition(x, y));
            if (node instanceof BaseNode baseNode) {
                baseNode.setNodeState(nodeState);
            }
            return node;
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

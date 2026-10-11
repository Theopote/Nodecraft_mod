package com.nodecraft.gui.editor.impl;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.MethodAccessor;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.NodePropertyBindings;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImGuiNodeHistoryPropertyChangeTest {

    @Test
    void propertyChangeUndoRedoRestoresValue() throws Throwable {
        NodeGraph graph = new NodeGraph("property-history");
        WritableNode node = new WritableNode();
        graph.addNode(node);

        StubHistoryEditor editor = new StubHistoryEditor(graph);
        ImGuiNodeHistory history = editor.history();

        history.recordPropertyChange(node.getId(), "size", 1.0d, 2.0d);
        NodePropertyBindings.writeProperty(node, "size", 2.0d);

        assertTrue(history.canUndo());
        assertEquals(ImGuiNodeHistory.ActionType.PROPERTY_CHANGE, history.getUndoTopActionType());
        assertTrue(history.undo());
        assertEquals(1.0d, node.size, 0.0d);

        assertTrue(history.canRedo());
        assertTrue(history.redo());
        assertEquals(2.0d, node.size, 0.0d);
    }

    @Test
    void consecutiveChangesCoalesceIntoSingleUndoStep() throws Throwable {
        NodeGraph graph = new NodeGraph("coalesce");
        WritableNode node = new WritableNode();
        graph.addNode(node);

        StubHistoryEditor editor = new StubHistoryEditor(graph);
        ImGuiNodeHistory history = editor.history();

        history.recordPropertyChange(node.getId(), "size", 1.0d, 1.5d);
        history.recordPropertyChange(node.getId(), "size", 1.0d, 2.0d);
        history.recordPropertyChange(node.getId(), "size", 1.0d, 2.5d);

        assertEquals(1, history.undoStackSize());
        NodePropertyBindings.writeProperty(node, "size", 2.5d);
        assertTrue(history.undo());
        assertEquals(1.0d, node.size, 0.0d);
    }

    @Test
    void applyPropertyValueRecordsHistoryThroughPanel() throws Throwable {
        NodeGraph graph = new NodeGraph("panel-history");
        WritableNode node = new WritableNode();
        graph.addNode(node);

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        editor.setCurrentGraph(graph);
        editor.getHistory().clear();

        PropertyPanelComponent panel = new PropertyPanelComponent();
        panel.applyPropertyValue(node, sizePropertyDescriptor(), 4.0d);

        assertTrue(editor.getHistory().canUndo());
        assertEquals(ImGuiNodeHistory.ActionType.PROPERTY_CHANGE, editor.getHistory().getUndoTopActionType());
        assertTrue(editor.undo());
        assertEquals(0.0d, node.size, 0.0d);
    }

    private static PropertyDescriptor sizePropertyDescriptor() {
        MethodAccessor getter = new MethodAccessor() {
            @Override
            public Object invoke(Object obj, Object... args) {
                return ((WritableNode) obj).size;
            }

            @Override
            public Class<?> getReturnType() {
                return double.class;
            }

            @Override
            public Class<?>[] getParameterTypes() {
                return new Class<?>[0];
            }
        };
        MethodAccessor setter = new MethodAccessor() {
            @Override
            public Object invoke(Object obj, Object... args) {
                ((WritableNode) obj).size = (double) args[0];
                return null;
            }

            @Override
            public Class<?> getReturnType() {
                return void.class;
            }

            @Override
            public Class<?>[] getParameterTypes() {
                return new Class<?>[]{double.class};
            }
        };
        return new PropertyDescriptor("size", "Size", double.class, getter, setter, null, "", "General", 0);
    }

    private static final class WritableNode extends BaseNode {
        @NodeProperty(displayName = "Size")
        private double size;

        WritableNode() {
            super(UUID.randomUUID(), "test.property_history");
        }

        @Override
        public String getDisplayName() {
            return "Writable";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
        }
    }

    private static final class StubHistoryEditor implements ICanvasEditor {
        private final NodeGraph graph;
        private final ImGuiNodeHistory history;

        private StubHistoryEditor(NodeGraph graph) {
            this.graph = graph;
            this.history = new ImGuiNodeHistory(this);
        }

        ImGuiNodeHistory history() {
            return history;
        }

        @Override
        public NodeGraph getCurrentGraph() {
            return graph;
        }

        @Override
        public boolean undo() {
            return history.undo();
        }

        @Override
        public boolean redo() {
            return history.redo();
        }

        @Override
        public float getCanvasZoom() {
            return 1.0f;
        }

        @Override
        public float getCanvasOffsetX() {
            return 0.0f;
        }

        @Override
        public float getCanvasOffsetY() {
            return 0.0f;
        }

        @Override
        public UUID getSelectedNodeId() {
            return null;
        }

        @Override
        public void setSelectedNodeId(UUID nodeId) {
        }

        @Override
        public boolean connectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            return false;
        }

        @Override
        public boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            return false;
        }

        @Override
        public Map<UUID, NodePosition> getNodePositions() {
            return Map.of();
        }

        @Override
        public NodePosition getNodePosition(UUID nodeId) {
            return null;
        }

        @Override
        public void setCurrentGraph(NodeGraph graph) {
        }

        @Override
        public void setNodePositions(Map<UUID, NodePosition> positions) {
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
        }

        @Override
        public void clearSelectedNodes() {
        }

        @Override
        public void removeSelectedNode(UUID nodeId) {
        }

        @Override
        public void removeNodePosition(UUID nodeId) {
        }

        @Override
        public UUID getNodeIdUnderMouse(float mouseX, float mouseY) {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public com.nodecraft.nodesystem.api.INode addNode(String nodeTypeId, float x, float y) {
            return null;
        }

        @Override
        public com.nodecraft.nodesystem.api.INode addNodeWithState(
                String nodeTypeId,
                @Nullable UUID oldNodeId,
                float x,
                float y,
                @Nullable Object nodeState) {
            return null;
        }

        @Override
        public java.util.Set<UUID> getSelectedNodeIds() {
            return java.util.Set.of();
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
        public Map<UUID, Map<String, imgui.ImVec2>> getPortScreenPositions() {
            return Map.of();
        }

        @Override
        public ImGuiNodeIO getNodeIO() {
            return null;
        }

        @Override
        public ImGuiNodeHistory getHistory() {
            return history;
        }

        @Override
        public ImGuiNodeClipboard getClipboard() {
            return null;
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
        public void selectAllNodes() {
        }

        @Override
        public boolean createSubgraphFromSelection() {
            return false;
        }

        @Override
        public boolean openSelectedSubgraph() {
            return false;
        }

        @Override
        public boolean dissolveSelectedSubgraph() {
            return false;
        }

        @Override
        public boolean restoreGraphSnapshot(SavedGraph snapshot) {
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
        public boolean alignNodes(java.util.Set<UUID> nodeIds, NodeAlignmentAction action) {
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

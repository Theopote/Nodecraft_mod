package com.nodecraft.gui.editor.connection;

import com.nodecraft.gui.editor.document.EditorDocumentState;
import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeClipboard;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.impl.ImGuiNodeIO;
import com.nodecraft.gui.editor.impl.ImGuiNodeInteraction;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionEditServiceConnectTest {

    @Test
    void duplicateConnectSkipsHistoryAndStructureNotify() {
        Fixture fixture = Fixture.create();
        StubNode source = new StubNode("source");
        StubNode target = new StubNode("target");
        fixture.graph.addNode(source);
        fixture.graph.addNode(target);

        assertTrue(fixture.service.connect(source.getId(), "out", target.getId(), "in"));
        assertEquals(1, fixture.history.undoStackSize());
        assertEquals(1, fixture.structureDirtyCount.get());
        assertEquals(1, fixture.connectionAddedCount.get());
        assertEquals(1, fixture.graph.getConnections().size());

        assertTrue(fixture.service.connect(source.getId(), "out", target.getId(), "in"));
        assertEquals(1, fixture.history.undoStackSize());
        assertEquals(1, fixture.structureDirtyCount.get());
        assertEquals(1, fixture.connectionAddedCount.get());
        assertEquals(1, fixture.graph.getConnections().size());
    }

    @Test
    void disconnectMissingEdgeDoesNotRecordHistory() {
        Fixture fixture = Fixture.create();
        StubNode source = new StubNode("source");
        StubNode target = new StubNode("target");
        fixture.graph.addNode(source);
        fixture.graph.addNode(target);

        assertFalse(fixture.service.disconnect(source.getId(), "out", target.getId(), "in"));
        assertEquals(0, fixture.history.undoStackSize());
        assertEquals(0, fixture.structureDirtyCount.get());
    }

    @Test
    void connectUndoRedoRestoresEdge() {
        Fixture fixture = Fixture.create();
        StubNode source = new StubNode("source");
        StubNode target = new StubNode("target");
        fixture.graph.addNode(source);
        fixture.graph.addNode(target);

        assertTrue(fixture.service.connect(source.getId(), "out", target.getId(), "in"));
        assertEquals(1, fixture.graph.getConnections().size());
        assertTrue(fixture.history.canUndo());

        assertTrue(fixture.history.undo());
        assertTrue(fixture.graph.getConnections().isEmpty());

        assertTrue(fixture.history.canRedo());
        assertTrue(fixture.history.redo());
        assertEquals(1, fixture.graph.getConnections().size());
        assertTrue(fixture.graph.isConnected(source.getId(), "out", target.getId(), "in"));
    }

    @Test
    void cleanupDanglingRemovesEdgeAndNotifiesStructureDirty() {
        Fixture fixture = Fixture.create();
        StubNode source = new StubNode("source");
        DynamicPortNode target = new DynamicPortNode("target");
        fixture.graph.addNode(source);
        fixture.graph.addNode(target);

        assertTrue(fixture.service.connect(source.getId(), "out", target.getId(), "dyn"));
        assertEquals(1, fixture.graph.getConnections().size());
        fixture.structureDirtyCount.set(0);

        assertTrue(target.removeDynamicInput());
        fixture.service.cleanupDangling();

        assertTrue(fixture.graph.getConnections().isEmpty());
        assertEquals(1, fixture.structureDirtyCount.get());
    }

    @Test
    void cleanupDanglingNoOpWhenGraphClean() {
        Fixture fixture = Fixture.create();
        StubNode source = new StubNode("source");
        StubNode target = new StubNode("target");
        fixture.graph.addNode(source);
        fixture.graph.addNode(target);
        assertTrue(fixture.service.connect(source.getId(), "out", target.getId(), "in"));
        fixture.structureDirtyCount.set(0);

        fixture.service.cleanupDangling();

        assertEquals(1, fixture.graph.getConnections().size());
        assertEquals(0, fixture.structureDirtyCount.get());
    }

    private static final class Fixture {
        final NodeGraph graph;
        final ImGuiNodeHistory history;
        final ConnectionEditService service;
        final AtomicInteger structureDirtyCount = new AtomicInteger();
        final AtomicInteger connectionAddedCount = new AtomicInteger();

        private Fixture(
                NodeGraph graph,
                ImGuiNodeHistory history,
                ConnectionEditService service) {
            this.graph = graph;
            this.history = history;
            this.service = service;
        }

        static Fixture create() {
            NodeGraph graph = new NodeGraph("connection-edit");
            EditorDocumentState document = new EditorDocumentState();
            document.setGraph(graph);

            StubCanvasEditor editor = new StubCanvasEditor(graph);
            ImGuiNodeHistory history = editor.history();

            Fixture[] holder = new Fixture[1];
            ConnectionEditService.Host host = new ConnectionEditService.Host() {
                @Override
                public EditorDocumentState document() {
                    return document;
                }

                @Override
                public ImGuiNodeHistory history() {
                    return history;
                }

                @Override
                public void notifyStructureDirty() {
                    holder[0].structureDirtyCount.incrementAndGet();
                }

                @Override
                public void notifyConnectionAdded(Map<String, Object> eventData) {
                    holder[0].connectionAddedCount.incrementAndGet();
                }

                @Override
                public INode addNode(String nodeTypeId, float x, float y) {
                    return null;
                }

                @Override
                public void removeNodePosition(UUID nodeId) {
                }

                @Override
                public void clearSelectedNodes() {
                }

                @Override
                public void setSelectedNodeId(UUID nodeId) {
                }
            };

            ConnectionEditService service = new ConnectionEditService(host);
            Fixture fixture = new Fixture(graph, history, service);
            holder[0] = fixture;
            return fixture;
        }
    }

    /** Minimal canvas editor so connection undo/redo can mutate the graph. */
    private static final class StubCanvasEditor implements ICanvasEditor {
        private final NodeGraph graph;
        private final ImGuiNodeHistory history;

        private StubCanvasEditor(NodeGraph graph) {
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
        public boolean connectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            return graph.connect(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        }

        @Override
        public boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            return graph.disconnectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
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
        public INode addNode(String nodeTypeId, float x, float y) {
            return null;
        }

        @Override
        public INode addNodeWithState(
                String nodeTypeId,
                @Nullable UUID oldNodeId,
                float x,
                float y,
                @Nullable Object nodeState) {
            return null;
        }

        @Override
        public Set<UUID> getSelectedNodeIds() {
            return Set.of();
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
        public boolean alignNodes(Set<UUID> nodeIds, NodeAlignmentAction action) {
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

    private static final class StubNode extends BaseNode {
        StubNode(String name) {
            super(UUID.randomUUID(), "test.connect." + name);
            addOutputPort(new BasePort("out", "Out", "", NodeDataType.ANY, this));
            addInputPort(new BasePort("in", "In", "", NodeDataType.ANY, this));
        }

        @Override
        public String getDisplayName() {
            return "Stub";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
        }
    }

    private static final class DynamicPortNode extends BaseNode {
        DynamicPortNode(String name) {
            super(UUID.randomUUID(), "test.connect.dyn." + name);
            addInputPort(new BasePort("dyn", "Dyn", "", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "", NodeDataType.ANY, this));
        }

        boolean removeDynamicInput() {
            return removePortById("dyn", true);
        }

        @Override
        public String getDisplayName() {
            return "Dynamic";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
        }
    }
}

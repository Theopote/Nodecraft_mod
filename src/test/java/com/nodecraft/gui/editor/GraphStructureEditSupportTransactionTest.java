package com.nodecraft.gui.editor;

import com.nodecraft.gui.editor.document.EditorDocumentState;
import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeClipboard;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.impl.ImGuiNodeIO;
import com.nodecraft.gui.editor.impl.ImGuiNodeInteraction;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.gui.editor.subgraph.SubgraphEditService;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.GraphLoadResult;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.nodes.utilities.assist.RelayNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphStructureEditSupportTransactionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphStructureEditSupportTransactionTest.class);

    @BeforeAll
    static void initRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void successWhileRecordingProducesSingleGraphTransaction() {
        Fixture fixture = Fixture.create();
        RelayNode node = new RelayNode();
        fixture.document.getGraph().addNode(node);
        fixture.document.setNodePosition(node.getId(), new NodePosition(0, 0));
        fixture.history.clear();

        GraphStructureEditSupport.Outcome outcome = GraphStructureEditSupport.run(
                fixture.structureHost(),
                "Add Tag State",
                () -> {
                    node.setTag("tx");
                    return true;
                }
        );

        assertEquals(GraphStructureEditSupport.Outcome.SUCCESS, outcome);
        assertEquals(1, fixture.history.undoStackSize());
        assertEquals(ImGuiNodeHistory.ActionType.GRAPH_TRANSACTION, fixture.history.getUndoTopActionType());
        assertTrue(fixture.history.isRecording());
    }

    @Test
    void midFailureRollsBackGraphAndRecordsNoUndo() {
        Fixture fixture = Fixture.create();
        RelayNode a = new RelayNode();
        RelayNode b = new RelayNode();
        fixture.document.getGraph().addNode(a);
        fixture.document.getGraph().addNode(b);
        fixture.document.setNodePosition(a.getId(), new NodePosition(0, 0));
        fixture.document.setNodePosition(b.getId(), new NodePosition(40, 0));
        assertTrue(fixture.document.getGraph().connect(a.getId(), "output_signal", b.getId(), "input_signal"));
        fixture.history.clear();

        int nodesBefore = fixture.document.getGraph().getNodes().size();
        int edgesBefore = fixture.document.getGraph().getConnections().size();

        GraphStructureEditSupport.Outcome outcome = GraphStructureEditSupport.run(
                fixture.structureHost(),
                "Partial Fail",
                () -> {
                    RelayNode orphan = new RelayNode();
                    fixture.document.getGraph().addNode(orphan);
                    fixture.document.setNodePosition(orphan.getId(), new NodePosition(80, 0));
                    return false;
                }
        );

        assertEquals(GraphStructureEditSupport.Outcome.ABORTED_ROLLED_BACK, outcome);
        assertEquals(0, fixture.history.undoStackSize());
        assertEquals(nodesBefore, fixture.document.getGraph().getNodes().size());
        assertEquals(edgesBefore, fixture.document.getGraph().getConnections().size());
    }

    @Test
    void failureWhileHistoryPausedStillRestoresAndKeepsPaused() {
        Fixture fixture = Fixture.create();
        RelayNode node = new RelayNode();
        fixture.document.getGraph().addNode(node);
        fixture.document.setNodePosition(node.getId(), new NodePosition(0, 0));
        fixture.history.clear();
        fixture.history.pauseRecording();

        int nodesBefore = fixture.document.getGraph().getNodes().size();
        GraphStructureEditSupport.Outcome outcome = GraphStructureEditSupport.run(
                fixture.structureHost(),
                "Paused Fail",
                () -> {
                    fixture.document.getGraph().addNode(new RelayNode());
                    return false;
                }
        );

        assertEquals(GraphStructureEditSupport.Outcome.ABORTED_ROLLED_BACK, outcome);
        assertEquals(nodesBefore, fixture.document.getGraph().getNodes().size());
        assertEquals(0, fixture.history.undoStackSize());
        assertFalse(fixture.history.isRecording());
    }

    @Test
    void failureWithNullHistoryStillRestores() {
        Fixture fixture = Fixture.create();
        RelayNode node = new RelayNode();
        fixture.document.getGraph().addNode(node);
        fixture.document.setNodePosition(node.getId(), new NodePosition(0, 0));
        int nodesBefore = fixture.document.getGraph().getNodes().size();

        GraphStructureEditSupport.Outcome outcome = GraphStructureEditSupport.run(
                fixture.nullHistoryHost(),
                "Null History Fail",
                () -> {
                    fixture.document.getGraph().addNode(new RelayNode());
                    return false;
                }
        );

        assertEquals(GraphStructureEditSupport.Outcome.ABORTED_ROLLED_BACK, outcome);
        assertEquals(nodesBefore, fixture.document.getGraph().getNodes().size());
    }

    @Test
    void restoreDocumentFalseThrowsRollbackException() {
        Fixture fixture = Fixture.create();
        RelayNode node = new RelayNode();
        fixture.document.getGraph().addNode(node);
        fixture.document.setNodePosition(node.getId(), new NodePosition(0, 0));

        assertThrows(StructureEditRollbackException.class, () -> GraphStructureEditSupport.run(
                fixture.failingRestoreHost(),
                "Restore Fail",
                () -> false
        ));
    }

    @Test
    void successNotifiesStructureDirtyOnce() {
        Fixture fixture = Fixture.create();
        RelayNode node = new RelayNode();
        fixture.document.getGraph().addNode(node);
        fixture.document.setNodePosition(node.getId(), new NodePosition(0, 0));

        GraphStructureEditSupport.Outcome outcome = GraphStructureEditSupport.run(
                fixture.structureHost(),
                "Dirty Notify",
                () -> {
                    node.setTag("dirty");
                    return true;
                }
        );

        assertEquals(GraphStructureEditSupport.Outcome.SUCCESS, outcome);
        assertEquals(1, fixture.structureDirtyCount.get());
    }

    @Test
    void abortDoesNotNotifyStructureDirty() {
        Fixture fixture = Fixture.create();
        RelayNode node = new RelayNode();
        fixture.document.getGraph().addNode(node);
        fixture.document.setNodePosition(node.getId(), new NodePosition(0, 0));

        GraphStructureEditSupport.Outcome outcome = GraphStructureEditSupport.run(
                fixture.structureHost(),
                "Dirty Abort",
                () -> false
        );

        assertEquals(GraphStructureEditSupport.Outcome.ABORTED_ROLLED_BACK, outcome);
        assertEquals(0, fixture.structureDirtyCount.get());
    }

    @Test
    void emptyGraphMutationFailureRestoresEmptyGraph() {
        Fixture fixture = Fixture.create();
        assertEquals(0, fixture.document.getGraph().getNodes().size());
        fixture.history.clear();

        GraphStructureEditSupport.Outcome outcome = GraphStructureEditSupport.run(
                fixture.structureHost(),
                "Empty Fail",
                () -> {
                    RelayNode orphan = new RelayNode();
                    fixture.document.getGraph().addNode(orphan);
                    fixture.document.setNodePosition(orphan.getId(), new NodePosition(0, 0));
                    return false;
                }
        );

        assertEquals(GraphStructureEditSupport.Outcome.ABORTED_ROLLED_BACK, outcome);
        assertEquals(0, fixture.document.getGraph().getNodes().size());
        assertTrue(fixture.document.getNodePositions().isEmpty());
        assertEquals(0, fixture.history.undoStackSize());
        assertTrue(fixture.history.isRecording());
    }

    @Test
    void afterSnapshotFailureRollsBackMutation() {
        Fixture fixture = Fixture.create();
        RelayNode node = new RelayNode();
        fixture.document.getGraph().addNode(node);
        fixture.document.setNodePosition(node.getId(), new NodePosition(0, 0));
        fixture.history.clear();
        int nodesBefore = fixture.document.getGraph().getNodes().size();
        fixture.failAfterSnapshotOnNthCapture = 2;

        GraphStructureEditSupport.Outcome outcome = GraphStructureEditSupport.run(
                fixture.structureHost(),
                "After Snapshot Fail",
                () -> {
                    fixture.document.getGraph().addNode(new RelayNode());
                    return true;
                }
        );

        assertEquals(GraphStructureEditSupport.Outcome.ABORTED_ROLLED_BACK, outcome);
        assertEquals(nodesBefore, fixture.document.getGraph().getNodes().size());
        assertEquals(0, fixture.history.undoStackSize());
        assertEquals(0, fixture.structureDirtyCount.get());
        assertTrue(fixture.history.isRecording());
    }

    @Test
    void notifyFailureRollsBackWithoutHistory() {
        Fixture fixture = Fixture.create();
        RelayNode node = new RelayNode();
        fixture.document.getGraph().addNode(node);
        fixture.document.setNodePosition(node.getId(), new NodePosition(0, 0));
        fixture.history.clear();
        int nodesBefore = fixture.document.getGraph().getNodes().size();
        fixture.throwOnNotify = true;

        GraphStructureEditSupport.Outcome outcome = GraphStructureEditSupport.run(
                fixture.structureHost(),
                "Notify Fail",
                () -> {
                    node.setTag("notify-fail");
                    return true;
                }
        );

        assertEquals(GraphStructureEditSupport.Outcome.ABORTED_ROLLED_BACK, outcome);
        assertEquals(nodesBefore, fixture.document.getGraph().getNodes().size());
        assertNull(findRelayByTag(fixture.document.getGraph(), "notify-fail"));
        assertEquals(0, fixture.history.undoStackSize());
        assertTrue(fixture.history.isRecording());
    }

    @Test
    void historyCommitFailureKeepsMutationWithoutUndo() {
        Fixture fixture = Fixture.createWithThrowingHistory();
        RelayNode node = new RelayNode();
        fixture.document.getGraph().addNode(node);
        fixture.document.setNodePosition(node.getId(), new NodePosition(0, 0));
        fixture.history.clear();

        GraphStructureEditSupport.Outcome outcome = GraphStructureEditSupport.run(
                fixture.structureHost(),
                "History Commit Fail",
                () -> {
                    node.setTag("history-fail");
                    return true;
                }
        );

        // History recording failed, but the graph edit must stick (Delete must not snap back).
        assertEquals(GraphStructureEditSupport.Outcome.SUCCESS, outcome);
        assertNotNull(findRelayByTag(fixture.document.getGraph(), "history-fail"));
        assertEquals(0, fixture.history.undoStackSize(), "orphan undo pushed before throw must be discarded");
        assertEquals(0, fixture.history.redoStackSize());
        assertTrue(fixture.history.isRecording());
    }

    @Test
    void nestedSuccessKeepsOuterPausedRecordingState() {
        Fixture fixture = Fixture.create();
        RelayNode node = new RelayNode();
        fixture.document.getGraph().addNode(node);
        fixture.document.setNodePosition(node.getId(), new NodePosition(0, 0));
        fixture.history.clear();
        fixture.history.pauseRecording();

        GraphStructureEditSupport.Outcome outcome = GraphStructureEditSupport.run(
                fixture.structureHost(),
                "Nested Success",
                () -> {
                    node.setTag("kept-paused");
                    return true;
                }
        );

        assertEquals(GraphStructureEditSupport.Outcome.SUCCESS, outcome);
        assertEquals(0, fixture.history.undoStackSize());
        assertFalse(fixture.history.isRecording());
    }

    @Test
    void mutationExceptionRollsBack() {
        Fixture fixture = Fixture.create();
        RelayNode node = new RelayNode();
        fixture.document.getGraph().addNode(node);
        fixture.document.setNodePosition(node.getId(), new NodePosition(0, 0));
        fixture.history.clear();
        int nodesBefore = fixture.document.getGraph().getNodes().size();

        GraphStructureEditSupport.Outcome outcome = GraphStructureEditSupport.run(
                fixture.structureHost(),
                "Throw Fail",
                () -> {
                    fixture.document.getGraph().addNode(new RelayNode());
                    throw new IllegalStateException("boom");
                }
        );

        assertEquals(GraphStructureEditSupport.Outcome.ABORTED_ROLLED_BACK, outcome);
        assertEquals(nodesBefore, fixture.document.getGraph().getNodes().size());
        assertEquals(0, fixture.history.undoStackSize());
        assertTrue(fixture.history.isRecording());
    }

    @Test
    void largeGraphSnapshotCompletesUnderBudget() {
        EditorDocumentState document = new EditorDocumentState();
        NodeGraph graph = new NodeGraph("perf-500");
        document.setGraph(graph);

        RelayNode prev = null;
        for (int i = 0; i < 500; i++) {
            RelayNode relay = new RelayNode();
            graph.addNode(relay);
            document.setNodePosition(relay.getId(), new NodePosition(i * 10f, 0));
            if (prev != null) {
                assertTrue(graph.connect(prev.getId(), "output_signal", relay.getId(), "input_signal"));
            }
            prev = relay;
        }

        long start = System.nanoTime();
        SavedGraph snapshot = GraphStructureEditSupport.snapshotDocument(document);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        LOGGER.info("500-relay snapshotDocument took {} ms", elapsedMs);

        assertNotNull(snapshot);
        assertNotNull(snapshot.nodes);
        assertEquals(500, snapshot.nodes.size());
        assertTrue(elapsedMs < 5_000L, "snapshot took " + elapsedMs + "ms");
    }

    @Nullable
    private static RelayNode findRelayByTag(NodeGraph graph, String tag) {
        return graph.getNodes().stream()
                .filter(RelayNode.class::isInstance)
                .map(RelayNode.class::cast)
                .filter(n -> tag.equals(n.getTag()))
                .findFirst()
                .orElse(null);
    }

    private static final class Fixture {
        final EditorDocumentState document;
        final ImGuiNodeHistory history;
        final DocumentEditor editor;
        final AtomicInteger structureDirtyCount = new AtomicInteger();
        final AtomicInteger captureCount = new AtomicInteger();
        int failAfterSnapshotOnNthCapture = -1;
        boolean throwOnNotify;

        private Fixture(EditorDocumentState document, ImGuiNodeHistory history, DocumentEditor editor) {
            this.document = document;
            this.history = history;
            this.editor = editor;
        }

        static Fixture create() {
            EditorDocumentState document = new EditorDocumentState();
            document.setGraph(new NodeGraph("tx"));
            DocumentEditor editor = new DocumentEditor(document);
            return new Fixture(document, editor.history(), editor);
        }

        static Fixture createWithThrowingHistory() {
            EditorDocumentState document = new EditorDocumentState();
            document.setGraph(new NodeGraph("tx"));
            DocumentEditor editor = new DocumentEditor(document, true);
            return new Fixture(document, editor.history(), editor);
        }

        GraphStructureEditSupport.Host structureHost() {
            return host(history, true);
        }

        GraphStructureEditSupport.Host nullHistoryHost() {
            return host(null, true);
        }

        GraphStructureEditSupport.Host failingRestoreHost() {
            return host(history, false);
        }

        private GraphStructureEditSupport.Host host(@Nullable ImGuiNodeHistory historyOverride, boolean restoreOk) {
            return new GraphStructureEditSupport.Host() {
                @Override
                public EditorDocumentState document() {
                    return document;
                }

                @Override
                public ImGuiNodeHistory history() {
                    return historyOverride;
                }

                @Override
                public boolean restoreDocument(SavedGraph snapshot) {
                    if (!restoreOk) {
                        return false;
                    }
                    return editor.restoreGraphSnapshot(snapshot);
                }

                @Override
                public SavedGraph captureSnapshot() {
                    int n = captureCount.incrementAndGet();
                    if (failAfterSnapshotOnNthCapture > 0 && n == failAfterSnapshotOnNthCapture) {
                        throw new IllegalStateException("after snapshot boom");
                    }
                    return GraphStructureEditSupport.Host.super.captureSnapshot();
                }

                @Override
                public void notifyStructureDirty() {
                    if (throwOnNotify) {
                        throw new IllegalStateException("notify boom");
                    }
                    structureDirtyCount.incrementAndGet();
                }
            };
        }
    }

    private static final class DocumentEditor implements ICanvasEditor {
        private final EditorDocumentState document;
        private final ImGuiNodeHistory history;

        private DocumentEditor(EditorDocumentState document) {
            this(document, false);
        }

        private DocumentEditor(EditorDocumentState document, boolean throwingHistory) {
            this.document = document;
            this.history = throwingHistory ? new ThrowingRecordHistory(this) : new ImGuiNodeHistory(this);
        }

        ImGuiNodeHistory history() {
            return history;
        }

        @Override
        public NodeGraph getCurrentGraph() {
            return document.getGraph();
        }

        @Override
        public boolean restoreGraphSnapshot(SavedGraph snapshot) {
            if (snapshot == null) {
                return false;
            }
            GraphLoadResult loadResult = GraphSerializer.loadFromSavedGraph(snapshot);
            if (!loadResult.hasLoadedNodes() && !GraphSerializer.isEmptyNodeList(snapshot)) {
                return false;
            }
            SubgraphEditService.LoadedGraph loaded = SubgraphEditService.toLoadedGraph(snapshot, loadResult);
            document.setGraph(loaded.graph());
            document.replaceNodePositions(loaded.positions());
            document.applySavedGraphMetadata(snapshot);
            return true;
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
            return document.getGraph().connect(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        }

        @Override
        public boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            return document.getGraph().disconnectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
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
            return document.getNodePositions();
        }

        @Override
        public NodePosition getNodePosition(UUID nodeId) {
            return document.getNodePosition(nodeId);
        }

        @Override
        public void setCurrentGraph(NodeGraph graph) {
            document.setGraph(graph);
        }

        @Override
        public void setNodePositions(Map<UUID, NodePosition> positions) {
            document.replaceNodePositions(positions);
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
            document.clearNodePositions();
        }

        @Override
        public void clearSelectedNodes() {
        }

        @Override
        public void removeSelectedNode(UUID nodeId) {
        }

        @Override
        public void removeNodePosition(UUID nodeId) {
            document.removeNodePosition(nodeId);
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

    private static final class ThrowingRecordHistory extends ImGuiNodeHistory {
        private ThrowingRecordHistory(ICanvasEditor editor) {
            super(editor);
        }

        @Override
        public void recordGraphTransaction(String label, SavedGraph before, SavedGraph after) {
            // Write first, then fail — exercises discard of the orphan undo entry on commit abort.
            super.recordGraphTransaction(label, before, after);
            throw new IllegalStateException("history commit boom after write");
        }
    }
}

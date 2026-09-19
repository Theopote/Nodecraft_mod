package com.nodecraft.gui.editor.command;

import com.nodecraft.gui.editor.document.EditorDocumentState;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.gui.editor.interaction.EditorInteractionState;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCommandServiceDeleteTest {

    @Test
    void deleteSelectedRemovesNodesPositionsAndSelection() {
        EditorDocumentState document = new EditorDocumentState();
        EditorInteractionState interaction = new EditorInteractionState();
        NodeGraph graph = new NodeGraph("delete");
        StubNode keep = new StubNode("keep");
        StubNode drop = new StubNode("drop");
        graph.addNode(keep);
        graph.addNode(drop);
        document.setGraph(graph);
        document.getNodePositions().put(keep.getId(), new NodePosition(0, 0));
        document.getNodePositions().put(drop.getId(), new NodePosition(40, 0));
        interaction.getSelectedNodeIds().add(drop.getId());

        AtomicInteger dirtyCalls = new AtomicInteger();
        NodeCommandService service = new NodeCommandService(host(document, interaction, dirtyCalls));

        assertTrue(service.deleteSelected());
        assertNull(graph.getNode(drop.getId()));
        assertEquals(keep, graph.getNode(keep.getId()));
        assertNull(document.getNodePosition(drop.getId()));
        assertFalse(interaction.getSelectedNodeIds().contains(drop.getId()));
        assertEquals(1, dirtyCalls.get());
    }

    @Test
    void deleteSelectedReturnsFalseWhenNothingSelected() {
        EditorDocumentState document = new EditorDocumentState();
        document.setGraph(new NodeGraph("empty-selection"));
        EditorInteractionState interaction = new EditorInteractionState();
        AtomicInteger dirtyCalls = new AtomicInteger();
        NodeCommandService service = new NodeCommandService(host(document, interaction, dirtyCalls));

        assertFalse(service.deleteSelected());
        assertEquals(0, dirtyCalls.get());
    }

    private static NodeCommandService.Host host(
            EditorDocumentState document,
            EditorInteractionState interaction,
            AtomicInteger dirtyCalls
    ) {
        return new NodeCommandService.Host() {
            @Override
            public EditorDocumentState document() {
                return document;
            }

            @Override
            public EditorInteractionState interaction() {
                return interaction;
            }

            @Override
            public ImGuiNodeHistory history() {
                return null;
            }

            @Override
            public void notifyStructureDirty() {
                dirtyCalls.incrementAndGet();
            }

            @Override
            public void notifyNodeAdded(Map<String, Object> eventData) {
            }

            @Override
            public void clearSelectedNodes() {
                interaction.clearSelection();
            }

            @Override
            public void setSelectedNodeId(UUID nodeId) {
                interaction.setPrimarySelectedNodeId(nodeId);
            }

            @Override
            public void removeNodePosition(UUID nodeId) {
                document.getNodePositions().remove(nodeId);
            }

            @Override
            public void removeSelectedNode(UUID nodeId) {
                interaction.removeFromSelection(nodeId);
            }
        };
    }

    private static final class StubNode extends BaseNode {
        StubNode(String name) {
            super(UUID.randomUUID(), "test.stub." + name);
            addOutputPort(new BasePort("out", "Out", "", NodeDataType.ANY, this));
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
}

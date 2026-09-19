package com.nodecraft.gui.editor.connection;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConnectionEditServicePreviewTest {

    @Test
    void rejectsSameDirectionPorts() {
        NodeGraph graph = new NodeGraph("preview");
        StubNode a = new StubNode("a");
        StubNode b = new StubNode("b");
        graph.addNode(a);
        graph.addNode(b);

        ConnectionEditService service = new ConnectionEditService(unusedHost());
        String reason = service.previewInvalidReason(
                graph,
                new ConnectionEditService.DragPreview(a.getId(), "out", true, b.getId(), "out", true)
        );
        assertEquals("只能连接到输入端", reason);
    }

    @Test
    void acceptsCompatibleWire() {
        NodeGraph graph = new NodeGraph("preview");
        StubNode a = new StubNode("a");
        StubNode b = new StubNode("b");
        graph.addNode(a);
        graph.addNode(b);

        ConnectionEditService service = new ConnectionEditService(unusedHost());
        assertNull(service.previewInvalidReason(
                graph,
                new ConnectionEditService.DragPreview(a.getId(), "out", true, b.getId(), "in", false)
        ));
    }

    @Test
    void findPortResolvesBySide() {
        StubNode node = new StubNode("n");
        assertNotNull(ConnectionEditService.findPort(node, "out", true));
        assertNull(ConnectionEditService.findPort(node, "out", false));
        assertNotNull(ConnectionEditService.findPort(node, "in", false));
    }

    private static ConnectionEditService.Host unusedHost() {
        return new ConnectionEditService.Host() {
            @Override
            public com.nodecraft.gui.editor.document.EditorDocumentState document() {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.nodecraft.gui.editor.impl.ImGuiNodeHistory history() {
                return null;
            }

            @Override
            public void notifyStructureDirty() {
            }

            @Override
            public void notifyConnectionAdded(java.util.Map<String, Object> eventData) {
            }

            @Override
            public com.nodecraft.nodesystem.api.INode addNode(String nodeTypeId, float x, float y) {
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
    }

    private static final class StubNode extends BaseNode {
        StubNode(String name) {
            super(UUID.randomUUID(), "test.stub." + name);
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
}

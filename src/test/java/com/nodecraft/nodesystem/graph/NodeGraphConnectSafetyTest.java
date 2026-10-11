package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeGraphConnectSafetyTest {

    @Test
    void connectToFalseRollsBackIndexWithoutClearingCache() {
        NodeGraph graph = new NodeGraph("connect-false");
        // connect() invokes sourcePort.connectTo(targetPort) — rejection must be on the source output.
        RejectingSource source = new RejectingSource("source");
        TestNode target = new TestNode("target");
        graph.addNode(source);
        graph.addNode(target);

        graph.getExecutionCache().record(source);
        assertEquals(1, graph.getExecutionCache().size());

        assertFalse(graph.connect(source.getId(), "out", target.getId(), "in"));
        assertTrue(graph.getConnections().isEmpty());
        assertTrue(graph.getOutgoingConnections(source.getId()).isEmpty());
        assertTrue(graph.getIncomingConnections(target.getId()).isEmpty());
        assertFalse(source.getOutputPorts().getFirst().isConnected());
        assertFalse(target.getInputPorts().getFirst().isConnected());
        assertEquals(1, graph.getExecutionCache().size(), "failed connect must not clear execution cache");
    }

    @Test
    void connectToExceptionRollsBackWithoutHalfLink() {
        NodeGraph graph = new NodeGraph("connect-throw");
        ThrowingSource source = new ThrowingSource("source");
        TestNode target = new TestNode("target");
        graph.addNode(source);
        graph.addNode(target);

        graph.getExecutionCache().record(target);
        assertEquals(1, graph.getExecutionCache().size());

        assertFalse(graph.connect(source.getId(), "out", target.getId(), "in"));
        assertTrue(graph.getConnections().isEmpty());
        assertTrue(graph.getOutgoingConnections(source.getId()).isEmpty());
        assertTrue(graph.getIncomingConnections(target.getId()).isEmpty());
        assertFalse(source.getOutputPorts().getFirst().isConnected());
        assertFalse(target.getInputPorts().getFirst().isConnected());
        assertEquals(1, graph.getExecutionCache().size(), "exception path must not clear execution cache");
    }

    @Test
    void duplicateConnectRemainsIdempotent() {
        NodeGraph graph = new NodeGraph("idempotent");
        TestNode source = new TestNode("source");
        TestNode target = new TestNode("target");
        graph.addNode(source);
        graph.addNode(target);

        assertTrue(graph.connect(source.getId(), "out", target.getId(), "in"));
        assertTrue(graph.connect(source.getId(), "out", target.getId(), "in"));
        assertEquals(1, graph.getConnections().size());
    }

    private static final class TestNode extends BaseNode {
        private TestNode(String suffix) {
            super(UUID.randomUUID(), "test.connect.safety." + suffix);
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable com.nodecraft.nodesystem.execution.ExecutionContext context) {
            outputValues.put("out", inputValues.get("in"));
        }
    }

    private static final class RejectingSource extends BaseNode {
        private RejectingSource(String suffix) {
            super(UUID.randomUUID(), "test.connect.reject." + suffix);
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new RejectingPort("out", this));
        }

        @Override
        public void processNode(@Nullable com.nodecraft.nodesystem.execution.ExecutionContext context) {
        }
    }

    private static final class ThrowingSource extends BaseNode {
        private ThrowingSource(String suffix) {
            super(UUID.randomUUID(), "test.connect.throw." + suffix);
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new ThrowingPort("out", this));
        }

        @Override
        public void processNode(@Nullable com.nodecraft.nodesystem.execution.ExecutionContext context) {
        }
    }

    private static final class RejectingPort extends BasePort {
        private RejectingPort(String id, BaseNode node) {
            super(id, id, "", NodeDataType.ANY, node);
        }

        @Override
        public boolean connectTo(IPort targetPort) {
            return false;
        }
    }

    private static final class ThrowingPort extends BasePort {
        private ThrowingPort(String id, BaseNode node) {
            super(id, id, "", NodeDataType.ANY, node);
        }

        @Override
        public boolean connectTo(IPort targetPort) {
            throw new IllegalStateException("simulated port link failure");
        }
    }
}

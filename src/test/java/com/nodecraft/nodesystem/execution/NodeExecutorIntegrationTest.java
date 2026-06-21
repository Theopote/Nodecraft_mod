package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.math.logic.IfNode;
import com.nodecraft.nodesystem.nodes.variable.GetVariableNode;
import com.nodecraft.nodesystem.nodes.variable.SetVariableNode;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeExecutorIntegrationTest {

    @Test
    void executesLinearGraphInTopologicalOrder() {
        NodeGraph graph = new NodeGraph("linear");
        PassThroughNode source = new PassThroughNode("source", "alpha");
        PassThroughNode sink = new PassThroughNode("sink", null);
        graph.addNode(source);
        graph.addNode(sink);
        graph.connect(source.getId(), "out", sink.getId(), "in");

        assertTrue(new NodeExecutor(graph).executeSync());
        assertEquals("alpha", sink.getOutput("out"));
    }

    @Test
    void rejectsCyclicGraphs() {
        NodeGraph graph = new NodeGraph("cycle");
        PassThroughNode nodeA = new PassThroughNode("a", null);
        PassThroughNode nodeB = new PassThroughNode("b", null);
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.connect(nodeA.getId(), "out", nodeB.getId(), "in");
        graph.connect(nodeB.getId(), "out", nodeA.getId(), "in");

        assertFalse(new NodeExecutor(graph).executeSync());
    }

    @Test
    void ifNodeReceivesWiredInputsThroughExecutor() {
        NodeGraph graph = new NodeGraph("if");
        PassThroughNode trueValue = new PassThroughNode("true_value", "yes");
        PassThroughNode falseValue = new PassThroughNode("false_value", "no");
        PassThroughNode condition = new PassThroughNode("condition", Boolean.TRUE);
        IfNode ifNode = new IfNode();

        graph.addNode(trueValue);
        graph.addNode(falseValue);
        graph.addNode(condition);
        graph.addNode(ifNode);

        graph.connect(trueValue.getId(), "out", ifNode.getId(), "input_true_value");
        graph.connect(falseValue.getId(), "out", ifNode.getId(), "input_false_value");
        graph.connect(condition.getId(), "out", ifNode.getId(), "input_condition");

        assertTrue(new NodeExecutor(graph).executeSync());
        assertEquals("yes", ifNode.getOutput("output_result"));
    }

    @Test
    void partialExecutionReusesUpstreamOutputsWithoutRecomputing() {
        NodeGraph graph = new NodeGraph("partial");
        CountingNode upstream = new CountingNode("upstream");
        PassThroughNode downstream = new PassThroughNode("downstream", null);
        graph.addNode(upstream);
        graph.addNode(downstream);
        graph.connect(upstream.getId(), "out", downstream.getId(), "in");

        NodeExecutor fullRun = new NodeExecutor(graph);
        assertTrue(fullRun.executeSync());
        assertEquals(1, upstream.executionCount());
        assertEquals("tick-1", downstream.getOutput("out"));

        upstream.setPayload("tick-2");
        NodeExecutor partialRun = new NodeExecutor(graph, null, Set.of(downstream.getId()));
        assertTrue(partialRun.executeSync());
        assertEquals(1, upstream.executionCount(), "upstream should not re-run during partial execution");
        assertEquals("tick-1", downstream.getOutput("out"), "partial run should reuse cached upstream output");
    }

    @Test
    void recordsExecutionProfileAfterSuccessfulRun() {
        NodeGraph graph = new NodeGraph("profile");
        PassThroughNode source = new PassThroughNode("source", "value");
        PassThroughNode sink = new PassThroughNode("sink", null);
        graph.addNode(source);
        graph.addNode(sink);
        graph.connect(source.getId(), "out", sink.getId(), "in");

        NodeExecutor executor = new NodeExecutor(graph);
        assertTrue(executor.executeSync());

        ExecutionProfiler.Profile profile = executor.getLastExecutionProfile();
        assertEquals(2, profile.executedNodeCount());
        assertTrue(profile.totalNanos() >= 0L);
        assertEquals(2, profile.nodeTimings().size());
    }

    @Test
    void setAndGetVariableRoundTripWithinSingleExecution() {
        NodeGraph graph = new NodeGraph("variables");
        PassThroughNode value = new PassThroughNode("value", 42);
        SetVariableNode setVariable = new SetVariableNode();
        setVariable.setNodeState(Map.of("defaultName", "answer"));
        GetVariableNode getVariable = new GetVariableNode();
        getVariable.setNodeState(Map.of("defaultName", "answer"));

        graph.addNode(value);
        graph.addNode(setVariable);
        graph.addNode(getVariable);
        graph.connect(value.getId(), "out", setVariable.getId(), "input_value");
        graph.connect(setVariable.getId(), "output_value", getVariable.getId(), "input_default_value");

        ExecutionContext context = ExecutionContext.createEmpty(null);
        assertTrue(new NodeExecutor(graph, context).executeSync());
        assertEquals(42, getVariable.getOutput("output_value"));
        assertEquals(true, getVariable.getOutput("output_exists"));
    }

    private static final class PassThroughNode extends BaseNode {
        private Object payload;

        private PassThroughNode(String suffix, Object payload) {
            super(java.util.UUID.randomUUID(), "test.pass." + suffix);
            this.payload = payload;
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            Object incoming = inputValues.get("in");
            outputValues.put("out", incoming != null ? incoming : payload);
        }
    }

    private static final class CountingNode extends BaseNode {
        private final AtomicInteger executions = new AtomicInteger();
        private String payload = "tick-1";

        private CountingNode(String suffix) {
            super(java.util.UUID.randomUUID(), "test.count." + suffix);
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.STRING, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            executions.incrementAndGet();
            outputValues.put("out", payload);
        }

        int executionCount() {
            return executions.get();
        }

        void setPayload(String payload) {
            this.payload = payload;
        }
    }
}

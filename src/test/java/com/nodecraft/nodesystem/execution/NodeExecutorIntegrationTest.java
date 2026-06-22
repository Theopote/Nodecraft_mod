package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.math.logic.IfNode;
import com.nodecraft.nodesystem.nodes.flow.loop.WhileLoopNode;
import com.nodecraft.nodesystem.nodes.variable.GetVariableNode;
import com.nodecraft.nodesystem.nodes.variable.SetVariableNode;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    void partialExecutionSkipsCachedCleanNodesInScopeWhenPreviewOptionsEnabled() {
        NodeGraph graph = new NodeGraph("partial-cache-skip");
        CountingNode upstream = new CountingNode("upstream");
        CountingNode middle = new CountingNode("middle");
        CountingNode downstream = new CountingNode("downstream");
        graph.addNode(upstream);
        graph.addNode(middle);
        graph.addNode(downstream);
        graph.connect(upstream.getId(), "out", middle.getId(), "in");
        graph.connect(middle.getId(), "out", downstream.getId(), "in");

        NodeExecutor fullRun = new NodeExecutor(graph);
        assertTrue(fullRun.executeSync());
        assertEquals(1, upstream.executionCount());
        assertEquals(1, middle.executionCount());
        assertEquals(1, downstream.executionCount());

        Set<UUID> unchangedScope = Set.of(middle.getId(), downstream.getId());
        NodeExecutor partialRun = new NodeExecutor(
                graph,
                null,
                unchangedScope,
                IncrementalExecutionOptions.previewDefaults()
        );
        assertTrue(partialRun.executeSync());
        assertEquals(1, upstream.executionCount());
        assertEquals(1, middle.executionCount(), "clean cached nodes in scope should be skipped");
        assertEquals(1, downstream.executionCount(), "clean cached nodes in scope should be skipped");
    }

    @Test
    void partialExecutionWithPreviewOptionsRecomputesDirtyUpstreamChain() {
        NodeGraph graph = new NodeGraph("partial-cache-chain");
        CountingNode upstream = new CountingNode("upstream");
        CountingNode middle = new CountingNode("middle");
        CountingNode downstream = new CountingNode("downstream");
        graph.addNode(upstream);
        graph.addNode(middle);
        graph.addNode(downstream);
        graph.connect(upstream.getId(), "out", middle.getId(), "in");
        graph.connect(middle.getId(), "out", downstream.getId(), "in");

        assertTrue(new NodeExecutor(graph).executeSync());

        upstream.markDirty();
        Set<UUID> dirtyScope = IncrementalExecutionPlanner.resolveInvalidationScope(graph, upstream.getId());
        assertEquals(3, dirtyScope.size(), "scope should include upstream, middle, and downstream");
        assertTrue(dirtyScope.contains(middle.getId()), "middle should be in invalidation scope");
        NodeExecutor partialRun = new NodeExecutor(
                graph,
                null,
                dirtyScope,
                IncrementalExecutionOptions.previewDefaults()
        );
        assertTrue(partialRun.executeSync());
        assertEquals(2, upstream.executionCount(), "upstream");
        assertEquals(2, middle.executionCount(), "middle");
        assertEquals(2, downstream.executionCount(), "downstream");
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

    @Test
    void partialExecExecFlowRecomputesCachedLoopNodesDespitePreviewCacheSkip() {
        NodeGraph graph = new NodeGraph("partial-exec-while");
        PassThroughNode condition = new PassThroughNode("condition", Boolean.TRUE);
        ExecCountingStepNode entry = new ExecCountingStepNode("entry");
        WhileLoopNode whileLoop = new WhileLoopNode();
        ExecCountingStepNode bodyStep = new ExecCountingStepNode("body");

        graph.addNode(condition);
        graph.addNode(entry);
        graph.addNode(whileLoop);
        graph.addNode(bodyStep);

        graph.connect(entry.getId(), "exec_out", whileLoop.getId(), "exec_in");
        graph.connect(condition.getId(), "out", whileLoop.getId(), "input_condition");
        graph.connect(whileLoop.getId(), "exec_body", bodyStep.getId(), "exec_in");
        graph.connect(bodyStep.getId(), "exec_out", whileLoop.getId(), "exec_in");

        ExecutionRunLimits limits = new ExecutionRunLimits(6L, 5_000L);

        NodeExecutor fullRun = new NodeExecutor(graph, null, null, IncrementalExecutionOptions.defaults(), limits);
        assertFalse(fullRun.executeSync());
        int afterFullRun = bodyStep.executions();
        assertTrue(afterFullRun >= 2);

        Set<UUID> scope = Set.of(entry.getId(), whileLoop.getId(), bodyStep.getId(), condition.getId());
        NodeExecutor partialRun = new NodeExecutor(
                graph,
                null,
                scope,
                IncrementalExecutionOptions.previewDefaults(),
                limits
        );
        assertFalse(partialRun.executeSync());
        assertTrue(bodyStep.executions() > afterFullRun, "exec frontier visits should bypass preview cache skip");
    }

    @Test
    void execFlowClearsFrontierSnapshotAfterRun() {
        NodeGraph graph = new NodeGraph("exec-snapshot-clear");
        ExecCountingStepNode entry = new ExecCountingStepNode("entry");
        ExecCountingStepNode sink = new ExecCountingStepNode("sink");
        graph.addNode(entry);
        graph.addNode(sink);
        graph.connect(entry.getId(), "exec_out", sink.getId(), "exec_in");

        NodeExecutor executor = new NodeExecutor(graph);
        assertTrue(executor.executeSync());
        assertFalse(executor.getExecFrontierSnapshot().isActive());
    }

    private static final class ExecCountingStepNode extends BaseNode {
        private final AtomicInteger executions = new AtomicInteger();

        private ExecCountingStepNode(String suffix) {
            super(UUID.randomUUID(), "test.exec." + suffix);
            addInputPort(new BasePort("exec_in", "Exec In", "input", NodeDataType.EXEC, this, true, false));
            addOutputPort(new BasePort("exec_out", "Exec Out", "output", NodeDataType.EXEC, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            executions.incrementAndGet();
            outputValues.put("exec_out", Boolean.TRUE);
        }

        int executions() {
            return executions.get();
        }
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
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.STRING, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            executions.incrementAndGet();
            Object incoming = inputValues.get("in");
            outputValues.put("out", incoming != null ? incoming : payload);
        }

        int executionCount() {
            return executions.get();
        }

        void setPayload(String payload) {
            this.payload = payload;
            markDirty();
        }
    }
}

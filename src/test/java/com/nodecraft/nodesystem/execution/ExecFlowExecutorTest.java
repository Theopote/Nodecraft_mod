package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.flow.control.BranchNode;
import com.nodecraft.nodesystem.nodes.flow.control.DoOnceNode;
import com.nodecraft.nodesystem.nodes.flow.control.SequenceNode;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecFlowExecutorTest {

    @Test
    void execFlowSkipsNodesOutsideExecFrontier() {
        NodeGraph graph = new NodeGraph("exec-skip");
        ExecStepNode entry = new ExecStepNode("entry");
        ExecStepNode middle = new ExecStepNode("middle");
        ExecStepNode orphan = new ExecStepNode("orphan");

        graph.addNode(entry);
        graph.addNode(middle);
        graph.addNode(orphan);
        graph.connect(entry.getId(), "exec_out", middle.getId(), "exec_in");

        assertTrue(new NodeExecutor(graph).executeSync());
        assertEquals(1, entry.executions());
        assertEquals(1, middle.executions());
        assertEquals(0, orphan.executions());
    }

    @Test
    void execFlowPullsDataUpstreamOutsideExecFrontier() {
        NodeGraph graph = new NodeGraph("exec-data-pull");
        PassThroughNode source = new PassThroughNode("source", "value");
        ExecStepNode entry = new ExecStepNode("entry");
        CaptureNode sink = new CaptureNode("sink");

        graph.addNode(source);
        graph.addNode(entry);
        graph.addNode(sink);
        graph.connect(source.getId(), "out", sink.getId(), "in");
        graph.connect(entry.getId(), "exec_out", sink.getId(), "exec_in");

        assertTrue(new NodeExecutor(graph).executeSync());
        assertEquals(1, entry.executions());
        assertEquals(1, source.executions());
        assertEquals(1, sink.executions());
        assertEquals("value", sink.getOutput("out"));
    }

    @Test
    void branchExecFlowSkipsUnselectedExecPath() {
        NodeGraph graph = new NodeGraph("branch-exec");
        ExecStepNode entry = new ExecStepNode("entry");
        PassThroughNode condition = new PassThroughNode("condition", Boolean.TRUE);
        PassThroughNode signal = new PassThroughNode("signal", "payload");
        BranchNode branch = new BranchNode();
        ExecStepNode trueSink = new ExecStepNode("true_sink");
        ExecStepNode falseSink = new ExecStepNode("false_sink");

        graph.addNode(entry);
        graph.addNode(condition);
        graph.addNode(signal);
        graph.addNode(branch);
        graph.addNode(trueSink);
        graph.addNode(falseSink);

        graph.connect(entry.getId(), "exec_out", branch.getId(), "exec_in");
        graph.connect(condition.getId(), "out", branch.getId(), "input_condition");
        graph.connect(signal.getId(), "out", branch.getId(), "input_signal");
        graph.connect(branch.getId(), "exec_true", trueSink.getId(), "exec_in");
        graph.connect(branch.getId(), "exec_false", falseSink.getId(), "exec_in");

        assertTrue(new NodeExecutor(graph).executeSync());
        assertEquals(1, entry.executions());
        assertEquals(1, branch.getActiveExecOutputPortIds().size());
        assertEquals(1, trueSink.executions());
        assertEquals(0, falseSink.executions());
    }

    @Test
    void branchExecFlowFollowsFalsePathWhenConditionIsFalse() {
        NodeGraph graph = new NodeGraph("branch-exec-false");
        ExecStepNode entry = new ExecStepNode("entry");
        PassThroughNode condition = new PassThroughNode("condition", Boolean.FALSE);
        PassThroughNode signal = new PassThroughNode("signal", "payload");
        BranchNode branch = new BranchNode();
        ExecStepNode trueSink = new ExecStepNode("true_sink");
        ExecStepNode falseSink = new ExecStepNode("false_sink");

        graph.addNode(entry);
        graph.addNode(condition);
        graph.addNode(signal);
        graph.addNode(branch);
        graph.addNode(trueSink);
        graph.addNode(falseSink);

        graph.connect(entry.getId(), "exec_out", branch.getId(), "exec_in");
        graph.connect(condition.getId(), "out", branch.getId(), "input_condition");
        graph.connect(signal.getId(), "out", branch.getId(), "input_signal");
        graph.connect(branch.getId(), "exec_true", trueSink.getId(), "exec_in");
        graph.connect(branch.getId(), "exec_false", falseSink.getId(), "exec_in");

        assertTrue(new NodeExecutor(graph).executeSync());
        assertEquals(0, trueSink.executions());
        assertEquals(1, falseSink.executions());
    }

    @Test
    void execFlowGuardStopsInfiniteExecCycle() {
        NodeGraph graph = new NodeGraph("exec-cycle");
        ExecStepNode nodeA = new ExecStepNode("a");
        ExecStepNode nodeB = new ExecStepNode("b");
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.connect(nodeA.getId(), "exec_out", nodeB.getId(), "exec_in");
        graph.connect(nodeB.getId(), "exec_out", nodeA.getId(), "exec_in");

        ExecutionRunLimits limits = new ExecutionRunLimits(5L, 5_000L);
        assertFalse(new NodeExecutor(graph, null, null, null, limits).executeSync());
    }

    @Test
    void sequenceExecFlowDrainsStepsInOrder() {
        NodeGraph graph = new NodeGraph("sequence-exec-order");
        List<String> order = new ArrayList<>();

        ExecStepNode entry = new ExecStepNode("entry", order);
        PassThroughNode signal = new PassThroughNode("signal", "payload");
        PassThroughNode stepCount = new PassThroughNode("step_count", 3);
        SequenceNode sequence = new SequenceNode();
        ExecStepNode step1 = new ExecStepNode("step1", order);
        ExecStepNode step2 = new ExecStepNode("step2", order);
        ExecStepNode step3 = new ExecStepNode("step3", order);

        graph.addNode(entry);
        graph.addNode(signal);
        graph.addNode(stepCount);
        graph.addNode(sequence);
        graph.addNode(step1);
        graph.addNode(step2);
        graph.addNode(step3);

        graph.connect(entry.getId(), "exec_out", sequence.getId(), "exec_in");
        graph.connect(signal.getId(), "out", sequence.getId(), "input_signal");
        graph.connect(stepCount.getId(), "out", sequence.getId(), "input_step_count");
        graph.connect(sequence.getId(), SequenceNode.execStepPortId(1), step1.getId(), "exec_in");
        graph.connect(sequence.getId(), SequenceNode.execStepPortId(2), step2.getId(), "exec_in");
        graph.connect(sequence.getId(), SequenceNode.execStepPortId(3), step3.getId(), "exec_in");

        assertTrue(new NodeExecutor(graph).executeSync());
        assertEquals(List.of("entry", "step1", "step2", "step3"), order);
        assertEquals(1, step1.executions());
        assertEquals(1, step2.executions());
        assertEquals(1, step3.executions());
    }

    @Test
    void doOnceExecFlowRoutesBlockedPathOnSecondPulse() {
        NodeGraph graph = new NodeGraph("do-once-exec");
        PassThroughNode signal = new PassThroughNode("signal", "payload");
        ExecStepNode entry = new ExecStepNode("entry", null);
        DoOnceNode gate = new DoOnceNode();
        ExecStepNode passSink = new ExecStepNode("pass_sink", null);
        ExecStepNode loopBack = new ExecStepNode("loop_back", null);
        ExecStepNode blockedSink = new ExecStepNode("blocked_sink", null);

        graph.addNode(signal);
        graph.addNode(entry);
        graph.addNode(gate);
        graph.addNode(passSink);
        graph.addNode(loopBack);
        graph.addNode(blockedSink);

        assertTrue(graph.connect(entry.getId(), "exec_out", gate.getId(), "exec_in"));
        assertTrue(graph.connect(signal.getId(), "out", gate.getId(), "input_signal"));
        assertTrue(graph.connect(gate.getId(), "exec_out", passSink.getId(), "exec_in"));
        assertTrue(graph.connect(gate.getId(), "exec_out", loopBack.getId(), "exec_in"));
        assertTrue(graph.connect(loopBack.getId(), "exec_out", gate.getId(), "exec_in"));
        assertTrue(graph.connect(gate.getId(), "exec_blocked", blockedSink.getId(), "exec_in"));

        assertTrue(new NodeExecutor(graph).executeSync());
        assertEquals(1, passSink.executions());
        assertEquals(1, loopBack.executions());
        assertEquals(1, blockedSink.executions());
    }

    private static final class ExecStepNode extends BaseNode {
        private final AtomicInteger executions = new AtomicInteger();
        @Nullable
        private final List<String> orderLog;
        private final String orderLabel;

        private ExecStepNode(String suffix) {
            this(suffix, null);
        }

        private ExecStepNode(String suffix, @Nullable List<String> orderLog) {
            super(UUID.randomUUID(), "test.exec." + suffix);
            this.orderLog = orderLog;
            this.orderLabel = suffix;
            addInputPort(new BasePort("exec_in", "Exec In", "input", NodeDataType.EXEC, this, false, false));
            addOutputPort(new BasePort("exec_out", "Exec Out", "output", NodeDataType.EXEC, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            executions.incrementAndGet();
            if (orderLog != null) {
                orderLog.add(orderLabel);
            }
            outputValues.put("exec_out", Boolean.TRUE);
        }

        int executions() {
            return executions.get();
        }
    }

    private static final class PassThroughNode extends BaseNode {
        private final Object payload;

        private PassThroughNode(String suffix, Object payload) {
            super(UUID.randomUUID(), "test.pass." + suffix);
            this.payload = payload;
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            executions.incrementAndGet();
            outputValues.put("out", payload);
        }

        int executions() {
            return executions.get();
        }
    }

    private static final class CaptureNode extends BaseNode {
        private final AtomicInteger executions = new AtomicInteger();

        private CaptureNode(String suffix) {
            super(UUID.randomUUID(), "test.capture." + suffix);
            addInputPort(new BasePort("exec_in", "Exec In", "input", NodeDataType.EXEC, this, false, false));
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this, false, false));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            executions.incrementAndGet();
            outputValues.put("out", inputValues.get("in"));
        }

        int executions() {
            return executions.get();
        }
    }
}

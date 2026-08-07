package com.nodecraft.nodesystem.execution.runtime;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionRunLimits;
import com.nodecraft.nodesystem.execution.IncrementalExecutionOptions;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientNodeExecutionSchedulerTest {

    @AfterEach
    void cancelLeftoverPreview() {
        NodeExecutionScheduler.client().cancelPreview();
    }

    @Test
    void previewPlanSkipsOutputExecuteSideEffects() {
        NodeGraph graph = new NodeGraph("preview-skip");
        PassThroughNode source = new PassThroughNode("source", "ok");
        SideEffectNode sideEffect = new SideEffectNode();
        graph.addNode(source);
        graph.addNode(sideEffect);
        graph.connect(source.getId(), "out", sideEffect.getId(), "in");

        NodeExecutor executor = new NodeExecutor(
                graph,
                null,
                null,
                IncrementalExecutionOptions.previewDefaults(),
                ExecutionRunLimits.defaults(),
                CancellationToken.none(),
                true,
                null,
                true
        );

        assertTrue(executor.executeSync());
        assertEquals(0, sideEffect.executionCount(), "output.execute.* must not compute in preview");
        assertEquals("ok", source.getOutput("out"));
    }

    @Test
    void submittingPreviewSupersedesPreviousSession() throws Exception {
        NodeExecutionScheduler scheduler = NodeExecutionScheduler.client();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger firstTailRuns = new AtomicInteger();

        NodeGraph firstGraph = new NodeGraph("first");
        BlockingNode blocker = new BlockingNode("block", started, release);
        CountingNode firstTail = new CountingNode("first-tail", firstTailRuns);
        firstGraph.addNode(blocker);
        firstGraph.addNode(firstTail);
        firstGraph.connect(blocker.getId(), "out", firstTail.getId(), "in");

        ExecutionSession first = scheduler.submit(
                firstGraph,
                null,
                ExecutionPlan.preview(null),
                1L
        );
        assertTrue(started.await(3, TimeUnit.SECONDS), "first session should start");

        AtomicInteger secondRuns = new AtomicInteger();
        NodeGraph secondGraph = new NodeGraph("second");
        CountingNode secondNode = new CountingNode("second", secondRuns);
        secondGraph.addNode(secondNode);

        ExecutionSession second = scheduler.submit(
                secondGraph,
                null,
                ExecutionPlan.preview(null),
                2L
        );

        assertTrue(first.cancellation().isCancelled(), "previous preview must be cancelled");
        assertEquals(second.sessionId(), scheduler.activePreview().orElseThrow().sessionId());

        release.countDown();

        Boolean firstResult = first.result().get(3, TimeUnit.SECONDS);
        assertFalse(Boolean.TRUE.equals(firstResult), "superseded session should not succeed");
        assertTrue(second.result().get(3, TimeUnit.SECONDS), "latest preview should complete");
        assertEquals(0, firstTailRuns.get(), "cancelled session must not continue after cancel point");
        assertEquals(1, secondRuns.get());
    }

    private static final class PassThroughNode extends BaseNode {
        private final Object payload;

        private PassThroughNode(String suffix, Object payload) {
            super(UUID.randomUUID(), "test.pass." + suffix);
            this.payload = payload;
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable com.nodecraft.nodesystem.execution.ExecutionContext context) {
            Object incoming = inputValues.get("in");
            outputValues.put("out", incoming != null ? incoming : payload);
        }
    }

    private static final class SideEffectNode extends BaseNode {
        private final AtomicInteger executions = new AtomicInteger();

        private SideEffectNode() {
            super(UUID.randomUUID(), "output.execute.test_side_effect");
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable com.nodecraft.nodesystem.execution.ExecutionContext context) {
            executions.incrementAndGet();
            outputValues.put("out", inputValues.get("in"));
        }

        int executionCount() {
            return executions.get();
        }
    }

    private static final class BlockingNode extends BaseNode {
        private final CountDownLatch started;
        private final CountDownLatch release;

        private BlockingNode(String suffix, CountDownLatch started, CountDownLatch release) {
            super(UUID.randomUUID(), "test.block." + suffix);
            this.started = started;
            this.release = release;
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable com.nodecraft.nodesystem.execution.ExecutionContext context) {
            started.countDown();
            try {
                assertTrue(release.await(3, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            outputValues.put("out", "blocked");
        }
    }

    private static final class CountingNode extends BaseNode {
        private final AtomicInteger executions;

        private CountingNode(String suffix, AtomicInteger executions) {
            super(UUID.randomUUID(), "test.count." + suffix);
            this.executions = executions;
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(@Nullable com.nodecraft.nodesystem.execution.ExecutionContext context) {
            executions.incrementAndGet();
            Object incoming = inputValues.get("in");
            outputValues.put("out", incoming != null ? incoming : "count");
        }
    }
}
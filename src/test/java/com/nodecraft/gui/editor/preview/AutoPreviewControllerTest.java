package com.nodecraft.gui.editor.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.ExecutionProfiler;
import com.nodecraft.nodesystem.execution.runtime.CancellationToken;
import com.nodecraft.nodesystem.execution.runtime.ExecutionMode;
import com.nodecraft.nodesystem.execution.runtime.ExecutionPlan;
import com.nodecraft.nodesystem.execution.runtime.ExecutionSession;
import com.nodecraft.nodesystem.execution.runtime.NodeExecutionScheduler;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.preview.PreviewManager;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoPreviewControllerTest {

    @AfterEach
    void tearDown() {
        NodeExecutionScheduler.client().cancelPreview();
        NodeExecutionScheduler.client().cancelManual();
        PreviewManager.clearEmptyInputGrace();
    }

    @Test
    void notifyStructureDirtyClearsExecutionCacheGraceAndAdvancesEpoch() {
        AtomicLong dirtyVersion = new AtomicLong(0L);
        NodeGraph graph = new NodeGraph("structure-dirty");
        PassThroughNode source = new PassThroughNode("source", "ok");
        graph.addNode(source);
        graph.getExecutionCache().record(source);
        assertEquals(1, graph.getExecutionCache().size());

        String previewNodeId = source.getId().toString();
        PreviewManager.touchNonEmptyForTests(previewNodeId, "ghost_block");
        assertTrue(PreviewManager.withinEmptyGraceForTests(previewNodeId, "ghost_block"));

        AutoPreviewController controller = new AutoPreviewController(
                () -> graph,
                dirtySource(dirtyVersion),
                () -> new ExecutionContext(null, null)
        );

        long epochBefore = controller.graphDirtyEpoch();
        controller.notifyStructureDirty();

        assertEquals(0, graph.getExecutionCache().size());
        assertFalse(PreviewManager.withinEmptyGraceForTests(previewNodeId, "ghost_block"));
        assertEquals(epochBefore + 1L, controller.graphDirtyEpoch());
        assertEquals(1L, dirtyVersion.get());
    }

    @Test
    void notifyNodeDirtyClearsGraceAndHidesImpactedNodePreviews() {
        AtomicLong dirtyVersion = new AtomicLong(0L);
        NodeGraph graph = new NodeGraph("node-dirty-ghost");
        PassThroughNode upstream = new PassThroughNode("upstream", "ok");
        PassThroughNode downstream = new PassThroughNode("downstream", "ok");
        graph.addNode(upstream);
        graph.addNode(downstream);
        assertTrue(graph.connect(upstream.getId(), "out", downstream.getId(), "in"));

        PreviewManager.touchNonEmptyForTests(upstream.getId().toString(), "ghost_block");
        PreviewManager.touchNonEmptyForTests(downstream.getId().toString(), "ghost_block");
        assertTrue(PreviewManager.withinEmptyGraceForTests(downstream.getId().toString(), "ghost_block"));

        AutoPreviewController controller = new AutoPreviewController(
                () -> graph,
                dirtySource(dirtyVersion),
                () -> new ExecutionContext(null, null)
        );

        controller.notifyNodeDirty(upstream, 1L);

        assertFalse(PreviewManager.withinEmptyGraceForTests(upstream.getId().toString(), "ghost_block"));
        assertFalse(PreviewManager.withinEmptyGraceForTests(downstream.getId().toString(), "ghost_block"));
        assertTrue(controller.invalidatedNodeIdsView().contains(downstream.getId()));
        assertEquals(1L, dirtyVersion.get());
    }

    @Test
    void debounceThenPreviewSkipsOutputExecuteSideEffects() throws Exception {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicLong dirtyVersion = new AtomicLong(0L);

        NodeGraph graph = new NodeGraph("auto-preview");
        PassThroughNode source = new PassThroughNode("source", "ok");
        SideEffectNode sideEffect = new SideEffectNode();
        graph.addNode(source);
        graph.addNode(sideEffect);
        graph.connect(source.getId(), "out", sideEffect.getId(), "in");

        AutoPreviewController controller = new AutoPreviewController(
                () -> graph,
                dirtySource(dirtyVersion),
                () -> new ExecutionContext(null, null),
                clock::get,
                NodeExecutionScheduler.client()
        );

        controller.notifyNodeDirty(source, 1L);
        assertEquals(1L, dirtyVersion.get());
        assertTrue(controller.invalidatedNodeIdsView().size() >= 1, "dirty node should invalidate a scope");

        controller.tick(); // still within debounce window stamped at notify
        assertNull(controller.lastSubmittedSession(), "must not submit inside debounce window");

        clock.addAndGet(AutoPreviewController.DEBOUNCE_MS + 1L);
        controller.tick();

        ExecutionSession session = controller.lastSubmittedSession();
        assertNotNull(session, "preview session should be submitted after debounce");
        assertTrue(Boolean.TRUE.equals(session.result().get(3, TimeUnit.SECONDS)));
        assertEquals(0, sideEffect.executionCount(), "preview must skip output.execute.*");
    }

    @Test
    void staleFailedSessionDoesNotOverwriteNewerSuccessState() {
        AtomicLong clock = new AtomicLong(4_000L);
        AtomicLong dirtyVersion = new AtomicLong(0L);
        DeferredPreviewScheduler scheduler = new DeferredPreviewScheduler();

        NodeGraph graph = new NodeGraph("auto-preview-stale-fail");
        PassThroughNode source = new PassThroughNode("source", "ok");
        graph.addNode(source);

        AutoPreviewController controller = new AutoPreviewController(
                () -> graph,
                dirtySource(dirtyVersion),
                () -> new ExecutionContext(null, null),
                clock::get,
                scheduler
        );

        controller.notifyNodeDirty(source, 1L);
        clock.addAndGet(AutoPreviewController.DEBOUNCE_MS + 1L);
        controller.tick();
        CompletableFuture<Boolean> older = scheduler.lastFuture();
        assertNotNull(older);

        controller.notifyNodeDirty(source, 2L);
        clock.addAndGet(AutoPreviewController.DEBOUNCE_MS + 1L);
        controller.tick();
        CompletableFuture<Boolean> newer = scheduler.lastFuture();
        assertNotNull(newer);
        assertTrue(older != newer);

        newer.complete(true);
        assertEquals(-1L, controller.lastFailedPreviewVersion());

        older.completeExceptionally(new IllegalStateException("late fail from older generation"));
        assertEquals(-1L, controller.lastFailedPreviewVersion(), "stale failure must not overwrite newer success");
    }

    @Test
    void failedPreviewSuppressesPollUntilDirtyAdvances() throws Exception {
        AtomicLong clock = new AtomicLong(3_000L);
        AtomicLong dirtyVersion = new AtomicLong(0L);
        AtomicInteger submitCount = new AtomicInteger();
        FailingPreviewScheduler scheduler = new FailingPreviewScheduler(submitCount);

        NodeGraph graph = new NodeGraph("auto-preview-fail-poll");
        PassThroughNode source = new PassThroughNode("source", "ok");
        graph.addNode(source);

        AutoPreviewController controller = new AutoPreviewController(
                () -> graph,
                dirtySource(dirtyVersion),
                () -> new ExecutionContext(null, null),
                clock::get,
                scheduler
        );

        controller.notifyNodeDirty(source, 1L);
        clock.addAndGet(AutoPreviewController.DEBOUNCE_MS + 1L);
        controller.tick();

        ExecutionSession failed = controller.lastSubmittedSession();
        assertNotNull(failed);
        assertTrue(failed.result().isCompletedExceptionally());
        assertEquals(1, submitCount.get());
        assertEquals(dirtyVersion.get(), controller.lastFailedPreviewVersion());

        clock.addAndGet(AutoPreviewController.POLL_INTERVAL_MS + 1L);
        controller.tick();
        assertEquals(1, submitCount.get(), "poll must not retry the same failed dirty version");

        controller.notifyNodeDirty(source, 2L);
        clock.addAndGet(AutoPreviewController.DEBOUNCE_MS + 1L);
        controller.tick();
        assertEquals(2, submitCount.get(), "new dirty version must allow another preview submit");
    }

    @Test
    void debounceClockAdvanceAfterNotifyAllowsImmediateSubmitOnNextTick() throws Exception {
        AtomicLong clock = new AtomicLong(2_000L);
        AtomicLong dirtyVersion = new AtomicLong(0L);

        NodeGraph graph = new NodeGraph("auto-preview-advance-first");
        PassThroughNode source = new PassThroughNode("source", "ok");
        graph.addNode(source);

        AutoPreviewController controller = new AutoPreviewController(
                () -> graph,
                dirtySource(dirtyVersion),
                () -> new ExecutionContext(null, null),
                clock::get,
                NodeExecutionScheduler.client()
        );

        controller.notifyNodeDirty(source, 1L);
        clock.addAndGet(AutoPreviewController.DEBOUNCE_MS + 1L);
        controller.tick();

        ExecutionSession session = controller.lastSubmittedSession();
        assertNotNull(session, "session should be submitted when clock already advanced past debounce");
        assertTrue(Boolean.TRUE.equals(session.result().get(3, TimeUnit.SECONDS)));
    }

    private static AutoPreviewController.DirtyVersionSource dirtySource(AtomicLong dirtyVersion) {
        return new AutoPreviewController.DirtyVersionSource() {
            @Override
            public long getDirtyVersion() {
                return dirtyVersion.get();
            }

            @Override
            public void markDirty() {
                dirtyVersion.incrementAndGet();
            }
        };
    }

    private static ExecutionSession sessionOf(long generation, CompletableFuture<Boolean> result) {
        return new ExecutionSession() {
            private final UUID id = UUID.randomUUID();

            @Override
            public UUID sessionId() {
                return id;
            }

            @Override
            public long generation() {
                return generation;
            }

            @Override
            public ExecutionMode mode() {
                return ExecutionMode.PREVIEW;
            }

            @Override
            public CancellationToken cancellation() {
                return CancellationToken.none();
            }

            @Override
            public CompletableFuture<Boolean> result() {
                return result;
            }

            @Override
            @Nullable
            public ExecutionProfiler.Profile lastProfile() {
                return null;
            }
        };
    }

    private static final class DeferredPreviewScheduler implements NodeExecutionScheduler {
        private volatile CompletableFuture<Boolean> lastFuture;

        CompletableFuture<Boolean> lastFuture() {
            return lastFuture;
        }

        @Override
        public ExecutionSession submit(
                NodeGraph graph,
                @Nullable ExecutionContext context,
                ExecutionPlan plan,
                long generation
        ) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            lastFuture = result;
            return sessionOf(generation, result);
        }

        @Override
        public void cancelPreview() {
        }

        @Override
        public void cancelManual() {
        }

        @Override
        public Optional<ExecutionSession> activePreview() {
            return Optional.empty();
        }

        @Override
        public Optional<ExecutionSession> activeManual() {
            return Optional.empty();
        }
    }

    private static final class FailingPreviewScheduler implements NodeExecutionScheduler {
        private final AtomicInteger submitCount;

        private FailingPreviewScheduler(AtomicInteger submitCount) {
            this.submitCount = submitCount;
        }

        @Override
        public ExecutionSession submit(
                NodeGraph graph,
                @Nullable ExecutionContext context,
                ExecutionPlan plan,
                long generation
        ) {
            submitCount.incrementAndGet();
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            result.completeExceptionally(new IllegalStateException("preview boom"));
            return sessionOf(generation, result);
        }

        @Override
        public void cancelPreview() {
        }

        @Override
        public void cancelManual() {
        }

        @Override
        public Optional<ExecutionSession> activePreview() {
            return Optional.empty();
        }

        @Override
        public Optional<ExecutionSession> activeManual() {
            return Optional.empty();
        }
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
        public void processNode(@Nullable ExecutionContext context) {
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
        public void processNode(@Nullable ExecutionContext context) {
            executions.incrementAndGet();
            outputValues.put("out", inputValues.get("in"));
        }

        int executionCount() {
            return executions.get();
        }
    }
}

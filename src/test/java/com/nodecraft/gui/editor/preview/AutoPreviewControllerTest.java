package com.nodecraft.gui.editor.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.ExecutionSession;
import com.nodecraft.nodesystem.execution.runtime.NodeExecutionScheduler;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoPreviewControllerTest {

    @AfterEach
    void tearDown() {
        NodeExecutionScheduler.client().cancelPreview();
        NodeExecutionScheduler.client().cancelManual();
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

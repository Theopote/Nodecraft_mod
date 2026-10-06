package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.runtime.CancellationToken;
import com.nodecraft.nodesystem.execution.runtime.ExecutionPlan;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preview stays dataflow even when EXEC wires exist, so design/build previews
 * off the exec frontier still run. Manual runs with EXEC edges use exec-flow.
 */
class HybridPreviewExecFlowTest {

    @Test
    void previewWithExecEdgesStillRunsDataflowPreviewsAndSkipsWorldWrite() {
        NodeGraph graph = new NodeGraph("hybrid-preview");
        CountingNode source = new CountingNode("source", NodeEffect.PURE);
        CountingNode preview = new CountingNode("preview", NodeEffect.PREVIEW_WRITE);
        CountingNode trigger = new CountingNode("trigger", NodeEffect.PURE);
        WorldWriteProbe apply = new WorldWriteProbe();

        graph.addNode(source);
        graph.addNode(preview);
        graph.addNode(trigger);
        graph.addNode(apply);
        assertTrue(graph.connect(source.getId(), "out", preview.getId(), "in"));
        assertTrue(graph.connect(trigger.getId(), "exec_out", apply.getId(), "exec_in"));

        ExecutionPlan previewPlan = ExecutionPlan.preview(null);
        NodeExecutor previewExecutor = new NodeExecutor(
                graph,
                null,
                previewPlan.scopeNodeIds(),
                previewPlan.options(),
                ExecutionRunLimits.defaults(),
                CancellationToken.none(),
                previewPlan.skipOutputExecuteSideEffects(),
                null,
                true
        );
        assertTrue(previewExecutor.executeSync());
        assertEquals(1, source.executions());
        assertEquals(1, preview.executions());
        assertEquals(1, trigger.executions());
        assertEquals(0, apply.executions());
    }

    @Test
    void manualWithExecEdgesUsesExecFlowAndFiresWorldWrite() {
        NodeGraph graph = new NodeGraph("hybrid-manual");
        CountingNode source = new CountingNode("source", NodeEffect.PURE);
        CountingNode preview = new CountingNode("preview", NodeEffect.PREVIEW_WRITE);
        CountingNode trigger = new CountingNode("trigger", NodeEffect.PURE);
        WorldWriteProbe apply = new WorldWriteProbe();

        graph.addNode(source);
        graph.addNode(preview);
        graph.addNode(trigger);
        graph.addNode(apply);
        assertTrue(graph.connect(source.getId(), "out", preview.getId(), "in"));
        assertTrue(graph.connect(trigger.getId(), "exec_out", apply.getId(), "exec_in"));

        ExecutionPlan manual = ExecutionPlan.manual(null);
        assertTrue(new NodeExecutor(
                graph,
                null,
                manual.scopeNodeIds(),
                manual.options(),
                ExecutionRunLimits.defaults(),
                CancellationToken.none(),
                manual.skipOutputExecuteSideEffects(),
                null,
                true
        ).executeSync());
        assertEquals(1, trigger.executions());
        assertEquals(1, apply.executions());
        assertEquals(0, preview.executions(), "preview is off the exec frontier in manual exec-flow");
        assertEquals(0, source.executions());
        assertTrue(Boolean.TRUE.equals(apply.getOutput("fired")));
    }

    @NodeInfo(effect = NodeEffect.PURE, id = "test.hybrid.counting", displayName = "Counting", category = "test")
    private static final class CountingNode extends BaseNode {
        private final AtomicInteger executions = new AtomicInteger();

        private CountingNode(String suffix, NodeEffect effect) {
            super(UUID.randomUUID(), "test.hybrid.counting." + suffix);
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
            addOutputPort(new BasePort("exec_out", "Exec Out", "exec", NodeDataType.EXEC, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            executions.incrementAndGet();
            outputValues.put("out", "ok");
            outputValues.put("exec_out", Boolean.TRUE);
        }

        int executions() {
            return executions.get();
        }
    }

    @NodeInfo(effect = NodeEffect.WORLD_WRITE, id = "test.hybrid.world_write", displayName = "World Write Probe", category = "test")
    private static final class WorldWriteProbe extends BaseNode {
        private final AtomicInteger executions = new AtomicInteger();

        private WorldWriteProbe() {
            super(UUID.randomUUID(), "test.hybrid.world_write");
            addInputPort(new BasePort("exec_in", "Exec In", "input", NodeDataType.EXEC, this, false, false));
            addOutputPort(new BasePort("fired", "Fired", "output", NodeDataType.BOOLEAN, this));
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
            executions.incrementAndGet();
            outputValues.put("fired", Boolean.TRUE);
        }

        int executions() {
            return executions.get();
        }
    }
}

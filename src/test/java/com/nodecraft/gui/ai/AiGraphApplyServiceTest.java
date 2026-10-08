package com.nodecraft.gui.ai;

import com.nodecraft.gui.editor.base.GraphApplyHistoryView;
import com.nodecraft.gui.editor.base.GraphApplyTarget;
import com.nodecraft.gui.editor.base.GraphNodeAnchor;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiGraphApplyServiceTest {

    private static final String TYPE = "test.apply.node";

    @Test
    void applyPatchCreatesNodesAndConnectionsThroughGraphApplyTarget() {
        NodeGraph graph = new NodeGraph("apply-test");
        RecordingGraphApplyTarget applyTarget = new RecordingGraphApplyTarget(graph);

        TestNode source = new TestNode(UUID.randomUUID(), TYPE);
        TestNode sink = new TestNode(UUID.randomUUID(), "test.apply.sink");
        graph.addNode(source);
        graph.addNode(sink);
        String sourceRef = source.getId().toString().substring(0, 8);

        AiGraphApplyService.ApplyResult result = AiGraphApplyService.applyPatch(
                applyTarget,
                graph,
                List.of(
                        new AiGraphApplyService.ApplyNode(sourceRef, TYPE, 0f, 0f, null),
                        new AiGraphApplyService.ApplyNode("new_node", "test.apply.created", 40f, 20f, null)
                ),
                List.of(new AiGraphApplyService.ApplyConnection(sourceRef, "out", "new_node", "in")),
                new float[]{100f, 200f},
                false,
                false
        );

        assertTrue(result.success());
        assertEquals(1, applyTarget.connectCalls);
        assertEquals(1, applyTarget.createdNodes.size());
        assertTrue(graph.getIncomingConnections(sink.getId()).isEmpty());
    }

    @Test
    void patchDoesNotReuseFirstUnusedSameTypeNode() {
        NodeGraph graph = new NodeGraph("patch-no-guess");
        RecordingGraphApplyTarget applyTarget = new RecordingGraphApplyTarget(graph);

        TestNode first = new TestNode(UUID.randomUUID(), TYPE);
        TestNode second = new TestNode(UUID.randomUUID(), TYPE);
        graph.addNode(first);
        graph.addNode(second);

        AiGraphApplyService.ApplyResult result = AiGraphApplyService.applyPatch(
                applyTarget,
                graph,
                List.of(new AiGraphApplyService.ApplyNode("planned_third", TYPE, 10f, 10f, null)),
                List.of(),
                new float[]{0f, 0f},
                false,
                false
        );

        assertTrue(result.success());
        assertEquals(1, applyTarget.createdNodes.size());
        assertEquals(3, graph.getNodes().size());
    }

    @Test
    void patchReusesNodeWhenRefMatchesShortUuid() {
        UUID targetId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        NodeGraph graph = new NodeGraph("patch-uuid");
        RecordingGraphApplyTarget applyTarget = new RecordingGraphApplyTarget(graph);

        TestNode existing = new TestNode(targetId, TYPE);
        graph.addNode(existing);

        AiGraphApplyService.ApplyResult result = AiGraphApplyService.applyPatch(
                applyTarget,
                graph,
                List.of(new AiGraphApplyService.ApplyNode("11111111", TYPE, 0f, 0f, null)),
                List.of(),
                new float[]{0f, 0f},
                false,
                false
        );

        assertTrue(result.success());
        assertTrue(applyTarget.createdNodes.isEmpty());
        assertEquals(1, graph.getNodes().size());
    }

    @Test
    void patchReusesNodeWhenParameterSignatureMatches() {
        NodeGraph graph = new NodeGraph("patch-signature");
        RecordingGraphApplyTarget applyTarget = new RecordingGraphApplyTarget(graph);

        Map<String, Object> state = new HashMap<>();
        state.put("value", 42.0d);
        TestNode existing = new TestNode(UUID.randomUUID(), TYPE);
        existing.setNodeState(state);
        graph.addNode(existing);

        Map<String, Object> plannedState = new HashMap<>();
        plannedState.put("value", 42.0d);

        AiGraphApplyService.ApplyResult result = AiGraphApplyService.applyPatch(
                applyTarget,
                graph,
                List.of(new AiGraphApplyService.ApplyNode("planned", TYPE, 0f, 0f, plannedState)),
                List.of(),
                new float[]{0f, 0f},
                false,
                false
        );

        assertTrue(result.success(), result.statusMessage());
        assertTrue(applyTarget.createdNodes.isEmpty(), result.statusMessage());
        assertEquals(1, graph.getNodes().size());
    }

    private static final class RecordingGraphApplyTarget implements GraphApplyTarget {
        private final NodeGraph graph;
        private final List<INode> createdNodes = new ArrayList<>();
        private int connectCalls = 0;

        private RecordingGraphApplyTarget(NodeGraph graph) {
            this.graph = graph;
        }

        @Override
        public INode addNode(String typeId, float x, float y) {
            TestNode node = new TestNode(UUID.randomUUID(), typeId);
            graph.addNode(node);
            createdNodes.add(node);
            return node;
        }

        @Override
        public INode addNodeWithState(String typeId, UUID oldNodeId, float x, float y, Object nodeState) {
            TestNode node = new TestNode(UUID.randomUUID(), typeId);
            node.setNodeState(nodeState);
            graph.addNode(node);
            createdNodes.add(node);
            return node;
        }

        @Override
        public boolean connectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            connectCalls++;
            return graph.connect(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        }

        @Override
        public boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
            return graph.disconnectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        }

        @Override
        public boolean undo() {
            return true;
        }

        @Override
        public void recordAiPatchApply(String summary, Map<UUID, Object> previousStates, int undoStepsTaken) {
        }

        @Override
        public GraphApplyHistoryView getApplyHistoryView() {
            return GraphApplyHistoryView.EMPTY;
        }

        @Override
        public GraphNodeAnchor getNodeAnchor(UUID nodeId) {
            return null;
        }
    }

    private static final class TestNode extends BaseNode {
        private Map<String, Object> rawState;

        private TestNode(UUID id, String typeId) {
            super(id, typeId);
            addInputPort(new BasePort("in", "In", "input", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "output", NodeDataType.ANY, this));
        }

        @Override
        public Object getNodeState() {
            return rawState;
        }

        @Override
        public void setNodeState(Object state) {
            if (state instanceof Map<?, ?> map) {
                rawState = new HashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    rawState.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            } else {
                rawState = null;
            }
        }

        @Override
        public void processNode(@Nullable com.nodecraft.nodesystem.execution.ExecutionContext context) {
            outputValues.put("out", inputValues.get("in"));
        }
    }
}

package com.nodecraft.gui.ai;

import com.nodecraft.gui.editor.base.GraphNodeAnchor;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPromptContextServiceTest {

    @Test
    void selectedNodeIncludesStableIdsFullParamsAndConnections() {
        NodeGraph graph = new NodeGraph("selection-context");
        TestNode selected = new TestNode(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), "geometry.wall");
        TestNode upstream = new TestNode(UUID.fromString("11111111-2222-3333-4444-555555555555"), "geometry.box");
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("height", 4);
        state.put("width", 2);
        state.put("count", 3);
        state.put("spacing", 1);
        state.put("alignment", "center");
        state.put("mode", "normal");
        state.put("offset", 0.5);
        selected.setNodeState(state);
        graph.addNode(upstream);
        graph.addNode(selected);
        graph.connect(upstream.getId(), "out", selected.getId(), "in");

        String summary = AiPromptContextService.buildSelectionContextSummary(
                true,
                true,
                selected,
                new GraphNodeAnchor(10f, 20f),
                graph
        );

        assertTrue(summary.contains("id: aaaaaaaa"));
        assertTrue(summary.contains("fullId: aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        assertTrue(summary.contains("type: geometry.wall"));
        assertTrue(summary.contains("displayName:"));
        assertTrue(summary.contains("height=4"));
        assertTrue(summary.contains("offset=0.5"), "selected params must not truncate at 6 fields");
        assertTrue(summary.contains("- in:"));
        assertTrue(summary.contains("selected-neighborhood-first") || summary.contains("[selected]"));
    }

    @Test
    void neighborhoodPrefersSelectedNeighborsOverPrefixNodes() {
        NodeGraph graph = new NodeGraph("neighborhood");
        // Many unrelated nodes first in list order
        for (int i = 0; i < 25; i++) {
            graph.addNode(new TestNode(UUID.randomUUID(), "noise.node." + i));
        }

        TestNode wall = new TestNode(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"), "geometry.wall");
        TestNode window = new TestNode(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"), "geometry.window");
        TestNode difference = new TestNode(UUID.fromString("cccccccc-0000-0000-0000-000000000003"), "geometry.difference");
        graph.addNode(wall);
        graph.addNode(window);
        graph.addNode(difference);
        // Chain so both neighbors appear without fighting a single-input port.
        assertTrue(graph.connect(wall.getId(), "out", window.getId(), "in"));
        assertTrue(graph.connect(window.getId(), "out", difference.getId(), "in"));

        String summary = AiPromptContextService.buildCurrentGraphContextSummary(graph, difference);

        assertTrue(summary.contains("bbbbbbbb") || summary.contains("geometry.window"), summary);
        assertTrue(summary.contains("aaaaaaaa") || summary.contains("geometry.wall"), summary);
        assertTrue(summary.contains("omittedNodes="), summary);
        // Unrelated prefix noise should not dominate: first listed structural nodes include selection neighborhood
        int selectedIdx = summary.indexOf("[selected]");
        int noiseIdx = summary.indexOf("noise.node.0");
        assertTrue(selectedIdx >= 0, summary);
        assertTrue(noiseIdx < 0 || selectedIdx < noiseIdx, summary);
    }

    @Test
    void disabledSelectionContextIsExplicit() {
        String summary = AiPromptContextService.buildSelectionContextSummary(
                false, false, null, null, null);
        assertTrue(summary.contains("Selection context disabled."));
        assertFalse(summary.contains("Selected node:"));
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

package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.nodes.output.debug.PanelNode;
import com.nodecraft.nodesystem.nodes.output.debug.PrintToChatNode;
import com.nodecraft.nodesystem.nodes.output.debug.StopwatchNode;
import com.nodecraft.nodesystem.nodes.output.debug.ValueMonitorNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.DebugValueFormatter;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputDebugLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
        "output.debug.value_monitor",
        "output.debug.print_to_chat",
        "output.debug.execution_timer",
        "output.debug.data_inspector"
    );

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void generationLimitsExposeDebugCaps() {
        assertEquals(16_384, GenerationLimits.MAX_DEBUG_TEXT_CHARS);
        assertEquals(64, GenerationLimits.MAX_DEBUG_ITEMS);
        assertEquals(8, GenerationLimits.MAX_DEBUG_DEPTH);
    }

    @Test
    void exactlyFourOutputDebugNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("output.debug."))
            .sorted()
            .toList();
        assertEquals(CANONICAL_IDS, Set.copyOf(ids), ids.toString());
    }

    @Test
    void effectsMatchFreezeContract() {
        assertEquals(NodeEffect.PURE,
            NodeEffectResolver.resolve(ValueMonitorNode.class, "output.debug.value_monitor"));
        assertEquals(NodeEffect.PURE,
            NodeEffectResolver.resolve(PanelNode.class, "output.debug.data_inspector"));
        assertEquals(NodeEffect.UI_EFFECT,
            NodeEffectResolver.resolve(PrintToChatNode.class, "output.debug.print_to_chat"));
        assertEquals(NodeEffect.CONTEXT_WRITE,
            NodeEffectResolver.resolve(StopwatchNode.class, "output.debug.execution_timer"));

        assertEquals(NodeEffect.UI_EFFECT,
            NodeEffectResolver.inferFromTypeId("output.debug.print_to_chat"));
        assertEquals(NodeEffect.CONTEXT_WRITE,
            NodeEffectResolver.inferFromTypeId("output.debug.execution_timer"));
        assertEquals(NodeEffect.PURE,
            NodeEffectResolver.inferFromTypeId("output.debug.value_monitor"));
        assertEquals(NodeEffect.PURE,
            NodeEffectResolver.inferFromTypeId("output.debug.data_inspector"));
    }

    @Test
    void stopwatchIsRegisteredWithEnglishDisplayNameAndExecPorts() {
        INode node = registry.createNodeInstance("output.debug.execution_timer");
        assertNotNull(node);
        assertInstanceOf(StopwatchNode.class, node);
        NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
        assertEquals("Stopwatch", info.displayName());
        assertEquals(NodeEffect.CONTEXT_WRITE, info.effect());

        assertEquals(NodeDataType.EXEC, findPort(node, "input_start").getDataType());
        assertEquals(NodeDataType.EXEC, findPort(node, "input_stop").getDataType());
        assertEquals(NodeDataType.EXEC, findPort(node, "input_reset").getDataType());
        assertEquals(NodeDataType.BOOLEAN, findPort(node, "input_auto_reset").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(node, "output_execution_time").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(node, "output_total_time").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(node, "output_average_time").getDataType());
    }

    @Test
    void stopwatchStateOmitsRuntimeTimingFields() {
        StopwatchNode node = new StopwatchNode();
        node.setInput("input_start", Boolean.TRUE);
        node.processNode(null);
        node.setInput("input_start", null);
        node.setInput("input_stop", Boolean.TRUE);
        node.processNode(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) node.getNodeState();
        assertNotNull(state);
        assertTrue(state.containsKey("autoReset"));
        assertTrue(state.containsKey("showMilliseconds"));
        assertTrue(state.containsKey("printToConsole"));
        assertTrue(state.containsKey("precision"));
        assertFalse(state.containsKey("startTime"));
        assertFalse(state.containsKey("startNs"));
        assertFalse(state.containsKey("lastExecutionTime"));
        assertFalse(state.containsKey("totalExecutionTime"));
        assertFalse(state.containsKey("executionCount"));

        node.setNodeState(Map.of("autoReset", false, "precision", 3));
        assertEquals(0.0d, node.getOutput("output_execution_time"));
        assertEquals(0, node.getOutput("output_count"));
    }

    @Test
    void valueMonitorHasPassthroughBinding() {
        INode node = registry.createNodeInstance("output.debug.value_monitor");
        assertNotNull(node);
        IPort in = findPort(node, "input_value");
        IPort out = findPort(node, "output_value");
        assertTrue(in.isPassthroughBinding());
        assertTrue(out.isPassthroughBinding());
        assertEquals("T", in.getListTypeVariable());

        ValueMonitorNode monitor = new ValueMonitorNode();
        monitor.setInput("input_value", 123);
        monitor.processNode(null);
        assertEquals(123, monitor.getOutput("output_value"));
    }

    @Test
    void panelHasNoRefreshAndAlwaysShowsCurrentInput() {
        INode node = registry.createNodeInstance("output.debug.data_inspector");
        assertNotNull(node);
        assertNull(findPort(node, "input_refresh"));
        NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
        assertEquals(NodeEffect.PURE, info.effect());

        PanelNode panel = new PanelNode();
        panel.setInput("input_data", List.of(1, 2, 3));
        panel.processNode(null);
        String first = (String) panel.getOutput("output_text");
        assertNotNull(first);
        assertFalse(first.isBlank());

        panel.setInput("input_data", "hello");
        panel.processNode(null);
        assertTrue(((String) panel.getOutput("output_text")).contains("hello"));
    }

    @Test
    void panelMaxLengthIsHardCapped() {
        PanelNode panel = new PanelNode();
        panel.setMaxDisplayLength(Integer.MAX_VALUE);
        assertEquals(GenerationLimits.MAX_DEBUG_TEXT_CHARS, panel.getMaxDisplayLength());
    }

    @Test
    void printToChatUsesExecTriggerAndExposesError() {
        INode node = registry.createNodeInstance("output.debug.print_to_chat");
        assertNotNull(node);
        assertEquals(NodeDataType.EXEC, findPort(node, "input_trigger").getDataType());
        assertNotNull(findPort(node, "output_error"));

        PrintToChatNode print = new PrintToChatNode();
        print.processNode(null);
        assertEquals(Boolean.FALSE, print.getOutput("output_success"));
        assertEquals("", print.getOutput("output_error"));

        print.setInput("input_trigger", Boolean.TRUE);
        print.setInput("input_data", "hi");
        print.processNode(null);
        assertEquals(Boolean.FALSE, print.getOutput("output_success"));
        assertFalse(((String) print.getOutput("output_error")).isBlank());
    }

    @Test
    void panelUsesBoundedFormatterForLargeLists() {
        PanelNode panel = new PanelNode();
        List<Integer> huge = new ArrayList<>(5_000);
        for (int i = 0; i < 5_000; i++) {
            huge.add(i);
        }
        panel.setInput("input_data", huge);
        panel.setMaxDisplayLength(500);
        panel.processNode(null);
        String text = (String) panel.getOutput("output_text");
        assertNotNull(text);
        assertTrue(text.length() <= 500);
        assertFalse(text.contains("4999"));
        DebugValueFormatter.FormatResult direct = DebugValueFormatter.format(
            huge,
            new DebugValueFormatter.FormatOptions(500, 8, 4, false)
        );
        assertTrue(direct.truncated());
    }

    private static IPort findPort(INode node, String portId) {
        for (IPort port : node.getInputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }
}

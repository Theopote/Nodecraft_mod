package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.ExecutionRunLimits;
import com.nodecraft.nodesystem.execution.IncrementalExecutionOptions;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.execution.runtime.CancellationToken;
import com.nodecraft.nodesystem.execution.runtime.SimpleCancellationToken;
import com.nodecraft.nodesystem.execution.subgraph.SubgraphCallFrameBridge;
import com.nodecraft.nodesystem.graph.GraphInterfaceValidator;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.graph.SubgraphInterfaceScanner;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedGraphComment;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.utilities.organization.GraphInputNode;
import com.nodecraft.nodesystem.nodes.utilities.organization.GraphOutputNode;
import com.nodecraft.nodesystem.nodes.utilities.organization.SubgraphNode;
import com.nodecraft.nodesystem.nodes.utilities.organization.SubgraphPortIds;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Organization & Subgraph v1 language fence (Graph V58).
 */
class OrganizationLanguageContractTest {

    private static NodeRegistry registry;

    private static final String SIDE_EFFECT_PROBE_ID = "test.organization.side_effect_probe";
    private static final String CHAINED_SIDE_EFFECT_PROBE_ID = "test.organization.chained_side_effect_probe";

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
        registerContractProbeTypes();
    }

    private static void registerContractProbeTypes() {
        registry.registerNode(new com.nodecraft.gui.node.NodeInfo(
                SIDE_EFFECT_PROBE_ID,
                "Side Effect Probe",
                "Contract test probe",
                "test",
                0,
                SideEffectProbe.class
        ));
        registry.registerNode(new com.nodecraft.gui.node.NodeInfo(
                CHAINED_SIDE_EFFECT_PROBE_ID,
                "Chained Side Effect Probe",
                "Contract test passthrough probe",
                "test",
                0,
                ChainedSideEffectProbe.class
        ));
    }

    @Test
    void currentGraphFormatIsV58() {
        assertEquals(58, GraphFormatVersion.V58);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V58);
    }

    @Test
    void exactlyThreeOrganizationRuntimeNodes() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("utilities.organization."))
                .sorted()
                .toList();
        assertEquals(3, ids.size(), ids.toString());
        assertEquals("utilities.organization.graph_input", ids.get(0));
        assertEquals("utilities.organization.graph_output", ids.get(1));
        assertEquals("utilities.organization.subgraph", ids.get(2));
    }

    @Test
    void organizationNodesDeclareExpectedEffects() {
        assertEffect("utilities.organization.graph_input", NodeEffect.CONTEXT_READ);
        assertEffect("utilities.organization.graph_output", NodeEffect.CONTEXT_WRITE);
        assertEffect("utilities.organization.subgraph", NodeEffect.COMPOSITE);
    }

    @Test
    void removedOrganizationNodesNotInRegistry() {
        assertFalse(registry.getAllNodeIds().contains("utilities.organization.subgraph_register"));
        assertFalse(registry.getAllNodeIds().contains("utilities.organization.preset"));
        assertFalse(registry.getAllNodeIds().contains("utilities.organization.comment"));
        assertFalse(registry.getAllNodeIds().contains("utilities.organization.group"));
    }

    @Test
    void graphInputHasNoOverridePortAndUsesPassthroughTyping() {
        GraphInputNode node = new GraphInputNode();
        Set<String> inputIds = portIds(node.getInputPorts());
        Set<String> outputIds = portIds(node.getOutputPorts());
        assertFalse(inputIds.contains("input_override"));
        assertTrue(inputIds.contains("input_default"));
        assertTrue(outputIds.contains("output_value"));
        assertTrue(outputIds.contains("output_was_provided"));
        assertTrue(outputIds.contains("output_error"));
        assertFalse(outputIds.contains("output_was_overridden"));
    }

    @Test
    void graphOutputHasNoOutputsAnyOrNameOverride() {
        GraphOutputNode node = new GraphOutputNode();
        Set<String> inputIds = portIds(node.getInputPorts());
        Set<String> outputIds = portIds(node.getOutputPorts());
        assertFalse(inputIds.contains("input_name_override"));
        assertFalse(outputIds.contains("output_outputs"));
    }

    @Test
    void graphInputUsesContainsKeySemanticsIncludingNullCallerValue() {
        GraphInputNode node = new GraphInputNode();
        node.setNodeState(Map.of("inputName", "in", "required", false));

        ExecutionContext context = ExecutionContext.createEmpty(null);
        Map<String, Object> frameInputs = new HashMap<>();
        frameInputs.put("in", null);
        SubgraphCallFrameBridge.FrameHandle handle = SubgraphCallFrameBridge.push(
                context,
                "test",
                frameInputs
        );
        try {
            node.processNode(context);
            assertTrue((Boolean) node.getOutput("output_was_provided"));
            assertNull(node.getOutput("output_value"));
            assertTrue((Boolean) node.getOutput("output_valid"));
        } finally {
            SubgraphCallFrameBridge.restore(handle);
        }
    }

    @Test
    void graphInputRequiredMissingKeyFailsClosed() {
        GraphInputNode node = new GraphInputNode();
        node.setNodeState(Map.of("inputName", "in", "required", true));

        ExecutionContext context = ExecutionContext.createEmpty(null);
        SubgraphCallFrameBridge.FrameHandle handle = SubgraphCallFrameBridge.push(context, "test", Map.of());
        try {
            node.processNode(context);
            assertFalse((Boolean) node.getOutput("output_valid"));
            assertFalse(String.valueOf(node.getOutput("output_error")).isBlank());
        } finally {
            SubgraphCallFrameBridge.restore(handle);
        }
    }

    @Test
    void subgraphBuildsTypedDynamicPortsFromChildInterface() {
        SavedGraph child = childGraphWithTypedIo();
        SubgraphNode subgraph = new SubgraphNode();
        subgraph.syncPortsFromDefinition(child);

        IPort inputPort = subgraph.getInputPorts().stream()
                .filter(port -> SubgraphPortIds.dynamicInputPortId("blocks").equals(port.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(NodeDataType.BLOCK_LIST, inputPort.getDataType());
    }

    @Test
    void duplicateInterfaceNamesAreInvalid() {
        SavedGraph graph = new SavedGraph();
        graph.nodes = new ArrayList<>();
        SavedNode in1 = savedNode("gi1", "utilities.organization.graph_input");
        in1.state = Map.of("inputName", "dup");
        SavedNode in2 = savedNode("gi2", "utilities.organization.graph_input");
        in2.state = Map.of("inputName", "dup");
        graph.nodes.add(in1);
        graph.nodes.add(in2);

        GraphInterfaceValidator.ValidationResult result = GraphInterfaceValidator.validate(graph);
        assertFalse(result.valid());
        assertNotNull(result.error());
    }

    @Test
    void subgraphConnectedNullEnabledFailsClosed() {
        SavedGraph definition = childGraphWithExecutionProbe();
        SubgraphNode subgraph = new SubgraphNode();
        subgraph.setNodeState(Map.of("subgraphRef", "inner"));
        subgraph.syncPortsFromDefinition(definition);

        NullBooleanSource nullEnabled = new NullBooleanSource();
        NodeGraph graph = new NodeGraph("enabled-null");
        graph.addNode(subgraph);
        graph.addNode(nullEnabled);
        graph.connect(nullEnabled.getId(), "out", subgraph.getId(), "input_enabled");

        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSubgraphDefinitions(Map.of("inner", definition));

        assertTrue(new NodeExecutor(graph, context).executeSync());
        assertFalse((Boolean) subgraph.getOutput("output_valid"));
        assertFalse(String.valueOf(subgraph.getOutput("output_error")).isBlank());
        assertEquals(0, ChainedSideEffectProbe.globalExecutionCount());
    }

    @Test
    void nestedExecutorInheritsParentCancellation() {
        SavedGraph definition = childGraphWithExecutionProbe();
        SubgraphNode wrapper = new SubgraphNode();
        wrapper.setNodeState(Map.of("subgraphRef", "inner"));
        wrapper.syncPortsFromDefinition(definition);

        NodeGraph outer = new NodeGraph("outer-cancel");
        outer.addNode(wrapper);

        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSubgraphDefinitions(Map.of("inner", definition));

        SimpleCancellationToken cancellation = new SimpleCancellationToken();
        cancellation.cancel();
        NodeExecutor executor = new NodeExecutor(
                outer,
                context,
                null,
                IncrementalExecutionOptions.defaults(),
                ExecutionRunLimits.defaults(),
                cancellation,
                false,
                null,
                true
        );
        assertFalse(executor.executeSync());
        assertEquals(0, ChainedSideEffectProbe.globalExecutionCount());
    }

    @Test
    void nestedExecutorSharesParentStepBudget() {
        ChainedSideEffectProbe.resetGlobalExecutionCount();
        NodeGraph inner = new NodeGraph("inner-budget");
        ChainedSideEffectProbe[] probes = new ChainedSideEffectProbe[6];
        for (int i = 0; i < probes.length; i++) {
            probes[i] = new ChainedSideEffectProbe();
            inner.addNode(probes[i]);
        }
        GraphInputNode input = new GraphInputNode();
        input.setNodeState(Map.of("inputName", "in"));
        GraphOutputNode output = new GraphOutputNode();
        output.setNodeState(Map.of("outputName", "out"));
        inner.addNode(input);
        inner.addNode(output);
        inner.connect(input.getId(), "output_value", probes[0].getId(), "in");
        for (int i = 0; i < probes.length - 1; i++) {
            inner.connect(probes[i].getId(), "out", probes[i + 1].getId(), "in");
        }
        inner.connect(probes[probes.length - 1].getId(), "out", output.getId(), "input_value");

        SavedGraph definition = GraphSerializer.toSavedGraph(inner);
        SubgraphNode wrapper = new SubgraphNode();
        wrapper.setNodeState(Map.of("subgraphRef", "inner"));
        wrapper.syncPortsFromDefinition(definition);

        NodeGraph outer = new NodeGraph("outer-budget");
        outer.addNode(wrapper);

        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSubgraphDefinitions(Map.of("inner", definition));

        ExecutionRunLimits limits = new ExecutionRunLimits(3L, 60_000L);
        NodeExecutor executor = new NodeExecutor(
                outer,
                context,
                null,
                IncrementalExecutionOptions.defaults(),
                limits,
                CancellationToken.none(),
                false,
                null,
                true
        );
        assertFalse(executor.executeSync());
        assertFalse((Boolean) wrapper.getOutput("output_valid"));
        assertTrue(ChainedSideEffectProbe.globalExecutionCount() >= 1);
        assertTrue(ChainedSideEffectProbe.globalExecutionCount() < probes.length);
    }

    @Test
    void nestedPreviewInheritsSkipSideEffectsPolicy() {
        SideEffectProbe.resetGlobalExecutionCount();
        NodeGraph inner = new NodeGraph("inner-side-effect");
        SideEffectProbe sideEffect = new SideEffectProbe();
        inner.addNode(sideEffect);

        SavedGraph definition = GraphSerializer.toSavedGraph(inner);
        SubgraphNode wrapper = new SubgraphNode();
        wrapper.setNodeState(Map.of("subgraphRef", "inner"));
        wrapper.syncPortsFromDefinition(definition);

        NodeGraph outer = new NodeGraph("outer");
        outer.addNode(wrapper);

        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setSubgraphDefinitions(Map.of("inner", definition));

        NodeExecutor previewExecutor = new NodeExecutor(
                outer,
                context,
                null,
                IncrementalExecutionOptions.previewDefaults(),
                ExecutionRunLimits.defaults(),
                CancellationToken.none(),
                true,
                null,
                true
        );
        assertTrue(previewExecutor.executeSync());
        assertEquals(0, SideEffectProbe.globalExecutionCount());
    }

    @Test
    void depthCapFailsClosed() {
        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setVariable(
                "__nodecraft.subgraph.call_stack",
                new ArrayList<>(List.of("a", "b", "c", "d", "e", "f", "g", "h"))
        );

        SavedGraph child = minimalChildGraph();
        context.setSubgraphDefinitions(Map.of("deep", child));

        SubgraphNode subgraph = new SubgraphNode();
        subgraph.setNodeState(Map.of("subgraphRef", "deep"));
        subgraph.syncPortsFromDefinition(child);

        subgraph.processNode(context);
        assertFalse((Boolean) subgraph.getOutput("output_valid"));
        assertTrue(String.valueOf(subgraph.getOutput("output_error")).contains(
                String.valueOf(GenerationLimits.MAX_SUBGRAPH_CALL_DEPTH)
        ));
    }

    @Test
    void migrateV57ToV58ExtractsEmbeddedJsonAndLiftsCommentGroup() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V57;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();
        graph.nodePositions = new HashMap<>();

        SavedNode subgraph = savedNode("sg1", "utilities.organization.subgraph");
        SavedGraph embedded = minimalChildGraph();
        Map<String, Object> state = new HashMap<>();
        state.put("subgraphRef", "helper");
        state.put("embeddedGraphJson", GraphSerializer.toJson(embedded));
        state.put("inputKey", "in");
        subgraph.state = state;
        graph.nodes.add(subgraph);

        SavedNode comment = savedNode("c1", "utilities.organization.comment");
        comment.state = new Object[] {"note", "#000", "#FFEB3B", 14f, false, false, "STICKY_NOTE", 200d, 120d};
        graph.nodes.add(comment);
        graph.nodePositions.put("c1", new com.nodecraft.nodesystem.io.SavedPosition(10f, 20f));

        SavedNode group = savedNode("g1", "utilities.organization.group");
        group.state = new Object[] {"Group", "#3498db", false, false, 300d, 200d, new UUID[0], new boolean[] {false, false}};
        graph.nodes.add(group);

        graph.connections.add(wire("sg1", "input_value", "sg1", "output_value"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertNotNull(migrated.subgraphDefinitions.get("helper"));
        assertEquals(1, migrated.nodes.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> migratedState = (Map<String, Object>) migrated.nodes.getFirst().state;
        assertFalse(migratedState.containsKey("embeddedGraphJson"));
        assertEquals(1, migrated.comments.size());
        assertEquals(1, migrated.groups.size());
        SavedGraphComment savedComment = migrated.comments.getFirst();
        assertEquals("note", savedComment.text);
        assertEquals(10f, savedComment.x);
    }

    @Test
    void blockListTypePreservedOnSubgraphInterface() {
        SavedGraph child = childGraphWithTypedIo();
        SubgraphInterfaceScanner.InterfaceSpec spec = SubgraphInterfaceScanner.scan(child);
        SubgraphInterfaceScanner.InputSpec input = spec.inputs().stream()
                .filter(entry -> "blocks".equals(entry.name()))
                .findFirst()
                .orElseThrow();
        assertEquals(NodeDataType.BLOCK_LIST, input.type());
    }

    private static SavedGraph childGraphWithExecutionProbe() {
        ChainedSideEffectProbe.resetGlobalExecutionCount();
        NodeGraph inner = new NodeGraph("inner-probe");
        GraphInputNode input = new GraphInputNode();
        input.setNodeState(Map.of("inputName", "in"));
        ChainedSideEffectProbe probe = new ChainedSideEffectProbe();
        GraphOutputNode output = new GraphOutputNode();
        output.setNodeState(Map.of("outputName", "out"));
        inner.addNode(input);
        inner.addNode(probe);
        inner.addNode(output);
        inner.connect(input.getId(), "output_value", probe.getId(), "in");
        inner.connect(probe.getId(), "out", output.getId(), "input_value");
        return GraphSerializer.toSavedGraph(inner);
    }

    private static SavedGraph childGraphWithTypedIo() {
        SavedGraph graph = new SavedGraph();
        graph.nodes = new ArrayList<>();
        SavedNode input = savedNode("in", "utilities.organization.graph_input");
        input.state = Map.of(
                "inputName", "blocks",
                "declaredType", "block_list",
                "inferredType", "block_list"
        );
        SavedNode output = savedNode("out", "utilities.organization.graph_output");
        output.state = Map.of("outputName", "blocks", "declaredType", "block_list");
        graph.nodes.add(input);
        graph.nodes.add(output);
        graph.connections = List.of(wire("in", "output_value", "out", "input_value"));
        return graph;
    }

    private static SavedGraph minimalChildGraph() {
        SavedGraph graph = new SavedGraph();
        graph.nodes = new ArrayList<>();
        SavedNode input = savedNode("in", "utilities.organization.graph_input");
        input.state = Map.of("inputName", "in");
        SavedNode output = savedNode("out", "utilities.organization.graph_output");
        output.state = Map.of("outputName", "out");
        graph.nodes.add(input);
        graph.nodes.add(output);
        graph.connections = List.of(wire("in", "output_value", "out", "input_value"));
        return graph;
    }

    private static void assertEffect(String typeId, NodeEffect expected) {
        INode node = registry.createNodeInstance(typeId);
        NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
        assertNotNull(info);
        assertEquals(expected, info.effect());
    }

    private static Set<String> portIds(List<IPort> ports) {
        return ports.stream().map(IPort::getId).collect(Collectors.toSet());
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        return node;
    }

    private static SavedConnection wire(String src, String srcPort, String dst, String dstPort) {
        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = src;
        connection.sourcePortId = srcPort;
        connection.targetNodeId = dst;
        connection.targetPortId = dstPort;
        return connection;
    }

    private static final class NullBooleanSource extends BaseNode {
        NullBooleanSource() {
            super(UUID.randomUUID(), "test.organization.null_boolean");
            addOutputPort(new BasePort("out", "Out", "", NodeDataType.BOOLEAN, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
            outputValues.put("out", null);
        }
    }

    @NodeInfo(
            effect = NodeEffect.PURE,
            id = "test.organization.side_effect_probe",
            displayName = "Side Effect Probe",
            description = "Contract test probe",
            category = "test",
            order = 0
    )
    public static final class SideEffectProbe extends BaseNode {
        private static final AtomicInteger GLOBAL_EXECUTION_COUNT = new AtomicInteger();

        public SideEffectProbe() {
            super(UUID.randomUUID(), SIDE_EFFECT_PROBE_ID);
            addInputPort(new BasePort("in", "In", "", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
            GLOBAL_EXECUTION_COUNT.incrementAndGet();
        }

        static void resetGlobalExecutionCount() {
            GLOBAL_EXECUTION_COUNT.set(0);
        }

        static int globalExecutionCount() {
            return GLOBAL_EXECUTION_COUNT.get();
        }
    }

    @NodeInfo(
            effect = NodeEffect.PURE,
            id = "test.organization.chained_side_effect_probe",
            displayName = "Chained Side Effect Probe",
            description = "Contract test passthrough probe",
            category = "test",
            order = 0
    )
    public static final class ChainedSideEffectProbe extends BaseNode {
        private static final AtomicInteger GLOBAL_EXECUTION_COUNT = new AtomicInteger();

        public ChainedSideEffectProbe() {
            super(UUID.randomUUID(), CHAINED_SIDE_EFFECT_PROBE_ID);
            addInputPort(new BasePort("in", "In", "", NodeDataType.ANY, this));
            addOutputPort(new BasePort("out", "Out", "", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
            GLOBAL_EXECUTION_COUNT.incrementAndGet();
            outputValues.put("out", getInput("in"));
        }

        static void resetGlobalExecutionCount() {
            GLOBAL_EXECUTION_COUNT.set(0);
        }

        static int globalExecutionCount() {
            return GLOBAL_EXECUTION_COUNT.get();
        }
    }
}

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

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV58() {
        assertEquals(58, GraphFormatVersion.V58);
        assertEquals(GraphFormatVersion.V58, GraphFormatVersion.CURRENT);
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
    void nestedPreviewInheritsSkipSideEffectsPolicy() {
        NodeGraph inner = new NodeGraph("inner-side-effect");
        SideEffectProbe sideEffect = new SideEffectProbe("world.write.organization_contract_probe");
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
        assertEquals(0, sideEffect.executionCount());
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

    private static final class SideEffectProbe extends BaseNode {
        private final AtomicInteger executionCount = new AtomicInteger();

        SideEffectProbe(String typeId) {
            super(UUID.randomUUID(), typeId);
            addInputPort(new BasePort("in", "In", "", NodeDataType.ANY, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
            executionCount.incrementAndGet();
        }

        int executionCount() {
            return executionCount.get();
        }
    }
}

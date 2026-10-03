package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.PortTypeResolver;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.VariableEntry;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.nodes.input.values.BooleanToggleNode;
import com.nodecraft.nodesystem.nodes.utilities.organization.GraphInputNode;
import com.nodecraft.nodesystem.nodes.utilities.organization.GraphOutputNode;
import com.nodecraft.nodesystem.nodes.utilities.organization.SubgraphNode;
import com.nodecraft.nodesystem.nodes.utilities.organization.SubgraphPortIds;
import com.nodecraft.nodesystem.nodes.variable.ClearVariablesNode;
import com.nodecraft.nodesystem.nodes.variable.FrameLocalVariableNode;
import com.nodecraft.nodesystem.nodes.variable.GetVariableNode;
import com.nodecraft.nodesystem.nodes.variable.SetVariableNode;
import com.nodecraft.nodesystem.nodes.variable.VariableListNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Variable Scope v1 language fence.
 */
class VariableLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "variable.set",
            "variable.get",
            "variable.list",
            "variable.frame_local",
            "variable.remove",
            "variable.clear"
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
    void exactlySixVariableNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("variable."))
                .sorted()
                .toList();
        assertEquals(6, ids.size(), ids.toString());
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
        int[] orders = CANONICAL_IDS.stream()
                .mapToInt(typeId -> {
                    INode node = registry.createNodeInstance(typeId);
                    assertNotNull(node);
                    NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
                    assertNotNull(info);
                    return info.order();
                })
                .sorted()
                .toArray();
        assertEquals(6, orders.length);
        for (int i = 0; i < orders.length; i++) {
            assertEquals(i, orders[i]);
        }
    }

    @Test
    void variableNodesDeclareExpectedEffects() {
        assertEffect("variable.set", NodeEffect.CONTEXT_WRITE);
        assertEffect("variable.get", NodeEffect.CONTEXT_READ);
        assertEffect("variable.list", NodeEffect.CONTEXT_READ);
        assertEffect("variable.frame_local", NodeEffect.CONTEXT_WRITE);
        assertEffect("variable.remove", NodeEffect.CONTEXT_WRITE);
        assertEffect("variable.clear", NodeEffect.CONTEXT_WRITE);
    }

    @Test
    void valuePortsBindPassthroughT() {
        for (String typeId : List.of("variable.set", "variable.get", "variable.frame_local")) {
            INode node = registry.createNodeInstance(typeId);
            assertNotNull(node);
            for (IPort port : allPorts(node)) {
                if (port.getDataType() == NodeDataType.ANY) {
                    assertTrue(port.isPassthroughBinding(), typeId + "#" + port.getId());
                    assertEquals("T", port.getListTypeVariable(), typeId + "#" + port.getId());
                }
            }
        }
    }

    @Test
    void blockPosThroughSetGetCannotWashToPoint() {
        PortStubNode blockSource = new PortStubNode(NodeDataType.BLOCK_POS);
        PortStubNode pointSink = new PortStubNode(NodeDataType.POINT, true);
        SetVariableNode set = new SetVariableNode();
        GetVariableNode get = new GetVariableNode();

        BasePort blockOut = (BasePort) blockSource.getOutputPorts().getFirst();
        BasePort setIn = (BasePort) set.getInputPorts().stream()
                .filter(port -> "input_value".equals(port.getId()))
                .findFirst()
                .orElseThrow();
        BasePort setOut = (BasePort) set.getOutputPorts().stream()
                .filter(port -> "output_value".equals(port.getId()))
                .findFirst()
                .orElseThrow();
        BasePort getOut = (BasePort) get.getOutputPorts().stream()
                .filter(port -> "output_value".equals(port.getId()))
                .findFirst()
                .orElseThrow();
        BasePort pointIn = (BasePort) pointSink.getInputPorts().getFirst();

        assertTrue(blockOut.connectTo(setIn));
        assertEquals(NodeDataType.BLOCK_POS, PortTypeResolver.resolveEffectiveType(setOut));
        assertTrue(setOut.connectTo((BasePort) get.getInputPorts().stream()
                .filter(port -> "input_default_value".equals(port.getId()))
                .findFirst()
                .orElseThrow()));
        assertEquals(NodeDataType.BLOCK_POS, PortTypeResolver.resolveEffectiveType(getOut));
        assertFalse(PortTypeResolver.isConnectable(getOut, pointIn));
    }

    @Test
    void getVariableContainsKeySemanticsIncludingNullValue() {
        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setVariable("nullKey", null);

        GetVariableNode get = new GetVariableNode();
        get.setNodeState(Map.of("defaultName", "nullKey"));
        get.compute(Map.of("input_default_value", "fallback"), context);

        assertTrue((Boolean) get.getOutput("output_valid"));
        assertTrue((Boolean) get.getOutput("output_exists"));
        assertTrue((Boolean) get.getOutput("output_is_null"));
        assertNull(get.getOutput("output_value"));
    }

    @Test
    void setVariableConnectedNullNameFailsClosed() {
        SetVariableProbe set = new SetVariableProbe();
        set.setNodeState(Map.of("defaultName", "fromProperty"));
        set.connectInput("input_name", NodeDataType.STRING);
        set.setInput("input_name", null);
        set.setInput("input_value", 1);
        set.processNode(null);

        assertFalse((Boolean) set.getOutput("output_valid"));
        assertFalse(set.getOutputPorts().stream().anyMatch(port -> "output_success".equals(port.getId())));
    }

    @Test
    void frameLocalConnectedNullWriteFailsClosed() {
        FrameLocalProbe frameLocal = new FrameLocalProbe();
        frameLocal.connectInput("input_write", NodeDataType.BOOLEAN);
        frameLocal.setInput("input_write", null);
        frameLocal.processNode(null);

        assertFalse((Boolean) frameLocal.getOutput("output_valid"));
    }

    @Test
    void clearVariablesConnectedNullClearFailsClosed() {
        ClearVariablesProbe clear = new ClearVariablesProbe();
        clear.connectInput("input_clear", NodeDataType.BOOLEAN);
        clear.setInput("input_clear", null);
        clear.processNode(null);

        assertFalse((Boolean) clear.getOutput("output_valid"));
    }

    @Test
    void variableListNamesPortIsStringList() {
        VariableListNode list = new VariableListNode();
        IPort names = list.getOutputPorts().stream()
                .filter(port -> "output_names".equals(port.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(NodeDataType.STRING_LIST, names.getDataType());
    }

    @Test
    void variableListHasNoEntriesPort() {
        VariableListNode list = new VariableListNode();
        assertFalse(list.getOutputPorts().stream().anyMatch(port -> "output_entries".equals(port.getId())));
    }

    @Test
    void setVariableCannotOverwriteWithWrongType() {
        ExecutionContext context = ExecutionContext.createEmpty(null);

        SetVariableProbe first = new SetVariableProbe();
        first.setNodeState(Map.of("defaultName", "foo"));
        first.connectInput("input_value", NodeDataType.VECTOR);
        first.setInput("input_value", new VectorData(1, 0, 0));
        first.processNode(context);
        assertTrue((Boolean) first.getOutput("output_valid"));

        SetVariableProbe second = new SetVariableProbe();
        second.setNodeState(Map.of("defaultName", "foo"));
        second.connectInput("input_value", NodeDataType.BLOCK_POS);
        second.setInput("input_value", new BlockPos(1, 2, 3));
        second.processNode(context);

        assertFalse((Boolean) second.getOutput("output_valid"));
        assertNull(second.getOutput("output_previous"));
        assertTrue(String.valueOf(second.getOutput("output_error")).contains("vector"));
        assertTrue(String.valueOf(second.getOutput("output_error")).contains("block_pos"));
        assertTrue(context.getVariable("foo") instanceof VectorData);
        Object stored = context.getVariableStorage("foo");
        assertTrue(stored instanceof VariableEntry);
        assertEquals(NodeDataType.VECTOR, ((VariableEntry) stored).type());
    }

    @Test
    void getVariableCannotReturnStoredValueWithDifferentBoundType() {
        ExecutionContext context = ExecutionContext.createEmpty(null);

        SetVariableProbe set = new SetVariableProbe();
        set.setNodeState(Map.of("defaultName", "foo"));
        set.connectInput("input_value", NodeDataType.VECTOR);
        set.setInput("input_value", new VectorData(0, 1, 0));
        set.processNode(context);
        assertTrue((Boolean) set.getOutput("output_valid"));

        GetVariableProbe get = new GetVariableProbe();
        get.setNodeState(Map.of("defaultName", "foo"));
        get.connectInput("input_default_value", NodeDataType.BLOCK_POS);
        get.setInput("input_default_value", new BlockPos(0, 0, 0));
        get.processNode(context);

        assertFalse((Boolean) get.getOutput("output_valid"));
        assertTrue((Boolean) get.getOutput("output_exists"));
        assertNull(get.getOutput("output_value"));
        assertTrue(String.valueOf(get.getOutput("output_error")).contains("vector"));
    }

    @Test
    void frameLocalCannotOverwriteWithWrongType() {
        ExecutionContext context = ExecutionContext.createEmpty(null);

        FrameLocalProbe first = new FrameLocalProbe();
        first.setNodeState(Map.of("defaultFrame", "f", "defaultName", "slot"));
        first.connectInput("input_write", NodeDataType.BOOLEAN);
        first.connectInput("input_value", NodeDataType.VECTOR);
        first.setInput("input_write", true);
        first.setInput("input_value", new VectorData(2, 0, 0));
        first.processNode(context);
        assertTrue((Boolean) first.getOutput("output_valid"));

        FrameLocalProbe second = new FrameLocalProbe();
        second.setNodeState(Map.of("defaultFrame", "f", "defaultName", "slot"));
        second.connectInput("input_write", NodeDataType.BOOLEAN);
        second.connectInput("input_value", NodeDataType.BLOCK_POS);
        second.setInput("input_write", true);
        second.setInput("input_value", new BlockPos(3, 4, 5));
        second.processNode(context);

        assertFalse((Boolean) second.getOutput("output_valid"));
        assertTrue((Boolean) second.getOutput("output_exists"));
        assertNull(second.getOutput("output_previous"));
    }

    @Test
    void frameLocalCannotReturnStoredValueWithDifferentBoundType() {
        ExecutionContext context = ExecutionContext.createEmpty(null);

        FrameLocalProbe write = new FrameLocalProbe();
        write.setNodeState(Map.of("defaultFrame", "f", "defaultName", "slot"));
        write.connectInput("input_write", NodeDataType.BOOLEAN);
        write.connectInput("input_value", NodeDataType.VECTOR);
        write.setInput("input_write", true);
        write.setInput("input_value", new VectorData(1, 0, 0));
        write.processNode(context);
        assertTrue((Boolean) write.getOutput("output_valid"));

        FrameLocalProbe read = new FrameLocalProbe();
        read.setNodeState(Map.of("defaultFrame", "f", "defaultName", "slot"));
        read.connectInput("input_write", NodeDataType.BOOLEAN);
        read.connectInput("input_default", NodeDataType.BLOCK_POS);
        read.setInput("input_write", false);
        read.setInput("input_default", new BlockPos(0, 0, 0));
        read.processNode(context);

        assertFalse((Boolean) read.getOutput("output_valid"));
        assertTrue((Boolean) read.getOutput("output_exists"));
        assertNull(read.getOutput("output_value"));
        assertTrue(String.valueOf(read.getOutput("output_error")).contains("vector"));
    }

    @Test
    void clearNeverRemovesInternalVariables() {
        ExecutionContext context = ExecutionContext.createEmpty(null);
        context.setVariable("userVar", "x");
        context.setVariable("__nodecraft.internal", "y");

        ClearVariablesProbe clear = new ClearVariablesProbe();
        clear.connectInput("input_clear", NodeDataType.BOOLEAN);
        clear.compute(Map.of("input_clear", true), context);

        assertTrue((Boolean) clear.getOutput("output_valid"));
        assertEquals("y", context.getVariable("__nodecraft.internal"));
        assertNull(context.getVariable("userVar"));
    }

    @Test
    void parentAndChildVariablesAreIsolated() {
        ExecutionContext context = ExecutionContext.createEmpty(null);

        SetVariableProbe parentSet = new SetVariableProbe();
        parentSet.setNodeState(Map.of("defaultName", "foo"));
        parentSet.connectInput("input_value", NodeDataType.INTEGER);
        parentSet.setInput("input_value", 1);
        parentSet.processNode(context);
        assertTrue((Boolean) parentSet.getOutput("output_valid"));

        SavedGraph child = childGraphSetThenGet();
        SubgraphNode subgraph = new SubgraphNode();
        subgraph.setNodeState(Map.of("subgraphRef", "inner"));
        subgraph.syncPortsFromDefinition(child);
        context.setSubgraphDefinitions(Map.of("inner", child));
        subgraph.setInput(SubgraphPortIds.dynamicInputPortId("in"), 2);
        subgraph.processNode(context);

        assertTrue((Boolean) subgraph.getOutput("output_valid"));
        assertEquals(2, subgraph.getOutput(SubgraphPortIds.dynamicOutputPortId("out")));

        GetVariableProbe parentGet = new GetVariableProbe();
        parentGet.setNodeState(Map.of("defaultName", "foo"));
        parentGet.connectInput("input_default_value", NodeDataType.INTEGER);
        parentGet.processNode(context);
        assertTrue((Boolean) parentGet.getOutput("output_valid"));
        assertEquals(1, parentGet.getOutput("output_value"));
    }

    @Test
    void childClearDoesNotClearParentVariables() {
        ExecutionContext context = ExecutionContext.createEmpty(null);

        SetVariableProbe parentSet = new SetVariableProbe();
        parentSet.setNodeState(Map.of("defaultName", "foo"));
        parentSet.connectInput("input_value", NodeDataType.INTEGER);
        parentSet.setInput("input_value", 1);
        parentSet.processNode(context);

        SavedGraph child = childGraphClear();
        SubgraphNode subgraph = new SubgraphNode();
        subgraph.setNodeState(Map.of("subgraphRef", "inner"));
        subgraph.syncPortsFromDefinition(child);
        context.setSubgraphDefinitions(Map.of("inner", child));
        subgraph.processNode(context);
        assertTrue((Boolean) subgraph.getOutput("output_valid"));

        GetVariableProbe parentGet = new GetVariableProbe();
        parentGet.setNodeState(Map.of("defaultName", "foo"));
        parentGet.connectInput("input_default_value", NodeDataType.INTEGER);
        parentGet.processNode(context);
        assertTrue((Boolean) parentGet.getOutput("output_valid"));
        assertEquals(1, parentGet.getOutput("output_value"));
    }

    @Test
    void frameLocalNamespaceDoesNotLeakAcrossSubgraphCalls() {
        ExecutionContext context = ExecutionContext.createEmpty(null);

        FrameLocalProbe parentWrite = new FrameLocalProbe();
        parentWrite.setNodeState(Map.of("defaultFrame", "session", "defaultName", "counter"));
        parentWrite.connectInput("input_write", NodeDataType.BOOLEAN);
        parentWrite.connectInput("input_value", NodeDataType.INTEGER);
        parentWrite.setInput("input_write", true);
        parentWrite.setInput("input_value", 1);
        parentWrite.processNode(context);
        assertTrue((Boolean) parentWrite.getOutput("output_valid"));

        SavedGraph child = childGraphFrameLocalWrite();
        SubgraphNode subgraph = new SubgraphNode();
        subgraph.setNodeState(Map.of("subgraphRef", "inner"));
        subgraph.syncPortsFromDefinition(child);
        context.setSubgraphDefinitions(Map.of("inner", child));
        subgraph.setInput(SubgraphPortIds.dynamicInputPortId("value"), 2);
        subgraph.processNode(context);
        assertTrue((Boolean) subgraph.getOutput("output_valid"));
        assertEquals(2, subgraph.getOutput(SubgraphPortIds.dynamicOutputPortId("out")));

        FrameLocalProbe parentRead = new FrameLocalProbe();
        parentRead.setNodeState(Map.of("defaultFrame", "session", "defaultName", "counter"));
        parentRead.connectInput("input_default", NodeDataType.INTEGER);
        parentRead.processNode(context);
        assertTrue((Boolean) parentRead.getOutput("output_valid"));
        assertEquals(1, parentRead.getOutput("output_value"));
    }

    @Test
    void variableListConnectedInvalidPrefixFailsClosed() {
        VariableListProbe list = new VariableListProbe();
        list.connectInput("input_prefix", NodeDataType.STRING);
        list.setInput("input_prefix", null);
        list.processNode(null);

        assertFalse((Boolean) list.getOutput("output_valid"));
        assertEquals(0, list.getOutput("output_count"));
        assertEquals(List.of(), list.getOutput("output_names"));
        assertEquals(List.of(), list.getOutput("output_values"));
    }

    @Test
    void overLengthVariableNameFailsClosed() {
        SetVariableProbe set = new SetVariableProbe();
        set.setNodeState(Map.of("defaultName", "a".repeat(GenerationLimits.MAX_VARIABLE_NAME_CHARS + 1)));
        set.connectInput("input_value", NodeDataType.INTEGER);
        set.setInput("input_value", 1);
        set.processNode(null);
        assertFalse((Boolean) set.getOutput("output_valid"));
        assertTrue(String.valueOf(set.getOutput("output_error")).contains("max length"));
    }

    @Test
    void overCapSetVariableFailsClosed() {
        ExecutionContext context = ExecutionContext.createEmpty(null);
        for (int i = 0; i < GenerationLimits.MAX_USER_VARIABLES_PER_SCOPE; i++) {
            SetVariableProbe fill = new SetVariableProbe();
            fill.setNodeState(Map.of("defaultName", "v" + i));
            fill.connectInput("input_value", NodeDataType.INTEGER);
            fill.setInput("input_value", i);
            fill.processNode(context);
            assertTrue((Boolean) fill.getOutput("output_valid"), "v" + i);
        }

        SetVariableProbe overflow = new SetVariableProbe();
        overflow.setNodeState(Map.of("defaultName", "overflow"));
        overflow.connectInput("input_value", NodeDataType.INTEGER);
        overflow.setInput("input_value", 0);
        overflow.processNode(context);
        assertFalse((Boolean) overflow.getOutput("output_valid"));
        assertTrue(String.valueOf(overflow.getOutput("output_error")).contains("max user variables"));
    }

    private static SavedGraph childGraphSetThenGet() {
        NodeGraph inner = new NodeGraph("inner-vars");
        GraphInputNode input = new GraphInputNode();
        input.setNodeState(Map.of("inputName", "in", "declaredType", "integer", "inferredType", "integer"));
        SetVariableNode set = new SetVariableNode();
        set.setNodeState(Map.of("defaultName", "foo"));
        GetVariableNode get = new GetVariableNode();
        get.setNodeState(Map.of("defaultName", "foo"));
        GraphOutputNode output = new GraphOutputNode();
        output.setNodeState(Map.of("outputName", "out", "declaredType", "integer", "inferredType", "integer"));
        inner.addNode(input);
        inner.addNode(set);
        inner.addNode(get);
        inner.addNode(output);
        assertTrue(inner.connect(input.getId(), "output_value", set.getId(), "input_value"));
        assertTrue(inner.connect(set.getId(), "output_value", get.getId(), "input_default_value"));
        assertTrue(inner.connect(get.getId(), "output_value", output.getId(), "input_value"));
        return GraphSerializer.toSavedGraph(inner);
    }

    private static SavedGraph childGraphClear() {
        NodeGraph inner = new NodeGraph("inner-clear");
        BooleanToggleNode toggle = new BooleanToggleNode();
        toggle.setNodeState(Map.of("value", true));
        ClearVariablesNode clear = new ClearVariablesNode();
        GraphOutputNode output = new GraphOutputNode();
        output.setNodeState(Map.of("outputName", "ok", "declaredType", "boolean", "inferredType", "boolean"));
        inner.addNode(toggle);
        inner.addNode(clear);
        inner.addNode(output);
        assertTrue(inner.connect(toggle.getId(), "output_value", clear.getId(), "input_clear"));
        assertTrue(inner.connect(clear.getId(), "output_valid", output.getId(), "input_value"));
        return GraphSerializer.toSavedGraph(inner);
    }

    private static SavedGraph childGraphFrameLocalWrite() {
        NodeGraph inner = new NodeGraph("inner-frame");
        GraphInputNode valueIn = new GraphInputNode();
        valueIn.setNodeState(Map.of("inputName", "value", "declaredType", "integer", "inferredType", "integer"));
        BooleanToggleNode writeToggle = new BooleanToggleNode();
        writeToggle.setNodeState(Map.of("value", true));
        FrameLocalVariableNode frameLocal = new FrameLocalVariableNode();
        frameLocal.setNodeState(Map.of("defaultFrame", "session", "defaultName", "counter"));
        GraphOutputNode output = new GraphOutputNode();
        output.setNodeState(Map.of("outputName", "out", "declaredType", "integer", "inferredType", "integer"));
        inner.addNode(valueIn);
        inner.addNode(writeToggle);
        inner.addNode(frameLocal);
        inner.addNode(output);
        assertTrue(inner.connect(valueIn.getId(), "output_value", frameLocal.getId(), "input_value"));
        assertTrue(inner.connect(writeToggle.getId(), "output_value", frameLocal.getId(), "input_write"));
        assertTrue(inner.connect(frameLocal.getId(), "output_value", output.getId(), "input_value"));
        return GraphSerializer.toSavedGraph(inner);
    }

    private static void assertEffect(String typeId, NodeEffect expected) {
        INode node = registry.createNodeInstance(typeId);
        assertNotNull(node);
        NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
        assertNotNull(info);
        assertEquals(expected, info.effect(), typeId);
    }

    private static List<IPort> allPorts(INode node) {
        List<IPort> ports = new ArrayList<>();
        ports.addAll(node.getInputPorts());
        ports.addAll(node.getOutputPorts());
        return ports;
    }

    private static SavedNode savedNode(String nodeId, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = nodeId;
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

    private static SavedNode nodeOf(SavedGraph graph, String nodeId) {
        return graph.nodes.stream()
                .filter(node -> nodeId.equals(node.nodeId))
                .findFirst()
                .orElseThrow();
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
                .filter(port -> inputPortId.equals(port.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(target.getTypeId() + " missing port " + inputPortId));
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
    }

    private static final class SetVariableProbe extends SetVariableNode {
        void connectInput(String portId, NodeDataType outputType) {
            VariableLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class GetVariableProbe extends GetVariableNode {
        void connectInput(String portId, NodeDataType outputType) {
            VariableLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class FrameLocalProbe extends FrameLocalVariableNode {
        void connectInput(String portId, NodeDataType outputType) {
            VariableLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class ClearVariablesProbe extends ClearVariablesNode {
        void connectInput(String portId, NodeDataType outputType) {
            VariableLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class VariableListProbe extends VariableListNode {
        void connectInput(String portId, NodeDataType outputType) {
            VariableLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            this(outputType, false);
        }

        PortStubNode(NodeDataType type, boolean input) {
            super(UUID.randomUUID(), "test.port_stub");
            if (input) {
                addInputPort(new BasePort("input_stub", "Stub", "", type, this));
            } else {
                addOutputPort(new BasePort("output_stub", "Stub", "", type, this));
            }
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }
}

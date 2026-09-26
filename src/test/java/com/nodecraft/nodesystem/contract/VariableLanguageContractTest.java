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
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.variable.ClearVariablesNode;
import com.nodecraft.nodesystem.nodes.variable.FrameLocalVariableNode;
import com.nodecraft.nodesystem.nodes.variable.GetVariableNode;
import com.nodecraft.nodesystem.nodes.variable.SetVariableNode;
import com.nodecraft.nodesystem.nodes.variable.VariableListNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
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
 * Variable Scope v1 language fence (Graph V59).
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
    void currentGraphFormatIsV59() {
        assertEquals(59, GraphFormatVersion.V59);
        assertEquals(GraphFormatVersion.V59, GraphFormatVersion.CURRENT);
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
    void migrateV58ToV59RenamesSuccessAndDropsEntries() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V58;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();
        graph.nodePositions = new HashMap<>();

        SavedNode setNode = savedNode("s1", "variable.set");
        SavedNode listNode = savedNode("l1", "variable.list");
        SavedNode clearNode = savedNode("c1", "variable.clear");
        clearNode.state = new HashMap<>(Map.of("includeInternalVariables", true));
        graph.nodes.add(setNode);
        graph.nodes.add(listNode);
        graph.nodes.add(clearNode);

        graph.connections.add(wire("s1", "output_success", "t1", "input_stub"));
        graph.connections.add(wire("l1", "output_entries", "t2", "input_stub"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.V59, migrated.formatVersion);
        assertEquals("output_valid", migrated.connections.getFirst().sourcePortId);
        assertEquals(1, migrated.connections.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> clearState = (Map<String, Object>) nodeOf(migrated, "c1").state;
        assertFalse(clearState.containsKey("includeInternalVariables"));
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

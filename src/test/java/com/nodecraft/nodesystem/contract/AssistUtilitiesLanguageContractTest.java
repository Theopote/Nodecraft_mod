package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.PortTypeResolver;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.RelayNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.StringFormatNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.ValidateNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Assist Utilities v1 language fence (Graph V55).
 */
class AssistUtilitiesLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "utilities.assist.string_format",
            "utilities.assist.validate",
            "utilities.assist.coalesce",
            "utilities.assist.relay",
            "utilities.assist.signal_fork"
    );

    private static final Set<String> LEGACY_IDS = Set.of(
            "utilities.assist.reroute",
            "utilities.assist.tag_relay",
            "utilities.assist.assert",
            "utilities.assist.signal_merge"
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
    void currentGraphFormatIsV55() {
        assertEquals(55, GraphFormatVersion.V55);
        assertEquals(GraphFormatVersion.V55, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyFiveCanonicalAssistNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("utilities.assist."))
                .sorted()
                .toList();
        assertEquals(5, ids.size(), "Expected 5 assist nodes: " + ids);
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
        for (String legacy : LEGACY_IDS) {
            assertFalse(registry.getAllNodeIds().contains(legacy), legacy);
        }
    }

    @Test
    void assistNodesHaveUniqueOrderZeroThroughFour() {
        int[] orders = CANONICAL_IDS.stream()
                .mapToInt(typeId -> {
                    INode created = registry.createNodeInstance(typeId);
                    assertNotNull(created, typeId);
                    NodeInfo info = created.getClass().getAnnotation(NodeInfo.class);
                    assertNotNull(info, typeId);
                    return info.order();
                })
                .sorted()
                .toArray();
        assertEquals(5, orders.length);
        for (int i = 0; i < orders.length; i++) {
            assertEquals(i, orders[i], "Expected unique order " + i);
        }
    }

    @Test
    void unboundAnyCannotImplicitlyConnectToTyped() {
        assertEquals(
                TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
                TypeConversionRegistry.classify(NodeDataType.ANY, NodeDataType.POINT));
        assertEquals(
                TypeConversionRegistry.ConversionPolicy.IMPLICIT_SAFE,
                TypeConversionRegistry.classify(NodeDataType.POINT, NodeDataType.ANY));
    }

    @Test
    void passthroughPortsBindTypeVariable() {
        for (String typeId : List.of(
                "utilities.assist.relay",
                "utilities.assist.signal_fork",
                "utilities.assist.validate",
                "utilities.assist.coalesce"
        )) {
            INode node = registry.createNodeInstance(typeId);
            boolean found = false;
            for (IPort port : node.getInputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    assertTrue(port.isPassthroughBinding(), typeId + "." + port.getId());
                    assertEquals("T", port.getListTypeVariable());
                    found = true;
                }
            }
            assertTrue(found, typeId + " expected ANY passthrough input");
        }
    }

    @Test
    void blockPosThroughRelayCannotWashToPoint() {
        PortStubNode blockSource = new PortStubNode(NodeDataType.BLOCK_POS);
        PortStubNode pointSink = new PortStubNode(NodeDataType.POINT, true);
        RelayNode relay = new RelayNode();

        BasePort blockOut = (BasePort) blockSource.getOutputPorts().getFirst();
        BasePort relayIn = (BasePort) relay.getInputPorts().getFirst();
        BasePort relayOut = (BasePort) relay.getOutputPorts().getFirst();
        BasePort pointIn = (BasePort) pointSink.getInputPorts().getFirst();

        assertTrue(blockOut.connectTo(relayIn));
        assertEquals(NodeDataType.BLOCK_POS, PortTypeResolver.resolveEffectiveType(relayOut));
        assertFalse(PortTypeResolver.isConnectable(relayOut, pointIn));
    }

    @Test
    void vectorThroughRelayCannotWashToPoint() {
        PortStubNode vectorSource = new PortStubNode(NodeDataType.VECTOR);
        PortStubNode pointSink = new PortStubNode(NodeDataType.POINT, true);
        RelayNode relay = new RelayNode();

        assertTrue(((BasePort) vectorSource.getOutputPorts().getFirst())
                .connectTo((BasePort) relay.getInputPorts().getFirst()));
        assertFalse(PortTypeResolver.isConnectable(
                relay.getOutputPorts().getFirst(),
                pointSink.getInputPorts().getFirst()));
    }

    @Test
    void pointThroughRelayPreservesPoint() {
        PortStubNode pointSource = new PortStubNode(NodeDataType.POINT);
        PortStubNode pointSink = new PortStubNode(NodeDataType.POINT, true);
        RelayNode relay = new RelayNode();

        BasePort src = (BasePort) pointSource.getOutputPorts().getFirst();
        BasePort relayIn = (BasePort) relay.getInputPorts().getFirst();
        BasePort relayOut = (BasePort) relay.getOutputPorts().getFirst();
        BasePort sink = (BasePort) pointSink.getInputPorts().getFirst();

        assertTrue(src.connectTo(relayIn));
        assertEquals(NodeDataType.POINT, PortTypeResolver.resolveEffectiveType(relayOut));
        assertTrue(PortTypeResolver.isConnectable(relayOut, sink));

        PointData point = new PointData(1, 2, 3);
        relay.setInput("input_signal", point);
        relay.processNode(null);
        assertEquals(point, relay.getOutput("output_signal"));
    }

    @Test
    void validateHasNoPassedPortOrFailHard() {
        ValidateNode validate = new ValidateNode();
        assertFalse(validate.getOutputPorts().stream().anyMatch(p -> "output_passed".equals(p.getId())));
        Map<?, ?> state = assertInstanceOf(Map.class, validate.getNodeState());
        assertFalse(state.containsKey("failHard"));
    }

    @Test
    void validateConnectedNullConditionFailsClosed() {
        ValidateProbe probe = new ValidateProbe();
        probe.connectInput("input_condition", NodeDataType.BOOLEAN);
        probe.setInput("input_condition", null);
        probe.setInput("input_value", new PointData(1, 0, 0));
        probe.processNode(null);
        assertEquals(Boolean.FALSE, probe.getOutput("output_valid"));
        assertEquals(null, probe.getOutput("output_value"));
    }

    @Test
    void coalesceSkipsConnectedNullAndUsesPreferPrimary() {
        CoalesceProbe coalesce = new CoalesceProbe();
        coalesce.connectInput("input_primary", NodeDataType.POINT);
        coalesce.connectInput("input_secondary", NodeDataType.POINT);
        coalesce.setInput("input_primary", null);
        PointData secondary = new PointData(5, 0, 0);
        coalesce.setInput("input_secondary", secondary);
        coalesce.processNode(null);
        assertEquals(Boolean.TRUE, coalesce.getOutput("output_valid"));
        assertEquals(secondary, coalesce.getOutput("output_signal"));
        assertEquals("secondary", coalesce.getOutput("output_source"));
    }

    @Test
    void stringFormatTemplateEmptyStringIsValidWhenConnected() {
        StringFormatProbe format = new StringFormatProbe();
        format.connectInput("input_template", NodeDataType.STRING);
        format.setInput("input_template", "");
        format.setInput("input_value_0", "x");
        format.processNode(null);
        assertEquals(Boolean.TRUE, format.getOutput("output_valid"));
        assertEquals("", format.getOutput("output_text"));
    }

    @Test
    void stringFormatValuesConnectedNullFails() {
        StringFormatProbe format = new StringFormatProbe();
        format.connectInput("input_values", NodeDataType.LIST);
        format.setInput("input_values", null);
        format.processNode(null);
        assertEquals(Boolean.FALSE, format.getOutput("output_valid"));
    }

    @Test
    void stringFormatFormatsVectorData() {
        StringFormatNode format = new StringFormatNode();
        format.setInput("input_value_0", new VectorData(1.0d, 2.0d, 3.0d));
        format.processNode(null);
        assertEquals(Boolean.TRUE, format.getOutput("output_valid"));
        String text = assertInstanceOf(String.class, format.getOutput("output_text"));
        assertTrue(text.contains("1") && text.contains("2") && text.contains("3"), text);
    }

    @Test
    void migrateV54ToV55RemapsTypesAndDropsPassedPort() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V54;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();

        SavedNode reroute = savedNode("n1", "utilities.assist.reroute");
        SavedNode tag = savedNode("n2", "utilities.assist.tag_relay");
        Map<String, Object> tagState = new HashMap<>();
        tagState.put("tag", "danger");
        tagState.put("color", "#E53935");
        tag.state = tagState;

        SavedNode assertNode = savedNode("n3", "utilities.assist.assert");
        Map<String, Object> assertState = new HashMap<>();
        assertState.put("failHard", true);
        assertState.put("defaultCondition", false);
        assertNode.state = assertState;

        SavedNode merge = savedNode("n4", "utilities.assist.signal_merge");
        SavedNode sink = savedNode("n5", "reference.vectors.vector");
        graph.nodes.addAll(List.of(reroute, tag, assertNode, merge, sink));

        graph.connections.add(wire("n3", "output_passed", "n5", "input_x"));
        graph.connections.add(wire("n3", "output_valid", "n5", "input_y"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("utilities.assist.relay", typeOf(migrated, "n1"));
        assertEquals("utilities.assist.relay", typeOf(migrated, "n2"));
        assertEquals("utilities.assist.validate", typeOf(migrated, "n3"));
        assertEquals("utilities.assist.coalesce", typeOf(migrated, "n4"));

        @SuppressWarnings("unchecked")
        Map<String, Object> tagMigrated = (Map<String, Object>) nodeOf(migrated, "n2").state;
        assertEquals("danger", tagMigrated.get("tag"));

        @SuppressWarnings("unchecked")
        Map<String, Object> validateState = (Map<String, Object>) nodeOf(migrated, "n3").state;
        assertFalse(validateState.containsKey("failHard"));

        assertEquals(1, migrated.connections.size());
        assertEquals("output_valid", migrated.connections.getFirst().sourcePortId);
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        return node;
    }

    private static SavedConnection wire(String src, String srcPort, String dst, String dstPort) {
        SavedConnection c = new SavedConnection();
        c.sourceNodeId = src;
        c.sourcePortId = srcPort;
        c.targetNodeId = dst;
        c.targetPortId = dstPort;
        return c;
    }

    private static String typeOf(SavedGraph graph, String nodeId) {
        return nodeOf(graph, nodeId).typeId;
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

    private static final class ValidateProbe extends ValidateNode {
        void connectInput(String portId, NodeDataType outputType) {
            AssistUtilitiesLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class CoalesceProbe
            extends com.nodecraft.nodesystem.nodes.utilities.assist.CoalesceNode {
        void connectInput(String portId, NodeDataType outputType) {
            AssistUtilitiesLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class StringFormatProbe extends StringFormatNode {
        void connectInput(String portId, NodeDataType outputType) {
            AssistUtilitiesLanguageContractTest.connectInput(this, portId, outputType);
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

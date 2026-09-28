package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.CoalesceNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.RelayNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.SignalForkNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.StringFormatNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.ValidateNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Assist Utilities Language v2 (Graph V89).
 */
class AssistUtilitiesLanguageV2ContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
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
    void currentGraphFormatIsAtLeastV89() {
        assertEquals(89, GraphFormatVersion.V89);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V89);
    }

    @Test
    void exactlyFiveNodesWithUniqueOrdersZeroToFour() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("utilities.assist."))
            .sorted()
            .toList();
        assertEquals(5, ids.size(), "Expected 5 assist nodes: " + ids);
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));
        for (String legacy : LEGACY_IDS) {
            assertFalse(registry.getAllNodeIds().contains(legacy), legacy);
        }

        Set<Integer> orders = new HashSet<>();
        for (String id : CANONICAL_IDS) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 5; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void targetedNodesExposeErrorPorts() {
        for (String id : List.of(
            "utilities.assist.string_format",
            "utilities.assist.validate",
            "utilities.assist.coalesce"
        )) {
            INode node = registry.createNodeInstance(id);
            assertTrue(hasPort(node, "output_error"), id);
        }
        assertFalse(hasPort(registry.createNodeInstance("utilities.assist.relay"), "output_error"));
        assertFalse(hasPort(registry.createNodeInstance("utilities.assist.signal_fork"), "output_error"));
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
    void unboundAnyCannotImplicitlyConnectToTyped() {
        assertEquals(
            TypeConversionRegistry.ConversionPolicy.UNSUPPORTED,
            TypeConversionRegistry.classify(NodeDataType.ANY, NodeDataType.POINT));
    }

    @Test
    void relayNullPassthroughPreservesReference() {
        RelayNode relay = new RelayNode();
        relay.setInput("input_signal", null);
        relay.processNode(null);
        assertNull(relay.getOutput("output_signal"));
    }

    @Test
    void relayPreservesSameRuntimeValueReference() {
        PointData point = new PointData(1, 2, 3);
        RelayNode relay = new RelayNode();
        relay.setInput("input_signal", point);
        relay.processNode(null);
        assertSame(point, relay.getOutput("output_signal"));
    }

    @Test
    void signalForkOutputsSameReferenceOnAllBranches() {
        PointData point = new PointData(4, 5, 6);
        SignalForkNode fork = new SignalForkNode();
        fork.setInput("input_signal", point);
        fork.processNode(null);
        assertSame(point, fork.getOutput("output_a"));
        assertSame(point, fork.getOutput("output_b"));
    }

    @Test
    void signalForkAddBranchPreservesExistingPortIdentityAndConnection() {
        SignalForkNode fork = new SignalForkNode();
        PortStubNode source = new PortStubNode(NodeDataType.POINT);
        PortStubNode sink = new PortStubNode(NodeDataType.POINT, true);
        BasePort sourceOut = (BasePort) source.getOutputPorts().getFirst();
        BasePort forkIn = (BasePort) fork.getInputPorts().getFirst();
        BasePort forkOutA = (BasePort) fork.getOutputPorts().stream()
            .filter(port -> "output_a".equals(port.getId()))
            .findFirst()
            .orElseThrow();
        BasePort sinkIn = (BasePort) sink.getInputPorts().getFirst();
        assertTrue(sourceOut.connectTo(forkIn));
        assertTrue(forkOutA.connectTo(sinkIn));

        fork.addOutputBranch();
        BasePort forkOutAAfter = (BasePort) fork.getOutputPorts().stream()
            .filter(port -> "output_a".equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertSame(forkOutA, forkOutAAfter);
        assertTrue(forkOutAAfter.isConnected());
    }

    @Test
    void coalesceAddBranchPreservesPrimaryAndPreferPrimaryPorts() {
        CoalesceNode coalesce = new CoalesceNode();
        BasePort primaryBefore = (BasePort) coalesce.getInputPorts().stream()
            .filter(port -> "input_primary".equals(port.getId()))
            .findFirst()
            .orElseThrow();
        BasePort preferBefore = (BasePort) coalesce.getInputPorts().stream()
            .filter(port -> "input_prefer_primary".equals(port.getId()))
            .findFirst()
            .orElseThrow();

        coalesce.addInputBranch();

        BasePort primaryAfter = (BasePort) coalesce.getInputPorts().stream()
            .filter(port -> "input_primary".equals(port.getId()))
            .findFirst()
            .orElseThrow();
        BasePort preferAfter = (BasePort) coalesce.getInputPorts().stream()
            .filter(port -> "input_prefer_primary".equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertSame(primaryBefore, primaryAfter);
        assertSame(preferBefore, preferAfter);
    }

    @Test
    void coalesceConnectedNullBranchIsLegal() {
        CoalesceProbe coalesce = new CoalesceProbe();
        coalesce.connectInput("input_primary", NodeDataType.POINT);
        coalesce.connectInput("input_secondary", NodeDataType.POINT);
        coalesce.setInput("input_primary", null);
        PointData secondary = new PointData(5, 0, 0);
        coalesce.setInput("input_secondary", secondary);
        coalesce.processNode(null);
        assertValid(coalesce);
        assertEquals(secondary, coalesce.getOutput("output_signal"));
        assertEquals("secondary", coalesce.getOutput("output_source"));
    }

    @Test
    void coalesceAllNullBranchesRemainValidWithEmptyError() {
        CoalesceProbe coalesce = new CoalesceProbe();
        coalesce.connectInput("input_primary", NodeDataType.POINT);
        coalesce.connectInput("input_secondary", NodeDataType.POINT);
        coalesce.setInput("input_primary", null);
        coalesce.setInput("input_secondary", null);
        coalesce.processNode(null);
        assertValid(coalesce);
        assertNull(coalesce.getOutput("output_signal"));
        assertEquals("none", coalesce.getOutput("output_source"));
        assertEquals("", coalesce.getOutput("output_error"));
    }

    @Test
    void coalescePreferPrimaryConnectedInvalidSetsError() {
        CoalesceProbe coalesce = new CoalesceProbe();
        coalesce.connectInput("input_prefer_primary", NodeDataType.BOOLEAN);
        coalesce.setInput("input_prefer_primary", null);
        coalesce.processNode(null);
        assertInvalid(coalesce);
        assertTrue(String.valueOf(coalesce.getOutput("output_error")).contains("Prefer Primary"));
    }

    @Test
    void validateFalseConditionUsesMessageNotError() {
        ValidateNode validate = new ValidateNode();
        validate.setNodeState(Map.of("defaultCondition", false));
        validate.setInput("input_value", new PointData(1, 0, 0));
        validate.processNode(null);
        assertEquals(Boolean.FALSE, validate.getOutput("output_valid"));
        assertEquals("Validation failed", validate.getOutput("output_message"));
        assertEquals("", validate.getOutput("output_error"));
    }

    @Test
    void validateInvalidConditionSetsError() {
        ValidateProbe validate = new ValidateProbe();
        validate.connectInput("input_condition", NodeDataType.BOOLEAN);
        validate.setInput("input_condition", null);
        validate.processNode(null);
        assertInvalid(validate);
        assertTrue(String.valueOf(validate.getOutput("output_error")).contains("Condition"));
    }

    @Test
    void validateExplicitBlankMessageIsLegal() {
        ValidateProbe validate = new ValidateProbe();
        validate.connectInput("input_message", NodeDataType.STRING);
        validate.connectInput("input_condition", NodeDataType.BOOLEAN);
        validate.setInput("input_condition", false);
        validate.setInput("input_message", "");
        validate.processNode(null);
        assertEquals(Boolean.FALSE, validate.getOutput("output_valid"));
        assertEquals("", validate.getOutput("output_message"));
        assertEquals("", validate.getOutput("output_error"));
    }

    @Test
    void validateConnectedInvalidMessageSetsError() {
        ValidateProbe validate = new ValidateProbe();
        validate.connectInput("input_message", NodeDataType.STRING);
        validate.setInput("input_message", null);
        validate.processNode(null);
        assertInvalid(validate);
        assertTrue(String.valueOf(validate.getOutput("output_error")).contains("Message"));
    }

    @Test
    void stringFormatConnectedBlankTemplateIsValid() {
        StringFormatProbe format = new StringFormatProbe();
        format.connectInput("input_template", NodeDataType.STRING);
        format.setInput("input_template", "");
        format.connectInput("input_value_0", NodeDataType.ANY);
        format.setInput("input_value_0", "x");
        format.processNode(null);
        assertValid(format);
        assertEquals("", format.getOutput("output_text"));
    }

    @Test
    void stringFormatConnectedEmptyValuesListIsValid() {
        StringFormatProbe format = new StringFormatProbe();
        format.connectInput("input_template", NodeDataType.STRING);
        format.setInput("input_template", "Hello");
        format.connectInput("input_values", NodeDataType.LIST);
        format.setInput("input_values", List.of());
        format.processNode(null);
        assertValid(format);
        assertEquals("Hello", format.getOutput("output_text"));
    }

    @Test
    void stringFormatValueZeroUsesConnectionState() {
        StringFormatProbe format = new StringFormatProbe();
        format.setInput("input_value_0", "ignored-unconnected");
        format.processNode(null);
        assertEquals(Boolean.FALSE, format.getOutput("output_valid"));
    }

    @Test
    void stringFormatConnectedNullValueZeroFormatsNullLiteral() {
        StringFormatProbe format = new StringFormatProbe();
        format.connectInput("input_value_0", NodeDataType.ANY);
        format.setInput("input_value_0", null);
        format.processNode(null);
        assertValid(format);
        assertEquals("null", format.getOutput("output_text"));
    }

    @Test
    void stringFormatCyclicListFailsClosed() {
        StringFormatProbe format = new StringFormatProbe();
        List<Object> cyclic = new ArrayList<>();
        cyclic.add(cyclic);
        format.connectInput("input_values", NodeDataType.LIST);
        format.setInput("input_values", cyclic);
        format.processNode(null);
        assertInvalid(format);
        assertTrue(String.valueOf(format.getOutput("output_error")).contains("Cyclic"));
    }

    @Test
    void stringFormatDepthCapFailsClosed() {
        StringFormatProbe format = new StringFormatProbe();
        Object nested = "leaf";
        for (int i = 0; i < GenerationLimits.MAX_FORMAT_DEPTH + 2; i++) {
            nested = List.of(nested);
        }
        format.connectInput("input_value_0", NodeDataType.ANY);
        format.setInput("input_value_0", nested);
        format.processNode(null);
        assertInvalid(format);
        assertTrue(String.valueOf(format.getOutput("output_error")).contains("depth"));
    }

    @Test
    void stringFormatValuesSizeCapFailsClosed() {
        StringFormatProbe format = new StringFormatProbe();
        format.connectInput("input_values", NodeDataType.LIST);
        format.setInput("input_values", Collections.nCopies(GenerationLimits.MAX_LIST_ELEMENTS + 1, "x"));
        format.processNode(null);
        assertInvalid(format);
        assertTrue(String.valueOf(format.getOutput("output_error")).contains("MAX_LIST_ELEMENTS"));
    }

    @Test
    void stringFormatTemplateCapFailsClosed() {
        StringFormatProbe format = new StringFormatProbe();
        format.connectInput("input_template", NodeDataType.STRING);
        format.setInput("input_template", "x".repeat(GenerationLimits.MAX_FORMAT_TEMPLATE_CHARS + 1));
        format.processNode(null);
        assertInvalid(format);
        assertTrue(String.valueOf(format.getOutput("output_error")).contains("MAX_FORMAT_TEMPLATE_CHARS"));
    }

    @Test
    void stringFormatNestedContainerSizeCapFailsClosed() {
        StringFormatProbe format = new StringFormatProbe();
        format.connectInput("input_value_0", NodeDataType.ANY);
        format.setInput("input_value_0",
            Collections.nCopies(GenerationLimits.MAX_FORMAT_CONTAINER_ELEMENTS + 1, "x"));
        format.processNode(null);
        assertInvalid(format);
        assertEquals("", format.getOutput("output_text"));
        assertTrue(String.valueOf(format.getOutput("output_error")).contains("MAX_FORMAT_CONTAINER_ELEMENTS"));
    }

    @Test
    void stringFormatNestedListStreamingOutputCapFailsWithoutPartialOutput() {
        StringFormatProbe format = new StringFormatProbe();
        format.setNodeState(Map.of("template", "{0}"));
        format.connectInput("input_value_0", NodeDataType.ANY);
        format.setInput("input_value_0",
            Collections.nCopies(8_000, "01234567890123456789"));
        format.processNode(null);
        assertInvalid(format);
        assertEquals("", format.getOutput("output_text"));
        assertTrue(String.valueOf(format.getOutput("output_error")).contains("MAX_FORMAT_OUTPUT_CHARS"));
    }

    @Test
    void stringFormatGraphFailureProducesEmptyText() {
        StringFormatProbe format = new StringFormatProbe();
        format.connectInput("input_template", NodeDataType.STRING);
        format.setInput("input_template", 123);
        format.processNode(null);
        assertInvalid(format);
        assertEquals("", format.getOutput("output_text"));
        assertEquals("", format.getOutput("output_message"));
    }

    @Test
    void signalForkRemoveBranchOnlyRemovesLastPort() {
        SignalForkNode fork = new SignalForkNode();
        fork.addOutputBranch();
        BasePort outA = (BasePort) fork.getOutputPorts().stream()
            .filter(port -> "output_a".equals(port.getId()))
            .findFirst()
            .orElseThrow();
        BasePort outC = (BasePort) fork.getOutputPorts().stream()
            .filter(port -> "output_3".equals(port.getId()))
            .findFirst()
            .orElseThrow();

        fork.removeLastOutputBranch();
        assertEquals(2, fork.getOutputBranchCount());
        assertSame(outA, fork.getOutputPorts().stream()
            .filter(port -> "output_a".equals(port.getId()))
            .findFirst()
            .orElseThrow());
        assertTrue(fork.getOutputPorts().stream().noneMatch(port -> "output_3".equals(port.getId())));
        assertTrue(outC.getConnectedPorts().isEmpty() || !fork.getOutputPorts().contains(outC));
    }

    @Test
    void stringFormatMissingPlaceholderUsesMessageNotError() {
        StringFormatProbe format = new StringFormatProbe();
        format.setNodeState(Map.of("template", "{0} {3}"));
        format.connectInput("input_value_0", NodeDataType.ANY);
        format.setInput("input_value_0", "A");
        format.processNode(null);
        assertEquals(Boolean.FALSE, format.getOutput("output_valid"));
        assertTrue(String.valueOf(format.getOutput("output_message")).contains("Missing"));
        assertEquals("", format.getOutput("output_error"));
        assertEquals("A {3}", format.getOutput("output_text"));
    }

    @Test
    void stringFormatFormatsVectorPointAndBlockPos() {
        StringFormatProbe format = new StringFormatProbe();
        format.connectInput("input_value_0", NodeDataType.ANY);
        format.setInput("input_value_0", new VectorData(1.0d, 2.0d, 3.0d));
        format.processNode(null);
        String vectorText = assertInstanceOf(String.class, format.getOutput("output_text"));
        assertTrue(vectorText.contains("1") && vectorText.contains("2") && vectorText.contains("3"));

        format.setInput("input_value_0", new PointData(4, 5, 6));
        format.processNode(null);
        String pointText = assertInstanceOf(String.class, format.getOutput("output_text"));
        assertTrue(pointText.contains("4") && pointText.contains("5") && pointText.contains("6"));

        format.setInput("input_value_0", new BlockPos(7, 8, 9));
        format.processNode(null);
        String blockText = assertInstanceOf(String.class, format.getOutput("output_text"));
        assertTrue(blockText.contains("7") && blockText.contains("8") && blockText.contains("9"));
    }

    @Test
    void stringFormatAnyListOnlyAppearAsInputs() {
        List<String> errors = new ArrayList<>();
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            for (IPort port : node.getOutputPorts()) {
                if (port.getDataType() == NodeDataType.LIST) {
                    errors.add(id + "." + port.getId() + " output is LIST");
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));

        StringFormatNode format = new StringFormatNode();
        boolean hasListInput = format.getInputPorts().stream()
            .anyMatch(port -> port.getDataType() == NodeDataType.LIST);
        assertTrue(hasListInput);
    }

    @Test
    void migrateV54ToV55LegacyAssistRemapPreserved() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V54;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();
        graph.nodes.add(savedNode("n1", "utilities.assist.reroute"));
        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals("utilities.assist.relay", migrated.nodes.getFirst().typeId);
    }

    @Test
    void migrateV88ToV89IsNoOp() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V88;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();
        graph.nodes.add(savedNode("n1", "utilities.assist.relay"));
        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("utilities.assist.relay", migrated.nodes.getFirst().typeId);
    }

    private static BaseNode node(String typeId) {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(typeId));
    }

    private static void assertValid(BaseNode node) {
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals("", node.getOutput("output_error"));
    }

    private static void assertInvalid(BaseNode node) {
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNotNull(node.getOutput("output_error"));
        assertFalse(String.valueOf(node.getOutput("output_error")).isBlank());
    }

    private static boolean hasPort(INode node, String portId) {
        return node.getInputPorts().stream().anyMatch(p -> portId.equals(p.getId()))
            || node.getOutputPorts().stream().anyMatch(p -> portId.equals(p.getId()));
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        return node;
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
    }

    private static final class ValidateProbe extends ValidateNode {
        void connectInput(String portId, NodeDataType outputType) {
            AssistUtilitiesLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class CoalesceProbe extends CoalesceNode {
        void connectInput(String portId, NodeDataType outputType) {
            AssistUtilitiesLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class StringFormatProbe extends StringFormatNode {
        void connectInput(String portId, NodeDataType outputType) {
            AssistUtilitiesLanguageV2ContractTest.connectInput(this, portId, outputType);
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

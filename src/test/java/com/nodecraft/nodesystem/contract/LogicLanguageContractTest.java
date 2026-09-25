package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.nodes.math.logic.AndNode;
import com.nodecraft.nodesystem.nodes.math.logic.IfNode;
import com.nodecraft.nodesystem.nodes.math.logic.NotNode;
import com.nodecraft.nodesystem.nodes.math.logic.OrNode;
import com.nodecraft.nodesystem.nodes.math.logic.SelectItemNode;
import com.nodecraft.nodesystem.nodes.math.logic.XorNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Logic v1 language fence: port types are sole semantics; no truthiness/coercion.
 */
class LogicLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void exactlySixLogicNodesRegistered() {
        List<String> logicIds = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("math.logic."))
                .sorted()
                .collect(Collectors.toList());
        assertEquals(6, logicIds.size(), "Expected 6 logic nodes: " + logicIds);
    }

    @Test
    void booleanNodesExposeNoAnyPorts() {
        assertFalse(hasAnyPort(new AndNode()));
        assertFalse(hasAnyPort(new OrNode()));
        assertFalse(hasAnyPort(new NotNode()));
        assertFalse(hasAnyPort(new XorNode()));
    }

    @Test
    void ifAndSwitchKeepAnyOnValuePortsOnly() {
        IfNode ifNode = new IfNode();
        assertEquals(NodeDataType.BOOLEAN, findPort(ifNode, "input_condition").getDataType());
        assertEquals(NodeDataType.ANY, findPort(ifNode, "input_true_value").getDataType());
        assertEquals(NodeDataType.ANY, findPort(ifNode, "input_false_value").getDataType());
        assertEquals(NodeDataType.ANY, findPort(ifNode, "output_result").getDataType());

        SelectItemNode switchNode = new SelectItemNode();
        assertEquals(NodeDataType.INTEGER, findPort(switchNode, "input_index").getDataType());
        assertEquals(NodeDataType.ANY, findPort(switchNode, "input_item_0").getDataType());
        assertEquals(NodeDataType.ANY, findPort(switchNode, "input_default").getDataType());
        assertEquals(NodeDataType.ANY, findPort(switchNode, "output_result").getDataType());
    }

    @Test
    void booleanTruthTables() {
        AndNode and = new AndNode();
        assertTrue((Boolean) and.compute(Map.of("input_a", true, "input_b", true)).get("output_result"));
        assertFalse((Boolean) and.compute(Map.of("input_a", true, "input_b", false)).get("output_result"));

        OrNode or = new OrNode();
        assertTrue((Boolean) or.compute(Map.of("input_a", false, "input_b", true)).get("output_result"));
        assertFalse((Boolean) or.compute(Map.of("input_a", false, "input_b", false)).get("output_result"));

        NotNode not = new NotNode();
        assertFalse((Boolean) not.compute(Map.of("input_value", true)).get("output_result"));
        assertTrue((Boolean) not.compute(Map.of("input_value", false)).get("output_result"));

        XorNode xor = new XorNode();
        assertTrue((Boolean) xor.compute(Map.of("input_a", true, "input_b", false)).get("output_result"));
        assertFalse((Boolean) xor.compute(Map.of("input_a", true, "input_b", true)).get("output_result"));
    }

    @Test
    void ifRejectsNonBooleanCondition() {
        IfNode node = new IfNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_condition", 1,
                "input_true_value", "true-branch",
                "input_false_value", "false-branch"
        ));
        assertEquals("false-branch", outputs.get("output_result"));
    }

    @Test
    void switchRejectsNonIntegerIndex() {
        SelectItemNode node = new SelectItemNode();
        Map<String, Object> base = Map.of(
                "input_item_0", "item0",
                "input_item_1", "item1",
                "input_default", "default"
        );

        assertEquals("default", node.compute(withIndex(base, 1.9d)).get("output_result"));
        assertEquals("default", node.compute(withIndex(base, "1")).get("output_result"));
        assertEquals("default", node.compute(withIndex(base, true)).get("output_result"));
        assertEquals("item1", node.compute(withIndex(base, 1)).get("output_result"));
        assertEquals("default", node.compute(withIndex(base, 5)).get("output_result"));
    }

    @Test
    void andRejectsNonBooleanInputs() {
        AndNode node = new AndNode();
        Map<String, Object> outputs = node.compute(Map.of("input_a", 1, "input_b", true));
        assertFalse((Boolean) outputs.get("output_result"));
    }

    private static Map<String, Object> withIndex(Map<String, Object> base, Object index) {
        return Map.of(
                "input_index", index,
                "input_item_0", base.get("input_item_0"),
                "input_item_1", base.get("input_item_1"),
                "input_default", base.get("input_default")
        );
    }

    private static boolean hasAnyPort(INode node) {
        for (IPort port : node.getInputPorts()) {
            if (port.getDataType() == NodeDataType.ANY) {
                return true;
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (port.getDataType() == NodeDataType.ANY) {
                return true;
            }
        }
        return false;
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
        throw new AssertionError("missing port " + portId + " on " + node.getTypeId());
    }
}

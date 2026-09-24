package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.nodes.math.random.RandomNumberNode;
import com.nodecraft.nodesystem.nodes.math.random.RandomNumbersNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomLanguageContractTest {

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void randomNumberOutputsDoubleOnly() {
        RandomNumberNode node = new RandomNumberNode();
        IPort output = node.getOutputPorts().stream()
            .filter(p -> "output_random".equals(p.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals(NodeDataType.DOUBLE, output.getDataType());

        var outputs = node.compute(java.util.Map.of(
            "input_domain", new NumericRangeData(0.0d, 1.0d)
        ));
        assertInstanceOf(Double.class, outputs.get("output_random"));
    }

    @Test
    void randomNumbersOutputsListOnly() {
        RandomNumbersNode node = new RandomNumbersNode();
        IPort output = node.getOutputPorts().stream()
            .filter(p -> "output_values".equals(p.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals(NodeDataType.LIST, output.getDataType());

        var outputs = node.compute(java.util.Map.of(
            "input_domain", new NumericRangeData(0.0d, 1.0d),
            "input_count", 5
        ));
        assertInstanceOf(java.util.List.class, outputs.get("output_values"));
        assertEquals(5, ((java.util.List<?>) outputs.get("output_values")).size());
    }

    @Test
    void randomNumberMustNotExposeAnyOrCount() {
        BaseNode node = (BaseNode) NodeRegistry.getInstance().createNodeInstance("math.random.random_number");
        for (IPort port : node.getInputPorts()) {
            assertFalse("input_count".equals(port.getId()));
            assertFalse(port.getDataType() == NodeDataType.ANY, port.getId());
        }
        for (IPort port : node.getOutputPorts()) {
            assertFalse(port.getDataType() == NodeDataType.ANY, port.getId());
        }
    }
}

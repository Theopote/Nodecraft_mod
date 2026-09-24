package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.nodes.input.numeric.RangeInputNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.RemapNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericDomainLanguageContractTest {

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void domainPreservesDirection() {
        NumericRangeData forward = new NumericRangeData(0.0d, 10.0d);
        NumericRangeData reverse = new NumericRangeData(10.0d, 0.0d);

        assertEquals(10.0d, forward.span(), 1.0e-12);
        assertEquals(-10.0d, reverse.span(), 1.0e-12);
        assertEquals(0.0d, forward.lower(), 1.0e-12);
        assertEquals(10.0d, forward.upper(), 1.0e-12);
        assertEquals(0.0d, reverse.lower(), 1.0e-12);
        assertEquals(10.0d, reverse.upper(), 1.0e-12);
    }

    @Test
    void domainInputDoesNotForceSort() {
        RangeInputNode node = new RangeInputNode();
        node.setStart(10.0d);
        node.setEnd(2.0d);
        node.processNode(null);

        NumericRangeData domain = (NumericRangeData) node.getOutput("output_domain");
        assertEquals(10.0d, domain.start(), 1.0e-12);
        assertEquals(2.0d, domain.end(), 1.0e-12);
        assertEquals(-8.0d, domain.span(), 1.0e-12);
    }

    @Test
    void remapSupportsReversedTarget() {
        RemapNode node = new RemapNode();
        NumericRangeData source = new NumericRangeData(0.0d, 1.0d);
        NumericRangeData target = new NumericRangeData(100.0d, 0.0d);

        var outputs = node.compute(java.util.Map.of(
            "input_value", 0.5d,
            "input_source", source,
            "input_target", target
        ));

        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(50.0d, (Double) outputs.get("output_result"), 1.0e-12);
    }

    @Test
    void domainInputExposesNumericRangeDomainPort() {
        BaseNode node = (BaseNode) NodeRegistry.getInstance().createNodeInstance("input.numeric.range");
        IPort domain = findPort(node.getOutputPorts(), "output_domain");
        assertEquals(NodeDataType.NUMERIC_RANGE, domain.getDataType());
        assertEquals("Domain", domain.getDisplayName());
    }

    @Test
    void remapUsesSourceAndTargetDomainPorts() {
        BaseNode node = (BaseNode) NodeRegistry.getInstance().createNodeInstance("math.scalar_math.remap");
        assertEquals(NodeDataType.NUMERIC_RANGE, findPort(node.getInputPorts(), "input_source").getDataType());
        assertEquals(NodeDataType.NUMERIC_RANGE, findPort(node.getInputPorts(), "input_target").getDataType());
        assertFalse(hasPort(node.getInputPorts(), "input_in_min"));
    }

    private static IPort findPort(Iterable<IPort> ports, String id) {
        for (IPort port : ports) {
            if (id.equals(port.getId())) {
                return port;
            }
        }
        throw new AssertionError("missing port " + id);
    }

    private static boolean hasPort(Iterable<IPort> ports, String id) {
        for (IPort port : ports) {
            if (id.equals(port.getId())) {
                return true;
            }
        }
        return false;
    }
}

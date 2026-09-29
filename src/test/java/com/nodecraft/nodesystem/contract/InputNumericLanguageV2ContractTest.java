package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.input.numeric.RangeInputNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Input Numeric Domain Finite Contract v2 (Graph V111).
 */
class InputNumericLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV111() {
        assertEquals(111, GraphFormatVersion.V111);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V111);
    }

    @Test
    void domainInputExposesValidAndError() {
        RangeInputNode node = new RangeInputNode();
        assertEquals(NodeDataType.BOOLEAN, findPort(node.getOutputPorts(), "output_valid").getDataType());
        assertEquals(NodeDataType.STRING, findPort(node.getOutputPorts(), "output_error").getDataType());
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals("", node.getOutput("output_error"));
    }

    @Test
    void domainOverflowDirectedSpanFailsClosed() {
        RangeInputNode node = new RangeInputNode();
        node.setStart(Double.MAX_VALUE);
        node.setEnd(-Double.MAX_VALUE);
        node.processNode(null);

        assertFalse((Boolean) node.getOutput("output_valid"));
        assertNull(node.getOutput("output_domain"));
        assertTrue(Double.isNaN((Double) node.getOutput("output_start")));
        assertTrue(Double.isNaN((Double) node.getOutput("output_end")));
        assertTrue(Double.isNaN((Double) node.getOutput("output_span")));
        assertFalse(((String) node.getOutput("output_error")).isBlank());
    }

    @Test
    void domainReversedRemainsValidWithNegativeSpan() {
        RangeInputNode node = new RangeInputNode();
        node.setStart(10.0d);
        node.setEnd(0.0d);
        node.processNode(null);

        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals("", node.getOutput("output_error"));
        assertEquals(-10.0d, (Double) node.getOutput("output_span"), 1.0e-12d);
        NumericRangeData domain = assertInstanceOf(NumericRangeData.class, node.getOutput("output_domain"));
        assertEquals(10.0d, domain.start(), 1.0e-12d);
        assertEquals(0.0d, domain.end(), 1.0e-12d);
    }

    @Test
    void canonicalRejectsNonFiniteEndpointsAndOverflowSpan() {
        assertNull(NumericRangeData.canonical(Double.NaN, 1.0d));
        assertNull(NumericRangeData.canonical(0.0d, Double.POSITIVE_INFINITY));
        assertNull(NumericRangeData.canonical(Double.MAX_VALUE, -Double.MAX_VALUE));
        assertNotNull(NumericRangeData.canonical(10.0d, 0.0d));
    }

    private static IPort findPort(Iterable<IPort> ports, String id) {
        for (IPort port : ports) {
            if (id.equals(port.getId())) {
                return port;
            }
        }
        throw new AssertionError("missing port " + id);
    }
}

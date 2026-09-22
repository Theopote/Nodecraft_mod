package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.nodes.reference.points.BlockToVectorNode;
import com.nodecraft.nodesystem.nodes.reference.points.ClosestPointNode;
import com.nodecraft.nodesystem.nodes.reference.points.DeconstructCoordinateNode;
import com.nodecraft.nodesystem.nodes.reference.points.DeconstructPointNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spatial P1 freeze: Closest Point continuous POINT output;
 * Deconstruct Block Position vs Deconstruct Point;
 * Block To Vector remains convenience-only.
 */
class SpatialReferenceLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void closestPointIsContinuousGeometryOnly() {
        ClosestPointNode node = new ClosestPointNode();
        assertEquals(NodeDataType.POINT, findPort(node, "input_point").getDataType());
        assertEquals(NodeDataType.POINT_LIST, findPort(node, "input_coordinates").getDataType());
        assertEquals(NodeDataType.POINT, findPort(node, "output_closest_point").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(node, "output_distance").getDataType());
        assertEquals(NodeDataType.INTEGER, findPort(node, "output_index").getDataType());
        assertFalse(hasPort(node, "output_vector"));
        assertFalse(hasPort(node, "output_point_data"));
        assertFalse(hasBlockPosOutput(node));
    }

    @Test
    void deconstructBlockPositionAndPointAreSplit() {
        DeconstructCoordinateNode block = new DeconstructCoordinateNode();
        assertEquals("reference.points.deconstruct_block_position", block.getTypeId());
        assertEquals(NodeDataType.BLOCK_POS, findPort(block, "input_coordinate").getDataType());
        assertEquals(NodeDataType.INTEGER, findPort(block, "output_x").getDataType());

        DeconstructPointNode point = new DeconstructPointNode();
        assertEquals("reference.points.deconstruct_point", point.getTypeId());
        assertEquals(NodeDataType.POINT, findPort(point, "input_point").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(point, "output_x").getDataType());

        assertNotNull(registry.createNodeInstance("reference.points.deconstruct_block_position"));
        assertNotNull(registry.createNodeInstance("reference.points.deconstruct_point"));
    }

    @Test
    void blockToVectorIsConvenience_canonicalPathIsBlockToPoint() {
        BlockToVectorNode convenience = new BlockToVectorNode();
        assertEquals(NodeDataType.BLOCK_POS, findPort(convenience, "input_coordinate").getDataType());
        assertEquals(NodeDataType.VECTOR, findPort(convenience, "output_vector").getDataType());
        assertTrue(convenience.getDescription().toLowerCase(Locale.ROOT).contains("convenience")
                || convenience.getDescription().toLowerCase(Locale.ROOT).contains("prefer block to point"));

        assertEquals(
                TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
                TypeConversionRegistry.classify(NodeDataType.BLOCK_POS, NodeDataType.POINT)
        );
        TypeConversionRegistry.ConversionSuggestion toPoint =
                TypeConversionRegistry.getSuggestedConversion(NodeDataType.BLOCK_POS, NodeDataType.POINT);
        assertNotNull(toPoint);
        assertEquals("reference.points.point_from_block", toPoint.nodeId());

        TypeConversionRegistry.ConversionSuggestion toVector =
                TypeConversionRegistry.getSuggestedConversion(NodeDataType.BLOCK_POS, NodeDataType.VECTOR);
        assertNotNull(toVector);
        assertEquals("reference.points.block_to_vector", toVector.nodeId());
    }

    private static boolean hasBlockPosOutput(INode node) {
        for (IPort port : node.getOutputPorts()) {
            if (port != null && port.getDataType() == NodeDataType.BLOCK_POS) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPort(INode node, String portId) {
        return findPortOrNull(node, portId) != null;
    }

    private static IPort findPort(INode node, String portId) {
        IPort port = findPortOrNull(node, portId);
        assertNotNull(port, "missing port " + portId + " on " + node.getTypeId());
        return port;
    }

    private static IPort findPortOrNull(INode node, String portId) {
        Collection<? extends IPort> ports = node.getInputPorts();
        if (ports != null) {
            for (IPort port : ports) {
                if (port != null && portId.equalsIgnoreCase(port.getId())) {
                    return port;
                }
            }
        }
        ports = node.getOutputPorts();
        if (ports != null) {
            for (IPort port : ports) {
                if (port != null && portId.equalsIgnoreCase(port.getId())) {
                    return port;
                }
            }
        }
        return null;
    }
}

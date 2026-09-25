package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.reference.vectors.ConstructVectorNode;
import com.nodecraft.nodesystem.nodes.reference.vectors.DeconstructVectorNode;
import com.nodecraft.nodesystem.nodes.reference.vectors.VectorInputNode;
import com.nodecraft.nodesystem.nodes.reference.vectors.VectorScalarDivideNode;
import com.nodecraft.nodesystem.nodes.reference.vectors.VectorScalarMultiplyNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freezes Vector producer/deconstruct language: producers emit VECTOR only.
 */
class VectorLanguageContractTest {

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void constructVectorEmitsVectorAndValidOnly() {
        ConstructVectorNode node = new ConstructVectorNode();
        assertEquals(NodeDataType.VECTOR, findPort(node, "output_vector").getDataType());
        assertEquals(NodeDataType.BOOLEAN, findPort(node, "output_valid").getDataType());
        assertFalse(hasPort(node, "output_x"));
        assertFalse(hasPort(node, "output_y"));
        assertFalse(hasPort(node, "output_z"));
    }

    @Test
    void vectorInputEmitsVectorOnly() {
        VectorInputNode node = new VectorInputNode();
        assertEquals(NodeDataType.VECTOR, findPort(node, "output_vector").getDataType());
        assertFalse(hasPort(node, "output_x"));
        assertFalse(hasPort(node, "output_y"));
        assertFalse(hasPort(node, "output_z"));
    }

    @Test
    void deconstructVectorProvidesComponents() {
        DeconstructVectorNode node = new DeconstructVectorNode();
        assertEquals(NodeDataType.VECTOR, findPort(node, "input_vector").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(node, "output_x").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(node, "output_y").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(node, "output_z").getDataType());
    }

    @Test
    void scalarNodesUseCanonicalDisplayNames() {
        assertEquals("Vector Scalar Multiply", new VectorScalarMultiplyNode().getDisplayName());
        assertEquals("Vector Scalar Divide", new VectorScalarDivideNode().getDisplayName());
    }

    @Test
    void v20ToV21MigrationDropsVectorProducerComponentWires() {
        SavedGraph v20 = new SavedGraph();
        v20.formatVersion = GraphFormatVersion.V20;

        SavedNode construct = new SavedNode();
        construct.nodeId = "cv";
        construct.typeId = "reference.vectors.construct_vector";
        SavedNode sink = new SavedNode();
        sink.nodeId = "sink";
        sink.typeId = "math.scalar_math.add";
        v20.nodes = new ArrayList<>(List.of(construct, sink));

        SavedConnection legacyX = new SavedConnection();
        legacyX.sourceNodeId = "cv";
        legacyX.sourcePortId = "output_x";
        legacyX.targetNodeId = "sink";
        legacyX.targetPortId = "input_a";

        SavedConnection kept = new SavedConnection();
        kept.sourceNodeId = "cv";
        kept.sourcePortId = "output_vector";
        kept.targetNodeId = "sink";
        kept.targetPortId = "input_b";

        v20.connections = new ArrayList<>(List.of(legacyX, kept));
        v20.nodePositions = java.util.Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v20);
        assertEquals(GraphFormatVersion.V23, migrated.formatVersion);
        assertEquals(1, migrated.connections.size());
        assertEquals("output_vector", migrated.connections.getFirst().sourcePortId);
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
        for (IPort port : node.getInputPorts()) {
            if (port != null && portId.equalsIgnoreCase(port.getId())) {
                return port;
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (port != null && portId.equalsIgnoreCase(port.getId())) {
                return port;
            }
        }
        return null;
    }
}

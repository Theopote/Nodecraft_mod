package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.nodes.math.fields.PointAttractorFieldNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldSamplePointNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldSamplePointsNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldSamplePointsNode;
import com.nodecraft.nodesystem.nodes.math.fields.VolumeAttractorFieldNode;
import com.nodecraft.nodesystem.nodes.math.fields.VortexFieldNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 12 freeze: Field System spatial roles + typed field ports.
 */
class FieldsFamilyContractTest {

    private static final Set<String> LOCATION_PORT_IDS = Set.of(
        "input_center",
        "input_origin",
        "input_point"
    );

    private static final Set<String> DIRECTION_PORT_IDS = Set.of(
        "input_axis"
    );

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void attractorCentersAndVortexOriginArePoints() {
        assertPortType(new PointAttractorFieldNode(), "input_center", NodeDataType.POINT);
        assertPortType(new VolumeAttractorFieldNode(), "input_center", NodeDataType.POINT);
        assertPortType(new VortexFieldNode(), "input_origin", NodeDataType.POINT);
        assertPortType(new VortexFieldNode(), "input_axis", NodeDataType.VECTOR);
    }

    @Test
    void samplePointUsesPoint_samplePointsUsePointList() {
        assertPortType(new ScalarFieldSamplePointNode(), "input_point", NodeDataType.POINT);
        assertPortType(new ScalarFieldSamplePointsNode(), "input_points", NodeDataType.POINT_LIST);
        assertPortType(new VectorFieldSamplePointsNode(), "input_points", NodeDataType.POINT_LIST);
        assertPortType(new VectorFieldSamplePointsNode(), "output_vectors", NodeDataType.VECTOR_LIST);
    }

    @Test
    void allFieldNodesForbidAny() {
        List<String> violations = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            if (!nodeId.toLowerCase(Locale.ROOT).startsWith("math.fields.")) {
                continue;
            }
            INode instance = tryCreate(nodeId);
            if (instance == null) {
                continue;
            }
            for (IPort port : allPorts(instance)) {
                if (port.getDataType() == NodeDataType.ANY) {
                    violations.add(nodeId + "#" + port.getId());
                }
            }
        }
        assertTrue(violations.isEmpty(), "Field nodes must not expose ANY: " + violations);
    }

    @Test
    void fieldLocationPortsAreNotTypedAsVector() {
        List<String> violations = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            if (!nodeId.toLowerCase(Locale.ROOT).startsWith("math.fields.")) {
                continue;
            }
            INode instance = tryCreate(nodeId);
            if (instance == null) {
                continue;
            }
            for (IPort port : instance.getInputPorts()) {
                String id = port.getId().toLowerCase(Locale.ROOT);
                if (LOCATION_PORT_IDS.contains(id)
                        && (port.getDataType() == NodeDataType.VECTOR
                        || port.getDataType() == NodeDataType.VECTOR_LIST)) {
                    violations.add(nodeId + "#" + port.getId() + "=" + port.getDataType());
                }
                if (DIRECTION_PORT_IDS.contains(id) && port.getDataType() != NodeDataType.VECTOR) {
                    violations.add(nodeId + "#" + port.getId() + " expected VECTOR, got " + port.getDataType());
                }
            }
        }
        assertTrue(violations.isEmpty(), "Field spatial role violations: " + violations);
    }

    @Test
    void sdfToFieldRequiresExplicitConversionNodes() {
        assertEquals(
            TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.SDF, NodeDataType.SCALAR_FIELD)
        );
        assertEquals(
            TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.SDF, NodeDataType.VECTOR_FIELD)
        );
        assertFalse(TypeConversionRegistry.isImplicitlyConnectable(NodeDataType.SDF, NodeDataType.SCALAR_FIELD));

        TypeConversionRegistry.ConversionSuggestion scalar =
            TypeConversionRegistry.getSuggestedConversion(NodeDataType.SDF, NodeDataType.SCALAR_FIELD);
        assertNotNull(scalar);
        assertEquals("math.fields.scalar_from_sdf", scalar.nodeId());

        TypeConversionRegistry.ConversionSuggestion vector =
            TypeConversionRegistry.getSuggestedConversion(NodeDataType.SDF, NodeDataType.VECTOR_FIELD);
        assertNotNull(vector);
        assertEquals("math.fields.vector_from_sdf_gradient", vector.nodeId());
    }

    private static void assertPortType(INode node, String portId, NodeDataType expected) {
        assertEquals(expected, findPort(node, portId).getDataType(), node.getTypeId() + "#" + portId);
    }

    private static List<IPort> allPorts(INode node) {
        List<IPort> ports = new ArrayList<>();
        ports.addAll(node.getInputPorts());
        ports.addAll(node.getOutputPorts());
        return ports;
    }

    private static IPort findPort(INode node, String portId) {
        for (IPort port : allPorts(node)) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        throw new AssertionError("missing port " + portId + " on " + node.getTypeId());
    }

    private static INode tryCreate(String nodeId) {
        try {
            return registry.createNodeInstance(nodeId);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }
}

package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.math.random.NoiseNode;
import com.nodecraft.nodesystem.nodes.math.random.RandomNumberNode;
import com.nodecraft.nodesystem.nodes.math.random.RandomNumbersNode;
import com.nodecraft.nodesystem.nodes.math.random.RandomVectorNode;
import com.nodecraft.nodesystem.nodes.math.random.RandomVectorsNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Random Strict Domain & Transactional Sampling Contract v2 (Graph V123).
 */
class RandomLanguageV2ContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsAtLeastV123() {
        assertEquals(123, GraphFormatVersion.V123);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V123);
    }

    @Test
    void allRandomNodesExposeValidAndError() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("math.random."))
                .sorted()
                .collect(Collectors.toList());
        assertEquals(6, ids.size());
        for (String nodeId : ids) {
            INode node = registry.createNodeInstance(nodeId);
            assertNotNull(findPort(node, "output_valid"), nodeId);
            assertNotNull(findPort(node, "output_error"), nodeId);
        }
    }

    @Test
    void randomNumberInvalidDomainDoesNotEmitZero() {
        RandomNumberProbe probe = new RandomNumberProbe();
        probe.putInput("input_domain", 1.0d);
        probe.putInput("input_seed", 0);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(Double.isNaN((Double) probe.getOutput("output_random")));
    }

    @Test
    void randomNumbersInvalidDomainFailsBeforeLoop() {
        RandomNumbersProbe probe = new RandomNumbersProbe();
        probe.putInput("input_domain", "bad");
        probe.putInput("input_count", 100);
        probe.putInput("input_seed", 0);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(((List<?>) probe.getOutput("output_values")).isEmpty());
    }

    @Test
    void randomNumbersValidRunMatchesCount() {
        RandomNumbersNode node = new RandomNumbersNode();
        node.setInput("input_domain", new NumericRangeData(0.0d, 1.0d));
        node.setInput("input_count", 5);
        node.setInput("input_seed", 0);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals(5, ((List<?>) node.getOutput("output_values")).size());
    }

    @Test
    void connectedInvalidSeedFailsClosed() {
        RandomNumberProbe probe = new RandomNumberProbe();
        probe.connectInput("input_seed", NodeDataType.INTEGER);
        probe.putInput("input_domain", new NumericRangeData(0.0d, 1.0d));
        probe.putInput("input_seed", "1");
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertTrue(Double.isNaN((Double) probe.getOutput("output_random")));
    }

    @Test
    void connectedInvalidCountFailsClosed() {
        RandomNumbersProbe probe = new RandomNumbersProbe();
        probe.connectInput("input_count", NodeDataType.INTEGER);
        probe.putInput("input_domain", new NumericRangeData(0.0d, 1.0d));
        probe.putInput("input_count", 1.9d);
        probe.putInput("input_seed", 0);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
    }

    @Test
    void undrivenSeedStillEqualsZeroAndDeterministic() {
        RandomNumberNode node = new RandomNumberNode();
        node.setInput("input_domain", new NumericRangeData(0.0d, 1.0d));
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        Object a = node.getOutput("output_random");

        RandomNumberNode seeded = new RandomNumberNode();
        seeded.setInput("input_domain", new NumericRangeData(0.0d, 1.0d));
        seeded.setInput("input_seed", 0);
        seeded.processNode(null);
        assertTrue((Boolean) seeded.getOutput("output_valid"));
        assertEquals(a, seeded.getOutput("output_random"));
    }

    @Test
    void randomVectorRejectsPartiallyInvalidDomain() {
        RandomVectorNode node = new RandomVectorNode();
        node.setInput("input_min_corner", new Vector3d(0.0d, 0.0d, -Double.MAX_VALUE));
        node.setInput("input_max_corner", new Vector3d(1.0d, 1.0d, Double.MAX_VALUE));
        node.setInput("input_seed", 0);
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertNull(node.getOutput("output_vector"));
    }

    @Test
    void randomVectorsTransactionalFailure() {
        RandomVectorsNode node = new RandomVectorsNode();
        node.setInput("input_min_corner", new Vector3d(0.0d, 0.0d, -Double.MAX_VALUE));
        node.setInput("input_max_corner", new Vector3d(1.0d, 1.0d, Double.MAX_VALUE));
        node.setInput("input_count", 3);
        node.setInput("input_seed", 0);
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertTrue(((List<?>) node.getOutput("output_vectors")).isEmpty());
    }

    @Test
    void noiseNegativeCoordinatesAreValid() {
        NoiseNode node = new NoiseNode();
        node.setInput("input_x", -1.5d);
        node.setInput("input_y", -2.0d);
        node.setInput("input_z", -3.0d);
        node.setInput("input_seed", 0);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertTrue(Double.isFinite((Double) node.getOutput("output_noise")));
    }

    @Test
    void determinismRegression() {
        RandomNumbersNode node = new RandomNumbersNode();
        Map<String, Object> inputs = Map.of(
                "input_domain", new NumericRangeData(0.0d, 1.0d),
                "input_count", 4,
                "input_seed", 11
        );
        Object first = node.compute(inputs).get("output_values");
        Object second = node.compute(inputs).get("output_values");
        assertEquals(first, second);
        assertTrue((Boolean) node.getOutput("output_valid"));
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

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
                .filter(port -> inputPortId.equals(port.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
        target.getInput(inputPortId);
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(com.nodecraft.nodesystem.execution.ExecutionContext context) {
        }
    }

    private static final class RandomNumberProbe extends RandomNumberNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            RandomLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class RandomNumbersProbe extends RandomNumbersNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            RandomLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }
}

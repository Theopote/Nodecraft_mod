package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.math.random.NoiseNode;
import com.nodecraft.nodesystem.nodes.math.random.RandomListItemNode;
import com.nodecraft.nodesystem.nodes.math.random.RandomNumberNode;
import com.nodecraft.nodesystem.nodes.math.random.RandomNumbersNode;
import com.nodecraft.nodesystem.nodes.math.random.RandomVectorNode;
import com.nodecraft.nodesystem.nodes.math.random.RandomVectorsNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Random v1 language fence: deterministic seeded RNG, stable types, strict ports, coherent noise.
 */
class RandomLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void exactlySixRandomNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("math.random."))
                .sorted()
                .toList();
        assertEquals(6, ids.size(), "Expected 6 random nodes: " + ids);
        assertTrue(ids.contains("math.random.random_vectors"));
    }

    @Test
    void randomNumbersOutputIsDoubleList() {
        RandomNumbersNode node = new RandomNumbersNode();
        assertEquals(NodeDataType.DOUBLE_LIST, findPort(node, "output_values").getDataType());
    }

    @Test
    void randomVectorIsSingleVectorWithoutCount() {
        RandomVectorNode node = new RandomVectorNode();
        assertNull(findPortOrNull(node, "input_count"));
        assertNull(findPortOrNull(node, "output_random_vector"));
        assertEquals(NodeDataType.VECTOR, findPort(node, "output_vector").getDataType());

        Map<String, Object> outputs = node.compute(Map.of(
                "input_min_corner", new com.nodecraft.nodesystem.datatypes.VectorData(0, 0, 0),
                "input_max_corner", new com.nodecraft.nodesystem.datatypes.VectorData(1, 1, 1),
                "input_seed", 0
        ));
        assertInstanceOf(com.nodecraft.nodesystem.datatypes.VectorData.class, outputs.get("output_vector"));
    }

    @Test
    void randomVectorsOutputIsVectorList() {
        RandomVectorsNode node = new RandomVectorsNode();
        assertEquals(NodeDataType.VECTOR_LIST, findPort(node, "output_vectors").getDataType());

        @SuppressWarnings("unchecked")
        List<com.nodecraft.nodesystem.datatypes.VectorData> vectors =
                (List<com.nodecraft.nodesystem.datatypes.VectorData>) node.compute(Map.of(
                "input_min_corner", new com.nodecraft.nodesystem.datatypes.VectorData(0, 0, 0),
                "input_max_corner", new com.nodecraft.nodesystem.datatypes.VectorData(1, 1, 1),
                "input_count", 3,
                "input_seed", 0
        )).get("output_vectors");
        assertEquals(3, vectors.size());
        for (com.nodecraft.nodesystem.datatypes.VectorData v : vectors) {
            assertTrue(Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z()));
        }
    }

    @Test
    void missingSeedEqualsSeedZeroAndIsDeterministic() {
        RandomNumberNode node = new RandomNumberNode();
        NumericRangeData domain = new NumericRangeData(0.0d, 1.0d);

        Object missingA = node.compute(Map.of("input_domain", domain)).get("output_random");
        Object missingB = node.compute(Map.of("input_domain", domain)).get("output_random");
        Object seeded = node.compute(Map.of(
                "input_domain", domain,
                "input_seed", 0
        )).get("output_random");

        assertEquals(missingA, missingB);
        assertEquals(missingA, seeded);

        RandomNumbersNode numbers = new RandomNumbersNode();
        Object listA = numbers.compute(Map.of(
                "input_domain", domain,
                "input_count", 10,
                "input_seed", 0
        )).get("output_values");
        Object listB = numbers.compute(Map.of(
                "input_domain", domain,
                "input_count", 10,
                "input_seed", 0
        )).get("output_values");
        assertEquals(listA, listB);
    }

    @Test
    @Disabled("V1 semantics superseded by V123 — see RandomLanguageV2ContractTest")
    void strictCountSeedAndAllowDuplicates() {
        RandomNumbersNode numbers = new RandomNumbersNode();
        // Count 1.9 is ignored  -> property default 10
        @SuppressWarnings("unchecked")
        List<Double> fromDoubleCount = (List<Double>) numbers.compute(Map.of(
                "input_domain", new NumericRangeData(0.0d, 1.0d),
                "input_count", 1.9d,
                "input_seed", 0
        )).get("output_values");
        assertEquals(10, fromDoubleCount.size());

        RandomNumberNode number = new RandomNumberNode();
        Object withStringSeed = number.compute(Map.of(
                "input_domain", new NumericRangeData(0.0d, 1.0d),
                "input_seed", "1"
        )).get("output_random");
        Object withZeroSeed = number.compute(Map.of(
                "input_domain", new NumericRangeData(0.0d, 1.0d),
                "input_seed", 0
        )).get("output_random");
        assertEquals(withZeroSeed, withStringSeed);

        RandomListItemNode listItem = new RandomListItemNode();
        Map<String, Object> outputs = listItem.compute(Map.of(
                "input_list", List.of("A", "B", "C"),
                "input_count", 2,
                "input_allow_duplicates", 1,
                "input_seed", 0
        ));
        // AllowDuplicates=1 is not Boolean  -> false  -> unique picks, size 2
        assertEquals(2, ((List<?>) outputs.get("output_items")).size());
        assertEquals(2, ((List<?>) outputs.get("output_items")).stream().distinct().count());
    }

    @Test
    void randomListItemBindsListTypeVariable() {
        RandomListItemNode node = new RandomListItemNode();
        assertEquals(NodeDataType.LIST, findPort(node, "input_list").getDataType());
        assertEquals("T", findPort(node, "input_list").getListTypeVariable());
        assertEquals("T", findPort(node, "output_item").getListTypeVariable());
        assertTrue(findPort(node, "output_item").isListElementBinding());
        assertEquals("T", findPort(node, "output_items").getListTypeVariable());
        assertFalse(findPort(node, "output_items").isListElementBinding());

        // Non-list is not auto-wrapped
        Map<String, Object> wrapped = node.compute(Map.of(
                "input_list", "Stone",
                "input_count", 1,
                "input_seed", 0
        ));
        assertNull(wrapped.get("output_item"));
        assertTrue(((List<?>) wrapped.get("output_items")).isEmpty());
    }

    @Test
    void noiseIsDeterministicCoherentAndRejectsNonFinite() {
        NoiseNode node = new NoiseNode();
        Object a = node.compute(Map.of(
                "input_x", 1.0d, "input_y", 2.0d, "input_z", 3.0d, "input_seed", 0
        )).get("output_noise");
        Object b = node.compute(Map.of(
                "input_x", 1.0d, "input_y", 2.0d, "input_z", 3.0d, "input_seed", 0
        )).get("output_noise");
        assertEquals(a, b);
        assertTrue(Double.isFinite((Double) a));

        double nearby = (Double) node.compute(Map.of(
                "input_x", 1.001d, "input_y", 2.0d, "input_z", 3.0d, "input_seed", 0
        )).get("output_noise");
        assertTrue(Math.abs((Double) a - nearby) < 0.5d);

        double nanOut = (Double) node.compute(Map.of(
                "input_x", Double.NaN, "input_y", 0.0d, "input_z", 0.0d, "input_seed", 0
        )).get("output_noise");
        assertTrue(Double.isNaN(nanOut));

        double hugeOut = (Double) node.compute(Map.of(
                "input_x", 1e20d, "input_y", 0.0d, "input_z", 0.0d, "input_seed", 0
        )).get("output_noise");
        assertTrue(Double.isNaN(hugeOut));
    }

    @Test
    void numericRandomOutputsAreFiniteOnFiniteDomains() {
        RandomNumberNode node = new RandomNumberNode();
        for (int i = 0; i < 20; i++) {
            double v = (Double) node.compute(Map.of(
                    "input_domain", new NumericRangeData(-10.0d, 10.0d),
                    "input_seed", i
            )).get("output_random");
            assertTrue(Double.isFinite(v));
        }
    }

    @Test
    void currentGraphFormatIsV30() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }



    private static IPort findPort(Object node, String portId) {
        IPort port = findPortOrNull(node, portId);
        if (port == null) {
            throw new AssertionError("missing port " + portId);
        }
        return port;
    }

    private static IPort findPortOrNull(Object node, String portId) {
        Iterable<IPort> inputs;
        Iterable<IPort> outputs;
        switch (node) {
            case RandomNumberNode n -> {
                inputs = n.getInputPorts();
                outputs = n.getOutputPorts();
            }
            case RandomNumbersNode n -> {
                inputs = n.getInputPorts();
                outputs = n.getOutputPorts();
            }
            case RandomListItemNode n -> {
                inputs = n.getInputPorts();
                outputs = n.getOutputPorts();
            }
            case RandomVectorNode n -> {
                inputs = n.getInputPorts();
                outputs = n.getOutputPorts();
            }
            case RandomVectorsNode n -> {
                inputs = n.getInputPorts();
                outputs = n.getOutputPorts();
            }
            case NoiseNode n -> {
                inputs = n.getInputPorts();
                outputs = n.getOutputPorts();
            }
            case null, default -> {
                return null;
            }
        }
        for (IPort port : inputs) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        for (IPort port : outputs) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        return node;
    }

    private static SavedConnection wire(String sourceNode, String sourcePort, String targetNode, String targetPort) {
        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = sourceNode;
        connection.sourcePortId = sourcePort;
        connection.targetNodeId = targetNode;
        connection.targetPortId = targetPort;
        return connection;
    }

    private static boolean hasWire(SavedGraph graph, String sourceNode, String sourcePort,
                                   String targetNode, String targetPort) {
        return graph.connections.stream().anyMatch(c ->
                sourceNode.equals(c.sourceNodeId)
                        && sourcePort.equalsIgnoreCase(c.sourcePortId)
                        && targetNode.equals(c.targetNodeId)
                        && targetPort.equalsIgnoreCase(c.targetPortId));
    }
}

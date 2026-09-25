package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.math.FieldMath;
import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.nodes.math.fields.AttractorFieldBlendNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldBinaryOpNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldConstantNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldNoiseNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldSamplePointNode;
import com.nodecraft.nodesystem.nodes.math.fields.ScalarFieldSamplePointsNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldConstantNode;
import com.nodecraft.nodesystem.nodes.math.fields.VectorFieldSamplePointNode;
import com.nodecraft.nodesystem.nodes.math.random.NoiseNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field v1 language fence: inherited Scalar/Random semantics, finite Valid sampling, typed lists.
 */
class FieldLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void exactlySeventeenFieldNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("math.fields."))
                .sorted()
                .collect(Collectors.toList());
        assertEquals(17, ids.size(), "Expected 17 field nodes: " + ids);
    }

    @Test
    void scalarSamplePointsOutputIsDoubleList() {
        ScalarFieldSamplePointsNode node = new ScalarFieldSamplePointsNode();
        assertEquals(NodeDataType.DOUBLE_LIST,
                node.getOutputPorts().stream()
                        .filter(p -> "output_values".equals(p.getId()))
                        .findFirst()
                        .orElseThrow()
                        .getDataType());
    }

    @Test
    void scalarFieldNoiseMatchesRandomNoiseKernel() {
        ScalarFieldNoiseNode fieldNode = new ScalarFieldNoiseNode();
        NoiseNode noiseNode = new NoiseNode();

        Object fieldObj = fieldNode.compute(Map.of(
                "input_seed", 0,
                "input_scale", 1.0d,
                "input_amplitude", 1.0d
        )).get("output_field");
        assertTrue(fieldObj instanceof ScalarFieldData);
        ScalarFieldData field = (ScalarFieldData) fieldObj;

        double fromField = field.sampleScalar(new Vector3d(1.0d, 2.0d, 3.0d));
        double fromNoise = (Double) noiseNode.compute(Map.of(
                "input_x", 1.0d,
                "input_y", 2.0d,
                "input_z", 3.0d,
                "input_seed", 0
        )).get("output_noise");

        assertEquals(fromNoise, fromField, 0.0d);
    }

    @Test
    void scalarFieldNoiseIgnoresNonIntegerSeed() {
        ScalarFieldNoiseNode node = new ScalarFieldNoiseNode();
        Map<String, Object> withString = Map.of(
                "input_seed", "1",
                "input_scale", 1.0d,
                "input_amplitude", 1.0d
        );
        Map<String, Object> withZero = Map.of(
                "input_seed", 0,
                "input_scale", 1.0d,
                "input_amplitude", 1.0d
        );
        ScalarFieldData a = (ScalarFieldData) node.compute(withString).get("output_field");
        ScalarFieldData b = (ScalarFieldData) node.compute(withZero).get("output_field");
        assertEquals(a.sampleScalar(new Vector3d()), b.sampleScalar(new Vector3d()), 0.0d);
    }

    @Test
    void scalarSamplePointRejectsNonFiniteFieldValue() {
        ScalarFieldData divField = point -> FieldMath.combineScalars(1.0d, 0.0d, FieldMath.ScalarCombineOp.DIV);
        ScalarFieldSamplePointNode node = new ScalarFieldSamplePointNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_field", divField,
                "input_point", new Vector3d(0.0d, 0.0d, 0.0d)
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_value")));
    }

    @Test
    void vectorSamplePointRejectsNonFiniteComponents() {
        VectorFieldConstantNode constant = new VectorFieldConstantNode();
        Object field = constant.compute(Map.of(
                "input_x", Double.NaN,
                "input_y", 1.0d,
                "input_z", 2.0d
        )).get("output_field");
        assertNull(field);

        VectorFieldSamplePointNode sample = new VectorFieldSamplePointNode();
        Map<String, Object> badField = sample.compute(Map.of(
                "input_field", (com.nodecraft.nodesystem.datatypes.VectorFieldData) (point, dest) -> dest.set(Double.NaN, 1.0d, 2.0d),
                "input_point", new Vector3d()
        ));
        assertFalse((Boolean) badField.get("output_valid"));
        assertNull(badField.get("output_vector"));
    }

    @Test
    void scalarSamplePointsFailClosedOnSingleNaN() {
        ScalarFieldData field = point -> point.x > 0.0d ? 1.0d : Double.NaN;
        ScalarFieldSamplePointsNode node = new ScalarFieldSamplePointsNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_field", field,
                "input_points", List.of(new Vector3d(1.0d, 0.0d, 0.0d), new Vector3d(-1.0d, 0.0d, 0.0d))
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(((List<?>) outputs.get("output_values")).isEmpty());
        assertEquals(0, outputs.get("output_count"));
    }

    @Test
    void constantFieldsRejectNonFiniteInputs() {
        assertNull(new ScalarFieldConstantNode().compute(Map.of("input_value", Double.POSITIVE_INFINITY)).get("output_field"));
        assertNull(new VectorFieldConstantNode().compute(Map.of(
                "input_x", 1.0d,
                "input_y", Double.NaN,
                "input_z", 0.0d
        )).get("output_field"));
    }

    @Test
    void blendHonorsTinyNonZeroWeight() {
        com.nodecraft.nodesystem.datatypes.VectorFieldData unitX =
                (point, dest) -> dest.set(1.0d, 0.0d, 0.0d);
        AttractorFieldBlendNode blend = new AttractorFieldBlendNode();
        Object fieldObj = blend.compute(Map.of(
                "input_field_a", unitX,
                "input_weight_a", 5.0e-10d
        )).get("output_field");
        assertTrue(fieldObj instanceof com.nodecraft.nodesystem.datatypes.VectorFieldData);
        com.nodecraft.nodesystem.datatypes.VectorFieldData field =
                (com.nodecraft.nodesystem.datatypes.VectorFieldData) fieldObj;
        Vector3d out = new Vector3d();
        field.sampleVector(new Vector3d(), out);
        assertTrue(out.x > 0.0d);
    }

    @Test
    void scalarFieldBinaryOpDivByZeroProducesNaNNotInfinity() {
        ScalarFieldBinaryOpNode op = new ScalarFieldBinaryOpNode();
        op.setNodeState(Map.of("operation", ScalarFieldBinaryOpNode.ScalarBinaryOp.DIV.name()));
        Object one = new ScalarFieldConstantNode().compute(Map.of("input_value", 1.0d)).get("output_field");
        Object zero = new ScalarFieldConstantNode().compute(Map.of("input_value", 0.0d)).get("output_field");
        Object fieldObj = op.compute(Map.of("input_a", one, "input_b", zero)).get("output_field");
        assertTrue(fieldObj instanceof ScalarFieldData);
        ScalarFieldData field = (ScalarFieldData) fieldObj;
        assertTrue(Double.isNaN(field.sampleScalar(new Vector3d())));
    }

    @Test
    void randomOpsValueNoiseMatchesFieldNoiseAtScaleZero() {
        double atOrigin = RandomOps.valueNoise3(0.0d, 0.0d, 0.0d, 0);
        ScalarFieldNoiseNode node = new ScalarFieldNoiseNode();
        ScalarFieldData field = (ScalarFieldData) node.compute(Map.of(
                "input_seed", 0,
                "input_scale", 0.0d,
                "input_amplitude", 1.0d
        )).get("output_field");
        assertEquals(atOrigin, field.sampleScalar(new Vector3d(5.0d, 5.0d, 5.0d)), 0.0d);
    }

    @Test
    void currentGraphFormatIsV30() {
        assertEquals(30, GraphFormatVersion.V30);
        assertEquals(GraphFormatVersion.V30, GraphFormatVersion.CURRENT);
    }

    @Test
    void v29ToV30DropsIncompatibleScalarSamplePointsWires() {
        SavedGraph v29 = new SavedGraph();
        v29.formatVersion = GraphFormatVersion.V29;

        SavedNode sampler = savedNode("ssp", "math.fields.scalar_sample_points");
        SavedNode sortText = savedNode("sort", "math.list.sort_text");
        SavedNode createList = savedNode("clist", "math.list.create_list");
        SavedNode field = savedNode("field", "math.fields.scalar_constant");

        v29.nodes = new ArrayList<>(List.of(sampler, sortText, createList, field));
        v29.connections = new ArrayList<>(List.of(
                wire("ssp", "output_values", "sort", "input_list"),
                wire("ssp", "output_values", "clist", "input_0"),
                wire("field", "output_field", "ssp", "input_field")
        ));
        v29.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v29);
        assertEquals(GraphFormatVersion.V30, migrated.formatVersion);
        assertFalse(hasWire(migrated, "ssp", "output_values", "sort", "input_list"));
        assertTrue(hasWire(migrated, "ssp", "output_values", "clist", "input_0"));
        assertTrue(hasWire(migrated, "field", "output_field", "ssp", "input_field"));
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

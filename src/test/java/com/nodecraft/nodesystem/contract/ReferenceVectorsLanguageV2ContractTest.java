package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.reference.vectors.AngleBetweenVectorsNode;
import com.nodecraft.nodesystem.nodes.reference.vectors.SlerpVectorsNode;
import com.nodecraft.nodesystem.nodes.reference.vectors.Vector2InputNode;
import com.nodecraft.nodesystem.nodes.reference.vectors.VectorInputNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Reference Vectors Language v2 (Graph V88).
 */
class ReferenceVectorsLanguageV2ContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
        "reference.vectors.vector",
        "reference.vectors.vector2_input",
        "reference.vectors.construct_vector",
        "reference.vectors.deconstruct_vector",
        "reference.vectors.vector_length",
        "reference.vectors.normalize_vector",
        "reference.vectors.vector_addition",
        "reference.vectors.vector_subtraction",
        "reference.vectors.vector_scalar_multiply",
        "reference.vectors.vector_scalar_divide",
        "reference.vectors.dot_product",
        "reference.vectors.cross_product",
        "reference.vectors.angle_between",
        "reference.vectors.lerp_vectors",
        "reference.vectors.slerp",
        "reference.vectors.reflect",
        "reference.vectors.project"
    );

    private static final Set<NodeDataType> FORBIDDEN_PORT_TYPES = Set.of(
        NodeDataType.ANY,
        NodeDataType.LIST,
        NodeDataType.POLYLINE,
        NodeDataType.CURVE,
        NodeDataType.BLOCK_LIST,
        NodeDataType.FILE_PATH
    );

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsAtLeastV88() {
        assertEquals(88, GraphFormatVersion.V88);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V88);
    }

    @Test
    void exactlySeventeenNodesWithUniqueOrdersZeroToSixteen() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("reference.vectors."))
            .sorted()
            .toList();
        assertEquals(17, ids.size(), "Expected 17 reference.vectors nodes: " + ids);
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("reference.vectors", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 17; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void allNodesExposeValidAndError() {
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            assertTrue(hasPort(node, "output_valid"), id + " missing output_valid");
            assertTrue(hasPort(node, "output_error"), id + " missing output_error");
            assertPortType(id, "output_error", false, NodeDataType.STRING);
        }
    }

    @Test
    void publicPortsForbidLegacyLooseTypes() {
        List<String> errors = new ArrayList<>();
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            for (IPort port : node.getInputPorts()) {
                if (FORBIDDEN_PORT_TYPES.contains(port.getDataType())) {
                    errors.add(id + "." + port.getId() + " input is " + port.getDataType());
                }
            }
            for (IPort port : node.getOutputPorts()) {
                if (FORBIDDEN_PORT_TYPES.contains(port.getDataType())) {
                    errors.add(id + "." + port.getId() + " output is " + port.getDataType());
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void vectorInputConnectedIntegerFailsStrictDouble() {
        VectorInputProbe input = new VectorInputProbe();
        input.connectInput("input_x", NodeDataType.DOUBLE);
        input.putRawInput("input_x", 3);
        input.processNode(null);
        assertInvalid(input);
    }

    @Test
    void vectorInputConnectedNullFailsClosed() {
        VectorInputProbe input = new VectorInputProbe();
        input.connectInput("input_x", NodeDataType.DOUBLE);
        input.putRawInput("input_x", null);
        input.processNode(null);
        assertInvalid(input);
    }

    @Test
    void vector2InputNonFinitePropertyFailsCleanly() {
        Vector2InputNode input = new Vector2InputNode();
        input.setX(Double.NaN);
        assertInvalid(input);
    }

    @Test
    void vector2InputStateLoadSkipsNonFiniteValues() {
        Vector2InputNode input = new Vector2InputNode();
        input.setNodeState(java.util.Map.of("x", Double.NaN, "y", 3.0d));
        input.processNode(null);
        assertValid(input);
        assertEquals(0.0d, input.getX());
        assertEquals(3.0d, input.getY());
    }

    @Test
    void constructVectorRejectsIntegerComponent() {
        BaseNode construct = node("reference.vectors.construct_vector");
        construct.setInput("input_x", 1);
        construct.setInput("input_y", 2.0d);
        construct.setInput("input_z", 3.0d);
        construct.processNode(null);
        assertInvalid(construct);
    }

    @Test
    void vectorLengthHugeFiniteVectorNeverInfinitySuccess() {
        BaseNode length = node("reference.vectors.vector_length");
        length.setInput("input_vector", new Vector3d(1e308d, 1e308d, 0));
        length.processNode(null);
        assertValid(length);
        assertTrue(Double.isFinite((Double) length.getOutput("output_length")));
    }

    @Test
    void normalizeHugeFiniteVectorYieldsFiniteUnit() {
        BaseNode normalize = node("reference.vectors.normalize_vector");
        normalize.setInput("input_vector", new Vector3d(1e308d, 1e308d, 0));
        normalize.processNode(null);
        assertValid(normalize);
        Vector3d unit = requireVector(normalize.getOutput("output_normalized_vector"));
        assertEquals(1.0d, unit.length(), 1.0e-6d);
    }

    @Test
    void normalizeZeroVectorFails() {
        BaseNode normalize = node("reference.vectors.normalize_vector");
        normalize.setInput("input_vector", new Vector3d(0, 0, 0));
        normalize.processNode(null);
        assertInvalid(normalize);
    }

    @Test
    void vectorAdditionOverflowFailsClosed() {
        BaseNode add = node("reference.vectors.vector_addition");
        add.setInput("input_vector_a", new Vector3d(1e308d, 0, 0));
        add.setInput("input_vector_b", new Vector3d(1e308d, 0, 0));
        add.processNode(null);
        assertInvalid(add);
    }

    @Test
    void vectorScalarMultiplyOverflowFailsClosed() {
        BaseNode multiply = node("reference.vectors.vector_scalar_multiply");
        multiply.setInput("input_vector", new Vector3d(1e308d, 0, 0));
        multiply.setInput("input_scalar", 2.0d);
        multiply.processNode(null);
        assertInvalid(multiply);
    }

    @Test
    void dotProductOverflowFailsClosed() {
        BaseNode dot = node("reference.vectors.dot_product");
        dot.setInput("input_vector_a", new Vector3d(1e308d, 0, 0));
        dot.setInput("input_vector_b", new Vector3d(1e308d, 0, 0));
        dot.processNode(null);
        assertInvalid(dot);
        assertEquals(Double.NaN, dot.getOutput("output_dot_product"));
    }

    @Test
    void crossProductParallelVectorsValidZero() {
        BaseNode cross = node("reference.vectors.cross_product");
        cross.setInput("input_vector_a", new Vector3d(1, 0, 0));
        cross.setInput("input_vector_b", new Vector3d(2, 0, 0));
        cross.processNode(null);
        assertValid(cross);
        assertEquals(0.0d, cross.getOutput("output_magnitude"));
    }

    @Test
    void lerpOppositeHugeValuesAvoidsIntermediateOverflow() {
        BaseNode lerp = node("reference.vectors.lerp_vectors");
        lerp.setInput("input_a", new Vector3d(1e308d, 0, 0));
        lerp.setInput("input_b", new Vector3d(-1e308d, 0, 0));
        lerp.setInput("input_t", 0.5d);
        lerp.processNode(null);
        assertValid(lerp);
        Vector3d result = requireVector(lerp.getOutput("output_result"));
        assertEquals(0.0d, result.x, 1e300d);
    }

    @Test
    void lerpExtrapolatesWithoutClamping() {
        BaseNode lerp = node("reference.vectors.lerp_vectors");
        lerp.setInput("input_a", new Vector3d(0, 0, 0));
        lerp.setInput("input_b", new Vector3d(1, 0, 0));
        lerp.setInput("input_t", 2.0d);
        lerp.processNode(null);
        assertValid(lerp);
        assertVectorEquals(new Vector3d(2, 0, 0), requireVector(lerp.getOutput("output_result")), 1.0e-9d);
    }

    @Test
    void lerpHugeEqualVectorsExtrapolationStaysFinite() {
        BaseNode lerp = node("reference.vectors.lerp_vectors");
        lerp.setInput("input_a", new Vector3d(1e308d, 0, 0));
        lerp.setInput("input_b", new Vector3d(1e308d, 0, 0));
        lerp.setInput("input_t", 2.0d);
        lerp.processNode(null);
        assertValid(lerp);
        assertVectorEquals(new Vector3d(1e308d, 0, 0), requireVector(lerp.getOutput("output_result")), 1.0e300d);
    }

    @Test
    void slerpPreserveMagnitudeHugeEqualMagnitudesExtrapolationStaysFinite() {
        SlerpVectorsNode slerp = new SlerpVectorsNode();
        slerp.setNodeState(java.util.Map.of("preserveMagnitude", true));
        slerp.setInput("input_a", new Vector3d(1e308d, 0, 0));
        slerp.setInput("input_b", new Vector3d(1e308d, 0, 0));
        slerp.setInput("input_t", 2.0d);
        slerp.processNode(null);
        assertValid(slerp);
        Vector3d result = requireVector(slerp.getOutput("output_result"));
        assertVectorEquals(new Vector3d(1e308d, 0, 0), result, 1.0e300d);
        assertEquals(1e308d, VectorUtils.safeLength(result), 1.0e300d);
    }

    @Test
    void safeScalarLerpHugeEqualMagnitudesExtrapolationStaysFinite() {
        assertEquals(1e308d, VectorUtils.safeScalarLerp(1e308d, 1e308d, 2.0d), 1.0e300d);
    }

    @Test
    void lerpStrictDoubleRejectsIntegerT() {
        BaseNode lerp = node("reference.vectors.lerp_vectors");
        lerp.setInput("input_a", new Vector3d(0, 0, 0));
        lerp.setInput("input_b", new Vector3d(1, 0, 0));
        lerp.setInput("input_t", 1);
        lerp.processNode(null);
        assertInvalid(lerp);
    }

    @Test
    void angleReferenceUnconnectedSemanticsPreserved() {
        BaseNode angle = node("reference.vectors.angle_between");
        angle.setInput("input_a", new Vector3d(1, 0, 0));
        angle.setInput("input_b", new Vector3d(0, 1, 0));
        angle.processNode(null);
        assertValid(angle);
        assertEquals(90.0d, angle.getOutput("output_angle"));
        assertEquals(Double.NaN, angle.getOutput("output_signed_angle"));
    }

    @Test
    void angleReferenceConnectedInvalidFailsClosed() {
        AngleBetweenVectorsProbe angle = new AngleBetweenVectorsProbe();
        angle.setInput("input_a", new Vector3d(1, 0, 0));
        angle.setInput("input_b", new Vector3d(0, 1, 0));
        angle.connectInput("input_reference", NodeDataType.VECTOR);
        angle.putRawInput("input_reference", null);
        angle.processNode(null);
        assertInvalid(angle);
    }

    @Test
    void reflectZeroIncomingVectorRemainsValid() {
        BaseNode reflect = node("reference.vectors.reflect");
        reflect.setInput("input_vector", new Vector3d(0, 0, 0));
        reflect.setInput("input_normal", new Vector3d(0, 0, 1));
        reflect.processNode(null);
        assertValid(reflect);
        Vector3d result = requireVector(reflect.getOutput("output_reflected"));
        assertEquals(0.0d, result.length(), 1.0e-9d);
    }

    @Test
    void reflectDotOverflowFailsClosed() {
        BaseNode reflect = node("reference.vectors.reflect");
        reflect.setInput("input_vector", new Vector3d(1e308d, 0, 0));
        reflect.setInput("input_normal", new Vector3d(1e308d, 0, 0));
        reflect.processNode(null);
        assertInvalid(reflect);
    }

    @Test
    void projectHugeAxisDoesNotSilentlyCollapseToZero() {
        BaseNode project = node("reference.vectors.project");
        project.setInput("input_a", new Vector3d(1, 2, 3));
        project.setInput("input_b", new Vector3d(1e200d, 0, 0));
        project.processNode(null);
        assertValid(project);
        Vector3d projection = requireVector(project.getOutput("output_projection"));
        assertTrue(projection.x > 0.0d);
    }

    @Test
    void zeroVectorDotProductRemainsValid() {
        BaseNode dot = node("reference.vectors.dot_product");
        dot.setInput("input_vector_a", new Vector3d(0, 0, 0));
        dot.setInput("input_vector_b", new Vector3d(3, 4, 0));
        dot.processNode(null);
        assertValid(dot);
        assertEquals(0.0d, dot.getOutput("output_dot_product"));
    }

    @Test
    void vectorOutputsAreCanonicalVectorData() {
        BaseNode construct = node("reference.vectors.construct_vector");
        construct.setInput("input_x", 1.0d);
        construct.setInput("input_y", 2.0d);
        construct.setInput("input_z", 3.0d);
        construct.processNode(null);
        assertInstanceOf(VectorData.class, construct.getOutput("output_vector"));
    }

    private static BaseNode node(String typeId) {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(typeId));
    }

    private static void assertValid(BaseNode node) {
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals("", node.getOutput("output_error"));
    }

    private static void assertInvalid(BaseNode node) {
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNotNull(node.getOutput("output_error"));
        assertFalse(String.valueOf(node.getOutput("output_error")).isBlank());
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
            .filter(candidate -> candidate.getId().equals(portId))
            .findFirst()
            .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }

    private static boolean hasPort(INode node, String portId) {
        return node.getInputPorts().stream().anyMatch(p -> portId.equals(p.getId()))
            || node.getOutputPorts().stream().anyMatch(p -> portId.equals(p.getId()));
    }

    private static Vector3d requireVector(Object value) {
        Vector3d vector = VectorUtils.toVector(value);
        assertNotNull(vector, "expected VECTOR payload");
        return vector;
    }

    private static void assertVectorEquals(Vector3d expected, Vector3d actual, double epsilon) {
        assertEquals(expected.x, actual.x, epsilon);
        assertEquals(expected.y, actual.y, epsilon);
        assertEquals(expected.z, actual.z, epsilon);
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
    }

    private static final class VectorInputProbe extends VectorInputNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ReferenceVectorsLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class AngleBetweenVectorsProbe extends AngleBetweenVectorsNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ReferenceVectorsLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }
}

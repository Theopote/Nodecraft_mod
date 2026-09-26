package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.reference.vectors.AngleBetweenVectorsNode;
import com.nodecraft.nodesystem.nodes.reference.vectors.Vector2InputNode;
import com.nodecraft.nodesystem.nodes.reference.vectors.VectorInputNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
 * Reference Vectors v1 language fence (Graph V50).
 */
class ReferenceVectorsLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
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
            "reference.vectors.project",
            "reference.vectors.component_minmax"
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
    void currentGraphFormatIsV50() {
        assertEquals(50, GraphFormatVersion.V50);
        assertEquals(GraphFormatVersion.V50, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyEighteenCanonicalReferenceVectorNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("reference.vectors."))
                .sorted()
                .toList();
        assertEquals(18, ids.size(), "Expected 18 reference.vectors nodes: " + ids);
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
    }

    @Test
    void referenceVectorNodesHaveUniqueOrderZeroThroughSeventeen() {
        int[] orders = CANONICAL_IDS.stream()
                .mapToInt(typeId -> {
                    INode node = registry.createNodeInstance(typeId);
                    assertNotNull(node, typeId);
                    NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
                    assertNotNull(info, typeId);
                    return info.order();
                })
                .sorted()
                .toArray();
        assertEquals(18, orders.length);
        for (int i = 0; i < orders.length; i++) {
            assertEquals(i, orders[i], "Expected unique order " + i);
        }
    }

    @Test
    void vectorInputHasValidOutput() {
        assertPortType("reference.vectors.vector", "output_valid", false, NodeDataType.BOOLEAN);
    }

    @Test
    void vectorInputConnectedNullFailsClosed() {
        VectorInputProbe input = new VectorInputProbe();
        input.connectInput("input_x", NodeDataType.DOUBLE);
        input.putRawInput("input_x", null);
        input.processNode(null);
        assertEquals(Boolean.FALSE, input.getOutput("output_valid"));
        assertNull(input.getOutput("output_vector"));
    }

    @Test
    void vectorInputUnconnectedUsesPropertyFallback() {
        VectorInputProbe input = new VectorInputProbe();
        input.setX(1.0d);
        input.setY(2.0d);
        input.setZ(3.0d);
        input.processNode(null);
        assertEquals(Boolean.TRUE, input.getOutput("output_valid"));
        Vector3d vector = assertInstanceOf(Vector3d.class, input.getOutput("output_vector"));
        assertVectorEquals(new Vector3d(1, 2, 3), vector, 1.0e-9d);
    }

    @Test
    void crossProductParallelVectorsAreValidZero() {
        BaseNode cross = node("reference.vectors.cross_product");
        cross.setInput("input_vector_a", new Vector3d(1, 0, 0));
        cross.setInput("input_vector_b", new Vector3d(2, 0, 0));
        cross.processNode(null);
        assertEquals(Boolean.TRUE, cross.getOutput("output_valid"));
        Vector3d result = assertInstanceOf(Vector3d.class, cross.getOutput("output_cross_product"));
        assertEquals(0.0d, result.length(), 1.0e-9d);
        assertEquals(0.0d, cross.getOutput("output_magnitude"));
    }

    @Test
    void crossProductZeroVectorIsValid() {
        BaseNode cross = node("reference.vectors.cross_product");
        cross.setInput("input_vector_a", new Vector3d(0, 0, 0));
        cross.setInput("input_vector_b", new Vector3d(1, 0, 0));
        cross.processNode(null);
        assertEquals(Boolean.TRUE, cross.getOutput("output_valid"));
        assertEquals(0.0d, cross.getOutput("output_magnitude"));
    }

    @Test
    void normalizeZeroVectorInvalidOutputsNull() {
        BaseNode normalize = node("reference.vectors.normalize_vector");
        normalize.setInput("input_vector", new Vector3d(0, 0, 0));
        normalize.processNode(null);
        assertEquals(Boolean.FALSE, normalize.getOutput("output_valid"));
        assertNull(normalize.getOutput("output_normalized_vector"));
    }

    @Test
    void dotProductZeroVectorIsValid() {
        BaseNode dot = node("reference.vectors.dot_product");
        dot.setInput("input_vector_a", new Vector3d(0, 0, 0));
        dot.setInput("input_vector_b", new Vector3d(3, 4, 0));
        dot.processNode(null);
        assertEquals(Boolean.TRUE, dot.getOutput("output_valid"));
        assertEquals(0.0d, dot.getOutput("output_dot_product"));
    }

    @Test
    void vectorLengthZeroIsValid() {
        BaseNode length = node("reference.vectors.vector_length");
        length.setInput("input_vector", new Vector3d(0, 0, 0));
        length.processNode(null);
        assertEquals(Boolean.TRUE, length.getOutput("output_valid"));
        assertEquals(0.0d, length.getOutput("output_length"));
    }

    @Test
    void vectorScalarMultiplyZeroIsValid() {
        BaseNode multiply = node("reference.vectors.vector_scalar_multiply");
        multiply.setInput("input_vector", new Vector3d(1, 2, 3));
        multiply.setInput("input_scalar", 0.0d);
        multiply.processNode(null);
        assertEquals(Boolean.TRUE, multiply.getOutput("output_valid"));
        Vector3d result = assertInstanceOf(Vector3d.class, multiply.getOutput("output_vector_product"));
        assertEquals(0.0d, result.length(), 1.0e-9d);
    }

    @Test
    void vectorScalarDivideNearZeroScalarFailsClosed() {
        BaseNode divide = node("reference.vectors.vector_scalar_divide");
        divide.setInput("input_vector", new Vector3d(1, 0, 0));
        divide.setInput("input_scalar", 1.0e-15d);
        divide.processNode(null);
        assertEquals(Boolean.FALSE, divide.getOutput("output_valid"));
        assertNull(divide.getOutput("output_vector_quotient"));
    }

    @Test
    void lerpVectorsExtrapolatesWithoutClamping() {
        BaseNode lerp = node("reference.vectors.lerp_vectors");
        lerp.setInput("input_a", new Vector3d(0, 0, 0));
        lerp.setInput("input_b", new Vector3d(1, 0, 0));
        lerp.setInput("input_t", 2.0d);
        lerp.processNode(null);
        assertEquals(Boolean.TRUE, lerp.getOutput("output_valid"));
        Vector3d result = assertInstanceOf(Vector3d.class, lerp.getOutput("output_result"));
        assertVectorEquals(new Vector3d(2, 0, 0), result, 1.0e-9d);
    }

    @Test
    void slerpPreservesEndpointsIncludingNegativeDot() {
        Vector3d a = new Vector3d(1, 0, 0);
        Vector3d b = new Vector3d(-0.8d, 0.6d, 0.0d);
        Vector3d bDir = new Vector3d(b).normalize();

        BaseNode slerp = node("reference.vectors.slerp");
        slerp.setInput("input_a", a);
        slerp.setInput("input_b", b);
        slerp.setInput("input_t", 0.0d);
        slerp.processNode(null);
        assertEquals(Boolean.TRUE, slerp.getOutput("output_valid"));
        assertVectorEquals(new Vector3d(1, 0, 0), assertInstanceOf(Vector3d.class, slerp.getOutput("output_result")), 1.0e-6d);

        slerp.setInput("input_t", 1.0d);
        slerp.processNode(null);
        assertEquals(Boolean.TRUE, slerp.getOutput("output_valid"));
        assertVectorEquals(bDir, assertInstanceOf(Vector3d.class, slerp.getOutput("output_result")), 1.0e-6d);
    }

    @Test
    void slerpAntiparallelEndpointsAreDeterministic() {
        Vector3d a = new Vector3d(1, 0, 0);
        Vector3d b = new Vector3d(-1, 0, 0);

        BaseNode slerp = node("reference.vectors.slerp");
        slerp.setInput("input_a", a);
        slerp.setInput("input_b", b);

        slerp.setInput("input_t", 0.0d);
        slerp.processNode(null);
        assertVectorEquals(a, assertInstanceOf(Vector3d.class, slerp.getOutput("output_result")), 1.0e-6d);

        slerp.setInput("input_t", 1.0d);
        slerp.processNode(null);
        assertVectorEquals(b, assertInstanceOf(Vector3d.class, slerp.getOutput("output_result")), 1.0e-6d);
    }

    @Test
    void angleReferenceUnconnectedYieldsUnsignedOnly() {
        BaseNode angle = node("reference.vectors.angle_between");
        angle.setInput("input_a", new Vector3d(1, 0, 0));
        angle.setInput("input_b", new Vector3d(0, 1, 0));
        angle.processNode(null);
        assertEquals(Boolean.TRUE, angle.getOutput("output_valid"));
        assertEquals(90.0d, angle.getOutput("output_angle"));
        assertEquals(Double.NaN, angle.getOutput("output_signed_angle"));
    }

    @Test
    void angleReferenceConnectedNullFailsClosed() {
        AngleBetweenVectorsProbe angle = new AngleBetweenVectorsProbe();
        angle.setInput("input_a", new Vector3d(1, 0, 0));
        angle.setInput("input_b", new Vector3d(0, 1, 0));
        angle.connectInput("input_reference", NodeDataType.VECTOR);
        angle.putRawInput("input_reference", null);
        angle.processNode(null);
        assertEquals(Boolean.FALSE, angle.getOutput("output_valid"));
        assertEquals(Double.NaN, angle.getOutput("output_angle"));
    }

    @Test
    void reflectHasNoNormalizedNormalOutput() {
        assertFalse(hasPort("reference.vectors.reflect", "output_normalized_normal", false));
    }

    @Test
    void reflectZeroNormalInvalidOutputsNull() {
        BaseNode reflect = node("reference.vectors.reflect");
        reflect.setInput("input_vector", new Vector3d(1, 0, 0));
        reflect.setInput("input_normal", new Vector3d(0, 0, 0));
        reflect.processNode(null);
        assertEquals(Boolean.FALSE, reflect.getOutput("output_valid"));
        assertNull(reflect.getOutput("output_reflected"));
    }

    @Test
    void projectZeroAxisFailsClosedWithNullOutputs() {
        BaseNode project = node("reference.vectors.project");
        project.setInput("input_a", new Vector3d(1, 2, 3));
        project.setInput("input_b", new Vector3d(0, 0, 0));
        project.processNode(null);
        assertEquals(Boolean.FALSE, project.getOutput("output_valid"));
        assertNull(project.getOutput("output_projection"));
        assertNull(project.getOutput("output_rejection"));
        assertEquals(Double.NaN, project.getOutput("output_scale"));
    }

    @Test
    void vector2InputEmitsVectorOnly() {
        Vector2InputNode node = new Vector2InputNode();
        assertEquals(NodeDataType.VECTOR, findPort(node, "output_vector").getDataType());
        assertFalse(hasPort(node, "output_x"));
        assertFalse(hasPort(node, "output_y"));
        assertFalse(hasPort(node, "output_uv"));
    }

    private static BaseNode node(String typeId) {
        BaseNode node = (BaseNode) registry.createNodeInstance(typeId);
        assertNotNull(node, typeId);
        return node;
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        assertNotNull(node);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
                .filter(candidate -> candidate.getId().equals(portId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }

    private static boolean hasPort(String typeId, String portId, boolean input) {
        INode node = registry.createNodeInstance(typeId);
        return (input ? node.getInputPorts() : node.getOutputPorts()).stream()
                .anyMatch(port -> port.getId().equals(portId));
    }

    private static IPort findPort(INode node, String portId) {
        return node.getOutputPorts().stream()
                .filter(port -> portId.equals(port.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing port " + portId));
    }

    private static boolean hasPort(INode node, String portId) {
        return node.getOutputPorts().stream().anyMatch(port -> portId.equals(port.getId()));
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
                .orElseThrow(() -> new AssertionError(target.getTypeId() + " missing port " + inputPortId));
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
    }

    private static final class VectorInputProbe extends VectorInputNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ReferenceVectorsLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class AngleBetweenVectorsProbe extends AngleBetweenVectorsNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ReferenceVectorsLanguageContractTest.connectInput(this, portId, outputType);
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

package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.nodes.geometry.primitives.SphereByCenterRadiusNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5G: wire / setInput / compute(Map) must share one optional-port drive rule.
 * Undriven → property fallback; driven invalid → fail closed (no silent property washout).
 */
class OptionalPortDriveParityContractTest {

    private static final String PORT = "input_value";
    private static final double PROPERTY_DOUBLE = 99.0d;
    private static final int PROPERTY_INT = 99;
    private static final Vector3d PROPERTY_POINT = new Vector3d(9, 9, 9);
    private static final Vector3d PROPERTY_VECTOR = new Vector3d(9, 9, 9);

    @BeforeAll
    static void initRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void undrivenFallsBackToProperty() {
        StubNode node = new StubNode(NodeDataType.DOUBLE);
        assertFalse(OptionalPortDrive.isDriven(node, PORT));
        assertEquals(PROPERTY_DOUBLE, OptionalPortDrive.resolveOptionalDouble(node, PORT, PROPERTY_DOUBLE));
    }

    @Test
    void doubleValidParityAcrossWireSetInputAndCompute() {
        Double wired = resolveDoubleViaWire(2.5d);
        Double viaSet = resolveDoubleViaSetInput(2.5d);
        Double viaCompute = resolveDoubleViaCompute(2.5d);
        assertNotNull(wired);
        assertEquals(2.5d, wired, 1e-9);
        assertEquals(wired, viaSet);
        assertEquals(wired, viaCompute);
    }

    @Test
    void doubleIllegalParityFailsClosedAcrossIngress() {
        assertNull(resolveDoubleViaWire(2));
        assertNull(resolveDoubleViaSetInput(2));
        assertNull(resolveDoubleViaCompute(2));
        assertNull(resolveDoubleViaWire(Double.NaN));
        assertNull(resolveDoubleViaSetInput(Double.POSITIVE_INFINITY));
        assertNull(resolveDoubleViaCompute(null));
    }

    @Test
    void integerValidAndIllegalParity() {
        assertEquals(7, resolveIntegerViaWire(7));
        assertEquals(7, resolveIntegerViaSetInput(7));
        assertEquals(7, resolveIntegerViaCompute(7));

        assertNull(resolveIntegerViaWire(7.0d));
        assertNull(resolveIntegerViaSetInput(7.0d));
        assertNull(resolveIntegerViaCompute("7"));
    }

    @Test
    void pointValidAndIllegalParity() {
        PointData point = new PointData(1, 2, 3);
        Vector3d wired = resolvePointViaWire(point);
        Vector3d viaSet = resolvePointViaSetInput(point);
        Vector3d viaCompute = resolvePointViaCompute(point);
        assertNotNull(wired);
        assertEquals(1.0d, wired.x, 1e-9);
        assertEquals(wired.x, viaSet.x, 1e-9);
        assertEquals(wired.x, viaCompute.x, 1e-9);

        assertNull(resolvePointViaWire(new Vector3d(1, 2, 3)));
        assertNull(resolvePointViaSetInput(new Vector3d(1, 2, 3)));
        assertNull(resolvePointViaCompute("not-a-point"));
    }

    @Test
    void vectorValidAndIllegalParity() {
        VectorData vector = new VectorData(1, 0, 0);
        Vector3d wired = resolveVectorViaWire(vector);
        Vector3d viaSet = resolveVectorViaSetInput(vector);
        Vector3d viaCompute = resolveVectorViaCompute(vector);
        assertNotNull(wired);
        assertEquals(1.0d, wired.x, 1e-9);
        assertEquals(wired.x, viaSet.x, 1e-9);
        assertEquals(wired.x, viaCompute.x, 1e-9);

        assertNull(resolveVectorViaWire(new Vector3d(1, 0, 0)));
        assertNull(resolveVectorViaSetInput(new PointData(1, 0, 0)));
        assertNull(resolveVectorViaCompute(null));
    }

    @Test
    void validInputRecoversAfterPriorIllegalDrive() {
        StubNode node = new StubNode(NodeDataType.DOUBLE);
        node.setInput(PORT, 2);
        assertTrue(OptionalPortDrive.isDriven(node, PORT));
        assertNull(OptionalPortDrive.resolveOptionalDouble(node, PORT, PROPERTY_DOUBLE));

        node.setInput(PORT, 3.0d);
        Double recovered = OptionalPortDrive.resolveOptionalDouble(node, PORT, PROPERTY_DOUBLE);
        assertNotNull(recovered);
        assertEquals(3.0d, recovered, 1e-9);
    }

    @Test
    void computeClearsStaleInjectedInputsBetweenRounds() {
        StubNode node = new StubNode(NodeDataType.DOUBLE);
        node.compute(Map.of(PORT, 4.0d));
        assertTrue(OptionalPortDrive.isDriven(node, PORT));
        assertEquals(4.0d, OptionalPortDrive.resolveOptionalDouble(node, PORT, PROPERTY_DOUBLE), 1e-9);

        node.compute(Map.of());
        assertFalse(OptionalPortDrive.isDriven(node, PORT));
        assertEquals(PROPERTY_DOUBLE, OptionalPortDrive.resolveOptionalDouble(node, PORT, PROPERTY_DOUBLE), 1e-9);
    }

    @Test
    void sphereNodeParityValidRadiusAcrossWireSetInputAndCompute() {
        SphereByCenterRadiusNode wired = new SphereByCenterRadiusNode();
        connectAndDrive(wired, "input_radius", NodeDataType.DOUBLE, 3.0d);
        wired.processNode(null);
        assertEquals(Boolean.TRUE, wired.getOutput("output_valid"));
        assertEquals(3.0d, (Double) wired.getOutput("output_radius"), 1e-9);

        SphereByCenterRadiusNode viaSet = new SphereByCenterRadiusNode();
        viaSet.setInput("input_radius", 3.0d);
        viaSet.processNode(null);
        assertEquals(Boolean.TRUE, viaSet.getOutput("output_valid"));
        assertEquals(3.0d, (Double) viaSet.getOutput("output_radius"), 1e-9);

        SphereByCenterRadiusNode viaCompute = new SphereByCenterRadiusNode();
        Map<String, Object> outputs = viaCompute.compute(Map.of("input_radius", 3.0d));
        assertEquals(Boolean.TRUE, outputs.get("output_valid"));
        assertEquals(3.0d, (Double) outputs.get("output_radius"), 1e-9);
    }

    @Test
    void sphereNodeIllegalRadiusFailsClosedWithoutPropertyWashout() {
        SphereByCenterRadiusNode viaSet = new SphereByCenterRadiusNode();
        viaSet.setInput("input_radius", 2);
        viaSet.processNode(null);
        assertEquals(Boolean.FALSE, viaSet.getOutput("output_valid"));
        assertNull(viaSet.getOutput("output_sphere"));

        SphereByCenterRadiusNode viaCompute = new SphereByCenterRadiusNode();
        Map<String, Object> outputs = viaCompute.compute(Map.of("input_radius", Double.NaN));
        assertEquals(Boolean.FALSE, outputs.get("output_valid"));
        assertNull(outputs.get("output_sphere"));

        SphereByCenterRadiusNode wired = new SphereByCenterRadiusNode();
        connectAndDrive(wired, "input_radius", NodeDataType.DOUBLE, -1.0d);
        wired.processNode(null);
        assertEquals(Boolean.FALSE, wired.getOutput("output_valid"));
        assertNull(wired.getOutput("output_sphere"));
    }

    @Test
    void sphereNodeIllegalCenterFailsClosedForSetInputAndCompute() {
        SphereByCenterRadiusNode viaSet = new SphereByCenterRadiusNode();
        viaSet.setInput("input_center", new Vector3d(1, 2, 3));
        viaSet.processNode(null);
        assertEquals(Boolean.FALSE, viaSet.getOutput("output_valid"));
        assertTrue(String.valueOf(viaSet.getOutput("output_error")).contains("invalid"));

        SphereByCenterRadiusNode viaCompute = new SphereByCenterRadiusNode();
        Map<String, Object> outputs = viaCompute.compute(Map.of("input_center", "bad"));
        assertEquals(Boolean.FALSE, outputs.get("output_valid"));
        assertNull(outputs.get("output_sphere"));
    }

    private static Double resolveDoubleViaWire(Object value) {
        StubNode node = new StubNode(NodeDataType.DOUBLE);
        connectAndDrive(node, PORT, NodeDataType.DOUBLE, value);
        return OptionalPortDrive.resolveOptionalDouble(node, PORT, PROPERTY_DOUBLE);
    }

    private static Double resolveDoubleViaSetInput(Object value) {
        StubNode node = new StubNode(NodeDataType.DOUBLE);
        node.setInput(PORT, value);
        return OptionalPortDrive.resolveOptionalDouble(node, PORT, PROPERTY_DOUBLE);
    }

    private static Double resolveDoubleViaCompute(Object value) {
        StubNode node = new StubNode(NodeDataType.DOUBLE);
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(PORT, value);
        node.compute(inputs);
        return OptionalPortDrive.resolveOptionalDouble(node, PORT, PROPERTY_DOUBLE);
    }

    private static Integer resolveIntegerViaWire(Object value) {
        StubNode node = new StubNode(NodeDataType.INTEGER);
        connectAndDrive(node, PORT, NodeDataType.INTEGER, value);
        return OptionalPortDrive.resolveOptionalInteger(node, PORT, PROPERTY_INT);
    }

    private static Integer resolveIntegerViaSetInput(Object value) {
        StubNode node = new StubNode(NodeDataType.INTEGER);
        node.setInput(PORT, value);
        return OptionalPortDrive.resolveOptionalInteger(node, PORT, PROPERTY_INT);
    }

    private static Integer resolveIntegerViaCompute(Object value) {
        StubNode node = new StubNode(NodeDataType.INTEGER);
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(PORT, value);
        node.compute(inputs);
        return OptionalPortDrive.resolveOptionalInteger(node, PORT, PROPERTY_INT);
    }

    private static Vector3d resolvePointViaWire(Object value) {
        StubNode node = new StubNode(NodeDataType.POINT);
        connectAndDrive(node, PORT, NodeDataType.POINT, value);
        return OptionalPortDrive.resolveOptionalPoint(node, PORT, PROPERTY_POINT);
    }

    private static Vector3d resolvePointViaSetInput(Object value) {
        StubNode node = new StubNode(NodeDataType.POINT);
        node.setInput(PORT, value);
        return OptionalPortDrive.resolveOptionalPoint(node, PORT, PROPERTY_POINT);
    }

    private static Vector3d resolvePointViaCompute(Object value) {
        StubNode node = new StubNode(NodeDataType.POINT);
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(PORT, value);
        node.compute(inputs);
        return OptionalPortDrive.resolveOptionalPoint(node, PORT, PROPERTY_POINT);
    }

    private static Vector3d resolveVectorViaWire(Object value) {
        StubNode node = new StubNode(NodeDataType.VECTOR);
        connectAndDrive(node, PORT, NodeDataType.VECTOR, value);
        return OptionalPortDrive.resolveOptionalVector(node, PORT, PROPERTY_VECTOR);
    }

    private static Vector3d resolveVectorViaSetInput(Object value) {
        StubNode node = new StubNode(NodeDataType.VECTOR);
        node.setInput(PORT, value);
        return OptionalPortDrive.resolveOptionalVector(node, PORT, PROPERTY_VECTOR);
    }

    private static Vector3d resolveVectorViaCompute(Object value) {
        StubNode node = new StubNode(NodeDataType.VECTOR);
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(PORT, value);
        node.compute(inputs);
        return OptionalPortDrive.resolveOptionalVector(node, PORT, PROPERTY_VECTOR);
    }

    private static void connectAndDrive(BaseNode target, String inputPortId, NodeDataType type, Object value) {
        StubNode source = new StubNode(type);
        BasePort output = (BasePort) source.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
        target.setInput(inputPortId, value);
    }

    private static final class StubNode extends BaseNode {
        StubNode(NodeDataType inputType) {
            super(UUID.randomUUID(), "test.optional_port_drive_parity");
            addInputPort(new BasePort(PORT, "Value", "", inputType, this));
            addOutputPort(new BasePort("output_value", "Value", "", inputType, this));
        }

        @Override
        public void processNode(com.nodecraft.nodesystem.execution.ExecutionContext context) {
        }
    }
}

package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.nodes.math.compare.EqualsNode;
import com.nodecraft.nodesystem.nodes.math.compare.GreaterThanNode;
import com.nodecraft.nodesystem.nodes.math.compare.LessThanNode;
import com.nodecraft.nodesystem.nodes.math.compare.NotEqualsNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.AbsoluteNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.DivisionNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.PowerNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.ArcTanNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.Atan2Node;
import com.nodecraft.nodesystem.nodes.math.trigonometry.CosineNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.SineNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.TangentNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freeze fence for Batch 10 math language: degrees angles + numeric DOUBLE ports.
 */
class MathLanguageContractTest {

    private static final Set<String> ANGLE_PORT_IDS = Set.of("input_angle", "output_angle");

    @Test
    void sineCosTanUseDegreesDoublePorts() {
        assertAngleInputIsDegreesDouble(new SineNode());
        assertAngleInputIsDegreesDouble(new CosineNode());
        assertAngleInputIsDegreesDouble(new TangentNode());
    }

    @Test
    void inverseTrigOutputsDegreesDouble() {
        assertAngleOutputIsDegreesDouble(new ArcTanNode());
        assertAngleOutputIsDegreesDouble(new Atan2Node());
    }

    @Test
    void noLegacyRadiansPortIdsRemainOnCoreTrig() {
        for (INode node : new INode[] {
            new SineNode(), new CosineNode(), new TangentNode(),
            new ArcTanNode(), new Atan2Node()
        }) {
            for (IPort port : node.getInputPorts()) {
                assertFalse(port.getId().contains("rad"), node.getTypeId() + " input " + port.getId());
            }
            for (IPort port : node.getOutputPorts()) {
                assertFalse(port.getId().endsWith("_rad") || port.getId().contains("angle_rad"),
                        node.getTypeId() + " output " + port.getId());
            }
        }
    }

    @Test
    void scalarNumericNodesRejectAnyPorts() {
        assertNoAnyPorts(new AbsoluteNode());
        assertNoAnyPorts(new PowerNode());
        assertNoAnyPorts(new DivisionNode());
    }

    @Test
    void numericCompareUsesDouble_equalityKeepsAny() {
        assertNoAnyPorts(new LessThanNode());
        assertNoAnyPorts(new GreaterThanNode());
        assertTrue(hasAnyInput(new EqualsNode()));
        assertTrue(hasAnyInput(new NotEqualsNode()));
    }

    private static void assertAngleInputIsDegreesDouble(INode node) {
        IPort angle = findPort(node.getInputPorts(), "input_angle");
        assertEquals(NodeDataType.DOUBLE, angle.getDataType(), node.getTypeId());
        assertFalse(angle.getDisplayName().toLowerCase().contains("rad"), angle.getDisplayName());
    }

    private static void assertAngleOutputIsDegreesDouble(INode node) {
        IPort angle = findPort(node.getOutputPorts(), "output_angle");
        assertEquals(NodeDataType.DOUBLE, angle.getDataType(), node.getTypeId());
        assertFalse(angle.getDisplayName().toLowerCase().contains("rad"), angle.getDisplayName());
        assertTrue(ANGLE_PORT_IDS.contains(angle.getId()));
    }

    private static void assertNoAnyPorts(INode node) {
        for (IPort port : node.getInputPorts()) {
            assertFalse(port.getDataType() == NodeDataType.ANY,
                    node.getTypeId() + " input " + port.getId() + " must not be ANY");
        }
    }

    private static boolean hasAnyInput(INode node) {
        for (IPort port : node.getInputPorts()) {
            if (port.getDataType() == NodeDataType.ANY) {
                return true;
            }
        }
        return false;
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

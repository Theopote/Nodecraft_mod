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
import com.nodecraft.nodesystem.nodes.math.trigonometry.ArcCosNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.ArcSinNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.ArcTanNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.Atan2Node;
import com.nodecraft.nodesystem.nodes.math.trigonometry.CosineNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.SineNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.TangentNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freeze fence for Batch 10 math language: degrees angles + numeric DOUBLE ports.
 */
class MathLanguageContractTest {

    private static final Set<String> ANGLE_PORT_IDS = Set.of("input_angle", "output_angle");

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void sineCosTanUseDegreesDoublePorts() {
        assertAngleInputIsDegreesDouble(new SineNode());
        assertAngleInputIsDegreesDouble(new CosineNode());
        assertAngleInputIsDegreesDouble(new TangentNode());
    }

    @Test
    void inverseTrigOutputsDegreesDouble() {
        assertAngleOutputIsDegreesDouble(new ArcSinNode());
        assertAngleOutputIsDegreesDouble(new ArcCosNode());
        assertAngleOutputIsDegreesDouble(new ArcTanNode());
        assertAngleOutputIsDegreesDouble(new Atan2Node());
    }

    @Test
    void allTrigonometryGraphFacingAnglePortsForbidRadSuffix() {
        List<String> violations = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            if (!nodeId.toLowerCase(Locale.ROOT).startsWith("math.trigonometry.")) {
                continue;
            }
            INode instance = tryCreate(nodeId);
            if (instance == null) {
                continue;
            }
            for (IPort port : instance.getInputPorts()) {
                if (port.getId().toLowerCase(Locale.ROOT).contains("rad")
                        && port.getId().toLowerCase(Locale.ROOT).contains("angle")) {
                    violations.add(nodeId + "#" + port.getId());
                }
            }
            for (IPort port : instance.getOutputPorts()) {
                String id = port.getId().toLowerCase(Locale.ROOT);
                if (id.contains("angle_rad") || id.endsWith("_rad") && id.contains("angle")) {
                    violations.add(nodeId + "#" + port.getId());
                }
            }
        }
        assertTrue(violations.isEmpty(), "Trig angle ports must not use radians ids: " + violations);
    }

    @Test
    void allScalarMathNodesForbidAnyPorts() {
        List<String> violations = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            if (!nodeId.toLowerCase(Locale.ROOT).startsWith("math.scalar_math.")) {
                continue;
            }
            INode instance = tryCreate(nodeId);
            if (instance == null) {
                continue;
            }
            for (IPort port : instance.getInputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    violations.add(nodeId + "#" + port.getId());
                }
            }
            for (IPort port : instance.getOutputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    violations.add(nodeId + "#" + port.getId());
                }
            }
        }
        assertTrue(violations.isEmpty(), "Scalar math must not expose ANY: " + violations);
    }

    @Test
    void inputAngleNodesForbidRadiansOutput() {
        List<String> violations = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            if (!nodeId.startsWith("input.numeric.")) {
                continue;
            }
            INode instance = tryCreate(nodeId);
            if (instance == null) {
                continue;
            }
            for (IPort port : instance.getOutputPorts()) {
                String id = port.getId().toLowerCase(Locale.ROOT);
                if (id.contains("radian") || id.endsWith("_rad")) {
                    violations.add(nodeId + "#" + port.getId());
                }
            }
        }
        assertTrue(violations.isEmpty(), "Input angle nodes must not expose radians: " + violations);
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
        assertFalse(angle.getDisplayName().toLowerCase(Locale.ROOT).contains("rad"), angle.getDisplayName());
    }

    private static void assertAngleOutputIsDegreesDouble(INode node) {
        IPort angle = findPort(node.getOutputPorts(), "output_angle");
        assertEquals(NodeDataType.DOUBLE, angle.getDataType(), node.getTypeId());
        assertFalse(angle.getDisplayName().toLowerCase(Locale.ROOT).contains("rad"), angle.getDisplayName());
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

    private static INode tryCreate(String nodeId) {
        try {
            return registry.createNodeInstance(nodeId);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }
}

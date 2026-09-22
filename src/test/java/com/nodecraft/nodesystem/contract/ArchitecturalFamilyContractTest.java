package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.FloorSlabWithBeamsNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RailingNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofGeneratorNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.StaircaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallWithOpeningsNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowArrayNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 13 freeze: Architectural Components language (Wall / Floor / Roof / Stair / Window Array).
 */
class ArchitecturalFamilyContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void coreFiveUseTypedFootprintAndPathPorts() {
        assertPortType(new WallWithOpeningsNode(), "input_face", NodeDataType.BOX_FACE);
        assertPortType(new FloorSlabWithBeamsNode(), "input_face", NodeDataType.BOX_FACE);
        assertPortType(new RoofGeneratorNode(), "input_face", NodeDataType.BOX_FACE);
        assertPortType(new WindowArrayNode(), "input_face", NodeDataType.BOX_FACE);
        assertPortType(new StaircaseNode(), "input_path", NodeDataType.PATH);
        assertPortType(new RailingNode(), "input_path", NodeDataType.PATH);
    }

    @Test
    void coreFiveEmitGeometryAndValid() {
        assertPortType(new WallWithOpeningsNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(new FloorSlabWithBeamsNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(new RoofGeneratorNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(new WindowArrayNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(new StaircaseNode(), "output_geometry", NodeDataType.GEOMETRY);
        assertPortType(new WallWithOpeningsNode(), "output_valid", NodeDataType.BOOLEAN);
        assertPortType(new FloorSlabWithBeamsNode(), "output_valid", NodeDataType.BOOLEAN);
        assertPortType(new RoofGeneratorNode(), "output_valid", NodeDataType.BOOLEAN);
        assertPortType(new WindowArrayNode(), "output_valid", NodeDataType.BOOLEAN);
        assertPortType(new StaircaseNode(), "output_valid", NodeDataType.BOOLEAN);
    }

    @Test
    void staircaseSpiralStartAngleIsDegreesDouble() {
        IPort angle = findPort(new StaircaseNode(), "input_spiral_start_angle");
        assertEquals(NodeDataType.DOUBLE, angle.getDataType());
        assertTrue(angle.getDescription().toLowerCase(Locale.ROOT).contains("degrees"));
    }

    @Test
    void architecturalFamilyForbidsAnyAndLegacyLinePorts() {
        List<String> violations = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            if (!nodeId.toLowerCase(Locale.ROOT).startsWith("geometry.architectural_primitives.")) {
                continue;
            }
            INode instance = tryCreate(nodeId);
            if (instance == null) {
                continue;
            }
            for (IPort port : allPorts(instance)) {
                if (port.getDataType() == NodeDataType.ANY) {
                    violations.add(nodeId + "#" + port.getId() + "=ANY");
                }
                String id = port.getId().toLowerCase(Locale.ROOT);
                if ("input_line".equals(id) || port.getDataType() == NodeDataType.LINE) {
                    violations.add(nodeId + "#" + port.getId() + "=LINE");
                }
            }
        }
        assertTrue(violations.isEmpty(), "Architectural language violations: " + violations);
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

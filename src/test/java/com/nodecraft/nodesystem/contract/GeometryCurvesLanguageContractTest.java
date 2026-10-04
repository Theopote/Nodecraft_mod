package com.nodecraft.nodesystem.contract;

import com.nodecraft.core.exception.NodeValidationException;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.geometry.curves.CurveEvaluateNode;
import com.nodecraft.nodesystem.nodes.geometry.curves.SplitPathNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import net.minecraft.util.math.Vec3d;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Geometry Curves / PATH v2 (Graph V71).
 */
class GeometryCurvesLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void graphFormatIncludesV71CurvesLanguage() {
        assertEquals(1, GraphFormatVersion.CURRENT);
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void exactlyTwentyEightCurveNodesWithUniqueOrdersZeroToTwentySeven() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("geometry.curves."))
            .filter(id -> !id.contains("frame_along_path"))
            .sorted()
            .toList();
        assertEquals(28, ids.size());

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("geometry.curves", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        assertEquals(28, orders.size());
        for (int i = 0; i < 28; i++) {
            assertTrue(orders.contains(i));
        }
    }

    @Test
    void allCurveNodesHaveValidAndErrorWithoutRawListPorts() {
        List<String> errors = new ArrayList<>();
        for (String id : registry.getAllNodeIds().stream()
            .filter(s -> s.startsWith("geometry.curves."))
            .filter(s -> !s.contains("frame_along_path"))
            .toList()) {
            INode node = registry.createNodeInstance(id);
            if (!hasPort(node, "output_valid")) {
                errors.add(id + " missing output_valid");
            }
            if (!hasPort(node, "output_error")) {
                errors.add(id + " missing output_error");
            }
            for (IPort port : node.getInputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    errors.add(id + "." + port.getId() + " input is ANY");
                }
                if (port.getDataType() == NodeDataType.LIST) {
                    errors.add(id + "." + port.getId() + " uses raw LIST");
                }
            }
            for (IPort port : node.getOutputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    errors.add(id + "." + port.getId() + " output is ANY");
                }
                if (port.getDataType() == NodeDataType.LIST) {
                    errors.add(id + "." + port.getId() + " uses raw LIST");
                }
                String pid = port.getId().toLowerCase(Locale.ROOT);
                if (pid.equals("output_curve") || pid.equals("output_polyline") || pid.equals("output_line")) {
                    errors.add(id + " still exposes mirror port " + port.getId());
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void bezierPrimaryOutputIsPath() {
        assertPortType("geometry.curves.bezier", "output_path", false, NodeDataType.PATH);
        assertFalse(hasPort(registry.createNodeInstance("geometry.curves.bezier"), "output_curve"));
        assertFalse(hasPort(registry.createNodeInstance("geometry.curves.bezier"), "output_polyline"));
    }

    @Test
    void splitPathRejectsEndpointParameters() {
        SplitPathNode split = new SplitPathNode();
        connectInput(split, "input_path", NodeDataType.PATH);
        connectInput(split, "input_parameter", NodeDataType.DOUBLE);
        split.setInput("input_path", new LineData(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0)));
        split.setInput("input_parameter", 0.0d);
        split.processNode(null);
        assertEquals(Boolean.FALSE, split.getOutput("output_valid"));

        split.setInput("input_parameter", 1.0d);
        split.processNode(null);
        assertEquals(Boolean.FALSE, split.getOutput("output_valid"));

        split.setInput("input_parameter", 0.5d);
        split.processNode(null);
        assertEquals(Boolean.TRUE, split.getOutput("output_valid"));
    }

    @Test
    void frameAlongPathIsRetiredInFavorOfPathFrames() {
        assertTrue(registry.getAllNodeIds().stream().noneMatch(id -> id.contains("frame_along_path")));
        assertThrows(NodeValidationException.class,
            () -> registry.createNodeInstance("geometry.curves.frame_along_path"));
    }

    @Test
    void evaluateClosedPathAtSeamUsesWrappedTangent() {
        CurveEvaluateNode evaluate = new CurveEvaluateNode();
        connectInput(evaluate, "input_path", NodeDataType.PATH);
        connectInput(evaluate, "input_t", NodeDataType.DOUBLE);
        evaluate.setInput("input_path", new PolylineData(List.of(
            new Vec3d(0, 0, 0),
            new Vec3d(10, 0, 0),
            new Vec3d(10, 0, 10),
            new Vec3d(0, 0, 0)
        )));
        evaluate.setInput("input_t", 0.0d);
        evaluate.processNode(null);
        assertEquals(Boolean.TRUE, evaluate.getOutput("output_valid"), String.valueOf(evaluate.getOutput("output_error")));
        assertInstanceOf(VectorData.class, evaluate.getOutput("output_tangent"));
        VectorData tangent = (VectorData) evaluate.getOutput("output_tangent");
        assertTrue(Double.isFinite(tangent.x()) && Double.isFinite(tangent.y()) && Double.isFinite(tangent.z()));
    }

    @Test
    void evaluatePathRejectsOpenPathOutOfRangeT() {
        CurveEvaluateNode evaluate = new CurveEvaluateNode();
        connectInput(evaluate, "input_path", NodeDataType.PATH);
        connectInput(evaluate, "input_t", NodeDataType.DOUBLE);
        evaluate.setInput("input_path", new LineData(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0)));
        evaluate.setInput("input_t", 1.2d);
        evaluate.processNode(null);
        assertEquals(Boolean.FALSE, evaluate.getOutput("output_valid"));
        assertNull(evaluate.getOutput("output_point"));
    }

    @Test
    void closestPointInvalidDoesNotEmitNaN() {
        BaseNode closest = (BaseNode) registry.createNodeInstance("geometry.curves.closest_point_on_path");
        closest.processNode(null);
        assertEquals(Boolean.FALSE, closest.getOutput("output_valid"));
        Object distance = closest.getOutput("output_distance");
        assertTrue(distance == null || (distance instanceof Double d && !d.isNaN()));
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
        target.getInput(inputPortId);
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        IPort port = findPort(node, portId, input);
        assertNotNull(port, typeId + " missing " + portId);
        assertEquals(expected, port.getDataType());
    }

    private static boolean hasPort(INode node, String portId) {
        return findPort(node, portId, true) != null || findPort(node, portId, false) != null;
    }

    private static IPort findPort(INode node, String portId, boolean input) {
        List<IPort> ports = input ? node.getInputPorts() : node.getOutputPorts();
        return ports.stream().filter(p -> portId.equals(p.getId())).findFirst().orElse(null);
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

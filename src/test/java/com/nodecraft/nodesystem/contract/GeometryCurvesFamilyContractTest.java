package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freeze fence for Batch 3 curves language: PATH inputs, linear sample hygiene, Arc numeric fallbacks.
 */
class GeometryCurvesFamilyContractTest {

    private static final Set<String> PATH_CONSUMER_IDS = Set.of(
            "geometry.curves.evaluate_curve",
            "geometry.curves.rebuild_curve_length",
            "geometry.curves.frame_along_path",
            "geometry.curves.offset_curve_plane",
            "geometry.curves.divide_curve_to_points",
            "geometry.curves.voxelize_curve",
            "geometry.curves.rainbow_curve_offset",
            "geometry.curves.tween_curves",
            "geometry.curves.blend_curves",
            "geometry.curves.resample_polyline_length",
            "geometry.curves.polyline_length",
            "geometry.curves.offset_polyline_plane"
    );

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void linePolylineAndCurveConnectImplicitlyToPath() {
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.LINE, NodeDataType.PATH));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.POLYLINE, NodeDataType.PATH));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.CURVE, NodeDataType.PATH));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.POINT, NodeDataType.PATH));
        assertFalse(NodeDataType.isConnectableTo(NodeDataType.VECTOR, NodeDataType.PATH));
        assertEquals(TypeConversionRegistry.ConversionPolicy.IMPLICIT_SAFE,
            TypeConversionRegistry.classify(NodeDataType.CURVE, NodeDataType.PATH));
    }

    @Test
    void pathConsumersUseSinglePathInput() {
        List<String> errors = new ArrayList<>();
        for (String typeId : PATH_CONSUMER_IDS) {
            INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
            if (node == null) {
                errors.add("missing instance: " + typeId);
                continue;
            }
            long pathInputs = node.getInputPorts().stream()
                .filter(port -> port.getDataType() == NodeDataType.PATH)
                .count();
            if (pathInputs < 1) {
                errors.add(typeId + " missing PATH input");
            }
            for (IPort port : node.getInputPorts()) {
                String id = port.getId();
                if (id.equals("input_curve") || id.equals("input_polyline") || id.equals("input_line")
                        || id.equals("input_curve_a") || id.equals("input_polyline_a") || id.equals("input_line_a")
                        || id.equals("input_curve_b") || id.equals("input_polyline_b") || id.equals("input_line_b")) {
                    errors.add(typeId + " still exposes legacy triple path port " + id);
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void curveEvaluateIsContractSampleForPathOutputs() {
        assertPortType("geometry.curves.evaluate_curve", "input_path", true, NodeDataType.PATH);
        assertPortType("geometry.curves.evaluate_curve", "output_point", false, NodeDataType.POINT);
        assertPortType("geometry.curves.evaluate_curve", "output_tangent", false, NodeDataType.VECTOR);
        assertPortType("geometry.curves.evaluate_curve", "output_normal", false, NodeDataType.VECTOR);
        assertPortType("geometry.curves.evaluate_curve", "output_binormal", false, NodeDataType.VECTOR);
    }

    @Test
    void arcDefaultsUseNumericCenterAndXzPlane() {
        BaseNode arc = (BaseNode) NodeRegistry.getInstance().createNodeInstance("geometry.curves.arc");
        assertInstanceOf(BaseNode.class, arc);
        arc.processNode(null);
        assertEquals(Boolean.TRUE, arc.getOutput("output_valid"));
        assertInstanceOf(PointData.class, ((List<?>) arc.getOutput("output_points")).getFirst());
    }

    @Test
    void pathToPointsExtractsLineWithoutDuplicateVertices() {
        BaseNode node = (BaseNode) NodeRegistry.getInstance().createNodeInstance("geometry.curves.divide_curve_to_points");
        LineData line = new LineData(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0));
        node.setInput("input_path", line);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(2, node.getOutput("output_count"));
    }

    @Test
    void pathToPointsExtractsLinearCurveWithoutDuplicateVertices() {
        BaseNode node = (BaseNode) NodeRegistry.getInstance().createNodeInstance("geometry.curves.divide_curve_to_points");
        Curve curve = new Curve(Curve.CurveType.LINEAR, 2);
        curve.addControlPoint(new Vec3d(0, 0, 0));
        curve.addControlPoint(new Vec3d(1, 0, 0));
        curve.addControlPoint(new Vec3d(2, 0, 0));
        node.setInput("input_path", curve);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(3, node.getOutput("output_count"));
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
        assertInstanceOf(INode.class, node);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
            .filter(candidate -> candidate.getId().equals(portId))
            .findFirst()
            .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }
}

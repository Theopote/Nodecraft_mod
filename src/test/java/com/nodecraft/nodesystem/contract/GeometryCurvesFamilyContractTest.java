package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freeze fence for Batch 3 curves language: PATH inputs, linear sample hygiene, Arc numeric fallbacks.
 */
class GeometryCurvesFamilyContractTest {

    /** Canonical path-operation chain nodes from Batch 3 acceptance scope. */
    private static final Set<String> CANONICAL_CHAIN_IDS = Set.of(
            "geometry.curves.rebuild_curve_length",
            "geometry.curves.evaluate_curve",
            "geometry.curves.frame_along_path",
            "geometry.curves.offset_curve_plane",
            "geometry.curves.tween_curves",
            "geometry.curves.fillet_polyline_corners",
            "geometry.curves.blend_curves",
            "geometry.curves.rainbow_curve_offset",
            "geometry.curves.voxelize_curve"
    );

    private static final Set<String> PATH_CONSUMER_IDS = Set.of(
            "geometry.curves.evaluate_curve",
            "geometry.curves.rebuild_curve_length",
            "geometry.curves.frame_along_path",
            "geometry.curves.offset_curve_plane",
            "geometry.curves.path_to_points",
            "geometry.curves.voxelize_curve",
            "geometry.curves.rainbow_curve_offset",
            "geometry.curves.tween_curves",
            "geometry.curves.blend_curves",
            "geometry.curves.resample_polyline_length",
            "geometry.curves.polyline_length",
            "geometry.curves.offset_polyline_plane",
            "geometry.curves.fillet_polyline_corners",
            "pattern.linear.curve_array_geometry",
            "geometry.architectural_primitives.array_along_curve",
            "transform.orientation.project_curve_to_plane",
            "reference.points.project_to_polyline",
            "reference.points.closest_point_to_object",
            "output.preview.preview_curves",
            "math.fields.curve_attractor_field",
            "transform.deformations.curve_attract"
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
    void pointsToPathEmitsPathOutput() {
        BaseNode node = node("geometry.curves.points_to_path");
        node.setInput("input_points", SpatialValueResolver.toPointDataList(List.of(
            new org.joml.Vector3d(0, 64, 0),
            new org.joml.Vector3d(10, 64, 0),
            new org.joml.Vector3d(10, 64, 10)
        )));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertInstanceOf(PathData.class, node.getOutput("output_path"));
        assertInstanceOf(PolylineData.class, node.getOutput("output_polyline"));
    }

    @Test
    void sweepAndAlongPathUseSinglePathInput() {
        assertPortType("geometry.solids.sweep", "input_path", true, NodeDataType.PATH);
        assertPortType("geometry.solids.sweep_from_points", "input_path", true, NodeDataType.PATH);
        assertFalse(hasInputPort("geometry.solids.sweep", "input_path_points"));
        assertFalse(hasInputPort("geometry.solids.sweep_from_points", "input_path_points"));
        assertPortType("pattern.linear.along_path", "input_path", true, NodeDataType.PATH);
        assertPortType("pattern.linear.path_instances", "input_path", true, NodeDataType.PATH);
        assertPortType("pattern.linear.curve_array_geometry", "input_path", true, NodeDataType.PATH);
        assertPortType("geometry.architectural_primitives.array_along_curve", "input_path", true, NodeDataType.PATH);
        assertPortType("transform.orientation.project_curve_to_plane", "input_path", true, NodeDataType.PATH);
        assertPortType("reference.points.project_to_polyline", "input_path", true, NodeDataType.PATH);
        assertPortType("reference.points.closest_point_to_object", "input_path", true, NodeDataType.PATH);
        assertPortType("output.preview.preview_curves", "input_path", true, NodeDataType.PATH);
        assertPortType("math.fields.curve_attractor_field", "input_path", true, NodeDataType.PATH);
        assertPortType("transform.deformations.curve_attract", "input_path", true, NodeDataType.PATH);
        assertPortType("geometry.curves.fillet_polyline_corners", "input_path", true, NodeDataType.PATH);
    }

    @Test
    void blendCurvesStartAndEndOutputsArePoints() {
        assertPortType("geometry.curves.blend_curves", "output_start_point", false, NodeDataType.POINT);
        assertPortType("geometry.curves.blend_curves", "output_end_point", false, NodeDataType.POINT);
    }

    @Test
    void pathToPointsExtractsLineWithoutDuplicateVertices() {
        BaseNode node = (BaseNode) NodeRegistry.getInstance().createNodeInstance("geometry.curves.path_to_points");
        LineData line = new LineData(new Vec3d(0, 0, 0), new Vec3d(10, 0, 0));
        node.setInput("input_path", line);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(2, node.getOutput("output_count"));
    }

    @Test
    void canonicalChainNodesAvoidSpatialAny() {
        List<String> errors = new ArrayList<>();
        for (String typeId : CANONICAL_CHAIN_IDS) {
            INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
            if (node == null) {
                errors.add("missing instance: " + typeId);
                continue;
            }
            for (IPort port : node.getInputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    errors.add(typeId + "." + port.getId() + " is ANY");
                }
            }
            for (IPort port : node.getOutputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    errors.add(typeId + "." + port.getId() + " output is ANY");
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void rebuildAndFrameDefaultSpacingProduceValidOutput() {
        LineData line = sampleLine10Blocks();

        BaseNode rebuild = node("geometry.curves.rebuild_curve_length");
        rebuild.setInput("input_path", line);
        rebuild.processNode(null);
        assertEquals(Boolean.TRUE, rebuild.getOutput("output_valid"));
        assertTrue(((List<?>) rebuild.getOutput("output_points")).size() >= 2);

        BaseNode frame = node("geometry.curves.frame_along_path");
        frame.setInput("input_path", line);
        frame.processNode(null);
        assertEquals(Boolean.TRUE, frame.getOutput("output_valid"));
        assertTrue((Integer) frame.getOutput("output_count") >= 2);
    }

    @Test
    void helixDefaultsProduceValidSampledPath() {
        BaseNode helix = node("geometry.curves.helix");
        helix.processNode(null);
        assertEquals(Boolean.TRUE, helix.getOutput("output_valid"));
        assertInstanceOf(Curve.class, helix.getOutput("output_curve"));
        assertInstanceOf(PolylineData.class, helix.getOutput("output_polyline"));
        assertTrue(((List<?>) helix.getOutput("output_points")).size() >= 2);
        assertTrue(((Number) helix.getOutput("output_length")).doubleValue() > 0.0d);
    }

    @Test
    void canonicalChainSmokeWithMinimalInputs() {
        LineData line = sampleLine10Blocks();
        LineData lineB = new LineData(new Vec3d(0, 0, 10), new Vec3d(10, 0, 10));

        assertValidWithPath("geometry.curves.evaluate_curve", line);
        assertValidWithPath("geometry.curves.rebuild_curve_length", line);
        assertValidWithPath("geometry.curves.frame_along_path", line);
        assertValidWithPath("geometry.curves.rainbow_curve_offset", line);
        assertValidWithPath("geometry.curves.voxelize_curve", line);

        BaseNode offset = node("geometry.curves.offset_curve_plane");
        offset.setInput("input_path", line);
        offset.setInput("input_plane", PlaneData.XZ_PLANE);
        offset.setInput("input_offset", 1.0d);
        offset.processNode(null);
        assertEquals(Boolean.TRUE, offset.getOutput("output_valid"));

        BaseNode tween = node("geometry.curves.tween_curves");
        tween.setInput("input_path_a", line);
        tween.setInput("input_path_b", lineB);
        tween.processNode(null);
        assertEquals(Boolean.TRUE, tween.getOutput("output_valid"));

        BaseNode blend = node("geometry.curves.blend_curves");
        blend.setInput("input_path_a", line);
        blend.setInput("input_path_b", lineB);
        blend.processNode(null);
        assertEquals(Boolean.TRUE, blend.getOutput("output_valid"));

        PolylineData elbow = new PolylineData(List.of(
                new Vec3d(0, 64, 0),
                new Vec3d(10, 64, 0),
                new Vec3d(10, 64, 10)));
        BaseNode fillet = node("geometry.curves.fillet_polyline_corners");
        fillet.setInput("input_path", elbow);
        fillet.setInput("input_plane", PlaneData.XZ_PLANE);
        fillet.setInput("input_radius", 1.0d);
        fillet.processNode(null);
        assertEquals(Boolean.TRUE, fillet.getOutput("output_valid"));
        assertInstanceOf(PolylineData.class, fillet.getOutput("output_polyline"));
    }

    @Test
    void pathToPointsExtractsLinearCurveWithoutDuplicateVertices() {
        BaseNode node = (BaseNode) NodeRegistry.getInstance().createNodeInstance("geometry.curves.path_to_points");
        Curve curve = new Curve(Curve.CurveType.LINEAR, 2);
        curve.addControlPoint(new Vec3d(0, 0, 0));
        curve.addControlPoint(new Vec3d(1, 0, 0));
        curve.addControlPoint(new Vec3d(2, 0, 0));
        node.setInput("input_path", curve);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(3, node.getOutput("output_count"));
    }

    private static LineData sampleLine10Blocks() {
        return new LineData(new Vec3d(0, 64, 0), new Vec3d(10, 64, 0));
    }

    private static BaseNode node(String typeId) {
        BaseNode node = (BaseNode) NodeRegistry.getInstance().createNodeInstance(typeId);
        assertNotNull(node, typeId);
        return node;
    }

    private static void assertValidWithPath(String typeId, LineData line) {
        BaseNode node = node(typeId);
        node.setInput("input_path", line);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"), typeId + " should be valid with line path");
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

    private static boolean hasInputPort(String typeId, String portId) {
        INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
        assertInstanceOf(INode.class, node);
        return node.getInputPorts().stream().anyMatch(port -> port.getId().equals(portId));
    }
}

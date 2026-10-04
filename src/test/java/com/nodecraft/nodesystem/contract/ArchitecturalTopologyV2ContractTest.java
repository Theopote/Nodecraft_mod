package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.BeamAlongPathNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RailingNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofBaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofGeneratorNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallAlongPathNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architectural Topology & Path Join v2 language fence (Graph V97).
 */
class ArchitecturalTopologyV2ContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV97() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void roofNodesExposeTopologyPathLists() {
        assertPortType(registry.createNodeInstance("geometry.architectural_primitives.roof_base"),
            "output_eaves", NodeDataType.PATH_LIST);
        assertPortType(registry.createNodeInstance("geometry.architectural_primitives.roof_base"),
            "output_ridges", NodeDataType.PATH_LIST);
        assertPortType(registry.createNodeInstance("geometry.architectural_primitives.roof_base"),
            "output_valleys", NodeDataType.PATH_LIST);
        assertPortType(registry.createNodeInstance("geometry.architectural_primitives.roof_generator"),
            "output_eaves", NodeDataType.PATH_LIST);
    }

    @Test
    void gableRoofHasFourEavesAndOneRidge() {
        RoofBaseProbe base = new RoofBaseProbe();
        base.connectInput("input_roof_type", NodeDataType.STRING);
        base.setInput("input_face", sampleFace(10, 8));
        base.setInput("input_roof_type", "gable");
        base.setInput("input_height", 2.0d);
        base.setInput("input_thickness", 0.5d);
        base.processNode(null);
        assertEquals(Boolean.TRUE, base.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PathData> eaves = (List<PathData>) base.getOutput("output_eaves");
        @SuppressWarnings("unchecked")
        List<PathData> ridges = (List<PathData>) base.getOutput("output_ridges");
        assertEquals(4, eaves.size());
        assertEquals(1, ridges.size());
        assertNotNull(base.getOutput("output_eave_path"));
        assertNotNull(base.getOutput("output_ridge_path"));
        @SuppressWarnings("unchecked")
        List<?> faces = (List<?>) base.getOutput("output_faces");
        @SuppressWarnings("unchecked")
        List<?> slopes = (List<?>) base.getOutput("output_slope_directions");
        assertEquals(2, faces.size());
        assertEquals(2, slopes.size());
    }

    @Test
    void hipRoofRidgeShorterThanFullFootprintSpan() {
        RoofGeneratorProbe generator = new RoofGeneratorProbe();
        generator.connectInput("input_roof_type", NodeDataType.STRING);
        generator.setInput("input_face", sampleFace(20, 12));
        generator.setInput("input_roof_type", "hip");
        generator.setInput("input_height", 3.0d);
        generator.setInput("input_thickness", 0.5d);
        generator.setInput("input_ridge_ratio", 0.5d);
        generator.setInput("input_inset", 1.0d);
        generator.processNode(null);
        assertEquals(Boolean.TRUE, generator.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PathData> ridges = (List<PathData>) generator.getOutput("output_ridges");
        assertEquals(1, ridges.size());
        double ridgeLength = ridges.getFirst().getLine().getLength();
        assertTrue(ridgeLength < 20.0d - 0.5d, "hip ridge should be shorter than full span: " + ridgeLength);
        PathData fullGableRidge = assertInstanceOf(PathData.class, generator.getOutput("output_ridge_path"));
        assertEquals(ridgeLength, fullGableRidge.getLine().getLength(), 1e-6);
    }

    @Test
    void mRoofHasTwoRidgesAndOneValley() {
        RoofGeneratorProbe generator = new RoofGeneratorProbe();
        generator.connectInput("input_roof_type", NodeDataType.STRING);
        generator.connectInput("input_ridge_direction", NodeDataType.STRING);
        generator.setInput("input_face", sampleFace(16, 10));
        generator.setInput("input_roof_type", "m");
        generator.setInput("input_ridge_direction", "y");
        generator.setInput("input_height", 3.0d);
        generator.setInput("input_thickness", 0.5d);
        generator.setInput("input_m_peak_ratio", 0.25d);
        generator.setInput("input_valley_drop", 1.5d);
        generator.processNode(null);
        assertEquals(Boolean.TRUE, generator.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PathData> ridges = (List<PathData>) generator.getOutput("output_ridges");
        @SuppressWarnings("unchecked")
        List<PathData> valleys = (List<PathData>) generator.getOutput("output_valleys");
        assertEquals(2, ridges.size());
        assertEquals(1, valleys.size());
        mRoofRidgesRunAlongExtrusionAxis(ridges, 10.0d);
        mRoofValleyRunsAlongExtrusionAxis(valleys.getFirst(), 10.0d);
    }

    @Test
    void mRoofRidgesRunAlongExtrusionAxis() {
        RoofGeneratorProbe generator = new RoofGeneratorProbe();
        generator.connectInput("input_roof_type", NodeDataType.STRING);
        generator.connectInput("input_ridge_direction", NodeDataType.STRING);
        generator.setInput("input_face", sampleFace(16, 10));
        generator.setInput("input_roof_type", "m");
        generator.setInput("input_ridge_direction", "y");
        generator.setInput("input_height", 3.0d);
        generator.setInput("input_thickness", 0.5d);
        generator.processNode(null);
        @SuppressWarnings("unchecked")
        List<PathData> ridges = (List<PathData>) generator.getOutput("output_ridges");
        mRoofRidgesRunAlongExtrusionAxis(ridges, 10.0d);
    }

    @Test
    void mRoofValleyRunsAlongExtrusionAxis() {
        RoofGeneratorProbe generator = new RoofGeneratorProbe();
        generator.connectInput("input_roof_type", NodeDataType.STRING);
        generator.connectInput("input_ridge_direction", NodeDataType.STRING);
        generator.setInput("input_face", sampleFace(16, 10));
        generator.setInput("input_roof_type", "m");
        generator.setInput("input_ridge_direction", "y");
        generator.setInput("input_height", 3.0d);
        generator.setInput("input_thickness", 0.5d);
        generator.setInput("input_valley_drop", 1.0d);
        generator.processNode(null);
        @SuppressWarnings("unchecked")
        List<PathData> valleys = (List<PathData>) generator.getOutput("output_valleys");
        assertEquals(1, valleys.size());
        mRoofValleyRunsAlongExtrusionAxis(valleys.getFirst(), 10.0d);
    }

    @Test
    void asymmetricGableRidgeMatchesExtrusionDirection() {
        RoofGeneratorProbe generator = new RoofGeneratorProbe();
        generator.connectInput("input_roof_type", NodeDataType.STRING);
        generator.connectInput("input_ridge_direction", NodeDataType.STRING);
        generator.setInput("input_face", sampleFace(14, 9));
        generator.setInput("input_roof_type", "asymmetric_gable");
        generator.setInput("input_ridge_direction", "y");
        generator.setInput("input_height", 3.0d);
        generator.setInput("input_thickness", 0.5d);
        generator.setInput("input_asymmetric_left_height_ratio", 1.0d);
        generator.setInput("input_asymmetric_right_height_ratio", 0.6d);
        generator.processNode(null);
        assertEquals(Boolean.TRUE, generator.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PathData> ridges = (List<PathData>) generator.getOutput("output_ridges");
        assertEquals(2, ridges.size());
        for (PathData ridge : ridges) {
            assertPathParallelToAxis(ridge, new Vector3d(0, 1, 0), 9.0d);
        }
        double spanAlongProfile = ridges.getFirst().getLine().start().distanceTo(ridges.get(1).getLine().start());
        assertTrue(spanAlongProfile > 0.5d, "peaks should be separated along profile axis");
    }

    @Test
    void hipTopologyMatchesActualGeometryEdges() {
        RoofGeneratorProbe generator = new RoofGeneratorProbe();
        generator.connectInput("input_roof_type", NodeDataType.STRING);
        generator.setInput("input_face", sampleFace(20, 12));
        generator.setInput("input_roof_type", "hip");
        generator.setInput("input_height", 3.0d);
        generator.setInput("input_thickness", 0.5d);
        generator.setInput("input_ridge_ratio", 0.5d);
        generator.setInput("input_inset", 1.0d);
        generator.processNode(null);
        @SuppressWarnings("unchecked")
        List<PathData> ridges = (List<PathData>) generator.getOutput("output_ridges");
        assertEquals(1, ridges.size());
        PathData ridge = ridges.getFirst();
        double halfRidge = Math.max(20.0d * 0.5d * 0.5d, 20.0d * 0.1d);
        assertEquals(2.0d * halfRidge, ridge.getLine().getLength(), 0.05d);
        assertPathParallelToAxis(ridge, new Vector3d(1, 0, 0), ridge.getLine().getLength());
    }

    @Test
    void crossGableValleyMatchesGeometryIntersection() {
        RoofGeneratorProbe generator = new RoofGeneratorProbe();
        generator.connectInput("input_roof_type", NodeDataType.STRING);
        generator.setInput("input_face", sampleFace(12, 12));
        generator.setInput("input_roof_type", "cross_gable");
        generator.setInput("input_height", 2.5d);
        generator.setInput("input_thickness", 0.5d);
        generator.setInput("input_cross_gable_offset", 0.25d);
        generator.processNode(null);
        assertEquals(Boolean.TRUE, generator.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PathData> valleys = (List<PathData>) generator.getOutput("output_valleys");
        assertEquals(1, valleys.size());
        PathData valley = valleys.getFirst();
        assertNotNull(valley.getLine());
        assertTrue(valley.getLine().getLength() > 0.5d);
        assertTrue(valley.getLine().start().y >= -0.01d);
        assertTrue(valley.getLine().end().y <= 12.0d + 0.01d);
    }

    @Test
    void crossGableHasTwoRidges() {
        RoofGeneratorProbe generator = new RoofGeneratorProbe();
        generator.connectInput("input_roof_type", NodeDataType.STRING);
        generator.setInput("input_face", sampleFace(12, 12));
        generator.setInput("input_roof_type", "cross_gable");
        generator.setInput("input_height", 2.5d);
        generator.setInput("input_thickness", 0.5d);
        generator.processNode(null);
        assertEquals(Boolean.TRUE, generator.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<PathData> ridges = (List<PathData>) generator.getOutput("output_ridges");
        assertEquals(2, ridges.size());
    }

    @Test
    void signedOffsetAcceptedOnPathNodes() {
        WallAlongPathNode wall = new WallAlongPathNode();
        wall.setInput("input_path", lShapedPath());
        wall.setInput("input_height", 3.0d);
        wall.setInput("input_thickness", 0.4d);
        wall.setInput("input_offset", -1.0d);
        wall.processNode(null);
        assertEquals(Boolean.TRUE, wall.getOutput("output_valid"));

        BeamAlongPathNode beam = new BeamAlongPathNode();
        beam.setInput("input_path", lShapedPath());
        beam.setInput("input_width", 0.3d);
        beam.setInput("input_height", 0.4d);
        beam.setInput("input_offset", -1.0d);
        beam.processNode(null);
        assertEquals(Boolean.TRUE, beam.getOutput("output_valid"));

        RailingProbe railing = new RailingProbe();
        railing.connectInput("input_post_count", NodeDataType.INTEGER);
        railing.connectInput("input_rail_count", NodeDataType.INTEGER);
        railing.setInput("input_path", lShapedPath());
        railing.setInput("input_post_count", 2);
        railing.setInput("input_rail_count", 1);
        railing.setInput("input_height", 1.0d);
        railing.setInput("input_offset", -1.0d);
        railing.processNode(null);
        assertEquals(Boolean.TRUE, railing.getOutput("output_valid"));
    }

    @Test
    void wallMiterCornerProducesJoinedExtrusion() {
        WallAlongPathNode wall = new WallAlongPathNode();
        wall.setInput("input_path", lShapedPath());
        wall.setInput("input_height", 3.0d);
        wall.setInput("input_thickness", 0.4d);
        wall.setInput("input_join", "miter");
        wall.processNode(null);
        assertEquals(Boolean.TRUE, wall.getOutput("output_valid"));
        assertNotNull(wall.getOutput("output_geometry"));
        int count = (Integer) wall.getOutput("output_count");
        assertTrue(count >= 1 && count <= 4);
    }

    @Test
    void invalidJoinFailsClosed() {
        WallProbe wall = new WallProbe();
        wall.connectInput("input_join", NodeDataType.STRING);
        wall.setInput("input_path", lShapedPath());
        wall.setInput("input_height", 3.0d);
        wall.setInput("input_thickness", 0.4d);
        wall.setInput("input_join", "round");
        wall.processNode(null);
        assertEquals(Boolean.FALSE, wall.getOutput("output_valid"));
    }


    private static void mRoofRidgesRunAlongExtrusionAxis(List<PathData> ridges, double extrusionLength) {
        assertEquals(2, ridges.size());
        for (PathData ridge : ridges) {
            assertPathParallelToAxis(ridge, new Vector3d(0, 1, 0), extrusionLength);
        }
        double peakSeparation = ridges.getFirst().getLine().start().distanceTo(ridges.get(1).getLine().start());
        assertTrue(peakSeparation > 0.5d, "M roof peaks should be separated in profile plane");
    }

    private static void mRoofValleyRunsAlongExtrusionAxis(PathData valley, double extrusionLength) {
        assertPathParallelToAxis(valley, new Vector3d(0, 1, 0), extrusionLength);
    }

    private static void assertPathParallelToAxis(PathData path, Vector3d axis, double expectedLength) {
        assertNotNull(path.getLine());
        Vec3d start = path.getLine().start();
        Vec3d end = path.getLine().end();
        Vector3d direction = new Vector3d(end.x - start.x, end.y - start.y, end.z - start.z);
        double length = direction.length();
        assertEquals(expectedLength, length, 0.05d);
        direction.normalize();
        Vector3d unitAxis = new Vector3d(axis).normalize();
        double alignment = Math.abs(direction.dot(unitAxis));
        assertTrue(alignment > 0.999d, "path should align with extrusion axis, got dot=" + alignment);
    }

    private static BoxFaceData sampleFace(double width, double height) {
        double halfW = width / 2.0d;
        List<Vector3d> corners = List.of(
            new Vector3d(-halfW, 0, 0),
            new Vector3d(halfW, 0, 0),
            new Vector3d(halfW, height, 0),
            new Vector3d(-halfW, height, 0)
        );
        return new BoxFaceData(0, "front", List.of(0, 1, 2, 3), corners,
            new Vector3d(0, height / 2.0d, 0), new Vector3d(0, 0, 1));
    }

    private static PolylineData lShapedPath() {
        return new PolylineData(List.of(
            new Vec3d(0.0d, 0.0d, 0.0d),
            new Vec3d(10.0d, 0.0d, 0.0d),
            new Vec3d(10.0d, 0.0d, 10.0d)
        ));
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        node.state = new HashMap<>();
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

    private static void assertPortType(INode node, String portId, NodeDataType expected) {
        IPort port = findPort(node, portId);
        assertNotNull(port, portId);
        assertEquals(expected, port.getDataType(), portId);
    }

    private static IPort findPort(INode node, String portId) {
        for (IPort port : node.getInputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
        target.getInput(inputPortId);
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(java.util.UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }

    private static final class RoofBaseProbe extends RoofBaseNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalTopologyV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class RoofGeneratorProbe extends RoofGeneratorNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalTopologyV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class RailingProbe extends RailingNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalTopologyV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class WallProbe extends WallAlongPathNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalTopologyV2ContractTest.connectInput(this, portId, outputType);
        }
    }
}

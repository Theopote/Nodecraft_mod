package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.geometry.analysis.BlockBoundsNode;
import com.nodecraft.nodesystem.nodes.geometry.analysis.GeometryBoundsNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryBoundsResolver;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Geometry Analysis v1 language fence (Graph V67).
 */
class GeometryAnalysisLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "geometry.analysis.block_bounds",
            "geometry.analysis.geometry_bounds"
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
    void currentGraphFormatIsAtLeastV67() {
        assertEquals(67, GraphFormatVersion.V67);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V67);
        assertEquals(GraphFormatVersion.V67, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyTwoAnalysisNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("geometry.analysis."))
                .sorted()
                .toList();
        assertEquals(2, ids.size(), ids.toString());
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
        assertFalse(registry.getAllNodeIds().contains("geometry.boolean.bounding_box"));
        assertFalse(registry.getAllNodeIds().contains("geometry.boolean.geometry_bounds"));
    }

    @Test
    void ordersEffectsAndDisplayNames() {
        assertEquals(0, orderOf("geometry.analysis.block_bounds"));
        assertEquals(1, orderOf("geometry.analysis.geometry_bounds"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(
                registry.createNodeInstance("geometry.analysis.block_bounds").getClass(),
                "geometry.analysis.block_bounds"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(
                registry.createNodeInstance("geometry.analysis.geometry_bounds").getClass(),
                "geometry.analysis.geometry_bounds"));
        assertEquals("Block Bounds",
                registry.getNodeInfo("geometry.analysis.block_bounds").getDisplayName());
        assertEquals("Geometry Bounds",
                registry.getNodeInfo("geometry.analysis.geometry_bounds").getDisplayName());
    }

    @Test
    void blockBoundsPortDomains() {
        INode node = registry.createNodeInstance("geometry.analysis.block_bounds");
        assertPortType(node, "input_blocks", NodeDataType.BLOCK_LIST);
        assertPortType(node, "input_region", NodeDataType.REGION);
        assertPortType(node, "output_bounding_box", NodeDataType.BOUNDING_BOX);
        assertPortType(node, "output_region", NodeDataType.REGION);
        assertPortType(node, "output_min_block", NodeDataType.BLOCK_POS);
        assertPortType(node, "output_max_block", NodeDataType.BLOCK_POS);
        assertPortType(node, "output_center", NodeDataType.POINT);
        assertPortType(node, "output_size_x", NodeDataType.INTEGER);
        assertPortType(node, "output_volume", NodeDataType.DOUBLE);
        assertPortType(node, "output_valid", NodeDataType.BOOLEAN);
        assertPortType(node, "output_error", NodeDataType.STRING);
        assertFalse(hasPort(node, "output_min_corner"));
    }

    @Test
    void geometryBoundsPortDomainsContinuousOnly() {
        INode node = registry.createNodeInstance("geometry.analysis.geometry_bounds");
        assertPortType(node, "input_geometry", NodeDataType.GEOMETRY);
        assertPortType(node, "output_bounding_box", NodeDataType.BOUNDING_BOX);
        assertPortType(node, "output_min_point", NodeDataType.POINT);
        assertPortType(node, "output_max_point", NodeDataType.POINT);
        assertPortType(node, "output_center_point", NodeDataType.POINT);
        assertPortType(node, "output_size_x", NodeDataType.DOUBLE);
        assertPortType(node, "output_volume", NodeDataType.DOUBLE);
        assertPortType(node, "output_valid", NodeDataType.BOOLEAN);
        assertPortType(node, "output_error", NodeDataType.STRING);
        assertFalse(hasPort(node, "output_region"));
        assertFalse(hasPort(node, "output_min_corner"));
        assertFalse(hasPort(node, "output_max_corner"));
        assertFalse(hasPort(node, "output_center"));
    }

    @Test
    void blockBoundsExactlyOneSource() {
        BlockBoundsProbe both = new BlockBoundsProbe();
        both.connectInput("input_blocks", NodeDataType.BLOCK_LIST);
        both.connectInput("input_region", NodeDataType.REGION);
        both.setInput("input_blocks", new BlockPosList(List.of(new BlockPos(0, 0, 0))));
        both.setInput("input_region", new RegionData(new BlockPos(1, 1, 1), new BlockPos(2, 2, 2)));
        both.processNode(null);
        assertEquals(Boolean.FALSE, both.getOutput("output_valid"));

        BlockBoundsProbe neither = new BlockBoundsProbe();
        neither.processNode(null);
        assertEquals(Boolean.FALSE, neither.getOutput("output_valid"));
    }

    @Test
    void blockBoundsConnectedInvalidBlocksNoRegionFallback() {
        BlockBoundsProbe node = new BlockBoundsProbe();
        node.connectInput("input_blocks", NodeDataType.BLOCK_LIST);
        node.setInput("input_blocks", List.of("not-a-block"));
        node.setInput("input_region", new RegionData(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1)));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNull(node.getOutput("output_bounding_box"));
    }

    @Test
    void blockBoundsSingleBlockEnvelopeCenter() {
        BlockBoundsProbe node = new BlockBoundsProbe();
        node.connectInput("input_blocks", NodeDataType.BLOCK_LIST);
        node.setInput("input_blocks", new BlockPosList(List.of(new BlockPos(0, 0, 0))));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));

        BoundingBoxData box = assertInstanceOf(BoundingBoxData.class, node.getOutput("output_bounding_box"));
        assertEquals(0.0d, box.getMin().x, 1e-12);
        assertEquals(1.0d, box.getMax().x, 1e-12);
        PointData center = assertInstanceOf(PointData.class, node.getOutput("output_center"));
        assertEquals(0.5d, center.getX(), 1e-12);
        assertEquals(0.5d, center.getY(), 1e-12);
        assertEquals(0.5d, center.getZ(), 1e-12);
        assertEquals(1.0d, (Double) node.getOutput("output_volume"), 1e-12);
    }

    @Test
    void blockBoundsExtremeSpanVolumeUsesDoubleNotIntOverflow() {
        BlockBoundsProbe node = new BlockBoundsProbe();
        node.connectInput("input_region", NodeDataType.REGION);
        // 46341^2 overflows int multiply; long/double product stays correct for a thin slab.
        BlockPos min = new BlockPos(0, 0, 0);
        BlockPos max = new BlockPos(46340, 0, 46340);
        node.setInput("input_region", new RegionData(min, max));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        double expected = 46341.0d * 1.0d * 46341.0d;
        assertEquals(expected, (Double) node.getOutput("output_volume"), 0.0d);
    }

    @Test
    void geometryBoundsSphereSubBlockPrecision() {
        GeometryBoundsNode node = new GeometryBoundsNode();
        SphereData sphere = new SphereData(new Vector3d(0.25d, 0.25d, 0.25d), 0.4d);
        Map<String, Object> outputs = node.compute(Map.of("input_geometry", sphere));
        assertEquals(Boolean.TRUE, outputs.get("output_valid"));
        BoundingBoxData box = assertInstanceOf(BoundingBoxData.class, outputs.get("output_bounding_box"));
        assertEquals(-0.15d, box.getMin().x, 1e-12);
        assertEquals(0.65d, box.getMax().x, 1e-12);
        assertEquals(0.8d, (Double) outputs.get("output_size_x"), 1e-12);
    }

    @Test
    void geometryBoundsSdfUsesContinuousMinMax() {
        GeometryBoundsNode node = new GeometryBoundsNode();
        SdfGeometryData sdf = new SdfGeometryData(
                point -> -1.0d,
                new Vector3d(0.1d, 0.2d, 0.3d),
                new Vector3d(1.1d, 2.2d, 3.3d),
                0.0d
        );
        Map<String, Object> outputs = node.compute(Map.of("input_geometry", sdf));
        assertEquals(Boolean.TRUE, outputs.get("output_valid"));
        BoundingBoxData box = assertInstanceOf(BoundingBoxData.class, outputs.get("output_bounding_box"));
        assertEquals(0.1d, box.getMin().x, 1e-12);
        assertEquals(3.3d, box.getMax().z, 1e-12);
    }

    @Test
    void geometryBoundsBoolopsRules() {
        BoxGeometryData a = new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(1, 1, 1));
        BoxGeometryData b = new BoxGeometryData(new Vector3d(3, 0, 0), new Vector3d(1, 1, 1));
        BoxGeometryData c = new BoxGeometryData(new Vector3d(0.5, 0, 0), new Vector3d(1, 1, 1));

        BoundingBoxData union = GeometryBoundsResolver.resolve(new CompositeGeometryData(List.of(a, b)));
        assertNotNull(union);
        assertEquals(-1.0d, union.getMin().x, 1e-12);
        assertEquals(4.0d, union.getMax().x, 1e-12);

        BoundingBoxData difference = GeometryBoundsResolver.resolve(new DifferenceGeometryData(a, b));
        assertNotNull(difference);
        assertEquals(-1.0d, difference.getMin().x, 1e-12);
        assertEquals(1.0d, difference.getMax().x, 1e-12);

        assertNull(GeometryBoundsResolver.resolve(new IntersectionGeometryData(a, b)));
        BoundingBoxData overlap = GeometryBoundsResolver.resolve(new IntersectionGeometryData(a, c));
        assertNotNull(overlap);
        assertEquals(-0.5d, overlap.getMin().x, 1e-12);
        assertEquals(1.0d, overlap.getMax().x, 1e-12);
    }

    @Test
    void compositeWithUnresolvableChildFailsClosed() {
        BoxGeometryData valid = new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(1, 1, 1));
        GeometryData unsupported = new GeometryData() {
        };
        CompositeGeometryData composite = new CompositeGeometryData(List.of(valid, unsupported));

        assertNull(GeometryBoundsResolver.resolve(composite));

        GeometryBoundsNode node = new GeometryBoundsNode();
        Map<String, Object> outputs = node.compute(Map.of("input_geometry", composite));
        assertEquals(Boolean.FALSE, outputs.get("output_valid"));
        assertNull(outputs.get("output_bounding_box"));

        assertNull(GeometryBoundsResolver.resolve(new CompositeGeometryData(List.of())));
    }

    @Test
    void derivedCenterSizeVolumeMustBeFinite() {
        BoundingBoxData extreme = BoundingBoxData.create(
                new Vector3d(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE),
                new Vector3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE));
        assertNotNull(extreme);
        assertNull(extreme.finiteSize());
        assertNull(extreme.finiteVolume());

        GeometryBoundsNode node = new GeometryBoundsNode();
        SdfGeometryData sdf = new SdfGeometryData(
                point -> -1.0d,
                new Vector3d(-Double.MAX_VALUE, 0.0d, 0.0d),
                new Vector3d(Double.MAX_VALUE, 1.0d, 1.0d),
                0.0d
        );
        Map<String, Object> outputs = node.compute(Map.of("input_geometry", sdf));
        assertEquals(Boolean.FALSE, outputs.get("output_valid"));
        assertTrue(String.valueOf(outputs.get("output_error")).toLowerCase(Locale.ROOT).contains("non-finite"));
        assertNull(outputs.get("output_bounding_box"));
        Object sizeX = outputs.get("output_size_x");
        assertTrue(sizeX instanceof Double && Double.isNaN((Double) sizeX));
    }

    @Test
    void boundingBoxDataRejectsNanAndInverted() {
        assertNull(BoundingBoxData.create(
                new Vector3d(Double.NaN, 0, 0),
                new Vector3d(1, 1, 1)));
        assertNull(BoundingBoxData.create(
                new Vector3d(2, 0, 0),
                new Vector3d(1, 1, 1)));
        assertNull(BoundingBoxData.intersection(
                BoundingBoxData.create(new Vector3d(0, 0, 0), new Vector3d(1, 1, 1)),
                BoundingBoxData.create(new Vector3d(2, 0, 0), new Vector3d(3, 1, 1))));
    }

    @Test
    void migrateV66ToV67RenamesAndRemapsPorts() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V66;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();

        graph.nodes.add(savedNode("bb", "geometry.boolean.bounding_box"));
        graph.nodes.add(savedNode("gb", "geometry.boolean.geometry_bounds"));

        graph.connections.add(wire("src", "out", "bb", "input_coordinates"));
        graph.connections.add(wire("bb", "output_min_corner", "sink", "in_block"));
        graph.connections.add(wire("gb", "output_region", "sink2", "in_region"));
        graph.connections.add(wire("gb", "output_bounding_box", "sink3", "in_box"));
        graph.connections.add(wire("gb", "output_size_x", "sink4", "in_size"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("geometry.analysis.block_bounds", nodeOf(migrated, "bb").typeId);
        assertEquals("geometry.analysis.geometry_bounds", nodeOf(migrated, "gb").typeId);

        List<String> wires = migrated.connections.stream()
                .map(c -> c.sourceNodeId + ":" + c.sourcePortId + "->" + c.targetNodeId + ":" + c.targetPortId)
                .sorted()
                .toList();
        assertEquals(List.of(
                "bb:output_min_block->sink:in_block",
                "gb:output_bounding_box->sink3:in_box",
                "gb:output_size_x->sink4:in_size",
                "src:out->bb:input_blocks"
        ), wires);
    }

    private static int orderOf(String typeId) {
        INode node = registry.createNodeInstance(typeId);
        assertNotNull(node, typeId);
        return node.getClass().getAnnotation(NodeInfo.class).order();
    }

    private static void assertPortType(INode node, String portId, NodeDataType expected) {
        IPort port = findPort(node, portId);
        assertNotNull(port, portId);
        assertEquals(expected, port.getDataType(), portId);
    }

    private static boolean hasPort(INode node, String portId) {
        return findPort(node, portId) != null;
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

    private static SavedNode nodeOf(SavedGraph graph, String nodeId) {
        return graph.nodes.stream().filter(n -> nodeId.equals(n.nodeId)).findFirst().orElseThrow();
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

    private static final class BlockBoundsProbe extends BlockBoundsNode {
        void connectInput(String portId, NodeDataType outputType) {
            GeometryAnalysisLanguageContractTest.connectInput(this, portId, outputType);
        }
    }
}

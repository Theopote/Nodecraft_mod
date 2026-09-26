package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Placement v1 language fence (Graph V54).
 */
class PlacementLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "transform.placement.place_geometry_on_frames",
            "transform.placement.place_geometry_on_plane",
            "transform.placement.orient_geometry_to_frame",
            "transform.placement.offset_block_position",
            "transform.placement.offset_block_positions",
            "transform.placement.rotate_block_positions",
            "transform.placement.scale_block_positions",
            "transform.placement.mirror_block_positions"
    );

    private static final Set<String> LEGACY_COORDINATE_IDS = Set.of(
            "transform.placement.offset_coordinate",
            "transform.placement.offset_coordinates",
            "transform.placement.rotate_coordinates",
            "transform.placement.scale_coordinates",
            "transform.placement.mirror_coordinates"
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
    void placementFreezeVersionIsV54() {
        assertEquals(54, GraphFormatVersion.V54);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V54);
    }

    @Test
    void exactlyEightCanonicalPlacementNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("transform.placement."))
                .sorted()
                .toList();
        assertEquals(8, ids.size(), "Expected 8 placement nodes: " + ids);
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
        for (String legacy : LEGACY_COORDINATE_IDS) {
            assertFalse(registry.getAllNodeIds().contains(legacy), legacy);
        }
    }

    @Test
    void placementNodesHaveUniqueOrderZeroThroughSeven() {
        int[] orders = CANONICAL_IDS.stream()
                .mapToInt(typeId -> {
                    INode created = registry.createNodeInstance(typeId);
                    assertNotNull(created, typeId);
                    NodeInfo info = created.getClass().getAnnotation(NodeInfo.class);
                    assertNotNull(info, typeId);
                    return info.order();
                })
                .sorted()
                .toArray();
        assertEquals(8, orders.length);
        for (int i = 0; i < orders.length; i++) {
            assertEquals(i, orders[i], "Expected unique order " + i);
        }
    }

    @Test
    void placeOnFramesHasNoGeometriesListPort() {
        INode place = registry.createNodeInstance("transform.placement.place_geometry_on_frames");
        assertFalse(place.getOutputPorts().stream().anyMatch(p -> "output_geometries".equals(p.getId())));
        assertTrue(place.getOutputPorts().stream().anyMatch(p -> "output_geometry".equals(p.getId())));
    }

    @Test
    void rotateScaleMirrorIdentityAroundOriginPreservesOriginCell() {
        BlockPosList input = new BlockPosList();
        input.add(new BlockPos(0, 0, 0));

        // Identity transforms: prove cellCenterâ†’floor round-trips (nearest would map centerâ†?1,1,1)).
        BaseNode rotate = node("transform.placement.rotate_block_positions");
        rotate.setInput("input_coordinates", input);
        rotate.setInput("input_center", new PointData(0, 0, 0));
        rotate.setInput("input_angle", 0.0d);
        rotate.processNode(null);
        assertEquals(Boolean.TRUE, rotate.getOutput("output_valid"));
        assertEquals(new BlockPos(0, 0, 0), firstBlock(rotate));

        BaseNode scale = node("transform.placement.scale_block_positions");
        scale.setInput("input_coordinates", input);
        scale.setInput("input_center", new PointData(0, 0, 0));
        scale.setInput("input_scale_factor", 1.0d);
        scale.processNode(null);
        assertEquals(Boolean.TRUE, scale.getOutput("output_valid"));
        assertEquals(new BlockPos(0, 0, 0), firstBlock(scale));

        MirrorProbe mirror = new MirrorProbe();
        mirror.setInput("input_coordinates", input);
        // Plane through cell center â†?reflection is identity on that point.
        mirror.connectInput("input_plane", NodeDataType.PLANE);
        mirror.setInput("input_plane", PlaneData.canonical(new Vector3d(0.5d, 0.5d, 0.5d), new Vector3d(0, 1, 0)));
        mirror.processNode(null);
        assertEquals(Boolean.TRUE, mirror.getOutput("output_valid"));
        assertEquals(new BlockPos(0, 0, 0), firstBlock(mirror));
    }

    @Test
    void scaleRejectsNonPositiveComponents() {
        ScaleProbe scale = new ScaleProbe();
        BlockPosList input = new BlockPosList();
        input.add(new BlockPos(1, 0, 0));
        scale.setInput("input_coordinates", input);
        scale.connectInput("input_scale_factor", NodeDataType.DOUBLE);
        scale.setInput("input_scale_factor", 0.0d);
        scale.processNode(null);
        assertEquals(Boolean.FALSE, scale.getOutput("output_valid"));
    }

    @Test
    void scalePreservesDuplicatesOnCollision() {
        BaseNode scale = node("transform.placement.scale_block_positions");
        BlockPosList input = new BlockPosList();
        input.add(new BlockPos(1, 0, 0));
        input.add(new BlockPos(1, 0, 0));
        scale.setInput("input_coordinates", input);
        scale.setInput("input_center", new PointData(0, 0, 0));
        scale.setInput("input_scale_factor", 1.0d);
        scale.processNode(null);
        assertEquals(Boolean.TRUE, scale.getOutput("output_valid"));
        BlockPosList out = assertInstanceOf(BlockPosList.class, scale.getOutput("output_coordinates"));
        assertEquals(2, out.size());
        assertEquals(new BlockPos(1, 0, 0), out.getPositions().get(0));
        assertEquals(new BlockPos(1, 0, 0), out.getPositions().get(1));
    }

    @Test
    void placeOnFramesMalformedFrameListFails() {
        PlaceFramesProbe place = new PlaceFramesProbe();
        place.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        place.connectInput("input_frames", NodeDataType.FRAME_LIST);
        place.setInput("input_frames", List.of(new FrameData(
                new Vector3d(), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)
        ), "bad"));
        place.processNode(null);
        assertEquals(Boolean.FALSE, place.getOutput("output_valid"));
        assertNull(place.getOutput("output_geometry"));
    }

    @Test
    void placeOnFramesBothFrameAndFramesConnectedFails() {
        PlaceFramesProbe place = new PlaceFramesProbe();
        place.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        FrameData frame = new FrameData(
                new Vector3d(1, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)
        );
        place.connectInput("input_frame", NodeDataType.FRAME);
        place.connectInput("input_frames", NodeDataType.FRAME_LIST);
        place.setInput("input_frame", frame);
        place.setInput("input_frames", List.of(frame));
        place.processNode(null);
        assertEquals(Boolean.FALSE, place.getOutput("output_valid"));
    }

    @Test
    void placeOnFramesOneBadFrameFailsWholeNode() {
        PlaceFramesProbe place = new PlaceFramesProbe();
        place.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        place.connectInput("input_frames", NodeDataType.FRAME_LIST);
        // Second frame has zero-length axes â†?orthonormalized() fails â†?placeOnFrame null
        FrameData good = new FrameData(
                new Vector3d(1, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)
        );
        FrameData bad = new FrameData(
                new Vector3d(2, 0, 0), new Vector3d(), new Vector3d(), new Vector3d()
        );
        place.setInput("input_frames", List.of(good, bad));
        place.processNode(null);
        assertEquals(Boolean.FALSE, place.getOutput("output_valid"));
        assertEquals(0, place.getOutput("output_count"));
        assertNull(place.getOutput("output_geometry"));
    }

    @Test
    void placeOnFramesOverInstanceCapFails() {
        PlaceFramesProbe place = new PlaceFramesProbe();
        place.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        place.connectInput("input_frames", NodeDataType.FRAME_LIST);
        List<FrameData> frames = IntStream.range(0, GenerationLimits.MAX_GEOMETRY_INSTANCES + 1)
                .mapToObj(i -> new FrameData(
                        new Vector3d(i, 0, 0),
                        new Vector3d(1, 0, 0),
                        new Vector3d(0, 1, 0),
                        new Vector3d(0, 0, 1)
                ))
                .toList();
        place.setInput("input_frames", frames);
        place.processNode(null);
        assertEquals(Boolean.FALSE, place.getOutput("output_valid"));
    }

    @Test
    void pivotConnectedNullFailsClosed() {
        PlaceFramesProbe place = new PlaceFramesProbe();
        place.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        place.connectInput("input_frame", NodeDataType.FRAME);
        place.connectInput("input_pivot", NodeDataType.POINT);
        place.setInput("input_frame", new FrameData(
                new Vector3d(), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)
        ));
        place.setInput("input_pivot", null);
        place.processNode(null);
        assertEquals(Boolean.FALSE, place.getOutput("output_valid"));
    }

    @Test
    void xHintConnectedNullFailsClosed() {
        PlacePlaneProbe place = new PlacePlaneProbe();
        place.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        place.setInput("input_plane", new PlaneData(new Vector3d(), new Vector3d(0, 1, 0)));
        place.connectInput("input_x_hint", NodeDataType.VECTOR);
        place.setInput("input_x_hint", null);
        place.processNode(null);
        assertEquals(Boolean.FALSE, place.getOutput("output_valid"));
    }

    @Test
    void xHintConnectedParallelToNormalFailsClosed() {
        PlacePlaneProbe place = new PlacePlaneProbe();
        place.setInput("input_geometry", new SphereData(new Vector3d(), 1.0d));
        place.setInput("input_plane", new PlaneData(new Vector3d(), new Vector3d(0, 1, 0)));
        place.connectInput("input_x_hint", NodeDataType.VECTOR);
        place.setInput("input_x_hint", new VectorData(0, 1, 0));
        place.processNode(null);
        assertEquals(Boolean.FALSE, place.getOutput("output_valid"));
    }

    @Test
    void migrateV53ToV54RemapsTypesAndDropsGeometriesPort() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V53;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();

        SavedNode rotate = savedNode("n1", "transform.placement.rotate_coordinates");
        Map<String, Object> rotateState = new HashMap<>();
        rotateState.put("rotationAxis", "CUSTOM");
        rotateState.put("defaultAngle", 45.0d);
        rotate.state = rotateState;

        SavedNode mirror = savedNode("n2", "transform.placement.mirror_coordinates");
        Map<String, Object> mirrorState = new HashMap<>();
        mirrorState.put("mirrorPlane", "CUSTOM");
        mirrorState.put("roundingMode", "NEAREST");
        mirror.state = mirrorState;

        SavedNode place = savedNode("n3", "transform.placement.place_geometry_on_frames");
        SavedNode sink = savedNode("n4", "reference.vectors.vector");
        graph.nodes.addAll(List.of(rotate, mirror, place, sink));

        graph.connections.add(wire("n3", "output_geometries", "n4", "input_x"));
        graph.connections.add(wire("n3", "output_valid", "n4", "input_y"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("transform.placement.rotate_block_positions", typeOf(migrated, "n1"));
        assertEquals("transform.placement.mirror_block_positions", typeOf(migrated, "n2"));

        @SuppressWarnings("unchecked")
        Map<String, Object> rotatedState = (Map<String, Object>) nodeOf(migrated, "n1").state;
        assertEquals("Y_AXIS", rotatedState.get("rotationAxis"));

        @SuppressWarnings("unchecked")
        Map<String, Object> mirroredState = (Map<String, Object>) nodeOf(migrated, "n2").state;
        assertEquals("XZ", mirroredState.get("mirrorPlane"));
        assertFalse(mirroredState.containsKey("roundingMode"));

        assertEquals(1, migrated.connections.size());
        assertEquals("output_valid", migrated.connections.getFirst().sourcePortId);
    }

    private static BlockPos firstBlock(BaseNode node) {
        BlockPosList list = assertInstanceOf(BlockPosList.class, node.getOutput("output_coordinates"));
        assertFalse(list.isEmpty());
        return list.getPositions().getFirst();
    }

    private static BaseNode node(String typeId) {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(typeId));
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        return node;
    }

    private static SavedConnection wire(String src, String srcPort, String dst, String dstPort) {
        SavedConnection c = new SavedConnection();
        c.sourceNodeId = src;
        c.sourcePortId = srcPort;
        c.targetNodeId = dst;
        c.targetPortId = dstPort;
        return c;
    }

    private static String typeOf(SavedGraph graph, String nodeId) {
        return nodeOf(graph, nodeId).typeId;
    }

    private static SavedNode nodeOf(SavedGraph graph, String nodeId) {
        return graph.nodes.stream()
                .filter(node -> nodeId.equals(node.nodeId))
                .findFirst()
                .orElseThrow();
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

    private static final class PlaceFramesProbe
            extends com.nodecraft.nodesystem.nodes.transform.placement.PlaceGeometryOnFramesNode {
        void connectInput(String portId, NodeDataType outputType) {
            PlacementLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class PlacePlaneProbe
            extends com.nodecraft.nodesystem.nodes.transform.placement.PlaceGeometryOnPlaneNode {
        void connectInput(String portId, NodeDataType outputType) {
            PlacementLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class ScaleProbe
            extends com.nodecraft.nodesystem.nodes.transform.placement.ScaleBlockPositionsNode {
        void connectInput(String portId, NodeDataType outputType) {
            PlacementLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class MirrorProbe
            extends com.nodecraft.nodesystem.nodes.transform.placement.MirrorBlockPositionsNode {
        void connectInput(String portId, NodeDataType outputType) {
            PlacementLanguageContractTest.connectInput(this, portId, outputType);
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

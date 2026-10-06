package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.FrameDataTestAccess;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.nodes.transform.placement.RotateBlockPositionsNode;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryStructureUtils;
import com.nodecraft.nodesystem.util.GeometryTransform;
import com.nodecraft.nodesystem.util.PlacementBlockUtils;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Placement Language v2 / Semantics & Budgeting v3.
 */
class PlacementLanguageV2ContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
        "transform.placement.place_geometry_on_frames",
        "transform.placement.place_geometry_on_plane",
        "transform.placement.orient_geometry_to_frame",
        "transform.placement.offset_block_position",
        "transform.placement.offset_block_positions",
        "transform.placement.rotate_block_positions",
        "transform.placement.scale_block_positions",
        "transform.placement.mirror_block_positions"
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
    void exactlyEightNodesWithUniqueOrdersZeroToSeven() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("transform.placement."))
            .sorted()
            .toList();
        assertEquals(8, ids.size());
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("transform.placement", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 8; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void allNodesHaveValidAndErrorWithoutBannedPortTypes() {
        List<String> errors = new ArrayList<>();
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            if (!hasPort(node, "output_valid")) {
                errors.add(id + " missing output_valid");
            }
            if (!hasPort(node, "output_error")) {
                errors.add(id + " missing output_error");
            }
            for (IPort port : node.getInputPorts()) {
                banPortType(errors, id, port);
            }
            for (IPort port : node.getOutputPorts()) {
                banPortType(errors, id, port);
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void blockTransformNodesUseCanonicalBlockPositionPortIds() {
        assertPort("transform.placement.offset_block_position", "input_block_position", true);
        assertPort("transform.placement.offset_block_position", "output_block_position", false);
        assertPort("transform.placement.offset_block_positions", "input_block_positions", true);
        assertPort("transform.placement.offset_block_positions", "output_block_positions", false);
        assertPort("transform.placement.rotate_block_positions", "input_block_positions", true);
        assertPort("transform.placement.rotate_block_positions", "output_block_positions", false);
        assertPort("transform.placement.scale_block_positions", "input_block_positions", true);
        assertPort("transform.placement.scale_block_positions", "output_block_positions", false);
        assertPort("transform.placement.mirror_block_positions", "input_block_positions", true);
        assertPort("transform.placement.mirror_block_positions", "output_block_positions", false);
    }

    @Test
    void cellCenterTransformFloorSnapRoundTripsAtOrigin() {
        BlockPosList input = new BlockPosList();
        input.add(new BlockPos(0, 0, 0));

        BaseNode rotate = node("transform.placement.rotate_block_positions");
        rotate.setInput("input_block_positions", input);
        rotate.setInput("input_center", new PointData(0, 0, 0));
        rotate.setInput("input_angle", 0.0d);
        rotate.processNode(null);
        assertEquals(Boolean.TRUE, rotate.getOutput("output_valid"));
        assertEquals(new BlockPos(0, 0, 0), firstBlock(rotate));

        BaseNode scale = node("transform.placement.scale_block_positions");
        scale.setInput("input_block_positions", input);
        scale.setInput("input_center", new PointData(0, 0, 0));
        scale.setInput("input_scale_factor", 1.0d);
        scale.processNode(null);
        assertEquals(Boolean.TRUE, scale.getOutput("output_valid"));
        assertEquals(new BlockPos(0, 0, 0), firstBlock(scale));

        MirrorProbe mirror = new MirrorProbe();
        mirror.setInput("input_block_positions", input);
        mirror.connectInput("input_plane", NodeDataType.PLANE);
        mirror.setInput("input_plane", PlaneData.canonical(new Vector3d(0.5d, 0.5d, 0.5d), new Vector3d(0, 1, 0)));
        mirror.processNode(null);
        assertEquals(Boolean.TRUE, mirror.getOutput("output_valid"));
        assertEquals(new BlockPos(0, 0, 0), firstBlock(mirror));
    }

    @Test
    void placeOnFramesFailsOnLeafTimesFrameWorkload() {
        List<SphereData> leaves = IntStream.range(0, 128)
            .mapToObj(i -> new SphereData(new Vector3d(i, 0, 0), 0.5d))
            .map(SphereData.class::cast)
            .toList();
        CompositeGeometryData composite = new CompositeGeometryData(new ArrayList<>(leaves));
        assertEquals(128, GeometryStructureUtils.countLeaves(composite));

        int frameCount = GenerationLimits.MAX_GEOMETRY_INSTANCES / 128 + 1;
        assertTrue(128L * frameCount > GenerationLimits.MAX_GEOMETRY_INSTANCES);

        PlaceFramesProbe place = new PlaceFramesProbe();
        place.setInput("input_geometry", composite);
        place.connectInput("input_frames", NodeDataType.FRAME_LIST);
        List<FrameData> frames = IntStream.range(0, frameCount)
            .mapToObj(i -> FrameDataTestAccess.unchecked(
                new Vector3d(i, 0, 0),
                new Vector3d(1, 0, 0),
                new Vector3d(0, 1, 0),
                new Vector3d(0, 0, 1)
            ))
            .toList();
        place.setInput("input_frames", frames);
        place.processNode(null);
        assertEquals(Boolean.FALSE, place.getOutput("output_valid"));
        assertTrue(String.valueOf(place.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("workload"));
    }

    @Test
    void orientDisplayNameIsApplyFrameOrientation() {
        BaseNode orient = node("transform.placement.orient_geometry_to_frame");
        assertEquals("Apply Frame Orientation", orient.getDisplayName());
        String desc = orient.getDescription().toLowerCase(Locale.ROOT);
        assertTrue(desc.contains("relative"));
        assertTrue(desc.contains("not an absolute"));
    }

    @Test
    void orientAppliesRelativeFrameRotation() {
        BoxGeometryData axisAligned = new BoxGeometryData(new Vector3d(), new Vector3d(4, 1, 1));
        GeometryData preRotated = GeometryTransform.transform(
            axisAligned, new Vector3d(), 0.0d, 45.0d, 0.0d, 1.0d);
        BoxGeometryData rotatedIn = assertInstanceOf(BoxGeometryData.class, preRotated);
        assertTrue(rotatedIn.isOriented());

        BaseNode orient = node("transform.placement.orient_geometry_to_frame");
        orient.setInput("input_geometry", rotatedIn);
        orient.setInput("input_frame", FrameData.orthonormal(
            new Vector3d(), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)
        ));
        orient.setInput("input_pivot", new PointData(0, 0, 0));
        orient.processNode(null);
        assertEquals(Boolean.TRUE, orient.getOutput("output_valid"));
        BoxGeometryData out = assertInstanceOf(BoxGeometryData.class, orient.getOutput("output_geometry"));
        assertTrue(out.isOriented());
        assertFalse(out.getOrientationMatrix().equals(new org.joml.Matrix3d().identity(), 1.0e-6d),
            "identity Frame must not reset absolute orientation of a pre-rotated box");
    }

    @Test
    void resolveStrictFrameListBoundedRejectsOversizeWithoutFullCopy() {
        List<FrameData> oversized = new AbstractList<>() {
            @Override
            public FrameData get(int index) {
                return FrameData.orthonormal(
                    new Vector3d(index, 0, 0),
                    new Vector3d(1, 0, 0),
                    new Vector3d(0, 1, 0),
                    new Vector3d(0, 0, 1)
                );
            }

            @Override
            public int size() {
                return GenerationLimits.MAX_GEOMETRY_INSTANCES + 1;
            }
        };
        assertNull(FrameUtils.resolveStrictFrameListBounded(
            oversized, GenerationLimits.MAX_GEOMETRY_INSTANCES));
    }

    @Test
    void countLeavesBoundedStopsEarly() {
        List<GeometryData> leaves = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            leaves.add(new SphereData(new Vector3d(i, 0, 0), 0.5d));
        }
        CompositeGeometryData composite = new CompositeGeometryData(leaves);
        assertEquals(10, GeometryStructureUtils.countLeavesBounded(composite, 9));
        assertEquals(64, GeometryStructureUtils.countLeavesBounded(composite, 64));
        assertEquals(64, GeometryStructureUtils.countLeaves(composite));
    }

    @Test
    void placeOnFramesNullGeometryFailsClosed() {
        PlaceFramesProbe place = new PlaceFramesProbe();
        place.connectInput("input_frame", NodeDataType.FRAME);
        place.setInput("input_frame", FrameData.orthonormal(
            new Vector3d(), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)
        ));
        place.processNode(null);
        assertEquals(Boolean.FALSE, place.getOutput("output_valid"));
    }

    @Test
    void blockListOverCapFailsClosed() {
        BlockPosList oversized = oversizedBlockList();
        BaseNode mirror = node("transform.placement.mirror_block_positions");
        mirror.setInput("input_block_positions", oversized);
        mirror.processNode(null);
        assertEquals(Boolean.FALSE, mirror.getOutput("output_valid"));
        assertTrue(String.valueOf(mirror.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("max_list_elements"));
        assertEquals(0, ((BlockPosList) mirror.getOutput("output_block_positions")).size());
    }

    @Test
    void offsetVectorXorComponentPortsFail() {
        OffsetSingleProbe offset = new OffsetSingleProbe();
        offset.setInput("input_block_position", new BlockPos(0, 0, 0));
        offset.connectInput("input_offset_vector", NodeDataType.VECTOR);
        offset.connectInput("input_offset_x", NodeDataType.INTEGER);
        offset.setInput("input_offset_vector", new VectorData(1, 0, 0));
        offset.setInput("input_offset_x", 1);
        offset.processNode(null);
        assertEquals(Boolean.FALSE, offset.getOutput("output_valid"));
        assertTrue(String.valueOf(offset.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("not both"));
    }

    @Test
    void scaleFactorXorScaleVectorFail() {
        ScaleProbe scale = new ScaleProbe();
        BlockPosList input = new BlockPosList();
        input.add(new BlockPos(1, 0, 0));
        scale.setInput("input_block_positions", input);
        scale.connectInput("input_scale_factor", NodeDataType.DOUBLE);
        scale.connectInput("input_scale_vector", NodeDataType.VECTOR);
        scale.setInput("input_scale_factor", 2.0d);
        scale.setInput("input_scale_vector", new VectorData(2, 2, 2));
        scale.processNode(null);
        assertEquals(Boolean.FALSE, scale.getOutput("output_valid"));
        assertTrue(String.valueOf(scale.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("not both"));
    }

    @Test
    void mirrorPlaneXorPointNormalFail() {
        MirrorProbe mirror = new MirrorProbe();
        mirror.setInput("input_block_positions", singleBlockList());
        mirror.connectInput("input_plane", NodeDataType.PLANE);
        mirror.connectInput("input_point", NodeDataType.POINT);
        mirror.setInput("input_plane", PlaneData.XZ_PLANE);
        mirror.setInput("input_point", new PointData(0, 0, 0));
        mirror.processNode(null);
        assertEquals(Boolean.FALSE, mirror.getOutput("output_valid"));
        assertTrue(String.valueOf(mirror.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("not both"));
    }

    @Test
    void mirrorPartialPointNormalFail() {
        MirrorProbe mirror = new MirrorProbe();
        mirror.setInput("input_block_positions", singleBlockList());
        mirror.connectInput("input_point", NodeDataType.POINT);
        mirror.setInput("input_point", new PointData(0, 0, 0));
        mirror.processNode(null);
        assertEquals(Boolean.FALSE, mirror.getOutput("output_valid"));
        assertTrue(String.valueOf(mirror.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("both point and normal"));
    }

    @Test
    void mirrorZeroNormalPointPlusNormalFailsClosed() {
        MirrorProbe mirror = new MirrorProbe();
        mirror.setInput("input_block_positions", singleBlockList());
        mirror.connectInput("input_point", NodeDataType.POINT);
        mirror.connectInput("input_normal", NodeDataType.VECTOR);
        mirror.setInput("input_point", new PointData(0, 0, 0));
        mirror.setInput("input_normal", new com.nodecraft.nodesystem.datatypes.VectorData(0, 0, 0));
        assertDoesNotThrow(() -> mirror.processNode(null));
        assertEquals(Boolean.FALSE, mirror.getOutput("output_valid"));
        assertFalse(String.valueOf(mirror.getOutput("output_error")).isBlank());
        assertEquals(0, ((BlockPosList) mirror.getOutput("output_block_positions")).size());
    }

    @Test
    void mirrorHugeNonNormalizableNormalFailsClosed() {
        MirrorProbe mirror = new MirrorProbe();
        mirror.setInput("input_block_positions", singleBlockList());
        mirror.connectInput("input_point", NodeDataType.POINT);
        mirror.connectInput("input_normal", NodeDataType.VECTOR);
        mirror.setInput("input_point", new PointData(0, 0, 0));
        mirror.setInput("input_normal", new com.nodecraft.nodesystem.datatypes.VectorData(Double.MAX_VALUE, Double.MAX_VALUE, 0));
        assertDoesNotThrow(() -> mirror.processNode(null));
        assertEquals(Boolean.FALSE, mirror.getOutput("output_valid"));
        assertFalse(String.valueOf(mirror.getOutput("output_error")).isBlank());
        assertEquals(0, ((BlockPosList) mirror.getOutput("output_block_positions")).size());
    }

    @Test
    void rotateHugeAxisFailsClosed() {
        RotateProbe rotate = new RotateProbe();
        rotate.setInput("input_block_positions", singleBlockList());
        rotate.connectInput("input_axis", NodeDataType.VECTOR);
        rotate.setInput("input_axis", new com.nodecraft.nodesystem.datatypes.VectorData(Double.MAX_VALUE, Double.MAX_VALUE, 0));
        rotate.processNode(null);
        assertEquals(Boolean.FALSE, rotate.getOutput("output_valid"));
        assertEquals(0, ((BlockPosList) rotate.getOutput("output_block_positions")).size());
    }

    @Test
    void rotateNonFiniteDegreesFailsClosed() {
        RotateProbe rotate = new RotateProbe();
        rotate.setInput("input_block_positions", singleBlockList());
        rotate.connectInput("input_angle", NodeDataType.DOUBLE);
        rotate.setInput("input_angle", Double.POSITIVE_INFINITY);
        rotate.processNode(null);
        assertEquals(Boolean.FALSE, rotate.getOutput("output_valid"));
        assertEquals(0, ((BlockPosList) rotate.getOutput("output_block_positions")).size());
    }

    @Test
    void rotateStateLoadSkipsNonFiniteAngle() {
        RotateBlockPositionsNode rotate = new RotateBlockPositionsNode();
        rotate.setDefaultAngle(45.0d);
        rotate.setNodeState(java.util.Map.of("defaultAngle", Double.NaN));
        assertEquals(45.0d, rotate.getDefaultAngle());
    }

    @Test
    void offsetHugeVectorFailsCheckedRound() {
        OffsetSingleProbe offset = new OffsetSingleProbe();
        offset.setInput("input_block_position", new BlockPos(0, 0, 0));
        offset.connectInput("input_offset_vector", NodeDataType.VECTOR);
        offset.setInput("input_offset_vector", new VectorData(1e20, 0, 0));
        offset.processNode(null);
        assertEquals(Boolean.FALSE, offset.getOutput("output_valid"));
        assertTrue(String.valueOf(offset.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("range"));
    }

    @Test
    void offsetOverflowIsTransactional() {
        OffsetSingleProbe offset = new OffsetSingleProbe();
        offset.setInput("input_block_position", new BlockPos(Integer.MAX_VALUE, 0, 0));
        offset.connectInput("input_offset_x", NodeDataType.INTEGER);
        offset.setInput("input_offset_x", 1);
        offset.processNode(null);
        assertEquals(Boolean.FALSE, offset.getOutput("output_valid"));
        assertNull(offset.getOutput("output_block_position"));
    }

    @Test
    void trySnapCellCenterRejectsHugeFiniteCoordinates() {
        assertNull(PlacementBlockUtils.trySnapCellCenter(new Vector3d(1e300, 0, 0)));
        assertNull(PlacementBlockUtils.trySnapCellCenter(new Vector3d(0, 1e300, 0)));
        assertNull(PlacementBlockUtils.trySnapCellCenter(new Vector3d(0, 0, 1e300)));
    }

    @Test
    void scaleHugeFiniteResultFailsClosed() {
        ScaleProbe scale = new ScaleProbe();
        BlockPosList input = new BlockPosList();
        input.add(new BlockPos(1, 0, 0));
        scale.setInput("input_block_positions", input);
        scale.setInput("input_center", new PointData(0, 0, 0));
        scale.connectInput("input_scale_factor", NodeDataType.DOUBLE);
        scale.setInput("input_scale_factor", 1e300);
        scale.processNode(null);
        assertEquals(Boolean.FALSE, scale.getOutput("output_valid"));
        assertFalse(String.valueOf(scale.getOutput("output_error")).isBlank());
        BlockPosList out = assertInstanceOf(BlockPosList.class, scale.getOutput("output_block_positions"));
        assertTrue(out.isEmpty());
        assertEquals(0, scale.getOutput("output_count"));
    }

    @Test
    void scaleRejectsNonPositiveWithError() {
        ScaleProbe scale = new ScaleProbe();
        scale.setInput("input_block_positions", singleBlockList());
        scale.connectInput("input_scale_factor", NodeDataType.DOUBLE);
        scale.setInput("input_scale_factor", 0.0d);
        scale.processNode(null);
        assertEquals(Boolean.FALSE, scale.getOutput("output_valid"));
        assertTrue(String.valueOf(scale.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("scale"));
    }

    @Test
    void scalePreservesDuplicatesOnCollision() {
        BaseNode scale = node("transform.placement.scale_block_positions");
        BlockPosList input = new BlockPosList();
        input.add(new BlockPos(1, 0, 0));
        input.add(new BlockPos(1, 0, 0));
        scale.setInput("input_block_positions", input);
        scale.setInput("input_center", new PointData(0, 0, 0));
        scale.setInput("input_scale_factor", 1.0d);
        scale.processNode(null);
        assertEquals(Boolean.TRUE, scale.getOutput("output_valid"));
        BlockPosList out = assertInstanceOf(BlockPosList.class, scale.getOutput("output_block_positions"));
        assertEquals(2, out.size());
        assertEquals(new BlockPos(1, 0, 0), out.getPositions().get(0));
        assertEquals(new BlockPos(1, 0, 0), out.getPositions().get(1));
    }


    private static BlockPosList singleBlockList() {
        BlockPosList list = new BlockPosList();
        list.add(new BlockPos(0, 0, 0));
        return list;
    }

    private static BlockPosList oversizedBlockList() {
        return new BlockPosList() {
            @Override
            public int size() {
                return GenerationLimits.MAX_LIST_ELEMENTS + 1;
            }
        };
    }

    private static BlockPos firstBlock(BaseNode node) {
        BlockPosList list = assertInstanceOf(BlockPosList.class, node.getOutput("output_block_positions"));
        assertFalse(list.isEmpty());
        return list.getPositions().getFirst();
    }

    private static BaseNode node(String typeId) {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(typeId));
    }

    private static void assertPort(String typeId, String portId, boolean input) {
        INode node = registry.createNodeInstance(typeId);
        assertTrue(hasPort(node, portId, input), typeId + " missing " + (input ? "input " : "output ") + portId);
    }

    private static boolean hasPort(INode node, String portId) {
        return hasPort(node, portId, true) || hasPort(node, portId, false);
    }

    private static boolean hasPort(INode node, String portId, boolean input) {
        List<? extends IPort> ports = input ? node.getInputPorts() : node.getOutputPorts();
        return ports.stream().anyMatch(p -> portId.equals(p.getId()));
    }

    private static void banPortType(List<String> errors, String nodeId, IPort port) {
        NodeDataType type = port.getDataType();
        if (type == NodeDataType.ANY
            || type == NodeDataType.LIST
            || type == NodeDataType.LINE
            || type == NodeDataType.POLYLINE) {
            errors.add(nodeId + " port " + port.getId() + " uses banned type " + type);
        }
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
            PlacementLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class OffsetSingleProbe
            extends com.nodecraft.nodesystem.nodes.transform.placement.OffsetBlockPositionNode {
        void connectInput(String portId, NodeDataType outputType) {
            PlacementLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class ScaleProbe
            extends com.nodecraft.nodesystem.nodes.transform.placement.ScaleBlockPositionsNode {
        void connectInput(String portId, NodeDataType outputType) {
            PlacementLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class MirrorProbe
            extends com.nodecraft.nodesystem.nodes.transform.placement.MirrorBlockPositionsNode {
        void connectInput(String portId, NodeDataType outputType) {
            PlacementLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class RotateProbe
            extends com.nodecraft.nodesystem.nodes.transform.placement.RotateBlockPositionsNode {
        void connectInput(String portId, NodeDataType outputType) {
            PlacementLanguageV2ContractTest.connectInput(this, portId, outputType);
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

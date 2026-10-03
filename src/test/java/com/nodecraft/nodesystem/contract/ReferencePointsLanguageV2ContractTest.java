package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.nodes.reference.points.CoordinateInputNode;
import com.nodecraft.nodesystem.nodes.reference.points.GetBoxFaceNode;
import com.nodecraft.nodesystem.nodes.reference.points.PointAlongVectorNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BoxFaceValidator;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Reference Points Language v2 (Graph V87).
 */
class ReferencePointsLanguageV2ContractTest {

    private static final List<String> CANONICAL_IDS = List.of(
        "reference.points.block_position",
        "reference.points.construct_coordinate",
        "reference.points.deconstruct_block_position",
        "reference.points.point_from_block",
        "reference.points.construct_point",
        "reference.points.deconstruct_point",
        "reference.points.translate_point",
        "reference.points.point_along_vector",
        "reference.points.mid_point",
        "reference.points.distance_between_points",
        "reference.points.vector_between_points",
        "reference.points.closest_point",
        "reference.points.point_list_center",
        "reference.points.point_list_bounds",
        "reference.points.get_box_corner",
        "reference.points.get_box_face",
        "reference.points.get_face_edge",
        "reference.points.deconstruct_face",
        "reference.points.deconstruct_edge"
    );

    private static final List<String> QUERY_IDS = List.of(
        "reference.points.get_box_corner",
        "reference.points.get_box_face",
        "reference.points.get_face_edge"
    );

    private static final Set<NodeDataType> FORBIDDEN_PORT_TYPES = Set.of(
        NodeDataType.ANY,
        NodeDataType.LIST,
        NodeDataType.POLYLINE,
        NodeDataType.CURVE,
        NodeDataType.BLOCK_LIST,
        NodeDataType.FILE_PATH
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
    void exactlyNineteenNodesWithUniqueOrdersZeroToEighteen() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("reference.points."))
            .sorted()
            .toList();
        assertEquals(19, ids.size(), "Expected 19 reference.points nodes: " + ids);
        assertEquals(Set.copyOf(CANONICAL_IDS), Set.copyOf(ids));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("reference.points", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 19; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void allNodesExposeValidAndError() {
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            assertTrue(hasPort(node, "output_valid"), id + " missing output_valid");
            assertTrue(hasPort(node, "output_error"), id + " missing output_error");
            assertPortType(id, "output_error", false, NodeDataType.STRING);
        }
    }

    @Test
    void queryNodesExposeFoundValidAndError() {
        for (String id : QUERY_IDS) {
            INode node = registry.createNodeInstance(id);
            assertTrue(hasPort(node, "output_found"), id + " missing output_found");
            assertTrue(hasPort(node, "output_valid"), id + " missing output_valid");
            assertTrue(hasPort(node, "output_error"), id + " missing output_error");
        }
    }

    @Test
    void publicPortsForbidLegacyLooseTypes() {
        List<String> errors = new ArrayList<>();
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            for (IPort port : node.getInputPorts()) {
                if (FORBIDDEN_PORT_TYPES.contains(port.getDataType())) {
                    errors.add(id + "." + port.getId() + " input is " + port.getDataType());
                }
            }
            for (IPort port : node.getOutputPorts()) {
                if (FORBIDDEN_PORT_TYPES.contains(port.getDataType())) {
                    errors.add(id + "." + port.getId() + " output is " + port.getDataType());
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void blockPositionInputConnectedInvalidFailsWithError() {
        BlockPositionInputProbe input = new BlockPositionInputProbe();
        input.connectInput("input_x", NodeDataType.INTEGER);
        input.putRawInput("input_x", 3.9d);
        input.processNode(null);
        assertInvalid(input);
        assertTrue(String.valueOf(input.getOutput("output_error")).contains("X"));
    }

    @Test
    void constructBlockPositionRejectsNonExactInteger() {
        BaseNode construct = node("reference.points.construct_coordinate");
        construct.setInput("input_x", 4.8d);
        construct.setInput("input_y", 1);
        construct.setInput("input_z", 2);
        construct.processNode(null);
        assertInvalid(construct);
    }

    @Test
    void constructPointRejectsIntegerComponents() {
        BaseNode construct = node("reference.points.construct_point");
        construct.setInput("input_x", 1);
        construct.setInput("input_y", 2.0d);
        construct.setInput("input_z", 3.0d);
        construct.processNode(null);
        assertInvalid(construct);
    }

    @Test
    void moveAlongDirectionRequiresStrictDoubleDistance() {
        MoveAlongProbe move = new MoveAlongProbe();
        move.setInput("input_point", new PointData(0, 0, 0));
        move.setInput("input_vector", new Vector3d(1, 0, 0));
        move.connectInput("input_distance", NodeDataType.DOUBLE);
        move.putRawInput("input_distance", 1);
        move.processNode(null);
        assertInvalid(move);
    }

    @Test
    void moveAlongDirectionHugeComponentsFailClosed() {
        BaseNode move = node("reference.points.point_along_vector");
        move.setInput("input_point", new PointData(0, 0, 0));
        move.setInput("input_vector", new Vector3d(Double.MAX_VALUE, Double.MAX_VALUE, 0));
        move.setInput("input_distance", 1.0d);
        move.processNode(null);
        assertInvalid(move);
    }

    @Test
    void vectorBetweenPointsPublishesVectorData() {
        BaseNode vector = node("reference.points.vector_between_points");
        vector.setInput("input_from", new PointData(1, 2, 3));
        vector.setInput("input_to", new PointData(4, 6, 3));
        vector.processNode(null);
        assertValid(vector);
        VectorData out = assertInstanceOf(VectorData.class, vector.getOutput("output_vector"));
        assertEquals(3.0d, out.x(), 1.0e-9d);
        assertEquals(4.0d, out.y(), 1.0e-9d);
        assertEquals(0.0d, out.z(), 1.0e-9d);
        assertEquals(5.0d, vector.getOutput("output_length"));
    }

    @Test
    void translatePointOverflowFailsClosed() {
        BaseNode translate = node("reference.points.translate_point");
        translate.setInput("input_point", new PointData(1e308d, 0, 0));
        translate.setInput("input_offset", new Vector3d(1e308d, 0, 0));
        translate.processNode(null);
        assertInvalid(translate);
    }

    @Test
    void midpointHugeCoordinatesFailOrStayFinite() {
        BaseNode mid = node("reference.points.mid_point");
        mid.setInput("input_point_a", new PointData(1e308d, 0, 0));
        mid.setInput("input_point_b", new PointData(-1e308d, 0, 0));
        mid.processNode(null);
        if (Boolean.TRUE.equals(mid.getOutput("output_valid"))) {
            assertInstanceOf(PointData.class, mid.getOutput("output_midpoint"));
        } else {
            assertInvalid(mid);
        }
    }

    @Test
    void distanceNonFiniteOutputsZeroWithError() {
        BaseNode distance = node("reference.points.distance_between_points");
        distance.setInput("input_point_a", new PointData(1e308d, 0, 0));
        distance.setInput("input_point_b", new PointData(-1e308d, 0, 0));
        distance.processNode(null);
        assertInvalid(distance);
        assertEquals(0.0d, distance.getOutput("output_distance"));
    }

    @Test
    void vectorBetweenPointsNonFiniteOutputsZeroWithError() {
        BaseNode vector = node("reference.points.vector_between_points");
        vector.setInput("input_from", new PointData(1e308d, 0, 0));
        vector.setInput("input_to", new PointData(-1e308d, 0, 0));
        vector.processNode(null);
        assertInvalid(vector);
        assertEquals(0.0d, vector.getOutput("output_length"));
        assertEquals(null, vector.getOutput("output_vector"));
    }

    @Test
    void closestPointHugeDistancesDoNotThrow() {
        BaseNode closest = node("reference.points.closest_point");
        closest.setInput("input_point", new PointData(0, 0, 0));
        closest.setInput("input_coordinates", List.of(
            new PointData(1e200d, 0, 0),
            new PointData(-1e200d, 0, 0)
        ));
        closest.processNode(null);
        assertValid(closest);
        assertEquals(0, closest.getOutput("output_index"));
    }

    @Test
    void closestPointBoundedListOverCapFailsClosed() {
        BaseNode closest = node("reference.points.closest_point");
        closest.setInput("input_point", new PointData(0, 0, 0));
        closest.setInput("input_coordinates", new AbstractList<>() {
            @Override
            public Object get(int index) {
                return new PointData(index, 0, 0);
            }

            @Override
            public int size() {
                return GenerationLimits.MAX_LIST_ELEMENTS + 1;
            }
        });
        closest.processNode(null);
        assertInvalid(closest);
    }

    @Test
    void pointListCenterMalformedListFailsClosed() {
        BaseNode center = node("reference.points.point_list_center");
        center.setInput("input_points", List.of(new PointData(0, 0, 0), "not-a-point"));
        center.processNode(null);
        assertInvalid(center);
        assertEquals(0, center.getOutput("output_count"));
    }

    @Test
    void pointListCenterHugeSameSignFiniteValuesStayFinite() {
        BaseNode center = node("reference.points.point_list_center");
        center.setInput("input_points", List.of(
            new PointData(1e308d, 0, 0),
            new PointData(1e308d, 0, 0),
            new PointData(1e308d, 0, 0)
        ));
        center.processNode(null);
        assertValid(center);
        assertEquals(3, center.getOutput("output_count"));
        PointData result = assertInstanceOf(PointData.class, center.getOutput("output_center_point"));
        assertEquals(1e308d, result.position().x, 1e300d);
        assertEquals(0.0d, result.position().y, 1.0e-9d);
        assertEquals(0.0d, result.position().z, 1.0e-9d);
    }

    @Test
    void pointListCenterMixedHugeValuesStayFiniteWhenRepresentable() {
        BaseNode center = node("reference.points.point_list_center");
        center.setInput("input_points", List.of(
            new PointData(1e308d, 0, 0),
            new PointData(1e308d, 0, 0),
            new PointData(-1e308d, 0, 0)
        ));
        center.processNode(null);
        assertValid(center);
        assertEquals(3, center.getOutput("output_count"));
        PointData result = assertInstanceOf(PointData.class, center.getOutput("output_center_point"));
        assertTrue(Double.isFinite(result.position().x));
        assertTrue(Double.isFinite(result.position().y));
        assertTrue(Double.isFinite(result.position().z));
    }

    @Test
    void pointListBoundsCenterAndSizeStayFinite() {
        BaseNode bounds = node("reference.points.point_list_bounds");
        bounds.setInput("input_points", List.of(
            new PointData(0, 0, 0),
            new PointData(1, 0, 0),
            new PointData(0, 1, 0)
        ));
        bounds.processNode(null);
        assertValid(bounds);
        assertInstanceOf(PointData.class, bounds.getOutput("output_center_point"));
        assertTrue(Double.isFinite((Double) bounds.getOutput("output_size_x")));
    }

    @Test
    void getBoxFaceUnknownNameIsValidNotFound() {
        BoxGeometryData box = new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(1, 1, 1));
        GetBoxFaceProbe face = new GetBoxFaceProbe();
        face.putRawInput("input_box_geometry", box);
        face.connectInput("input_face_name", NodeDataType.STRING);
        face.putRawInput("input_face_name", "banana");
        face.processNode(null);
        assertEquals(Boolean.TRUE, face.getOutput("output_valid"));
        assertEquals(Boolean.FALSE, face.getOutput("output_found"));
        assertEquals("", face.getOutput("output_error"));
    }

    @Test
    void getBoxFaceMalformedConnectedNameIsInvalid() {
        BoxGeometryData box = new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(1, 1, 1));
        GetBoxFaceProbe face = new GetBoxFaceProbe();
        face.putRawInput("input_box_geometry", box);
        face.connectInput("input_face_name", NodeDataType.STRING);
        face.putRawInput("input_face_name", null);
        face.processNode(null);
        assertEquals(Boolean.FALSE, face.getOutput("output_valid"));
        assertEquals(Boolean.FALSE, face.getOutput("output_found"));
        assertFalse(String.valueOf(face.getOutput("output_error")).isBlank());
    }

    @Test
    void getBoxCornerOutOfRangeIndexIsValidNotFound() {
        BoxGeometryData box = new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(1, 1, 1));
        BaseNode corner = node("reference.points.get_box_corner");
        corner.setInput("input_box_geometry", box);
        corner.setInput("input_index", 99);
        corner.processNode(null);
        assertEquals(Boolean.TRUE, corner.getOutput("output_valid"));
        assertEquals(Boolean.FALSE, corner.getOutput("output_found"));
    }

    @Test
    void getFaceEdgeUsesBoxFaceValidator() {
        List<Vector3d> corners = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(2, 0, 0),
            new Vector3d(2, 2, 0),
            new Vector3d(0, 2, 1)
        );
        BoxFaceData malformed = new BoxFaceData(
            0, "front", List.of(0, 1, 2, 3), corners,
            new Vector3d(1, 1, 0.25), new Vector3d(0, 0, 1)
        );
        assertNotNull(BoxFaceValidator.validate(malformed));

        BaseNode edge = node("reference.points.get_face_edge");
        edge.setInput("input_face", malformed);
        edge.setInput("input_index", 0);
        edge.processNode(null);
        assertEquals(Boolean.FALSE, edge.getOutput("output_valid"));
        assertFalse(String.valueOf(edge.getOutput("output_error")).isBlank());
    }

    @Test
    void deconstructBoxFaceMalformedFaceFailsTransactionally() {
        List<Vector3d> corners = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(2, 0, 0),
            new Vector3d(2, 2, 0),
            new Vector3d(0, 2, 1)
        );
        BoxFaceData malformed = new BoxFaceData(
            0, "front", List.of(0, 1, 2, 3), corners,
            new Vector3d(1, 1, 0.25), new Vector3d(0, 0, 1)
        );

        BaseNode deconstruct = node("reference.points.deconstruct_face");
        deconstruct.setInput("input_face", malformed);
        deconstruct.processNode(null);
        assertInvalid(deconstruct);
        assertEquals(List.of(), deconstruct.getOutput("output_edges"));
    }

    @Test
    void deconstructFaceEdgeSafeLengthAndMidpoint() {
        BaseNode deconstruct = node("reference.points.deconstruct_edge");
        deconstruct.setInput("input_edge", new LineData(new Vec3d(0, 0, 0), new Vec3d(2, 0, 0)));
        deconstruct.processNode(null);
        assertValid(deconstruct);
        assertEquals(2.0d, deconstruct.getOutput("output_length"));
        assertInstanceOf(PointData.class, deconstruct.getOutput("output_midpoint"));
        assertInstanceOf(VectorData.class, deconstruct.getOutput("output_direction"));
        assertInstanceOf(VectorData.class, deconstruct.getOutput("output_vector"));
    }

    @Test
    void blockToPointDoesNotImplicitlyConvertPointToBlockPos() {
        INode blockToPoint = registry.createNodeInstance("reference.points.point_from_block");
        assertTrue(blockToPoint.getInputPorts().stream()
            .noneMatch(port -> port.getDataType() == NodeDataType.POINT));
        assertTrue(blockToPoint.getOutputPorts().stream()
            .noneMatch(port -> port.getDataType() == NodeDataType.BLOCK_POS));
    }

    @Test
    void deconstructPointInvalidOutputsZero() {
        BaseNode deconstruct = node("reference.points.deconstruct_point");
        deconstruct.processNode(null);
        assertInvalid(deconstruct);
        assertEquals(0.0d, deconstruct.getOutput("output_x"));
    }

    @Test
    void blockToPointRoundTripFromBlockPosition() {
        BaseNode blockToPoint = node("reference.points.point_from_block");
        blockToPoint.setInput("input_coordinate", new BlockPos(3, 4, 5));
        blockToPoint.processNode(null);
        assertValid(blockToPoint);
        assertInstanceOf(PointData.class, blockToPoint.getOutput("output_point"));
    }

    private static BaseNode node(String typeId) {
        return assertInstanceOf(BaseNode.class, registry.createNodeInstance(typeId));
    }

    private static void assertValid(BaseNode node) {
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals("", node.getOutput("output_error"));
    }

    private static void assertInvalid(BaseNode node) {
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNotNull(node.getOutput("output_error"));
        assertFalse(String.valueOf(node.getOutput("output_error")).isBlank());
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
            .filter(candidate -> candidate.getId().equals(portId))
            .findFirst()
            .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }

    private static boolean hasPort(INode node, String portId) {
        return node.getInputPorts().stream().anyMatch(p -> portId.equals(p.getId()))
            || node.getOutputPorts().stream().anyMatch(p -> portId.equals(p.getId()));
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
    }

    private static final class BlockPositionInputProbe extends CoordinateInputNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ReferencePointsLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class GetBoxFaceProbe extends GetBoxFaceNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ReferencePointsLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class MoveAlongProbe extends PointAlongVectorNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ReferencePointsLanguageV2ContractTest.connectInput(this, portId, outputType);
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

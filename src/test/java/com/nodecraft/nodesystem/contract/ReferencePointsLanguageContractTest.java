package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.reference.points.CoordinateInputNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reference Points v1 language fence (Graph V49).
 */
class ReferencePointsLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
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

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV49() {
        assertEquals(49, GraphFormatVersion.V49);
        assertEquals(GraphFormatVersion.V49, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyNineteenCanonicalReferencePointNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("reference.points."))
                .sorted()
                .toList();
        assertEquals(19, ids.size(), "Expected 19 reference.points nodes: " + ids);
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
    }

    @Test
    void referencePointNodesHaveUniqueOrderZeroThroughEighteen() {
        int[] orders = CANONICAL_IDS.stream()
                .mapToInt(typeId -> {
                    INode node = registry.createNodeInstance(typeId);
                    assertNotNull(node, typeId);
                    NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
                    assertNotNull(info, typeId);
                    return info.order();
                })
                .sorted()
                .toArray();
        assertEquals(19, orders.length);
        for (int i = 0; i < orders.length; i++) {
            assertEquals(i, orders[i], "Expected unique order " + i);
        }
    }

    @Test
    void blockPositionInputHasValidOutput() {
        assertPortType("reference.points.block_position", "output_valid", false, NodeDataType.BOOLEAN);
    }

    @Test
    void constructBlockPositionRejectsNonIntegerComponents() {
        BaseNode construct = node("reference.points.construct_coordinate");
        construct.setInput("input_x", 4.8d);
        construct.setInput("input_y", 1);
        construct.setInput("input_z", 2);
        construct.processNode(null);
        assertEquals(Boolean.FALSE, construct.getOutput("output_valid"));
        assertNull(construct.getOutput("output_block_pos"));
    }

    @Test
    void blockPositionInputConnectedDoubleFailsClosed() {
        BlockPositionInputProbe input = new BlockPositionInputProbe();
        input.putRawInput("input_x", 3.9d);
        input.processNode(null);
        assertEquals(Boolean.FALSE, input.getOutput("output_valid"));
        assertNull(input.getOutput("output_block_pos"));
    }

    @Test
    void deconstructPointInvalidOutputsAreNaN() {
        BaseNode deconstruct = node("reference.points.deconstruct_point");
        deconstruct.processNode(null);
        assertEquals(Boolean.FALSE, deconstruct.getOutput("output_valid"));
        assertEquals(Double.NaN, deconstruct.getOutput("output_x"));
        assertEquals(Double.NaN, deconstruct.getOutput("output_y"));
        assertEquals(Double.NaN, deconstruct.getOutput("output_z"));
    }

    @Test
    void closestPointMalformedPointListFailsClosed() {
        BaseNode closest = node("reference.points.closest_point");
        closest.setInput("input_point", new PointData(0, 0, 0));
        closest.setInput("input_coordinates", List.of(new PointData(1, 0, 0), new Vector3d(2, 0, 0)));
        closest.processNode(null);
        assertEquals(Boolean.FALSE, closest.getOutput("output_valid"));
        assertEquals(-1, closest.getOutput("output_index"));
        assertEquals(Double.NaN, closest.getOutput("output_distance"));
    }

    @Test
    void pointListCenterMalformedListFailsClosedWithZeroCount() {
        BaseNode center = node("reference.points.point_list_center");
        center.setInput("input_points", List.of(new PointData(0, 0, 0), "not-a-point"));
        center.processNode(null);
        assertEquals(Boolean.FALSE, center.getOutput("output_valid"));
        assertEquals(0, center.getOutput("output_count"));
    }

    @Test
    void pointListBoundsCountIsFullListSizeWhenValid() {
        BaseNode bounds = node("reference.points.point_list_bounds");
        bounds.setInput("input_points", List.of(
                new PointData(0, 0, 0),
                new PointData(1, 0, 0),
                new PointData(0, 1, 0)
        ));
        bounds.processNode(null);
        assertEquals(Boolean.TRUE, bounds.getOutput("output_valid"));
        assertEquals(3, bounds.getOutput("output_count"));
    }

    @Test
    void getBoxCornerRejectsNonIntegerIndex() {
        BoxGeometryData box = new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(1, 1, 1));
        BaseNode corner = node("reference.points.get_box_corner");
        corner.setInput("input_box_geometry", box);
        corner.setInput("input_index", 1.9d);
        corner.processNode(null);
        assertEquals(Boolean.FALSE, corner.getOutput("output_found"));
    }

    @Test
    void getBoxCornerNegativeIndexResolvesWhenEnabled() {
        BoxGeometryData box = new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(1, 1, 1));
        BaseNode corner = node("reference.points.get_box_corner");
        corner.setInput("input_box_geometry", box);
        corner.setInput("input_index", -1);
        corner.processNode(null);
        assertEquals(Boolean.TRUE, corner.getOutput("output_found"));
        assertInstanceOf(PointData.class, corner.getOutput("output_corner"));
    }

    @Test
    void getBoxFaceConnectedNameBeatsIndex() {
        BoxGeometryData box = new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(1, 1, 1));
        BaseNode face = node("reference.points.get_box_face");
        face.setInput("input_box_geometry", box);
        face.setInput("input_face_name", "top");
        face.setInput("input_index", 0);
        face.processNode(null);
        assertEquals(Boolean.TRUE, face.getOutput("output_found"));
        assertEquals("Top", face.getOutput("output_name"));
    }

    @Test
    void getBoxFaceInvalidConnectedNameFailsClosed() {
        BoxGeometryData box = new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(1, 1, 1));
        BaseNode face = node("reference.points.get_box_face");
        face.setInput("input_box_geometry", box);
        face.setInput("input_face_name", "not-a-face");
        face.setInput("input_index", 0);
        face.processNode(null);
        assertEquals(Boolean.FALSE, face.getOutput("output_found"));
        assertNull(face.getOutput("output_face"));
    }

    @Test
    void deconstructBoxFaceUsesTypedTopologyOutputs() {
        assertPortType("reference.points.deconstruct_face", "output_edges", false, NodeDataType.LINE_LIST);
        assertPortType("reference.points.deconstruct_face", "output_corner_indices", false, NodeDataType.INTEGER_LIST);
        assertPortType("reference.points.deconstruct_face", "output_valid", false, NodeDataType.BOOLEAN);
        assertFalse(hasPort("reference.points.deconstruct_face", "output_edge_corner_index_pairs", false));
    }

    @Test
    void deconstructBoxFaceInvalidInputFailsClosed() {
        BaseNode deconstruct = node("reference.points.deconstruct_face");
        deconstruct.processNode(null);
        assertEquals(Boolean.FALSE, deconstruct.getOutput("output_valid"));
        assertEquals(List.of(), deconstruct.getOutput("output_edges"));
    }

    @Test
    void deconstructFaceEdgeRejectsZeroLengthLine() {
        BaseNode deconstruct = node("reference.points.deconstruct_edge");
        deconstruct.setInput("input_edge", new LineData(new Vec3d(1, 2, 3), new Vec3d(1, 2, 3)));
        deconstruct.processNode(null);
        assertEquals(Boolean.FALSE, deconstruct.getOutput("output_valid"));
        assertEquals(Double.NaN, deconstruct.getOutput("output_length"));
    }

    @Test
    void translatePointUsesVectorResolver() {
        BaseNode translate = node("reference.points.translate_point");
        translate.setInput("input_point", new PointData(1, 2, 3));
        translate.setInput("input_offset", new PointData(0, 1, 0));
        translate.processNode(null);
        assertEquals(Boolean.FALSE, translate.getOutput("output_valid"));
        assertNull(translate.getOutput("output_point"));

        translate.setInput("input_offset", new Vector3d(0, 1, 0));
        translate.processNode(null);
        assertEquals(Boolean.TRUE, translate.getOutput("output_valid"));
        assertInstanceOf(PointData.class, translate.getOutput("output_point"));
    }

    @Test
    void movePointAlongDirectionUsesVectorResolver() {
        BaseNode move = node("reference.points.point_along_vector");
        move.setInput("input_point", new PointData(0, 0, 0));
        move.setInput("input_vector", new PointData(1, 0, 0));
        move.setInput("input_distance", 2.0d);
        move.processNode(null);
        assertEquals(Boolean.FALSE, move.getOutput("output_valid"));

        move.setInput("input_vector", new Vector3d(0, 0, 1));
        move.processNode(null);
        assertEquals(Boolean.TRUE, move.getOutput("output_valid"));
        assertInstanceOf(PointData.class, move.getOutput("output_point"));
    }

    private static BaseNode node(String typeId) {
        BaseNode node = (BaseNode) registry.createNodeInstance(typeId);
        assertNotNull(node, typeId);
        return node;
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        assertNotNull(node);
        IPort port = (input ? node.getInputPorts() : node.getOutputPorts()).stream()
                .filter(candidate -> candidate.getId().equals(portId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(typeId + " missing port " + portId));
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }

    private static boolean hasPort(String typeId, String portId, boolean input) {
        INode node = registry.createNodeInstance(typeId);
        return (input ? node.getInputPorts() : node.getOutputPorts()).stream()
                .anyMatch(port -> port.getId().equals(portId));
    }

    /** Bypasses port compatibility so runtime strict-Integer behavior can be exercised. */
    private static final class BlockPositionInputProbe extends CoordinateInputNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }
}

package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.reference.points.BlockToPointNode;
import com.nodecraft.nodesystem.nodes.reference.points.ConstructPointNode;
import com.nodecraft.nodesystem.nodes.reference.points.CoordinateInputNode;
import com.nodecraft.nodesystem.nodes.reference.points.MidpointNode;
import com.nodecraft.nodesystem.nodes.reference.points.PointAlongVectorNode;
import com.nodecraft.nodesystem.nodes.reference.points.PointListBoundsNode;
import com.nodecraft.nodesystem.nodes.reference.points.PointListCenterNode;
import com.nodecraft.nodesystem.nodes.reference.points.TranslatePointNode;
import com.nodecraft.nodesystem.nodes.reference.points.VectorBetweenPointsNode;
import com.nodecraft.nodesystem.nodes.reference.vectors.AngleBetweenVectorsNode;
import com.nodecraft.nodesystem.nodes.reference.vectors.SlerpVectorsNode;
import com.nodecraft.nodesystem.nodes.transform.orientation.ProjectPointToPlaneNode;
import com.nodecraft.core.exception.NodeValidationException;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freezes Point/Vector Node Language v1 semantics.
 */
class PointVectorLanguageContractTest {

    private static final Set<String> NO_POSITION_AS_VECTOR_NODE_IDS = Set.of(
            "reference.points.mid_point",
            "reference.points.point_from_block",
            "reference.points.point_along_vector",
            "reference.points.point_list_center",
            "transform.orientation.project_to_plane"
    );

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV28() {
        assertEquals(28, GraphFormatVersion.V28);
        assertEquals(29, GraphFormatVersion.V29);
        assertEquals(30, GraphFormatVersion.V30);
        assertEquals(33, GraphFormatVersion.V33);
        assertEquals(34, GraphFormatVersion.V34);
        assertEquals(35, GraphFormatVersion.V35);
        assertEquals(36, GraphFormatVersion.V36);
        assertEquals(GraphFormatVersion.V58, GraphFormatVersion.CURRENT);
    }

    @Test
    void pointProducersDoNotEmitPositionAsVector() {
        List<String> violations = new ArrayList<>();
        for (String typeId : NO_POSITION_AS_VECTOR_NODE_IDS) {
            INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
            if (node == null) {
                violations.add("missing: " + typeId);
                continue;
            }
            for (IPort port : node.getOutputPorts()) {
                if (port != null && port.getDataType() == NodeDataType.VECTOR
                        && port.getId().toLowerCase(Locale.ROOT).contains("vector")) {
                    violations.add(typeId + " emits position-as-vector port " + port.getId());
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    void angleBetweenVectorsIsDegreesOnly() {
        AngleBetweenVectorsNode node = new AngleBetweenVectorsNode();
        assertNotNull(findPort(node, "output_angle"));
        assertNotNull(findPort(node, "output_signed_angle"));
        assertFalse(hasPort(node, "output_radians"));
        assertFalse(hasPort(node, "output_signed_radians"));
        assertFalse(hasPort(node, "output_degrees"));
    }

    @Test
    void slerpVectorsAngleOutputIsDegrees() {
        SlerpVectorsNode node = new SlerpVectorsNode();
        IPort anglePort = findPort(node, "output_angle");
        assertEquals("Angle", anglePort.getDisplayName());
        assertTrue(anglePort.getDescription().toLowerCase(java.util.Locale.ROOT).contains("degrees"));
        assertFalse(anglePort.getDisplayName().toLowerCase(java.util.Locale.ROOT).contains("rad"));
        assertFalse(hasPort(node, "output_angle_radians"));

        node.setInput("input_a", new Vector3d(1, 0, 0));
        node.setInput("input_b", new Vector3d(0, 1, 0));
        node.setInput("input_t", 0.5d);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        double angle = (Double) node.getOutput("output_angle");
        assertTrue(Math.abs(angle - 90.0d) < 0.01d);
    }

    @Test
    void pointListNodesUsePointListInput() {
        assertEquals(NodeDataType.POINT_LIST, findPort(new PointListCenterNode(), "input_points").getDataType());
        assertEquals(NodeDataType.POINT_LIST, findPort(new PointListBoundsNode(), "input_points").getDataType());
        assertFalse(hasPort(new PointListCenterNode(), "output_center_vector"));
        assertFalse(hasPort(new PointListBoundsNode(), "output_region"));
    }

    @Test
    void movePointAlongDirectionHasNoNormalizeProperty() {
        PointAlongVectorNode node = new PointAlongVectorNode();
        assertEquals("Move Point Along Direction", node.getDisplayName());
        assertFalse(hasPort(node, "output_vector"));
        assertFalse(hasPort(node, "output_direction"));
        assertNull(node.getNodeState() instanceof java.util.Map<?, ?> map ? map.get("normalizeDirection") : null);
    }

    @Test
    void newCoreSpatialNodesExist() {
        assertNotNull(NodeRegistry.getInstance().createNodeInstance("reference.points.construct_point"));
        assertNotNull(NodeRegistry.getInstance().createNodeInstance("reference.points.translate_point"));
        assertNotNull(NodeRegistry.getInstance().createNodeInstance("reference.points.vector_between_points"));

        BaseNode construct = (BaseNode) new ConstructPointNode();
        construct.setInput("input_x", 1.0d);
        construct.setInput("input_y", 2.0d);
        construct.setInput("input_z", 3.0d);
        construct.processNode(null);
        assertInstanceOf(PointData.class, construct.getOutput("output_point"));

        BaseNode translate = (BaseNode) new TranslatePointNode();
        translate.setInput("input_point", new PointData(0, 0, 0));
        translate.setInput("input_offset", new Vector3d(1, 2, 3));
        translate.processNode(null);
        assertInstanceOf(PointData.class, translate.getOutput("output_point"));

        BaseNode between = (BaseNode) new VectorBetweenPointsNode();
        between.setInput("input_from", new PointData(0, 0, 0));
        between.setInput("input_to", new PointData(3, 4, 0));
        between.processNode(null);
        assertEquals(Boolean.TRUE, between.getOutput("output_valid"));
        assertEquals(5.0d, (Double) between.getOutput("output_length"), 1.0e-9);
    }

    @Test
    void deletedNodesAreNotRegistered() {
        NodeRegistry registry = NodeRegistry.getInstance();
        assertThrows(NodeValidationException.class,
            () -> registry.createNodeInstance("reference.points.block_to_vector"));
        assertThrows(NodeValidationException.class,
            () -> registry.createNodeInstance("reference.points.closest_point_to_object"));
    }

    @Test
    void blockProducersEmitBlockPosOnly() {
        CoordinateInputNode input = new CoordinateInputNode();
        assertEquals(NodeDataType.BLOCK_POS, findPort(input, "output_block_pos").getDataType());
        assertFalse(hasPort(input, "output_coordinate"));
        assertFalse(hasPort(input, "output_x"));

        BlockToPointNode blockToPoint = new BlockToPointNode();
        assertFalse(hasPort(blockToPoint, "output_vector"));
        assertFalse(hasPort(blockToPoint, "output_x"));
    }

    @Test
    void midPointAndProjectToPlaneAreClean() {
        assertFalse(hasPort(new MidpointNode(), "output_vector"));
        assertFalse(hasPort(new ProjectPointToPlaneNode(), "output_vector"));
    }

    @Test
    void v18ToV19MigrationDropsLegacyPortsAndDeletedNodes() {
        SavedGraph v18 = new SavedGraph();
        v18.formatVersion = GraphFormatVersion.V18;

        SavedNode mid = new SavedNode();
        mid.nodeId = "mid";
        mid.typeId = "reference.points.mid_point";
        SavedNode deleted = new SavedNode();
        deleted.nodeId = "deleted";
        deleted.typeId = "reference.points.block_to_vector";
        SavedNode sink = new SavedNode();
        sink.nodeId = "sink";
        sink.typeId = "reference.vectors.vector_length";
        v18.nodes = new ArrayList<>(List.of(mid, deleted, sink));

        SavedConnection legacyVector = new SavedConnection();
        legacyVector.sourceNodeId = "mid";
        legacyVector.sourcePortId = "output_vector";
        legacyVector.targetNodeId = "sink";
        legacyVector.targetPortId = "input_vector";

        SavedConnection toDeleted = new SavedConnection();
        toDeleted.sourceNodeId = "mid";
        toDeleted.sourcePortId = "output_midpoint";
        toDeleted.targetNodeId = "deleted";
        toDeleted.targetPortId = "input_coordinate";

        v18.connections = new ArrayList<>(List.of(legacyVector, toDeleted));
        v18.nodePositions = java.util.Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v18);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals(2, migrated.nodes.size());
        assertTrue(migrated.nodes.stream().noneMatch(n -> "reference.points.block_to_vector".equals(n.typeId)));
        assertTrue(migrated.connections.isEmpty());
    }

    private static boolean hasPort(INode node, String portId) {
        return findPortOrNull(node, portId) != null;
    }

    private static IPort findPort(INode node, String portId) {
        IPort port = findPortOrNull(node, portId);
        assertNotNull(port, "missing port " + portId + " on " + node.getTypeId());
        return port;
    }

    private static IPort findPortOrNull(INode node, String portId) {
        for (IPort port : node.getInputPorts()) {
            if (port != null && portId.equalsIgnoreCase(port.getId())) {
                return port;
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (port != null && portId.equalsIgnoreCase(port.getId())) {
                return port;
            }
        }
        return null;
    }
}

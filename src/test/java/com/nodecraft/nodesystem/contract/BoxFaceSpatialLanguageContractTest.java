package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.reference.points.DeconstructFaceEdgeNode;
import com.nodecraft.nodesystem.nodes.reference.points.GetBoxCornerNode;
import com.nodecraft.nodesystem.nodes.reference.points.GetBoxFaceNode;
import com.nodecraft.nodesystem.nodes.reference.points.GetFaceEdgeNode;
import com.nodecraft.nodesystem.nodes.world.selection.PointToBlockIfGridNode;
import com.nodecraft.nodesystem.nodes.world.selection.SnapPointListToBlocksNode;
import com.nodecraft.nodesystem.nodes.world.selection.SnapPointToBlockNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freezes remaining reference.points box/face getters and world.selection Point↔BlockPos typing.
 */
class BoxFaceSpatialLanguageContractTest {

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void getBoxCornerEmitsPointNotVector() {
        GetBoxCornerNode node = new GetBoxCornerNode();
        assertEquals(NodeDataType.POINT, findPort(node, "output_corner").getDataType());
        assertFalse(hasVectorNamedPort(node, "corner"));
    }

    @Test
    void getBoxFaceIsKeepProducer() {
        GetBoxFaceNode node = new GetBoxFaceNode();
        assertEquals(NodeDataType.BOX_FACE, findPort(node, "output_face").getDataType());
        assertEquals(NodeDataType.BOX_GEOMETRY, findPort(node, "input_box_geometry").getDataType());
    }

    @Test
    void getFaceEdgeIsSlimmedToLine() {
        GetFaceEdgeNode node = new GetFaceEdgeNode();
        assertEquals(NodeDataType.LINE, findPort(node, "output_edge").getDataType());
        assertFalse(hasPort(node, "output_start"));
        assertFalse(hasPort(node, "output_end"));
        assertFalse(hasPort(node, "output_start_corner_index"));
        assertFalse(hasPort(node, "output_end_corner_index"));
    }

    @Test
    void deconstructFaceEdgePositionsArePoints() {
        DeconstructFaceEdgeNode node = new DeconstructFaceEdgeNode();
        assertEquals(NodeDataType.POINT, findPort(node, "output_start").getDataType());
        assertEquals(NodeDataType.POINT, findPort(node, "output_end").getDataType());
        assertEquals(NodeDataType.POINT, findPort(node, "output_midpoint").getDataType());
        assertEquals(NodeDataType.VECTOR, findPort(node, "output_direction").getDataType());
        assertEquals(NodeDataType.VECTOR, findPort(node, "output_vector").getDataType());
        assertFalse(hasPort(node, "input_start_corner_index"));
        assertFalse(hasPort(node, "output_start_corner_index"));
    }

    @Test
    void snapPointNodesUseTypedPointPorts() {
        SnapPointToBlockNode snap = new SnapPointToBlockNode();
        assertEquals(NodeDataType.POINT, findPort(snap, "input_point").getDataType());
        assertEquals(NodeDataType.BLOCK_POS, findPort(snap, "output_coordinate").getDataType());
        assertFalse(hasPort(snap, "output_x"));

        SnapPointListToBlocksNode snapList = new SnapPointListToBlocksNode();
        assertEquals(NodeDataType.POINT_LIST, findPort(snapList, "input_points").getDataType());

        PointToBlockIfGridNode ifGrid = new PointToBlockIfGridNode();
        assertEquals(NodeDataType.POINT, findPort(ifGrid, "input_point").getDataType());
    }

    @Test
    void v19ToV20MigrationDropsSlimmedFaceEdgePorts() {
        SavedGraph v19 = new SavedGraph();
        v19.formatVersion = GraphFormatVersion.V19;

        SavedNode edge = new SavedNode();
        edge.nodeId = "edge";
        edge.typeId = "reference.points.get_face_edge";
        SavedNode sink = new SavedNode();
        sink.nodeId = "sink";
        sink.typeId = "reference.vectors.vector_length";
        v19.nodes = new ArrayList<>(List.of(edge, sink));

        SavedConnection legacyStart = new SavedConnection();
        legacyStart.sourceNodeId = "edge";
        legacyStart.sourcePortId = "output_start";
        legacyStart.targetNodeId = "sink";
        legacyStart.targetPortId = "input_vector";

        SavedConnection kept = new SavedConnection();
        kept.sourceNodeId = "edge";
        kept.sourcePortId = "output_found";
        kept.targetNodeId = "sink";
        kept.targetPortId = "input_vector";

        v19.connections = new ArrayList<>(List.of(legacyStart, kept));
        v19.nodePositions = java.util.Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v19);
        assertEquals(GraphFormatVersion.V24, migrated.formatVersion);
        assertEquals(1, migrated.connections.size());
        assertEquals("output_found", migrated.connections.getFirst().sourcePortId);
    }

    private static boolean hasVectorNamedPort(INode node, String nameFragment) {
        for (IPort port : node.getOutputPorts()) {
            if (port != null && port.getDataType() == NodeDataType.VECTOR
                    && port.getId().toLowerCase().contains(nameFragment)) {
                return true;
            }
        }
        return false;
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

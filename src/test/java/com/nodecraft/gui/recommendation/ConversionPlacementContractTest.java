package com.nodecraft.gui.recommendation;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.math.list_sequence.CreateListNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port-drag VIA_CONVERSION placement: target at drop, conversion to the left.
 */
class ConversionPlacementContractTest {

    @BeforeAll
    static void ensureRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
        NodeRecommendations.get().initialize();
        NodeRecommendations.get().reloadRules();
        NodeRecommendations.get().invalidateCache();
    }

    @Test
    void portDragConversionKeepsTargetAtDropPosition() {
        NodeGraph graph = new NodeGraph();
        CreateListNode list = new CreateListNode();
        list.setPosition(100, 100);
        graph.addNode(list);

        NodeRecommendationContext context = new NodeRecommendationContext(
            RecommendationTrigger.PORT_DRAG,
            RecommendationDirection.DOWNSTREAM,
            list.getId(),
            "output_list",
            NodeDataType.LIST,
            1000f,
            500f,
            24);

        List<NodeRecommendation> recommendations =
            NodeRecommendations.get().recommend(graph, context);
        NodeRecommendation viaTree = recommendations.stream()
            .filter(rec -> rec.connectionPlan() == NodeRecommendation.ConnectionPlan.VIA_CONVERSION)
            .filter(rec -> rec.connectPortType() == NodeDataType.DATA_TREE)
            .findFirst()
            .orElse(null);
        assertNotNull(viaTree, "expected a LIST→DATA_TREE VIA_CONVERSION recommendation, got "
            + recommendations.stream().map(NodeRecommendation::nodeId).toList());

        RecordingCanvasEditor editor = new RecordingCanvasEditor(graph);
        NodeRecommendationApplyResult result =
            NodeRecommendations.get().apply(editor, graph, context, viaTree);
        assertTrue(result.success(), result.message());

        List<RecordingCanvasEditor.Placement> placements = editor.placements();
        assertEquals(2, placements.size(), "conversion chain should create two nodes, got " + placements);

        RecordingCanvasEditor.Placement conversion = placements.get(0);
        RecordingCanvasEditor.Placement target = placements.get(1);

        assertEquals(1000f, target.x(), 0.01f, "target must land at drop X");
        assertEquals(500f, target.y(), 0.01f, "target must land at drop Y");
        assertEquals(780f, conversion.x(), 0.01f, "conversion must be dropX - portDragOffset(220)");
        assertEquals(500f, conversion.y(), 0.01f, "conversion must share drop Y");
        assertFalse(conversion.nodeTypeId().equalsIgnoreCase(viaTree.nodeId()),
            "first placement should be the conversion node, not the target");
        assertTrue(target.nodeTypeId().equalsIgnoreCase(viaTree.nodeId()),
            "second placement should be the recommended target");
    }
}

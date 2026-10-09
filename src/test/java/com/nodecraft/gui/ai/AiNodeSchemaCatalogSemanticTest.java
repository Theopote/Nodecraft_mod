package com.nodecraft.gui.ai;

import com.nodecraft.nodesystem.semantic.NodeCapability;
import com.nodecraft.nodesystem.semantic.NodeSemanticCatalog;
import com.nodecraft.nodesystem.semantic.NodeSemanticEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiNodeSchemaCatalogSemanticTest {

    @Test
    void enrichFromCatalogAttachesCapabilitiesAndEdges() {
        String wallId = "geometry.architectural_primitives.wall_slab";
        NodeSemanticCatalog.get().refreshIfNeeded();

        AiNodeSchemaCatalog.NodeSchema bare = new AiNodeSchemaCatalog.NodeSchema(
                wallId,
                "Wall Slab",
                "test",
                "geometry.architectural_primitives",
                List.of(),
                List.of(),
                List.of()
        );
        AiNodeSchemaCatalog.NodeSchema enriched = AiNodeSchemaCatalog.enrichFromCatalog(bare);

        assertTrue(enriched.capabilities().contains("WALL"));
        assertTrue(NodeSemanticCatalog.get().capabilities(wallId).contains(NodeCapability.WALL));

        List<NodeSemanticEdge> downstream = NodeSemanticCatalog.get().downstream(wallId);
        assertEquals(downstream.isEmpty(), enriched.recommendedNext().isEmpty());
        if (!downstream.isEmpty()) {
            assertFalse(enriched.recommendedNext().isEmpty());
            assertEquals(downstream.getFirst().targetNodeId(), enriched.recommendedNext().getFirst().nodeId());
        }
    }
}

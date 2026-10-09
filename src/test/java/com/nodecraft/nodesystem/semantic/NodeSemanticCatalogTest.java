package com.nodecraft.nodesystem.semantic;

import com.nodecraft.nodesystem.api.NodeEffect;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeSemanticCatalogTest {

    @Test
    void wallSlabDerivesWallCapability() {
        Set<NodeCapability> caps = NodeSemanticCatalog.get()
                .capabilities("geometry.architectural_primitives.wall_slab");
        assertTrue(caps.contains(NodeCapability.WALL));
    }

    @Test
    void differenceDerivesBooleanCut() {
        Set<NodeCapability> caps = NodeSemanticCatalog.get()
                .capabilities("geometry.boolean.difference");
        assertTrue(caps.contains(NodeCapability.BOOLEAN_CUT));
    }

    @Test
    void previewDerivesPreviewCapability() {
        Set<NodeCapability> caps = NodeSemanticCatalog.get()
                .capabilities("output.preview.preview_blocks");
        assertTrue(caps.contains(NodeCapability.PREVIEW));
        assertEquals(NodeEffect.PREVIEW_WRITE,
                NodeSemanticCatalog.get().effect("output.preview.preview_blocks"));
    }

    @Test
    void surfaceStripIsPureVoxelize() {
        String id = "geometry.voxel.surface_strip_to_blocks";
        assertEquals(NodeEffect.PURE, NodeSemanticCatalog.get().effect(id));
        assertTrue(NodeSemanticCatalog.get().capabilities(id).contains(NodeCapability.VOXELIZE));
    }

    @Test
    void domainsStaySeparateFromCapabilities() {
        Set<NodeDomain> domains = NodeSemanticDeriver.deriveDomains(
                "geometry.architectural_primitives.wall_slab",
                "geometry.architectural_primitives");
        assertTrue(domains.contains(NodeDomain.ARCHITECTURE));
        assertTrue(domains.contains(NodeDomain.GEOMETRY));
        Set<NodeCapability> caps = NodeSemanticDeriver.deriveCapabilities(
                "geometry.architectural_primitives.wall_slab",
                "geometry.architectural_primitives",
                NodeEffect.PURE);
        assertTrue(caps.contains(NodeCapability.WALL));
        assertTrue(caps.stream().noneMatch(c -> c.name().equals("ARCHITECTURE")));
    }

    @Test
    void invalidateRulesBumpsRevision() {
        long before = NodeSemanticCatalog.getRulesRevision();
        NodeSemanticCatalog.get().invalidateRules();
        assertTrue(NodeSemanticCatalog.getRulesRevision() > before);
    }
}

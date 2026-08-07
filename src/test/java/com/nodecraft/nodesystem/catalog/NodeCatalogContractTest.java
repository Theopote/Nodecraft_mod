package com.nodecraft.nodesystem.catalog;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract gates for the build-time {@link GeneratedNodeCatalog}.
 */
class NodeCatalogContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void catalogHasSubstantialCoverage() {
        assertTrue(NodeCatalog.entryCount() >= 400,
                "expected a large built-in catalog, was " + NodeCatalog.entryCount());
        assertEquals(GeneratedNodeCatalog.ENTRY_COUNT, GeneratedNodeCatalog.IDS.length);
        assertEquals(GeneratedNodeCatalog.ENTRY_COUNT, NodeCatalog.entryCount());
    }

    @Test
    void catalogIdsAreUnique() {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (String id : GeneratedNodeCatalog.IDS) {
            if (!seen.add(id)) {
                duplicates.add(id);
            }
        }
        assertTrue(duplicates.isEmpty(), "duplicate catalog ids: " + duplicates);
    }

    @Test
    void registryContainsEveryCatalogId() {
        List<String> missing = new ArrayList<>();
        for (String id : GeneratedNodeCatalog.IDS) {
            NodeInfo info = registry.getNodeInfo(id);
            if (info == null || info.getNodeClass() == null) {
                missing.add(id);
            }
        }
        assertTrue(missing.isEmpty(),
                "catalog ids missing from registry (" + missing.size() + "): "
                        + missing.subList(0, Math.min(12, missing.size())));
        assertTrue(registry.getNodeCount() >= NodeCatalog.entryCount());
    }
}

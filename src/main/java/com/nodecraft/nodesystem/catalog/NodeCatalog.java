package com.nodecraft.nodesystem.catalog;

import com.nodecraft.nodesystem.registry.NodeRegistry;

/**
 * Facade over the build-time {@link GeneratedNodeCatalog}.
 */
public final class NodeCatalog {

    private NodeCatalog() {
    }

    /**
     * @return number of catalog entries emitted at build time
     */
    public static int entryCount() {
        return GeneratedNodeCatalog.ENTRY_COUNT;
    }

    /**
     * Registers all build-time catalog entries into {@code registry}.
     *
     * @return number of successful registrations
     */
    public static int registerAll(NodeRegistry registry) {
        return GeneratedNodeCatalog.register(registry);
    }
}

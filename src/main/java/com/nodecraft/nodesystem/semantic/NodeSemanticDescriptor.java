package com.nodecraft.nodesystem.semantic;

import com.nodecraft.nodesystem.api.NodeEffect;

import java.util.List;
import java.util.Set;

/**
 * Read-only semantic view of one registered node.
 */
public record NodeSemanticDescriptor(
        String nodeId,
        String category,
        NodeEffect effect,
        Set<NodeDomain> domains,
        Set<NodeCapability> capabilities,
        List<NodeSemanticEdge> downstream,
        List<NodeSemanticEdge> upstream
) {
    public NodeSemanticDescriptor {
        domains = domains == null || domains.isEmpty() ? Set.of() : Set.copyOf(domains);
        capabilities = capabilities == null || capabilities.isEmpty()
                ? Set.of()
                : Set.copyOf(capabilities);
        downstream = downstream == null || downstream.isEmpty() ? List.of() : List.copyOf(downstream);
        upstream = upstream == null || upstream.isEmpty() ? List.of() : List.copyOf(upstream);
    }
}

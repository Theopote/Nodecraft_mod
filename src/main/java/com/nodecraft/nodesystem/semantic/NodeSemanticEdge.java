package com.nodecraft.nodesystem.semantic;

/**
 * Derived workflow edge from recommendation rules (not a separately stored table).
 */
public record NodeSemanticEdge(
        String targetNodeId,
        String sourcePortId,
        String targetPortId,
        String reason,
        int priority
) {
}

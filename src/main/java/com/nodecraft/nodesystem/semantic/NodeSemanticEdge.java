package com.nodecraft.nodesystem.semantic;

/**
 * Derived workflow edge from recommendation rules (not a separately stored table).
 */
public record NodeSemanticEdge(
        String targetNodeId,
        String sourcePortId,
        String targetPortId,
        String reason,
        int priority,
        NodeSemanticEdgeKind kind
) {
    public NodeSemanticEdge {
        if (kind == null) {
            kind = NodeSemanticEdgeKind.EXACT;
        }
    }

    /** Backward-compatible constructor defaulting to {@link NodeSemanticEdgeKind#EXACT}. */
    public NodeSemanticEdge(
            String targetNodeId,
            String sourcePortId,
            String targetPortId,
            String reason,
            int priority
    ) {
        this(targetNodeId, sourcePortId, targetPortId, reason, priority, NodeSemanticEdgeKind.EXACT);
    }
}

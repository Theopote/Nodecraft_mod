package com.nodecraft.nodesystem.semantic;

/**
 * Provenance of a semantic edge projected from recommendation rules.
 * Lower ordinal = higher trust (Composer search cost can map EXACT=1, CATEGORY=3, TYPE=5).
 */
public enum NodeSemanticEdgeKind {
    EXACT,
    CATEGORY,
    TYPE
}

package com.nodecraft.nodesystem.api;

/**
 * Semantic element kind for list port types. Typed lists compare compatibility by kind
 * rather than erased {@link java.util.List} runtime class.
 */
public enum ListElementKind {
    NONE,
    UNCONSTRAINED,
    BLOCK_POS,
    VECTOR,
    REGION,
    BLOCK_INFO,
    BLOCK_PLACEMENT,
    PLANT_STRUCTURE,
    L_SYSTEM_RULE,
    PLANT_BLOCK
}

package com.nodecraft.nodesystem.api;

/**
 * Semantic element kind for list port types. Typed lists compare compatibility by kind
 * rather than erased {@link java.util.List} runtime class.
 */
public enum ListElementKind {
    NONE,
    UNCONSTRAINED,
    INTEGER,
    DOUBLE,
    BOOLEAN,
    STRING,
    BLOCK_POS,
    POINT,
    VECTOR,
    PLANE,
    FRAME,
    PATH,
    LINE,
    TREE_PATH,
    POLYGON_PROFILE,
    REGION,
    BLOCK_INFO,
    BLOCK_PLACEMENT,
    PLANT_STRUCTURE,
    L_SYSTEM_RULE,
    PLANT_BLOCK,
    COLOR
}

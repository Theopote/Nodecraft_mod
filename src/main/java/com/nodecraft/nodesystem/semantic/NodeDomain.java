package com.nodecraft.nodesystem.semantic;

/**
 * Modeling domain tags. Orthogonal to {@link NodeCapability} — a node may be
 * {@code GEOMETRY + BOOLEAN_CUT} or {@code ARCHITECTURE + WALL}.
 */
public enum NodeDomain {
    ARCHITECTURE,
    CURVE,
    PROFILE,
    TERRAIN,
    MATERIAL,
    SDF,
    FIELD,
    ARRAY,
    WORLD,
    MATH,
    DATA_TREE,
    GEOMETRY
}

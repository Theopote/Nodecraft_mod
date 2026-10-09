package com.nodecraft.nodesystem.semantic;

/**
 * Product-level modeling capabilities. Kept coarse on purpose — port and edge roles
 * live on {@link NodeSemanticEdge}, not as fine-grained capability enums.
 */
public enum NodeCapability {
    WALL,
    OPENING,
    WINDOW,
    ROOF,
    ARRAY,
    BOOLEAN_CUT,
    MATERIAL,
    PREVIEW,
    SPHERE,
    BOX,
    CURVE,
    TERRAIN,
    SDF,
    FIELD,
    WORLD_APPLY,
    VOXELIZE,
    SWEEP,
    EXTRUDE,
    APPLY
}

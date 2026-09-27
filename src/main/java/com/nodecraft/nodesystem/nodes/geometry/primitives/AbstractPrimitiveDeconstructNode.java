package com.nodecraft.nodesystem.nodes.geometry.primitives;

import java.util.UUID;

/**
 * Lightweight deconstruct base: Valid + Error ports and shared invalid helpers (Graph V74).
 */
abstract class AbstractPrimitiveDeconstructNode extends AbstractPrimitiveNode {

    protected AbstractPrimitiveDeconstructNode(String typeName) {
        super(UUID.randomUUID(), typeName);
    }
}

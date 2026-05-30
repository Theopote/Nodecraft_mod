package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.core.BaseNode;

import java.util.UUID;

abstract class AbstractCurveNode extends BaseNode {

    protected AbstractCurveNode(UUID id, String typeName) {
        super(id, typeName);
    }
}
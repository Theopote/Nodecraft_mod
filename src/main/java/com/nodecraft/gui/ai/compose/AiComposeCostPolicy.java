package com.nodecraft.gui.ai.compose;

import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.semantic.NodeSemanticEdgeKind;

/**
 * Deterministic edge / node costs for Composer UCS. Keep weights few and fixed.
 */
public final class AiComposeCostPolicy {

    public static final int COST_EXACT = 1;
    public static final int COST_CATEGORY = 3;
    public static final int COST_TYPE = 5;
    public static final int COST_CONVERSION = 4;
    public static final int COST_NEW_NODE = 1;
    public static final int COST_WORLD_READ = 1;

    public static final int DEFAULT_MAX_NODES = 12;

    private AiComposeCostPolicy() {
    }

    public static int edgeCost(NodeSemanticEdgeKind kind) {
        if (kind == null) {
            return COST_TYPE;
        }
        return switch (kind) {
            case EXACT -> COST_EXACT;
            case CATEGORY -> COST_CATEGORY;
            case TYPE -> COST_TYPE;
        };
    }

    public static int nodePenalty(NodeEffect effect) {
        int cost = COST_NEW_NODE;
        if (effect == NodeEffect.WORLD_READ) {
            cost += COST_WORLD_READ;
        }
        return cost;
    }
}

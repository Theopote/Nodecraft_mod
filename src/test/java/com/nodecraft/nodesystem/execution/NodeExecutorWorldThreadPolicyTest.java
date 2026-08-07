package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.nodes.math.logic.IfNode;
import com.nodecraft.nodesystem.nodes.output.execute.ApplyChangesNode;
import com.nodecraft.nodesystem.nodes.world.query.GetNeighborBlocksNode;
import com.nodecraft.nodesystem.nodes.world.read.GetBlockNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeExecutorWorldThreadPolicyTest {

    @Test
    void worldAccessNodesRunOnWorldThread() {
        assertTrue(NodeExecutor.requiresWorldThread(new GetNeighborBlocksNode()));
        assertTrue(NodeExecutor.requiresWorldThread(new GetBlockNode()));
        assertTrue(NodeExecutor.requiresWorldThread(new ApplyChangesNode()));
    }

    @Test
    void permanentSideEffectNodesAreDetectedByTypePrefix() {
        assertTrue(NodeExecutor.isPermanentSideEffectNode(new ApplyChangesNode()));
        assertFalse(NodeExecutor.isPermanentSideEffectNode(new IfNode()));
    }
}

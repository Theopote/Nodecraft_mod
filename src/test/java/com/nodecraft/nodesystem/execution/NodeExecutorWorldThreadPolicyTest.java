package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.nodes.math.logic.IfNode;
import com.nodecraft.nodesystem.nodes.output.execute.ApplyChangesNode;
import com.nodecraft.nodesystem.nodes.world.query.GetNeighborBlocksNode;
import com.nodecraft.nodesystem.nodes.world.read.GetBlockNode;
import com.nodecraft.nodesystem.nodes.world.write.SetBlockNode;
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
    void previewForbiddenNodesAreDetectedByNodeEffect() {
        assertTrue(NodeExecutor.isForbiddenInPreviewNode(new ApplyChangesNode()));
        assertTrue(NodeExecutor.isForbiddenInPreviewNode(new SetBlockNode()));
        assertFalse(NodeExecutor.isForbiddenInPreviewNode(new IfNode()));
        assertFalse(NodeExecutor.isForbiddenInPreviewNode(new GetBlockNode()));
    }
}

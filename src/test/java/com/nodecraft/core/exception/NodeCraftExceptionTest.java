package com.nodecraft.core.exception;

import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCraftExceptionTest {

    @AfterEach
    void clearRegistry() {
        NodeRegistry.getInstance().clear();
    }

    @Test
    void nodeRegistryThrowsValidationExceptionForUnknownType() {
        NodeValidationException ex = assertThrows(
            NodeValidationException.class,
            () -> NodeRegistry.getInstance().createNodeInstance("missing.node.type")
        );
        assertTrue(ex.getMessage().contains("missing.node.type"));
    }

    @Test
    void hierarchyMatchesReviewContract() {
        assertInstanceOf(NodeValidationException.class, new GeometryException("geometry"));
        assertInstanceOf(NodeValidationException.class, new ExpressionEvaluationException("expr"));
        assertInstanceOf(NodeCraftException.class, new NodeExecutionException("run"));
        assertInstanceOf(NodeCraftException.class, new GraphException("graph"));
    }
}

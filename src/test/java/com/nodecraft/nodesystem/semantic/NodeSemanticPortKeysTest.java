package com.nodecraft.nodesystem.semantic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeSemanticPortKeysTest {

    @Test
    void physicalBaseStripsVariant() {
        assertEquals("output_face", NodeSemanticPortKeys.physicalBase("output_face:horizontal"));
        assertEquals("output_face", NodeSemanticPortKeys.physicalBase("output_face"));
        assertTrue(NodeSemanticPortKeys.isVariant("output_face:vertical"));
        assertFalse(NodeSemanticPortKeys.isVariant("output_face"));
    }

    @Test
    void matchesEdgePortIsExactOnly() {
        assertTrue(NodeSemanticPortKeys.matchesEdgePort("output_face:horizontal", "output_face:horizontal"));
        assertFalse(NodeSemanticPortKeys.matchesEdgePort("output_face:horizontal", "output_face"));
        assertFalse(NodeSemanticPortKeys.matchesEdgePort("output_face:vertical", "output_face:horizontal"));
    }
}

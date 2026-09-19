package com.nodecraft.gui.editor.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorDocumentFactoryTest {

    @Test
    void createEmptyProducesBlankGraph() {
        var graph = EditorDocumentFactory.createEmpty();
        assertNotNull(graph);
        assertEquals(EditorDocumentFactory.DEFAULT_UNTITLED_NAME, graph.getName());
        assertTrue(graph.getNodes().isEmpty());
        assertTrue(graph.getConnections().isEmpty());
    }

    @Test
    void createEmptyDocumentIsNotDirty() {
        EditorDocumentState document = EditorDocumentFactory.createEmptyDocument();
        assertFalse(document.isDirty());
        assertEquals(0L, document.getDirtyVersion());
        assertNotNull(document.getGraph());
        assertTrue(document.getGraph().getNodes().isEmpty());
        assertTrue(document.getNodePositions().isEmpty());
    }
}

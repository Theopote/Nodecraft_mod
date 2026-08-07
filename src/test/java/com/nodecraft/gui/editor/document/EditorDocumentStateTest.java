package com.nodecraft.gui.editor.document;

import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorDocumentStateTest {

    @Test
    void markDirtyIncrementsVersionAndSetsUnsavedFlag() {
        EditorDocumentState document = new EditorDocumentState();
        assertEquals(0L, document.getDirtyVersion());
        assertFalse(document.isDirty());

        document.markDirty();
        assertEquals(1L, document.getDirtyVersion());
        assertTrue(document.isDirty());

        document.clearUnsavedFlag();
        assertFalse(document.isDirty());
        assertEquals(1L, document.getDirtyVersion());
    }

    @Test
    void positionsAreLiveAndReplaceable() {
        EditorDocumentState document = new EditorDocumentState();
        UUID id = UUID.randomUUID();
        document.setNodePosition(id, new NodePosition(1f, 2f));
        assertEquals(1f, document.getNodePosition(id).x);

        Map<UUID, NodePosition> next = new HashMap<>();
        UUID other = UUID.randomUUID();
        next.put(other, new NodePosition(9f, 8f));
        document.replaceNodePositions(next);

        assertNull(document.getNodePosition(id));
        assertSame(next, document.getNodePositions());
        assertEquals(8f, document.getNodePosition(other).y);
    }

    @Test
    void resetForNewGraphClearsLayoutAndUnsavedFlag() {
        EditorDocumentState document = new EditorDocumentState();
        document.setGraph(new NodeGraph("old"));
        document.setNodePosition(UUID.randomUUID(), new NodePosition(0f, 0f));
        document.markDirty();

        NodeGraph fresh = new NodeGraph("fresh");
        document.resetForNewGraph(fresh);

        assertSame(fresh, document.getGraph());
        assertTrue(document.getNodePositions().isEmpty());
        assertFalse(document.isDirty());
        assertEquals(1L, document.getDirtyVersion());
    }

    @Test
    void replaceNodePositionsNullYieldsEmptyMap() {
        EditorDocumentState document = new EditorDocumentState();
        Map<UUID, NodePosition> original = document.getNodePositions();
        document.replaceNodePositions(null);
        assertNotSame(original, document.getNodePositions());
        assertTrue(document.getNodePositions().isEmpty());
    }
}

package com.nodecraft.gui.editor.session;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorSessionTest {

    @Test
    void openFlagDefaultsClosed() {
        EditorSession session = new EditorSession();
        assertFalse(session.isOpen());
        session.setOpen(true);
        assertTrue(session.isOpen());
    }

    @Test
    void displayModeAndPreviewFlagsRoundTrip() {
        EditorSession session = new EditorSession();
        assertEquals(EditorSession.DISPLAY_MODE_FULL, session.getNodeDisplayMode());
        assertTrue(session.isShowNodePreviews());

        session.setNodeDisplayMode(EditorSession.DISPLAY_MODE_COMPACT);
        session.setShowNodePreviews(false);
        assertEquals(EditorSession.DISPLAY_MODE_COMPACT, session.getNodeDisplayMode());
        assertFalse(session.isShowNodePreviews());
    }

    @Test
    void customColorsArePerNode() {
        EditorSession session = new EditorSession();
        UUID id = UUID.randomUUID();
        assertFalse(session.hasNodeCustomColor(id));
        session.setNodeCustomColor(id, 0xFF112233);
        assertTrue(session.hasNodeCustomColor(id));
        assertEquals(0xFF112233, session.getNodeCustomColor(id));
        session.removeNodeCustomColor(id);
        assertNull(session.getNodeCustomColor(id));
    }

    @Test
    void disableAndVisibilityNotifyStructureListener() {
        AtomicInteger dirtyCalls = new AtomicInteger();
        AtomicInteger previewClears = new AtomicInteger();
        EditorSession session = new EditorSession(new EditorSession.Host() {
            @Override
            public void notifyStructureDirty() {
                dirtyCalls.incrementAndGet();
            }

            @Override
            public void clearNodePreviewArtifacts(UUID nodeId) {
                previewClears.incrementAndGet();
            }
        });
        UUID id = UUID.randomUUID();

        assertFalse(session.isNodeDisabled(id));
        assertTrue(session.isNodeVisible(id));

        assertTrue(session.toggleNodeDisabled(id));
        assertTrue(session.isNodeDisabled(id));
        assertEquals(1, dirtyCalls.get());
        assertEquals(1, previewClears.get());

        session.setNodeVisible(id, false);
        assertFalse(session.isNodeVisible(id));
        assertEquals(2, dirtyCalls.get());

        // Same return as legacy ImGuiNodeEditor: !wasHidden (true iff now hidden)
        assertFalse(session.toggleNodeVisible(id));
        assertTrue(session.isNodeVisible(id));
        assertEquals(3, dirtyCalls.get());
    }
}

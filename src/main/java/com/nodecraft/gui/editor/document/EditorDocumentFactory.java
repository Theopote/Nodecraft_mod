package com.nodecraft.gui.editor.document;

import com.nodecraft.nodesystem.graph.NodeGraph;

/**
 * Factory for blank editor documents — no demo nodes, no connections, not dirty.
 */
public final class EditorDocumentFactory {

    public static final String DEFAULT_UNTITLED_NAME = "Untitled";

    private EditorDocumentFactory() {
    }

    /**
     * Empty graph: zero nodes, zero connections.
     */
    public static NodeGraph createEmpty() {
        return new NodeGraph(DEFAULT_UNTITLED_NAME);
    }

    /**
     * Fresh document bound to an empty graph with {@code dirty = false}.
     */
    public static EditorDocumentState createEmptyDocument() {
        EditorDocumentState document = new EditorDocumentState();
        document.resetForNewGraph(createEmpty());
        return document;
    }
}

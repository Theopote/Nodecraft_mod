package com.nodecraft.gui.editor.document;

import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.gui.editor.preview.AutoPreviewController;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Document-level editor state: active graph, layout positions, and dirty generation.
 * <p>
 * See {@code docs/architecture/editor-document-state.md}.
 */
public final class EditorDocumentState implements AutoPreviewController.DirtyVersionSource {

    private @Nullable NodeGraph graph;
    private Map<UUID, NodePosition> nodePositions = new HashMap<>();
    private boolean dirty;
    private long dirtyVersion;

    public @Nullable NodeGraph getGraph() {
        return graph;
    }

    public void setGraph(@Nullable NodeGraph graph) {
        this.graph = graph;
    }

    /**
     * Live mutable layout map for the active graph. Callers may put/remove entries.
     */
    public Map<UUID, NodePosition> getNodePositions() {
        return nodePositions;
    }

    public @Nullable NodePosition getNodePosition(UUID nodeId) {
        return nodePositions.get(nodeId);
    }

    public void setNodePosition(UUID nodeId, NodePosition position) {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(position, "position");
        nodePositions.put(nodeId, position);
    }

    public void removeNodePosition(UUID nodeId) {
        if (nodeId != null) {
            nodePositions.remove(nodeId);
        }
    }

    public void clearNodePositions() {
        nodePositions.clear();
    }

    /**
     * Replaces the layout map reference (used when loading snapshots / entering subgraphs).
     */
    public void replaceNodePositions(@Nullable Map<UUID, NodePosition> positions) {
        this.nodePositions = positions != null ? positions : new HashMap<>();
    }

    public boolean isDirty() {
        return dirty;
    }

    @Override
    public long getDirtyVersion() {
        return dirtyVersion;
    }

    @Override
    public void markDirty() {
        dirty = true;
        dirtyVersion++;
    }

    /**
     * Clears the unsaved flag after a successful save. Does not reset {@link #dirtyVersion}.
     */
    public void clearUnsavedFlag() {
        dirty = false;
    }

    /**
     * Resets document content for a new/loaded graph without bumping dirty version.
     */
    public void resetForNewGraph(@Nullable NodeGraph newGraph) {
        this.graph = newGraph;
        this.nodePositions = new HashMap<>();
        this.dirty = false;
    }
}

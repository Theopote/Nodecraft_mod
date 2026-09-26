package com.nodecraft.gui.editor.document;

import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.gui.editor.preview.AutoPreviewController;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedGraphComment;
import com.nodecraft.nodesystem.io.SavedGraphGroup;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    private Map<String, SavedGraph> subgraphDefinitions = new LinkedHashMap<>();
    private List<SavedGraphComment> comments = new ArrayList<>();
    private List<SavedGraphGroup> groups = new ArrayList<>();
    private boolean dirty;
    private long dirtyVersion;

    public @Nullable NodeGraph getGraph() {
        return graph;
    }

    public void setGraph(@Nullable NodeGraph graph) {
        this.graph = graph;
    }

    public Map<String, SavedGraph> getSubgraphDefinitions() {
        return subgraphDefinitions;
    }

    public void setSubgraphDefinitions(@Nullable Map<String, SavedGraph> definitions) {
        subgraphDefinitions = definitions != null ? new LinkedHashMap<>(definitions) : new LinkedHashMap<>();
    }

    public List<SavedGraphComment> getComments() {
        return comments;
    }

    public void setComments(@Nullable List<SavedGraphComment> comments) {
        this.comments = comments != null ? new ArrayList<>(comments) : new ArrayList<>();
    }

    public List<SavedGraphGroup> getGroups() {
        return groups;
    }

    public void setGroups(@Nullable List<SavedGraphGroup> groups) {
        this.groups = groups != null ? new ArrayList<>(groups) : new ArrayList<>();
    }

    public void applySavedGraphMetadata(@Nullable SavedGraph savedGraph) {
        if (savedGraph == null) {
            setSubgraphDefinitions(Map.of());
            setComments(List.of());
            setGroups(List.of());
            return;
        }
        setSubgraphDefinitions(savedGraph.subgraphDefinitions);
        setComments(savedGraph.comments);
        setGroups(savedGraph.groups);
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
        this.subgraphDefinitions = new LinkedHashMap<>();
        this.comments = new ArrayList<>();
        this.groups = new ArrayList<>();
        this.dirty = false;
    }
}

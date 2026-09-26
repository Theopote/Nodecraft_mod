package com.nodecraft.nodesystem.io;

import java.util.List;
import java.util.Map;

/**
 * Represents the top-level structure for saving/loading a node graph.
 */
public class SavedGraph {
    /** Saved graph format version. */
    public int formatVersion = GraphFormatVersion.CURRENT;
    public String graphName;
    public List<SavedNode> nodes;
    public List<SavedConnection> connections;
    public Map<String, SavedPosition> nodePositions; // Key: Node UUID as String

    /** Graph-local subgraph definitions keyed by {@code Subgraph Ref}. */
    public Map<String, SavedGraph> subgraphDefinitions;

    /** Editor-only comment overlays (not runtime nodes). */
    public java.util.List<SavedGraphComment> comments;

    /** Editor-only group overlays (not runtime nodes). */
    public java.util.List<SavedGraphGroup> groups;

    // Default constructor for Gson
    public SavedGraph() {}
} 
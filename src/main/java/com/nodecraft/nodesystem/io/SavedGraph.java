package com.nodecraft.nodesystem.io;

import java.util.List;
import java.util.Map;

/**
 * Represents the top-level structure for saving/loading a node graph.
 */
public class SavedGraph {
    /**
     * Saved graph format version. {@link GraphFormat#LEGACY_UNSPECIFIED} means a pre-versioning file.
     */
    public int formatVersion = GraphFormat.LEGACY_UNSPECIFIED;
    public String graphName;
    public List<SavedNode> nodes;
    public List<SavedConnection> connections;
    public Map<String, SavedPosition> nodePositions; // Key: Node UUID as String

    // Default constructor for Gson
    public SavedGraph() {}
} 
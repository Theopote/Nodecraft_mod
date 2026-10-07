package com.nodecraft.gui.recommendation;

import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface NodeRecommendationService {

    void initialize();

    void invalidateCache();

    List<NodeRecommendation> recommend(NodeGraph graph, NodeRecommendationContext context);

    List<NodeRecommendation> recommendForSelectedNode(NodeGraph graph, INode selectedNode, int limit);

    NodeRecommendationApplyResult apply(
            ICanvasEditor editor,
            NodeGraph graph,
            NodeRecommendationContext context,
            NodeRecommendation recommendation);

    void reloadRules();

    /** Bumped whenever recommendation rules are (re)loaded — used by Overlay cache identity. */
    long getRulesRevision();

    /**
     * Stable semantic key for selection-level recommendations (e.g. Get Box Face orientation).
     * Empty string when the node has no dynamic semantic axis.
     */
    String resolveSelectionSemanticKey(INode sourceNode);
}

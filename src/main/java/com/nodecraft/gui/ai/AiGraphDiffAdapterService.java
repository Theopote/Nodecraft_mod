package com.nodecraft.gui.ai;

import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.nodesystem.graph.NodeGraph;

public final class AiGraphDiffAdapterService {

    private AiGraphDiffAdapterService() {
    }

    public static AiGraphDiffService.GraphDiffSummary buildGraphDiffSummary(AiGraphPlan plan, NodeGraph graph) {
        return AiGraphDiffService.buildGraphDiffSummary(plan, graph);
    }

    public static AiGraphDiffService.MappedDiffSummary buildMappedDiffSummary(AiGraphPlan plan, NodeGraph graph) {
        return AiGraphDiffService.buildMappedDiffSummary(plan, graph);
    }
}

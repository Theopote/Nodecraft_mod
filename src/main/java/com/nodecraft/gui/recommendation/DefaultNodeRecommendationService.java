package com.nodecraft.gui.recommendation;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DefaultNodeRecommendationService implements NodeRecommendationService {

    private final NodePortIndex portIndex = new NodePortIndex();
    private NodeRecommendationRules rules = new NodeRecommendationRules();
    private NodeRecommendationScorer scorer;
    private NodeRecommendationConnector connector;
    private volatile boolean initialized;

    @Override
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        rules = NodeRecommendationRulesLoader.load();
        scorer = new NodeRecommendationScorer(rules, portIndex);
        connector = new NodeRecommendationConnector(portIndex, rules);
        initialized = true;
        NodeCraft.LOGGER.info("Node recommendation service initialized (rules v{})", rules.version);
    }

    @Override
    public synchronized void invalidateCache() {
        portIndex.invalidate();
    }

    @Override
    public synchronized void reloadRules() {
        rules = NodeRecommendationRulesLoader.load();
        scorer = new NodeRecommendationScorer(rules, portIndex);
        connector = new NodeRecommendationConnector(portIndex, rules);
        initialized = true;
    }

    @Override
    public List<NodeRecommendation> recommend(NodeGraph graph, NodeRecommendationContext context) {
        ensureInitialized();
        if (graph == null || context == null || context.sourceNodeId() == null) {
            return List.of();
        }

        INode sourceNode = graph.getNode(context.sourceNodeId());
        if (sourceNode == null) {
            return List.of();
        }

        int limit = context.limit() != null ? context.limit() : rules.defaults.limit;
        NodeInfo sourceInfo = NodeRegistry.getInstance().getNodeInfo(sourceNode.getTypeId());
        String sourceCategory = sourceInfo != null ? sourceInfo.getCategoryId() : null;

        List<SourcePortContext> sourcePorts = resolveSourcePorts(sourceNode, context);
        List<NodeRecommendation> raw = new ArrayList<>();

        for (SourcePortContext sourcePort : sourcePorts) {
            List<NodePortIndex.CandidatePort> candidates = context.direction() == RecommendationDirection.DOWNSTREAM
                    ? portIndex.findDownstreamCandidates(sourcePort.dataType())
                    : portIndex.findUpstreamCandidates(sourcePort.dataType());

            for (NodePortIndex.CandidatePort candidate : candidates) {
                if (shouldExclude(candidate.nodeId(), candidate.categoryId())) {
                    continue;
                }
                if (candidate.nodeId().equalsIgnoreCase(sourceNode.getTypeId())) {
                    continue;
                }

                String preferredPortId = resolvePreferredPortId(
                        sourceNode.getTypeId(),
                        sourcePort.portId(),
                        sourcePort.dataType(),
                        candidate.nodeId(),
                        context.direction());

                NodePortIndex.CandidatePort resolvedCandidate = connector.pickBestCandidatePort(
                        context.direction(),
                        sourcePort.dataType(),
                        candidate.nodeId(),
                        preferredPortId);
                if (resolvedCandidate == null) {
                    continue;
                }

                raw.add(scorer.scoreCandidate(
                        context.direction(),
                        sourceNode.getTypeId(),
                        sourceCategory,
                        sourcePort.portId(),
                        sourcePort.dataType(),
                        resolvedCandidate,
                        preferredPortId));
            }
        }

        Map<String, NodeRecommendation> deduped = NodeRecommendationScorer.dedupeKeepBest(raw);
        return NodeRecommendationScorer.mergeAndSort(deduped, Math.max(1, limit));
    }

    @Override
    public List<NodeRecommendation> recommendForSelectedNode(NodeGraph graph, INode selectedNode, int limit) {
        if (selectedNode == null) {
            return List.of();
        }
        NodeRecommendationContext context = NodeRecommendationContext.forSelectedNode(
                selectedNode.getId(),
                (float) selectedNode.getPositionX(),
                (float) selectedNode.getPositionY(),
                limit);
        return recommend(graph, context);
    }

    @Override
    public NodeRecommendationApplyResult apply(
            ICanvasEditor editor,
            NodeGraph graph,
            NodeRecommendationContext context,
            NodeRecommendation recommendation) {
        ensureInitialized();
        return connector.apply(editor, graph, context, recommendation);
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private List<SourcePortContext> resolveSourcePorts(INode sourceNode, NodeRecommendationContext context) {
        List<SourcePortContext> ports = new ArrayList<>();

        if (context.sourcePortId() != null && context.sourceDataType() != null) {
            ports.add(new SourcePortContext(context.sourcePortId(), context.sourceDataType()));
            return ports;
        }

        if (context.sourcePortId() != null) {
            NodeDataType type = NodeRecommendationPorts.resolvePortDataType(
                    sourceNode,
                    context.sourcePortId(),
                    context.direction() == RecommendationDirection.DOWNSTREAM);
            if (type != null) {
                ports.add(new SourcePortContext(context.sourcePortId(), type));
                return ports;
            }
        }

        boolean collapseByType = context.trigger() == RecommendationTrigger.SELECTION_PANEL
                || context.trigger() == RecommendationTrigger.NODE_CONTEXT_MENU;
        if (context.direction() == RecommendationDirection.DOWNSTREAM) {
            collectPorts(sourceNode.getOutputPorts(), ports, collapseByType);
        } else {
            collectPorts(sourceNode.getInputPorts(), ports, collapseByType);
        }

        if (ports.isEmpty() && context.sourceDataType() != null) {
            ports.add(new SourcePortContext(context.sourcePortId(), context.sourceDataType()));
        }
        return ports;
    }

    private static void collectPorts(List<IPort> nodePorts, List<SourcePortContext> ports, boolean collapseByType) {
        if (collapseByType) {
            Map<NodeDataType, String> uniqueByType = new LinkedHashMap<>();
            for (IPort port : nodePorts) {
                if (port.getDataType() == NodeDataType.EXEC) {
                    continue;
                }
                uniqueByType.putIfAbsent(port.getDataType(), port.getId());
            }
            for (Map.Entry<NodeDataType, String> entry : uniqueByType.entrySet()) {
                ports.add(new SourcePortContext(entry.getValue(), entry.getKey()));
            }
            return;
        }

        for (IPort port : nodePorts) {
            if (port.getDataType() == NodeDataType.EXEC) {
                continue;
            }
            ports.add(new SourcePortContext(port.getId(), port.getDataType()));
        }
    }

    private String resolvePreferredPortId(
            String sourceNodeTypeId,
            String sourcePortId,
            NodeDataType sourceDataType,
            String candidateNodeId,
            RecommendationDirection direction) {
        String dirKey = direction == RecommendationDirection.DOWNSTREAM ? "downstream" : "upstream";

        if (sourceNodeTypeId != null && rules.sourceNodes != null) {
            NodeRecommendationRules.SourceNodeRule sourceRule =
                    rules.sourceNodes.get(sourceNodeTypeId.toLowerCase(Locale.ROOT));
            if (sourceRule != null && sourcePortId != null && sourceRule.outputs != null) {
                NodeRecommendationRules.PortDirectionRule portRule = sourceRule.outputs.get(sourcePortId);
                String fromRule = findConnectPortId(portRule, dirKey, candidateNodeId);
                if (fromRule != null) {
                    return fromRule;
                }
            }
        }

        if (sourceDataType != null && rules.outputTypes != null) {
            NodeRecommendationRules.OutputTypeRule typeRule = rules.outputTypes.get(sourceDataType.getId());
            if (typeRule != null) {
                List<NodeRecommendationRules.RuleEntry> entries =
                        direction == RecommendationDirection.DOWNSTREAM ? typeRule.downstream : typeRule.upstream;
                return findConnectPortIdInEntries(entries, candidateNodeId);
            }
        }
        return null;
    }

    private String findConnectPortIdInEntries(List<NodeRecommendationRules.RuleEntry> entries, String candidateNodeId) {
        if (entries == null || candidateNodeId == null) {
            return null;
        }
        for (NodeRecommendationRules.RuleEntry entry : entries) {
            if (entry != null
                    && entry.nodeId != null
                    && entry.nodeId.equalsIgnoreCase(candidateNodeId)
                    && entry.connectPortId != null
                    && !entry.connectPortId.isBlank()) {
                return entry.connectPortId;
            }
        }
        return null;
    }

    private String findConnectPortId(
            NodeRecommendationRules.PortDirectionRule portRule,
            String dirKey,
            String candidateNodeId) {
        if (portRule == null || candidateNodeId == null) {
            return null;
        }
        List<NodeRecommendationRules.RuleEntry> entries =
                "downstream".equals(dirKey) ? portRule.downstream : portRule.upstream;
        return findConnectPortIdInEntries(entries, candidateNodeId);
    }

    private boolean shouldExclude(String nodeId, String categoryId) {
        if (nodeId == null) {
            return true;
        }
        for (String excluded : rules.defaults.excludeNodeIds) {
            if (nodeId.equalsIgnoreCase(excluded)) {
                return true;
            }
        }
        if (categoryId == null) {
            return false;
        }
        String lowerCategory = categoryId.toLowerCase(Locale.ROOT);
        for (String excludedCategory : rules.defaults.excludeCategories) {
            String lowerExcluded = excludedCategory.toLowerCase(Locale.ROOT);
            if (lowerCategory.equals(lowerExcluded) || lowerCategory.startsWith(lowerExcluded + ".")) {
                return true;
            }
        }
        return false;
    }

    private record SourcePortContext(String portId, NodeDataType dataType) {
    }
}

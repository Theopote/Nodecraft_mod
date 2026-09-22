package com.nodecraft.gui.recommendation;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class NodeRecommendationScorer {

    private final NodeRecommendationRules rules;
    private final NodePortIndex portIndex;

    NodeRecommendationScorer(NodeRecommendationRules rules, NodePortIndex portIndex) {
        this.rules = rules;
        this.portIndex = portIndex;
    }

    NodeRecommendation scoreCandidate(
            RecommendationDirection direction,
            String sourceNodeTypeId,
            String sourceCategoryId,
            String sourcePortId,
            NodeDataType sourceDataType,
            NodePortIndex.CandidatePort candidate,
            String preferredConnectPortId) {
        NodeInfo info = NodeRegistry.getInstance().getNodeInfo(candidate.nodeId());
        String displayName = info != null ? info.getDisplayName() : candidate.displayName();
        String categoryId = info != null ? info.getCategoryId() : candidate.categoryId();

        int score = scoreTypeMatch(sourceDataType, candidate.dataType());
        score += scoreWorkflowCategory(categoryId);
        score += scoreRuleTable(
                direction,
                sourceNodeTypeId,
                sourceCategoryId,
                sourcePortId,
                sourceDataType,
                candidate.nodeId(),
                preferredConnectPortId);

        NodeRecommendation.ConnectionPlan plan = resolvePlan(sourceDataType, candidate.dataType());
        if (plan == NodeRecommendation.ConnectionPlan.VIA_CONVERSION
                && TypeConversionRegistry.getSuggestedConversion(sourceDataType, candidate.dataType()) != null) {
            // Keep known conversion bridges visible in the short suggested list.
            score += 90;
        }
        String reason = buildReason(score, categoryId, plan, sourceDataType, candidate.dataType());

        return new NodeRecommendation(
                candidate.nodeId(),
                displayName,
                categoryId,
                candidate.portId(),
                candidate.dataType(),
                sourcePortId,
                sourceDataType,
                plan,
                score,
                reason);
    }

    private int scoreTypeMatch(NodeDataType sourceType, NodeDataType targetPortType) {
        if (sourceType == null || targetPortType == null) {
            return 40;
        }
        if (sourceType == targetPortType) {
            return 120;
        }
        if (NodeDataType.isConnectableTo(sourceType, targetPortType)) {
            return 80;
        }
        if (TypeConversionRegistry.requiresExplicitConversion(sourceType, targetPortType)) {
            return 40;
        }
        return 0;
    }

    private int scoreWorkflowCategory(String categoryId) {
        if (categoryId == null || rules.defaults.workflowOrder.isEmpty()) {
            return 0;
        }
        String lower = categoryId.toLowerCase(Locale.ROOT);
        List<String> order = rules.defaults.workflowOrder;
        for (int i = 0; i < order.size(); i++) {
            String prefix = order.get(i).toLowerCase(Locale.ROOT);
            if (lower.equals(prefix) || lower.startsWith(prefix + ".")) {
                return Math.max(0, 60 - i * 5);
            }
        }
        return 0;
    }

    private int scoreRuleTable(
            RecommendationDirection direction,
            String sourceNodeTypeId,
            String sourceCategoryId,
            String sourcePortId,
            NodeDataType sourceDataType,
            String candidateNodeId,
            String preferredConnectPortId) {
        String dirKey = direction == RecommendationDirection.DOWNSTREAM ? "downstream" : "upstream";
        int best = 0;

        if (sourceNodeTypeId != null && rules.sourceNodes != null) {
            NodeRecommendationRules.SourceNodeRule sourceRule = rules.sourceNodes.get(sourceNodeTypeId.toLowerCase(Locale.ROOT));
            if (sourceRule != null && sourcePortId != null && sourceRule.outputs != null) {
                NodeRecommendationRules.PortDirectionRule portRule = sourceRule.outputs.get(sourcePortId);
                best = Math.max(best, scoreEntries(portRule, dirKey, candidateNodeId, preferredConnectPortId, 1000));
            }
        }

        if (sourceCategoryId != null && rules.sourceCategories != null) {
            for (Map.Entry<String, NodeRecommendationRules.SourceCategoryRule> entry : rules.sourceCategories.entrySet()) {
                if (!matchesCategoryPrefix(sourceCategoryId, entry.getKey())) {
                    continue;
                }
                NodeRecommendationRules.SourceCategoryRule categoryRule = entry.getValue();
                if (sourceDataType != null && categoryRule.outputTypes != null) {
                    NodeRecommendationRules.PortDirectionRule typeRule =
                            categoryRule.outputTypes.get(sourceDataType.getId());
                    best = Math.max(best, scoreEntries(typeRule, dirKey, candidateNodeId, preferredConnectPortId, 800));
                }
            }
        }

        if (sourceDataType != null && rules.outputTypes != null) {
            NodeRecommendationRules.OutputTypeRule typeRule = rules.outputTypes.get(sourceDataType.getId());
            if (typeRule != null) {
                List<NodeRecommendationRules.RuleEntry> entries =
                        direction == RecommendationDirection.DOWNSTREAM ? typeRule.downstream : typeRule.upstream;
                best = Math.max(best, scoreEntryList(entries, candidateNodeId, preferredConnectPortId, 600));
            }
        }

        return best;
    }

    private int scoreEntries(
            NodeRecommendationRules.PortDirectionRule portRule,
            String dirKey,
            String candidateNodeId,
            String preferredConnectPortId,
            int baseScore) {
        if (portRule == null || candidateNodeId == null) {
            return 0;
        }
        List<NodeRecommendationRules.RuleEntry> entries =
                "downstream".equals(dirKey) ? portRule.downstream : portRule.upstream;
        return scoreEntryList(entries, candidateNodeId, preferredConnectPortId, baseScore);
    }

    private int scoreEntryList(
            List<NodeRecommendationRules.RuleEntry> entries,
            String candidateNodeId,
            String preferredConnectPortId,
            int baseScore) {
        if (entries == null || candidateNodeId == null) {
            return 0;
        }
        for (NodeRecommendationRules.RuleEntry entry : entries) {
            if (entry == null || entry.nodeId == null) {
                continue;
            }
            if (!entry.nodeId.equalsIgnoreCase(candidateNodeId)) {
                continue;
            }
            int score = baseScore - entry.order;
            if (preferredConnectPortId != null
                    && entry.connectPortId != null
                    && entry.connectPortId.equals(preferredConnectPortId)) {
                score += 20;
            }
            return score;
        }
        return 0;
    }

    private static boolean matchesCategoryPrefix(String categoryId, String prefix) {
        if (categoryId == null || prefix == null) {
            return false;
        }
        String lowerCategory = categoryId.toLowerCase(Locale.ROOT);
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return lowerCategory.equals(lowerPrefix) || lowerCategory.startsWith(lowerPrefix + ".");
    }

    private NodeRecommendation.ConnectionPlan resolvePlan(NodeDataType sourceType, NodeDataType targetPortType) {
        if (sourceType == null || targetPortType == null) {
            return NodeRecommendation.ConnectionPlan.MANUAL;
        }
        if (NodeDataType.isConnectableTo(sourceType, targetPortType)) {
            return NodeRecommendation.ConnectionPlan.DIRECT;
        }
        if (TypeConversionRegistry.requiresExplicitConversion(sourceType, targetPortType)) {
            return NodeRecommendation.ConnectionPlan.VIA_CONVERSION;
        }
        return NodeRecommendation.ConnectionPlan.MANUAL;
    }

    private String buildReason(
            int score,
            String categoryId,
            NodeRecommendation.ConnectionPlan plan,
            NodeDataType sourceType,
            NodeDataType targetType) {
        if (plan == NodeRecommendation.ConnectionPlan.VIA_CONVERSION) {
            TypeConversionRegistry.ConversionSuggestion conversion =
                    TypeConversionRegistry.getSuggestedConversion(sourceType, targetType);
            if (conversion != null) {
                return "Via " + conversion.displayName();
            }
            return "Requires conversion node";
        }
        if (score >= 1000) {
            return "Rule table · exact match";
        }
        if (score >= 800) {
            return "Rule table · category workflow";
        }
        if (score >= 600) {
            return "Rule table · type default chain";
        }
        if (categoryId != null) {
            return "Type compatible · " + categoryId;
        }
        return "Type compatible";
    }

    static List<NodeRecommendation> mergeAndSort(Map<String, NodeRecommendation> deduped, int limit) {
        List<NodeRecommendation> sorted = new ArrayList<>(deduped.values());
        sorted.sort(Comparator
                .comparingInt(NodeRecommendation::score).reversed()
                .thenComparing(NodeRecommendation::displayName, String.CASE_INSENSITIVE_ORDER));
        if (sorted.size() <= limit) {
            return sorted;
        }
        return sorted.subList(0, limit);
    }

    static Map<String, NodeRecommendation> dedupeKeepBest(List<NodeRecommendation> recommendations) {
        Map<String, NodeRecommendation> deduped = new LinkedHashMap<>();
        for (NodeRecommendation recommendation : recommendations) {
            deduped.merge(recommendation.nodeId(), recommendation, (left, right) ->
                    right.score() >= left.score() ? right : left);
        }
        return deduped;
    }
}

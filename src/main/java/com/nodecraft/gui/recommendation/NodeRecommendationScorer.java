package com.nodecraft.gui.recommendation;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
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
            String rulePortKey,
            NodeDataType sourceDataType,
            NodePortIndex.CandidatePort candidate,
            String preferredConnectPortId) {
        NodeInfo info = NodeRegistry.getInstance().getNodeInfo(candidate.nodeId());
        String displayName = info != null ? info.getDisplayName() : candidate.displayName();
        String categoryId = info != null ? info.getCategoryId() : candidate.categoryId();

        int score = scoreTypeMatch(sourceDataType, candidate.dataType());
        score += scoreWorkflowCategory(categoryId);
        RuleMatch ruleMatch = scoreRuleTable(
                direction,
                sourceNodeTypeId,
                sourceCategoryId,
                rulePortKey != null ? rulePortKey : sourcePortId,
                sourceDataType,
                candidate.nodeId(),
                preferredConnectPortId);
        score += ruleMatch.score();
        // Soft-demote effectful candidates only when ranking by type compatibility alone;
        // exact/category rule rows already encode intended Preview vs bake order.
        if (ruleMatch.score() == 0) {
            score += scoreEffectPenalty(info, candidate.nodeId());
            score += scoreSourceWorldReadBias(sourceNodeTypeId, candidate.nodeId());
        }

        NodeRecommendation.ConnectionPlan plan = resolvePlan(sourceDataType, candidate.dataType());
        if (plan == NodeRecommendation.ConnectionPlan.VIA_CONVERSION
                && TypeConversionRegistry.getSuggestedConversion(sourceDataType, candidate.dataType()) != null) {
            // Small visibility bump only — must stay below exact (+120) and compatible-direct (+80).
            score += 20;
        }
        String reason = buildReason(
                ruleMatch.reason(),
                score,
                categoryId,
                plan,
                sourceDataType,
                candidate.dataType());

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

    /** Soft demote preview-write candidates so bake/material stays ahead of ghost preview. */
    private static int scoreEffectPenalty(NodeInfo info, String nodeId) {
        Class<? extends INode> nodeClass = info != null ? info.getNodeClass() : null;
        NodeEffect effect = NodeEffectResolver.resolve(nodeClass, nodeId);
        if (effect == NodeEffect.PREVIEW_WRITE) {
            return -20;
        }
        if (effect == NodeEffect.WORLD_WRITE
                || effect == NodeEffect.FILE_IO
                || effect == NodeEffect.CONTEXT_WRITE) {
            return -40;
        }
        return 0;
    }

    /**
     * When the source is WORLD_READ, demote write/file candidates that only matched by type
     * so analysis/PURE nodes stay ahead on Port Drag.
     */
    private static int scoreSourceWorldReadBias(String sourceNodeTypeId, String candidateNodeId) {
        if (sourceNodeTypeId == null || candidateNodeId == null) {
            return 0;
        }
        NodeInfo sourceInfo = NodeRegistry.getInstance().getNodeInfo(sourceNodeTypeId);
        Class<? extends INode> sourceClass = sourceInfo != null ? sourceInfo.getNodeClass() : null;
        NodeEffect sourceEffect = NodeEffectResolver.resolve(sourceClass, sourceNodeTypeId);
        if (sourceEffect != NodeEffect.WORLD_READ) {
            return 0;
        }
        NodeInfo candidateInfo = NodeRegistry.getInstance().getNodeInfo(candidateNodeId);
        Class<? extends INode> candidateClass = candidateInfo != null ? candidateInfo.getNodeClass() : null;
        NodeEffect candidateEffect = NodeEffectResolver.resolve(candidateClass, candidateNodeId);
        if (candidateEffect == NodeEffect.WORLD_WRITE
                || candidateEffect == NodeEffect.FILE_IO
                || candidateEffect == NodeEffect.CONTEXT_WRITE) {
            return -80;
        }
        return 0;
    }

    private RuleMatch scoreRuleTable(
            RecommendationDirection direction,
            String sourceNodeTypeId,
            String sourceCategoryId,
            String sourcePortId,
            NodeDataType sourceDataType,
            String candidateNodeId,
            String preferredConnectPortId) {
        String dirKey = direction == RecommendationDirection.DOWNSTREAM ? "downstream" : "upstream";
        RuleMatch best = RuleMatch.NONE;

        if (sourceNodeTypeId != null && rules.sourceNodes != null) {
            NodeRecommendationRules.SourceNodeRule sourceRule =
                    rules.sourceNodes.get(sourceNodeTypeId.toLowerCase(Locale.ROOT));
            if (sourceRule != null && sourcePortId != null) {
                Map<String, NodeRecommendationRules.PortDirectionRule> portMap =
                        direction == RecommendationDirection.DOWNSTREAM
                                ? sourceRule.outputs
                                : sourceRule.inputs;
                if (portMap != null) {
                    NodeRecommendationRules.PortDirectionRule portRule = portMap.get(sourcePortId);
                    best = best.max(scoreEntries(portRule, dirKey, candidateNodeId, preferredConnectPortId, 1000));
                }
            }
        }

        if (sourceCategoryId != null && rules.sourceCategories != null) {
            for (Map.Entry<String, NodeRecommendationRules.SourceCategoryRule> entry : rules.sourceCategories.entrySet()) {
                if (!matchesCategoryPrefix(sourceCategoryId, entry.getKey())) {
                    continue;
                }
                NodeRecommendationRules.SourceCategoryRule categoryRule = entry.getValue();
                Map<String, NodeRecommendationRules.PortDirectionRule> typeMap =
                        direction == RecommendationDirection.DOWNSTREAM
                                ? categoryRule.outputTypes
                                : categoryRule.inputTypes;
                if (sourceDataType != null && typeMap != null) {
                    NodeRecommendationRules.PortDirectionRule typeRule = typeMap.get(sourceDataType.getId());
                    best = best.max(scoreEntries(typeRule, dirKey, candidateNodeId, preferredConnectPortId, 800));
                }
            }
        }

        if (sourceDataType != null && rules.outputTypes != null) {
            NodeRecommendationRules.OutputTypeRule typeRule = rules.outputTypes.get(sourceDataType.getId());
            if (typeRule != null) {
                List<NodeRecommendationRules.RuleEntry> entries =
                        direction == RecommendationDirection.DOWNSTREAM ? typeRule.downstream : typeRule.upstream;
                best = best.max(scoreEntryList(entries, candidateNodeId, preferredConnectPortId, 600));
            }
        }

        return best;
    }

    private RuleMatch scoreEntries(
            NodeRecommendationRules.PortDirectionRule portRule,
            String dirKey,
            String candidateNodeId,
            String preferredConnectPortId,
            int baseScore) {
        if (portRule == null || candidateNodeId == null) {
            return RuleMatch.NONE;
        }
        List<NodeRecommendationRules.RuleEntry> entries =
                "downstream".equals(dirKey) ? portRule.downstream : portRule.upstream;
        return scoreEntryList(entries, candidateNodeId, preferredConnectPortId, baseScore);
    }

    private RuleMatch scoreEntryList(
            List<NodeRecommendationRules.RuleEntry> entries,
            String candidateNodeId,
            String preferredConnectPortId,
            int baseScore) {
        if (entries == null || candidateNodeId == null) {
            return RuleMatch.NONE;
        }
        for (NodeRecommendationRules.RuleEntry entry : entries) {
            if (entry == null || entry.nodeId == null) {
                continue;
            }
            if (!entry.nodeId.equalsIgnoreCase(candidateNodeId)) {
                continue;
            }
            int score = baseScore - entry.order;
            if (entry.connectPortId != null
                    && entry.connectPortId.equals(preferredConnectPortId)) {
                score += 20;
            }
            String reason = entry.reason != null && !entry.reason.isBlank() ? entry.reason.trim() : null;
            return new RuleMatch(score, reason);
        }
        return RuleMatch.NONE;
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
            String ruleReason,
            int score,
            String categoryId,
            NodeRecommendation.ConnectionPlan plan,
            NodeDataType sourceType,
            NodeDataType targetType) {
        if (ruleReason != null && !ruleReason.isBlank()) {
            return ruleReason;
        }
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

    private record RuleMatch(int score, String reason) {
        static final RuleMatch NONE = new RuleMatch(0, null);

        RuleMatch max(RuleMatch other) {
            if (other == null) {
                return this;
            }
            return other.score > this.score ? other : this;
        }
    }
}

package com.nodecraft.gui.ai;

import com.nodecraft.gui.ai.AiIntentAnalysisService.DomainTag;
import com.nodecraft.gui.ai.AiNodeSchemaCatalog.NodeSchema;
import com.nodecraft.gui.ai.AiNodeSchemaCatalog.RecommendationHint;
import com.nodecraft.gui.recommendation.NodeRecommendationRules;
import com.nodecraft.gui.recommendation.NodeRecommendationRulesLoader;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Two-phase AI schema retrieval: lexical seed then semantic neighbor expansion via
 * {@link NodeRecommendationRules} and explicit {@link TypeConversionRegistry} converters.
 */
public final class AiSchemaRetrievalService {

    private static final int LEXICAL_SEED_LIMIT = 20;
    private static final int MAX_HINTS_PER_DIRECTION = 3;
    private static final int MAX_NEIGHBORS_PER_SEED = 4;
    private static final int MAX_EXPANSION_HOPS = 2;

    private static final int WEIGHT_PROMPT_MATCH = 100;
    private static final int WEIGHT_EXACT_NEIGHBOR = 80;
    private static final int WEIGHT_CATEGORY_WORKFLOW = 50;
    private static final int WEIGHT_CONVERTER = 40;
    private static final int WEIGHT_CORE_INCLUDE = 30;

    private AiSchemaRetrievalService() {
    }

    public static List<NodeSchema> selectRelevant(List<NodeSchema> allSchemas, String userPrompt, int limit) {
        if (allSchemas == null || allSchemas.isEmpty()) {
            return List.of();
        }
        int safeLimit = Math.max(1, limit);
        String prompt = userPrompt == null ? "" : userPrompt.toLowerCase(Locale.ROOT);
        Set<String> tokens = AiNodeSchemaCatalog.expandIntentTokens(prompt, AiNodeSchemaCatalog.tokenize(prompt));
        boolean geometryIntent = AiNodeSchemaCatalog.hasGeometryIntent(prompt);
        boolean spatialIntent = AiNodeSchemaCatalog.hasSpatialIntent(prompt);
        Set<DomainTag> domainTags = AiIntentAnalysisService.detectDomainTags(userPrompt);
        boolean worldApply = AiIntentAnalysisService.hasWorldApplyIntent(userPrompt);

        Map<String, NodeSchema> byTypeId = indexByTypeId(allSchemas);
        NodeRecommendationRules rules = NodeRecommendationRulesLoader.load();

        Map<String, Integer> scores = new HashMap<>();
        for (NodeSchema schema : allSchemas) {
            int lexical = AiNodeSchemaCatalog.relevanceScore(schema, prompt, tokens, geometryIntent, spatialIntent);
            if (lexical > 0) {
                scores.merge(schema.typeId(), WEIGHT_PROMPT_MATCH + lexical, Math::max);
            }
        }

        List<NodeSchema> seeds = topByScore(allSchemas, scores, LEXICAL_SEED_LIMIT);
        Set<String> selected = new LinkedHashSet<>();
        for (NodeSchema seed : seeds) {
            selected.add(seed.typeId());
        }

        // Phase B: semantic expansion (1-2 hops from exact recommendation neighbors)
        Set<String> frontier = new LinkedHashSet<>(selected);
        for (int hop = 0; hop < MAX_EXPANSION_HOPS; hop++) {
            Set<String> next = new LinkedHashSet<>();
            for (String typeId : frontier) {
                for (NeighborEdge edge : collectExactNeighbors(typeId, byTypeId.get(typeId), rules)) {
                    if (!byTypeId.containsKey(edge.nodeId())) {
                        continue;
                    }
                    next.add(edge.nodeId());
                    scores.merge(edge.nodeId(), WEIGHT_EXACT_NEIGHBOR - edge.order(), Math::max);
                }
                for (String categoryNeighbor : collectCategoryWorkflowNeighbors(byTypeId.get(typeId), rules)) {
                    if (!byTypeId.containsKey(categoryNeighbor)) {
                        continue;
                    }
                    next.add(categoryNeighbor);
                    scores.merge(categoryNeighbor, WEIGHT_CATEGORY_WORKFLOW, Math::max);
                }
            }
            selected.addAll(next);
            frontier = next;
            if (frontier.isEmpty()) {
                break;
            }
        }

        // Explicit converters for selected schemas' port types (no generic compatibility flood)
        for (String typeId : List.copyOf(selected)) {
            NodeSchema schema = byTypeId.get(typeId);
            if (schema == null) {
                continue;
            }
            for (String converterId : collectConverterNeighbors(schema)) {
                if (byTypeId.containsKey(converterId)) {
                    selected.add(converterId);
                    scores.merge(converterId, WEIGHT_CONVERTER, Math::max);
                }
            }
        }

        // Intent-aware core includes (small; do not evacuate prompt-relevant slots)
        for (NodeSchema core : resolveIntentAwareCore(allSchemas, domainTags, worldApply, geometryIntent)) {
            selected.add(core.typeId());
            scores.merge(core.typeId(), WEIGHT_CORE_INCLUDE, Math::max);
        }

        List<NodeSchema> ranked = new ArrayList<>();
        for (String typeId : selected) {
            NodeSchema schema = byTypeId.get(typeId);
            if (schema != null) {
                ranked.add(schema);
            }
        }
        ranked.sort(Comparator
                .comparingInt((NodeSchema s) -> scores.getOrDefault(s.typeId(), 0))
                .reversed()
                .thenComparing(NodeSchema::typeId, String.CASE_INSENSITIVE_ORDER));

        List<NodeSchema> result = new ArrayList<>(safeLimit);
        Set<String> inResult = new HashSet<>();
        for (NodeSchema schema : ranked) {
            if (result.size() >= safeLimit) {
                break;
            }
            result.add(schema);
            inResult.add(schema.typeId());
        }

        // Phase C: category diversity after primary ranking (fill remaining slots only)
        for (String categoryPrefix : AiNodeSchemaCatalog.DIVERSITY_CATEGORY_PREFIXES) {
            if (result.size() >= safeLimit) {
                break;
            }
            if (containsCategoryPrefix(result, categoryPrefix)) {
                continue;
            }
            NodeSchema candidate = findFirstByCategoryPrefix(ranked, categoryPrefix, inResult);
            if (candidate == null) {
                candidate = findFirstByCategoryPrefix(allSchemas, categoryPrefix, inResult);
            }
            if (candidate != null) {
                result.add(candidate);
                inResult.add(candidate.typeId());
            }
        }

        for (NodeSchema schema : ranked) {
            if (result.size() >= safeLimit) {
                break;
            }
            if (inResult.add(schema.typeId())) {
                result.add(schema);
            }
        }

        List<NodeSchema> withHints = new ArrayList<>(result.size());
        for (NodeSchema schema : result) {
            withHints.add(attachRecommendationHints(schema, rules));
        }
        return withHints;
    }

    private static List<NodeSchema> resolveIntentAwareCore(
            List<NodeSchema> allSchemas,
            Set<DomainTag> domainTags,
            boolean worldApply,
            boolean geometryIntent
    ) {
        List<NodeSchema> core = new ArrayList<>();
        addExactIfPresent(allSchemas, core, "input.context.player_position");

        boolean wantsBlocks = domainTags.contains(DomainTag.MATERIAL) || worldApply;
        if (wantsBlocks) {
            addExactIfPresent(allSchemas, core, "output.preview.preview_blocks");
        } else {
            addExactIfPresent(allSchemas, core, "output.preview.preview_geometry");
        }

        if (geometryIntent || domainTags.contains(DomainTag.GEOMETRY) || domainTags.contains(DomainTag.ARCHITECTURE)) {
            addExactIfPresent(allSchemas, core, "geometry.voxel.voxelize_geometry");
        }

        if (domainTags.contains(DomainTag.MATH)) {
            addPrefixLimited(allSchemas, core, "math.scalar_math.", 6);
        }
        if (domainTags.contains(DomainTag.DATA_TREE)) {
            addPrefixLimited(allSchemas, core, "math.data_tree.", 8);
        }
        if (domainTags.contains(DomainTag.FIELD) || domainTags.contains(DomainTag.SDF)) {
            addPrefixLimited(allSchemas, core, "math.fields.", 8);
        }
        if (worldApply) {
            addExactIfPresent(allSchemas, core, "output.execute.apply_changes");
            addPrefixLimited(allSchemas, core, "world.write.", 4);
            addPrefixLimited(allSchemas, core, "output.execute.", 4);
        }
        return core;
    }

    private static void addExactIfPresent(List<NodeSchema> all, List<NodeSchema> into, String typeId) {
        for (NodeSchema schema : all) {
            if (typeId.equalsIgnoreCase(schema.typeId())) {
                into.add(schema);
                return;
            }
        }
    }

    private static void addPrefixLimited(List<NodeSchema> all, List<NodeSchema> into, String prefix, int max) {
        int added = 0;
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        for (NodeSchema schema : all) {
            if (schema.typeId() != null && schema.typeId().toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                into.add(schema);
                added++;
                if (added >= max) {
                    return;
                }
            }
        }
    }

    private record NeighborEdge(String nodeId, String fromPort, String toPort, String reason, int order) {
    }

    private static List<NeighborEdge> collectExactNeighbors(
            String typeId,
            NodeSchema schema,
            NodeRecommendationRules rules
    ) {
        List<NeighborEdge> edges = new ArrayList<>();
        if (typeId == null || rules == null || rules.sourceNodes == null) {
            return edges;
        }
        NodeRecommendationRules.SourceNodeRule rule =
                rules.sourceNodes.get(typeId.toLowerCase(Locale.ROOT));
        if (rule == null) {
            return edges;
        }

        if (rule.outputs != null) {
            for (Map.Entry<String, NodeRecommendationRules.PortDirectionRule> entry : rule.outputs.entrySet()) {
                List<NodeRecommendationRules.RuleEntry> downstream =
                        entry.getValue() == null ? List.of() : entry.getValue().downstream;
                List<NodeRecommendationRules.RuleEntry> sorted = sortByOrder(downstream);
                int taken = 0;
                for (NodeRecommendationRules.RuleEntry row : sorted) {
                    if (row == null || row.nodeId == null || row.nodeId.isBlank()) {
                        continue;
                    }
                    edges.add(new NeighborEdge(
                            row.nodeId,
                            entry.getKey(),
                            row.connectPortId,
                            row.reason,
                            row.order
                    ));
                    taken++;
                    if (taken >= MAX_NEIGHBORS_PER_SEED) {
                        break;
                    }
                }
            }
        }

        if (rule.inputs != null) {
            for (Map.Entry<String, NodeRecommendationRules.PortDirectionRule> entry : rule.inputs.entrySet()) {
                List<NodeRecommendationRules.RuleEntry> upstream =
                        entry.getValue() == null ? List.of() : entry.getValue().upstream;
                List<NodeRecommendationRules.RuleEntry> sorted = sortByOrder(upstream);
                int taken = 0;
                for (NodeRecommendationRules.RuleEntry row : sorted) {
                    if (row == null || row.nodeId == null || row.nodeId.isBlank()) {
                        continue;
                    }
                    edges.add(new NeighborEdge(
                            row.nodeId,
                            row.connectPortId,
                            entry.getKey(),
                            row.reason,
                            row.order
                    ));
                    taken++;
                    if (taken >= MAX_NEIGHBORS_PER_SEED) {
                        break;
                    }
                }
            }
        }
        return edges;
    }

    private static List<String> collectCategoryWorkflowNeighbors(NodeSchema schema, NodeRecommendationRules rules) {
        List<String> neighbors = new ArrayList<>();
        if (schema == null || rules == null || rules.sourceCategories == null) {
            return neighbors;
        }
        String category = schema.category() == null ? "" : schema.category().toLowerCase(Locale.ROOT);
        NodeRecommendationRules.SourceCategoryRule categoryRule = rules.sourceCategories.get(category);
        if (categoryRule == null) {
            return neighbors;
        }
        if (categoryRule.outputTypes != null) {
            for (NodeRecommendationRules.PortDirectionRule direction : categoryRule.outputTypes.values()) {
                if (direction == null || direction.downstream == null) {
                    continue;
                }
                for (NodeRecommendationRules.RuleEntry row : sortByOrder(direction.downstream)) {
                    if (row != null && row.nodeId != null && !row.nodeId.isBlank()) {
                        neighbors.add(row.nodeId);
                        if (neighbors.size() >= MAX_NEIGHBORS_PER_SEED) {
                            return neighbors;
                        }
                    }
                }
            }
        }
        return neighbors;
    }

    private static List<String> collectConverterNeighbors(NodeSchema schema) {
        Set<String> converters = new LinkedHashSet<>();
        if (schema == null) {
            return List.of();
        }
        Set<NodeDataType> present = new HashSet<>();
        if (schema.outputs() != null) {
            for (AiNodeSchemaCatalog.PortSchema port : schema.outputs()) {
                NodeDataType type = parseDataType(port.dataType());
                if (type != null) {
                    present.add(type);
                }
            }
        }
        if (schema.inputs() != null) {
            for (AiNodeSchemaCatalog.PortSchema port : schema.inputs()) {
                NodeDataType type = parseDataType(port.dataType());
                if (type != null) {
                    present.add(type);
                }
            }
        }

        // Only known explicit LANGUAGE_V1 bridges when those types appear on the seed schema.
        if (present.contains(NodeDataType.LIST) || present.contains(NodeDataType.DATA_TREE)) {
            addConverter(converters, NodeDataType.LIST, NodeDataType.DATA_TREE);
            addConverter(converters, NodeDataType.DATA_TREE, NodeDataType.LIST);
        }
        if (present.contains(NodeDataType.SDF)
                || present.contains(NodeDataType.SCALAR_FIELD)
                || present.contains(NodeDataType.VECTOR_FIELD)) {
            addConverter(converters, NodeDataType.SDF, NodeDataType.SCALAR_FIELD);
            addConverter(converters, NodeDataType.SDF, NodeDataType.VECTOR_FIELD);
        }
        return List.copyOf(converters);
    }

    private static void addConverter(Set<String> converters, NodeDataType out, NodeDataType in) {
        TypeConversionRegistry.ConversionSuggestion suggestion =
                TypeConversionRegistry.getSuggestedConversion(out, in);
        if (suggestion != null && suggestion.nodeId() != null) {
            converters.add(suggestion.nodeId());
        }
    }

    private static NodeDataType parseDataType(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return NodeDataType.fromId(id);
        } catch (Exception e) {
            return null;
        }
    }

    private static NodeSchema attachRecommendationHints(NodeSchema schema, NodeRecommendationRules rules) {
        List<RecommendationHint> next = new ArrayList<>();
        List<RecommendationHint> upstream = new ArrayList<>();
        if (schema == null || schema.typeId() == null || rules == null || rules.sourceNodes == null) {
            return schema;
        }
        NodeRecommendationRules.SourceNodeRule rule =
                rules.sourceNodes.get(schema.typeId().toLowerCase(Locale.ROOT));
        if (rule == null) {
            return schema.withHints(List.of(), List.of());
        }

        if (rule.outputs != null) {
            for (Map.Entry<String, NodeRecommendationRules.PortDirectionRule> entry : rule.outputs.entrySet()) {
                for (NodeRecommendationRules.RuleEntry row : sortByOrder(
                        entry.getValue() == null ? List.of() : entry.getValue().downstream)) {
                    if (row == null || row.nodeId == null) {
                        continue;
                    }
                    next.add(new RecommendationHint(
                            row.nodeId,
                            entry.getKey(),
                            row.connectPortId,
                            row.reason
                    ));
                    if (next.size() >= MAX_HINTS_PER_DIRECTION) {
                        break;
                    }
                }
                if (next.size() >= MAX_HINTS_PER_DIRECTION) {
                    break;
                }
            }
        }

        if (rule.inputs != null) {
            for (Map.Entry<String, NodeRecommendationRules.PortDirectionRule> entry : rule.inputs.entrySet()) {
                for (NodeRecommendationRules.RuleEntry row : sortByOrder(
                        entry.getValue() == null ? List.of() : entry.getValue().upstream)) {
                    if (row == null || row.nodeId == null) {
                        continue;
                    }
                    upstream.add(new RecommendationHint(
                            row.nodeId,
                            row.connectPortId,
                            entry.getKey(),
                            row.reason
                    ));
                    if (upstream.size() >= MAX_HINTS_PER_DIRECTION) {
                        break;
                    }
                }
                if (upstream.size() >= MAX_HINTS_PER_DIRECTION) {
                    break;
                }
            }
        }

        return schema.withHints(next, upstream);
    }

    private static List<NodeRecommendationRules.RuleEntry> sortByOrder(List<NodeRecommendationRules.RuleEntry> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<NodeRecommendationRules.RuleEntry> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(row -> row == null ? Integer.MAX_VALUE : row.order));
        return sorted;
    }

    private static Map<String, NodeSchema> indexByTypeId(List<NodeSchema> schemas) {
        Map<String, NodeSchema> map = new HashMap<>();
        for (NodeSchema schema : schemas) {
            if (schema != null && schema.typeId() != null) {
                map.put(schema.typeId(), schema);
            }
        }
        return map;
    }

    private static List<NodeSchema> topByScore(List<NodeSchema> schemas, Map<String, Integer> scores, int limit) {
        List<NodeSchema> sorted = new ArrayList<>(schemas);
        sorted.sort(Comparator
                .comparingInt((NodeSchema s) -> scores.getOrDefault(s.typeId(), 0))
                .reversed()
                .thenComparing(NodeSchema::typeId, String.CASE_INSENSITIVE_ORDER));
        List<NodeSchema> top = new ArrayList<>();
        for (NodeSchema schema : sorted) {
            if (scores.getOrDefault(schema.typeId(), 0) <= 0) {
                continue;
            }
            top.add(schema);
            if (top.size() >= limit) {
                break;
            }
        }
        return top;
    }

    private static boolean containsCategoryPrefix(List<NodeSchema> schemas, String categoryPrefix) {
        String prefix = categoryPrefix == null ? "" : categoryPrefix.toLowerCase(Locale.ROOT);
        for (NodeSchema schema : schemas) {
            if (schema != null && AiNodeSchemaCatalog.safeLower(schema.category()).startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static NodeSchema findFirstByCategoryPrefix(
            List<NodeSchema> schemas,
            String categoryPrefix,
            Set<String> exclude
    ) {
        String prefix = categoryPrefix == null ? "" : categoryPrefix.toLowerCase(Locale.ROOT);
        for (NodeSchema schema : schemas) {
            if (schema == null || schema.typeId() == null) {
                continue;
            }
            if (exclude.contains(schema.typeId())) {
                continue;
            }
            if (AiNodeSchemaCatalog.safeLower(schema.category()).startsWith(prefix)) {
                return schema;
            }
        }
        return null;
    }
}

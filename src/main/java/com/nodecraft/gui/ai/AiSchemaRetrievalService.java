package com.nodecraft.gui.ai;

import com.nodecraft.gui.ai.AiNodeSchemaCatalog.NodeSchema;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.semantic.NodeDomain;
import com.nodecraft.nodesystem.semantic.NodeSemanticCatalog;
import com.nodecraft.nodesystem.semantic.NodeSemanticEdge;
import com.nodecraft.nodesystem.semantic.NodeSemanticEdgeKind;

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
 * {@link NodeSemanticCatalog} (recommendation edges + converters).
 */
public final class AiSchemaRetrievalService {

    private static final int LEXICAL_SEED_LIMIT = 20;
    private static final int MAX_NEIGHBORS_PER_SEED = 4;
    private static final int MAX_EXPANSION_HOPS = 2;

    private static final int WEIGHT_PROMPT_MATCH = 100;
    private static final int WEIGHT_EXACT_NEIGHBOR = 80;
    private static final int WEIGHT_CATEGORY_WORKFLOW = 50;
    private static final int WEIGHT_TYPE_WORKFLOW = 35;
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
        Set<NodeDomain> domainTags = AiIntentAnalysisService.detectDomainTags(userPrompt);
        boolean worldApply = AiIntentAnalysisService.hasWorldApplyIntent(userPrompt);

        Map<String, NodeSchema> byTypeId = indexByTypeId(allSchemas);
        NodeSemanticCatalog catalog = NodeSemanticCatalog.get();
        catalog.refreshIfNeeded();

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

        // Phase B: semantic expansion via catalog effective edges (exact / category / type)
        Set<String> frontier = new LinkedHashSet<>(selected);
        for (int hop = 0; hop < MAX_EXPANSION_HOPS; hop++) {
            Set<String> next = new LinkedHashSet<>();
            for (String typeId : frontier) {
                int taken = 0;
                for (NodeSemanticEdge edge : catalog.effectiveDownstream(typeId)) {
                    if (edge == null || edge.targetNodeId() == null || !byTypeId.containsKey(edge.targetNodeId())) {
                        continue;
                    }
                    next.add(edge.targetNodeId());
                    scores.merge(edge.targetNodeId(), neighborWeight(edge), Math::max);
                    taken++;
                    if (taken >= MAX_NEIGHBORS_PER_SEED) {
                        break;
                    }
                }
                int takenUp = 0;
                for (NodeSemanticEdge edge : catalog.effectiveUpstream(typeId)) {
                    if (edge == null || edge.targetNodeId() == null || !byTypeId.containsKey(edge.targetNodeId())) {
                        continue;
                    }
                    next.add(edge.targetNodeId());
                    scores.merge(edge.targetNodeId(), neighborWeight(edge), Math::max);
                    takenUp++;
                    if (takenUp >= MAX_NEIGHBORS_PER_SEED) {
                        break;
                    }
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
            for (String converterId : collectConverterNeighbors(schema, catalog)) {
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
            withHints.add(AiNodeSchemaCatalog.enrichFromCatalog(schema));
        }
        return withHints;
    }

    private static int neighborWeight(NodeSemanticEdge edge) {
        NodeSemanticEdgeKind kind = edge.kind() == null ? NodeSemanticEdgeKind.EXACT : edge.kind();
        int base = switch (kind) {
            case EXACT -> WEIGHT_EXACT_NEIGHBOR;
            case CATEGORY -> WEIGHT_CATEGORY_WORKFLOW;
            case TYPE -> WEIGHT_TYPE_WORKFLOW;
        };
        return base - edge.priority();
    }

    private static List<NodeSchema> resolveIntentAwareCore(
            List<NodeSchema> allSchemas,
            Set<NodeDomain> domainTags,
            boolean worldApply,
            boolean geometryIntent
    ) {
        List<NodeSchema> core = new ArrayList<>();
        addExactIfPresent(allSchemas, core, "input.context.player_position");

        boolean wantsBlocks = domainTags.contains(NodeDomain.MATERIAL) || worldApply;
        if (wantsBlocks) {
            addExactIfPresent(allSchemas, core, "output.preview.preview_blocks");
        } else {
            addExactIfPresent(allSchemas, core, "output.preview.preview_geometry");
        }

        if (geometryIntent || domainTags.contains(NodeDomain.GEOMETRY) || domainTags.contains(NodeDomain.ARCHITECTURE)) {
            addExactIfPresent(allSchemas, core, "geometry.voxel.voxelize_geometry");
        }

        if (domainTags.contains(NodeDomain.MATH)) {
            addPrefixLimited(allSchemas, core, "math.scalar_math.", 6);
        }
        if (domainTags.contains(NodeDomain.DATA_TREE)) {
            addPrefixLimited(allSchemas, core, "math.data_tree.", 8);
        }
        if (domainTags.contains(NodeDomain.FIELD) || domainTags.contains(NodeDomain.SDF)) {
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

    private static List<String> collectConverterNeighbors(NodeSchema schema, NodeSemanticCatalog catalog) {
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

        if (present.contains(NodeDataType.LIST) || present.contains(NodeDataType.DATA_TREE)) {
            addConverter(converters, catalog, NodeDataType.LIST, NodeDataType.DATA_TREE);
            addConverter(converters, catalog, NodeDataType.DATA_TREE, NodeDataType.LIST);
        }
        if (present.contains(NodeDataType.SDF)
                || present.contains(NodeDataType.SCALAR_FIELD)
                || present.contains(NodeDataType.VECTOR_FIELD)) {
            addConverter(converters, catalog, NodeDataType.SDF, NodeDataType.SCALAR_FIELD);
            addConverter(converters, catalog, NodeDataType.SDF, NodeDataType.VECTOR_FIELD);
        }
        return List.copyOf(converters);
    }

    private static void addConverter(
            Set<String> converters,
            NodeSemanticCatalog catalog,
            NodeDataType out,
            NodeDataType in
    ) {
        TypeConversionRegistry.ConversionSuggestion suggestion =
                catalog == null
                        ? TypeConversionRegistry.getSuggestedConversion(out, in)
                        : catalog.suggestedConversion(out, in);
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

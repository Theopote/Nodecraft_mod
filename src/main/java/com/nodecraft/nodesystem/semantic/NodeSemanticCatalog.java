package com.nodecraft.nodesystem.semantic;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.gui.recommendation.NodeRecommendationRules;
import com.nodecraft.gui.recommendation.NodeRecommendationRulesLoader;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Read-only semantic facade over {@link NodeRegistry}, {@link NodeEffectResolver},
 * {@link NodeRecommendationRules}, and {@link TypeConversionRegistry}.
 *
 * <p>Not a fifth knowledge base — descriptors are derived views keyed by
 * {@code (registryEpoch, rulesRevision)}.</p>
 */
public final class NodeSemanticCatalog {

    private static final NodeSemanticCatalog INSTANCE = new NodeSemanticCatalog();
    private static final AtomicLong RULES_REVISION = new AtomicLong(0L);

    private volatile long cachedRegistryEpoch = -1L;
    private volatile long cachedRulesRevision = -1L;
    private volatile Map<String, NodeSemanticDescriptor> byId = Map.of();
    private volatile NodeRecommendationRules rules = new NodeRecommendationRules();

    private NodeSemanticCatalog() {
    }

    public static NodeSemanticCatalog get() {
        return INSTANCE;
    }

    /**
     * Shared rules revision used as part of the catalog cache key.
     * Bumped when recommendation rules are reloaded (see {@link #invalidateRules()}).
     */
    public static long getRulesRevision() {
        return RULES_REVISION.get();
    }

    /** Call when {@code node_recommendations.json} is reloaded elsewhere. */
    public void invalidateRules() {
        RULES_REVISION.incrementAndGet();
    }

    public synchronized void refreshIfNeeded() {
        long epoch = NodeRegistry.getInstance().getIntrospectionEpoch();
        long revision = RULES_REVISION.get();
        if (epoch == cachedRegistryEpoch
                && revision == cachedRulesRevision
                && !byId.isEmpty()) {
            return;
        }
        rebuild(epoch, revision);
    }

    /** Force rebuild regardless of cache key (tests). */
    public synchronized void forceRefresh() {
        rebuild(NodeRegistry.getInstance().getIntrospectionEpoch(), RULES_REVISION.get());
    }

    private void rebuild(long epoch, long revision) {
        NodeRecommendationRules loaded = NodeRecommendationRulesLoader.load();
        this.rules = loaded == null ? new NodeRecommendationRules() : loaded;

        Map<String, NodeSemanticDescriptor> next = new HashMap<>();
        NodeRegistry registry = NodeRegistry.getInstance();
        for (String nodeId : registry.getAllNodeIds()) {
            NodeInfo info = registry.getNodeInfo(nodeId);
            if (info == null) {
                continue;
            }
            String canonical = info.getId();
            NodeEffect effect = NodeEffectResolver.resolve(info.getNodeClass(), canonical);
            Set<NodeCapability> capabilities =
                    NodeSemanticDeriver.deriveCapabilities(canonical, info.getCategoryId(), effect);
            Set<NodeDomain> domains =
                    NodeSemanticDeriver.deriveDomains(canonical, info.getCategoryId());
            List<NodeSemanticEdge> downstream = collectDownstream(canonical, loaded);
            List<NodeSemanticEdge> upstream = collectUpstream(canonical, loaded);
            next.put(canonical.toLowerCase(Locale.ROOT), new NodeSemanticDescriptor(
                    canonical,
                    info.getCategoryId(),
                    effect,
                    domains,
                    capabilities,
                    downstream,
                    upstream
            ));
        }

        this.byId = Map.copyOf(next);
        this.cachedRegistryEpoch = epoch;
        this.cachedRulesRevision = revision;
    }

    public NodeSemanticDescriptor describe(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        refreshIfNeeded();
        String key = resolveKey(nodeId);
        return byId.get(key);
    }

    public Set<NodeCapability> capabilities(String nodeId) {
        NodeSemanticDescriptor descriptor = describe(nodeId);
        if (descriptor != null) {
            return descriptor.capabilities();
        }
        // Registry miss (e.g. unit test without full registration): still derive from typeId.
        return NodeSemanticDeriver.deriveCapabilities(nodeId, null, NodeEffectResolver.inferFromTypeId(nodeId));
    }

    public Set<NodeDomain> domains(String nodeId) {
        NodeSemanticDescriptor descriptor = describe(nodeId);
        if (descriptor != null) {
            return descriptor.domains();
        }
        return NodeSemanticDeriver.deriveDomains(nodeId, null);
    }

    public NodeEffect effect(String nodeId) {
        NodeSemanticDescriptor descriptor = describe(nodeId);
        if (descriptor != null) {
            return descriptor.effect();
        }
        return NodeEffectResolver.inferFromTypeId(nodeId);
    }

    public List<NodeSemanticEdge> downstream(String nodeId) {
        NodeSemanticDescriptor descriptor = describe(nodeId);
        return descriptor == null ? List.of() : descriptor.downstream();
    }

    public List<NodeSemanticEdge> upstream(String nodeId) {
        NodeSemanticDescriptor descriptor = describe(nodeId);
        return descriptor == null ? List.of() : descriptor.upstream();
    }

    public TypeConversionRegistry.ConversionPolicy conversionBetween(NodeDataType from, NodeDataType to) {
        return TypeConversionRegistry.classify(from, to);
    }

    public TypeConversionRegistry.ConversionSuggestion suggestedConversion(NodeDataType from, NodeDataType to) {
        return TypeConversionRegistry.getSuggestedConversion(from, to);
    }

    public NodeRecommendationRules currentRules() {
        refreshIfNeeded();
        return rules;
    }

    private String resolveKey(String nodeId) {
        String canonical = NodeRegistry.getInstance().resolveCanonicalNodeId(nodeId);
        if (canonical == null || canonical.isBlank()) {
            canonical = nodeId;
        }
        return canonical.toLowerCase(Locale.ROOT);
    }

    private static List<NodeSemanticEdge> collectDownstream(String nodeId, NodeRecommendationRules rules) {
        List<NodeSemanticEdge> edges = new ArrayList<>();
        if (rules == null || rules.sourceNodes == null || nodeId == null) {
            return edges;
        }
        NodeRecommendationRules.SourceNodeRule rule =
                rules.sourceNodes.get(nodeId.toLowerCase(Locale.ROOT));
        if (rule == null || rule.outputs == null) {
            return edges;
        }
        for (Map.Entry<String, NodeRecommendationRules.PortDirectionRule> entry : rule.outputs.entrySet()) {
            List<NodeRecommendationRules.RuleEntry> rows =
                    entry.getValue() == null ? List.of() : entry.getValue().downstream;
            for (NodeRecommendationRules.RuleEntry row : sortByOrder(rows)) {
                if (row == null || row.nodeId == null || row.nodeId.isBlank()) {
                    continue;
                }
                edges.add(new NodeSemanticEdge(
                        row.nodeId,
                        entry.getKey(),
                        row.connectPortId,
                        row.reason,
                        row.order
                ));
            }
        }
        return List.copyOf(edges);
    }

    private static List<NodeSemanticEdge> collectUpstream(String nodeId, NodeRecommendationRules rules) {
        List<NodeSemanticEdge> edges = new ArrayList<>();
        if (rules == null || rules.sourceNodes == null || nodeId == null) {
            return edges;
        }
        NodeRecommendationRules.SourceNodeRule rule =
                rules.sourceNodes.get(nodeId.toLowerCase(Locale.ROOT));
        if (rule == null || rule.inputs == null) {
            return edges;
        }
        for (Map.Entry<String, NodeRecommendationRules.PortDirectionRule> entry : rule.inputs.entrySet()) {
            List<NodeRecommendationRules.RuleEntry> rows =
                    entry.getValue() == null ? List.of() : entry.getValue().upstream;
            for (NodeRecommendationRules.RuleEntry row : sortByOrder(rows)) {
                if (row == null || row.nodeId == null || row.nodeId.isBlank()) {
                    continue;
                }
                edges.add(new NodeSemanticEdge(
                        row.nodeId,
                        row.connectPortId,
                        entry.getKey(),
                        row.reason,
                        row.order
                ));
            }
        }
        return List.copyOf(edges);
    }

    private static List<NodeRecommendationRules.RuleEntry> sortByOrder(
            List<NodeRecommendationRules.RuleEntry> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<NodeRecommendationRules.RuleEntry> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(r -> r == null ? Integer.MAX_VALUE : r.order));
        return sorted;
    }
}

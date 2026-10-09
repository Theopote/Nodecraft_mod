package com.nodecraft.nodesystem.semantic;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.recommendation.NodeRecommendationRules;
import com.nodecraft.nodesystem.recommendation.NodeRecommendationRulesLoader;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only semantic facade over {@link NodeRegistry}, {@link NodeEffectResolver},
 * {@link NodeRecommendationRules}, and {@link TypeConversionRegistry}.
 *
 * <p>Not a fifth knowledge base — descriptors are derived views keyed by
 * {@code (registryEpoch, rulesRevision)}.</p>
 */
public final class NodeSemanticCatalog {

    private static final NodeSemanticCatalog INSTANCE = new NodeSemanticCatalog();

    private volatile long cachedRegistryEpoch = -1L;
    private volatile long cachedRulesRevision = -1L;
    private volatile Map<String, NodeSemanticDescriptor> byId = Map.of();
    private volatile NodeRecommendationRules rules = new NodeRecommendationRules();

    private NodeSemanticCatalog() {
    }

    public static NodeSemanticCatalog get() {
        return INSTANCE;
    }

    /** Shared rules revision (owned by {@link NodeRecommendationRulesLoader}). */
    public static long getRulesRevision() {
        return NodeRecommendationRulesLoader.getRulesRevision();
    }

    /** Call when {@code node_recommendations.json} is reloaded elsewhere. */
    public void invalidateRules() {
        NodeRecommendationRulesLoader.bumpRulesRevision();
    }

    public synchronized void refreshIfNeeded() {
        long epoch = NodeRegistry.getInstance().getIntrospectionEpoch();
        long revision = NodeRecommendationRulesLoader.getRulesRevision();
        if (epoch == cachedRegistryEpoch
                && revision == cachedRulesRevision
                && !byId.isEmpty()) {
            return;
        }
        rebuild(epoch, revision);
    }

    /** Force rebuild regardless of cache key (tests). */
    public synchronized void forceRefresh() {
        rebuild(NodeRegistry.getInstance().getIntrospectionEpoch(),
                NodeRecommendationRulesLoader.getRulesRevision());
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

            PortSnapshot ports = resolvePorts(registry, canonical);
            List<NodeSemanticEdge> downstream = mergeEdges(
                    collectExactDownstream(canonical, loaded),
                    collectCategoryDownstream(info.getCategoryId(), ports.outputs(), loaded),
                    collectTypeDownstream(ports.outputs(), loaded)
            );
            List<NodeSemanticEdge> upstream = mergeEdges(
                    collectExactUpstream(canonical, loaded),
                    collectCategoryUpstream(info.getCategoryId(), ports.inputs(), loaded),
                    collectTypeUpstream(ports.inputs(), loaded)
            );

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
        return byId.get(resolveKey(nodeId));
    }

    public Set<NodeCapability> capabilities(String nodeId) {
        NodeSemanticDescriptor descriptor = describe(nodeId);
        if (descriptor != null) {
            return descriptor.capabilities();
        }
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

    /** Effective merged edges (exact + category + type). Same as {@link #effectiveDownstream(String)}. */
    public List<NodeSemanticEdge> downstream(String nodeId) {
        return effectiveDownstream(nodeId);
    }

    public List<NodeSemanticEdge> upstream(String nodeId) {
        return effectiveUpstream(nodeId);
    }

    public List<NodeSemanticEdge> exactDownstream(String nodeId) {
        return filterKind(effectiveDownstream(nodeId), NodeSemanticEdgeKind.EXACT);
    }

    public List<NodeSemanticEdge> exactUpstream(String nodeId) {
        return filterKind(effectiveUpstream(nodeId), NodeSemanticEdgeKind.EXACT);
    }

    public List<NodeSemanticEdge> effectiveDownstream(String nodeId) {
        NodeSemanticDescriptor descriptor = describe(nodeId);
        if (descriptor != null) {
            return descriptor.downstream();
        }
        refreshIfNeeded();
        return collectEffectiveDownstreamOnDemand(nodeId, null, null);
    }

    public List<NodeSemanticEdge> effectiveUpstream(String nodeId) {
        NodeSemanticDescriptor descriptor = describe(nodeId);
        if (descriptor != null) {
            return descriptor.upstream();
        }
        refreshIfNeeded();
        return collectEffectiveUpstreamOnDemand(nodeId, null, null);
    }

    public List<NodeSemanticEdge> effectiveDownstream(String nodeId, String portId, NodeDataType dataType) {
        List<NodeSemanticEdge> all = effectiveDownstream(nodeId);
        if (portId == null && dataType == null) {
            return all;
        }
        if (portId != null || dataType != null) {
            // Prefer port-local recompute when registry miss / filtering needed.
            refreshIfNeeded();
            List<NodeSemanticEdge> local = collectEffectiveDownstreamOnDemand(nodeId, portId, dataType);
            if (!local.isEmpty() || describe(nodeId) == null) {
                return local;
            }
        }
        return filterPort(all, portId);
    }

    public List<NodeSemanticEdge> effectiveUpstream(String nodeId, String portId, NodeDataType dataType) {
        List<NodeSemanticEdge> all = effectiveUpstream(nodeId);
        if (portId == null && dataType == null) {
            return all;
        }
        if (portId != null || dataType != null) {
            refreshIfNeeded();
            List<NodeSemanticEdge> local = collectEffectiveUpstreamOnDemand(nodeId, portId, dataType);
            if (!local.isEmpty() || describe(nodeId) == null) {
                return local;
            }
        }
        return filterPort(all, portId);
    }

    public TypeConversionRegistry.ConversionPolicy conversionBetween(NodeDataType from, NodeDataType to) {
        return TypeConversionRegistry.classify(from, to);
    }

    public TypeConversionRegistry.ConversionSuggestion suggestedConversion(NodeDataType from, NodeDataType to) {
        return TypeConversionRegistry.getSuggestedConversion(from, to);
    }

    /** Package-facing rules snapshot for tests / recommendation service alignment. */
    NodeRecommendationRules currentRules() {
        refreshIfNeeded();
        return rules;
    }

    private List<NodeSemanticEdge> collectEffectiveDownstreamOnDemand(
            String nodeId,
            String portId,
            NodeDataType dataType
    ) {
        NodeRegistry registry = NodeRegistry.getInstance();
        NodeInfo info = registry.getNodeInfo(nodeId);
        String canonical = info != null ? info.getId() : nodeId;
        String category = info != null ? info.getCategoryId() : inferCategory(canonical);
        PortSnapshot ports = resolvePorts(registry, canonical);
        List<PortRef> outputs = filterPortRefs(ports.outputs(), portId, dataType);
        if (outputs.isEmpty() && dataType != null) {
            outputs = List.of(new PortRef(portId == null || portId.isBlank() ? "*" : portId, dataType));
        }
        return mergeEdges(
                filterPort(collectExactDownstream(canonical, rules), portId),
                collectCategoryDownstream(category, outputs, rules),
                collectTypeDownstream(outputs, rules)
        );
    }

    private List<NodeSemanticEdge> collectEffectiveUpstreamOnDemand(
            String nodeId,
            String portId,
            NodeDataType dataType
    ) {
        NodeRegistry registry = NodeRegistry.getInstance();
        NodeInfo info = registry.getNodeInfo(nodeId);
        String canonical = info != null ? info.getId() : nodeId;
        String category = info != null ? info.getCategoryId() : inferCategory(canonical);
        PortSnapshot ports = resolvePorts(registry, canonical);
        List<PortRef> inputs = filterPortRefs(ports.inputs(), portId, dataType);
        if (inputs.isEmpty() && dataType != null) {
            inputs = List.of(new PortRef(portId == null || portId.isBlank() ? "*" : portId, dataType));
        }
        return mergeEdges(
                filterPort(collectExactUpstream(canonical, rules), portId),
                collectCategoryUpstream(category, inputs, rules),
                collectTypeUpstream(inputs, rules)
        );
    }

    private static String inferCategory(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        int lastDot = nodeId.lastIndexOf('.');
        return lastDot > 0 ? nodeId.substring(0, lastDot) : nodeId;
    }

    private String resolveKey(String nodeId) {
        String canonical = NodeRegistry.getInstance().resolveCanonicalNodeId(nodeId);
        if (canonical == null || canonical.isBlank()) {
            canonical = nodeId;
        }
        return canonical.toLowerCase(Locale.ROOT);
    }

    private static PortSnapshot resolvePorts(NodeRegistry registry, String nodeId) {
        try {
            INode node = registry.createNodeInstance(nodeId);
            if (node == null) {
                return PortSnapshot.EMPTY;
            }
            List<PortRef> outputs = toPortRefs(node.getOutputPorts());
            List<PortRef> inputs = toPortRefs(node.getInputPorts());
            return new PortSnapshot(outputs, inputs);
        } catch (Exception e) {
            return PortSnapshot.EMPTY;
        }
    }

    private static List<PortRef> toPortRefs(List<IPort> ports) {
        if (ports == null || ports.isEmpty()) {
            return List.of();
        }
        List<PortRef> refs = new ArrayList<>(ports.size());
        for (IPort port : ports) {
            if (port == null || port.getId() == null) {
                continue;
            }
            refs.add(new PortRef(port.getId(), port.getDataType()));
        }
        return List.copyOf(refs);
    }

    private static List<PortRef> filterPortRefs(List<PortRef> ports, String portId, NodeDataType dataType) {
        if (ports == null || ports.isEmpty()) {
            return List.of();
        }
        if (portId == null && dataType == null) {
            return ports;
        }
        List<PortRef> filtered = new ArrayList<>();
        for (PortRef port : ports) {
            if (portId != null && !portId.equalsIgnoreCase(port.id())) {
                continue;
            }
            if (dataType != null && port.dataType() != dataType) {
                continue;
            }
            filtered.add(port);
        }
        return filtered;
    }

    private static List<NodeSemanticEdge> collectExactDownstream(String nodeId, NodeRecommendationRules rules) {
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
            for (NodeRecommendationRules.RuleEntry row : sortByOrder(
                    entry.getValue() == null ? List.of() : entry.getValue().downstream)) {
                if (row == null || row.nodeId == null || row.nodeId.isBlank()) {
                    continue;
                }
                edges.add(new NodeSemanticEdge(
                        row.nodeId,
                        entry.getKey(),
                        row.connectPortId,
                        row.reason,
                        row.order,
                        NodeSemanticEdgeKind.EXACT
                ));
            }
        }
        return edges;
    }

    private static List<NodeSemanticEdge> collectExactUpstream(String nodeId, NodeRecommendationRules rules) {
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
            for (NodeRecommendationRules.RuleEntry row : sortByOrder(
                    entry.getValue() == null ? List.of() : entry.getValue().upstream)) {
                if (row == null || row.nodeId == null || row.nodeId.isBlank()) {
                    continue;
                }
                edges.add(new NodeSemanticEdge(
                        row.nodeId,
                        row.connectPortId,
                        entry.getKey(),
                        row.reason,
                        row.order,
                        NodeSemanticEdgeKind.EXACT
                ));
            }
        }
        return edges;
    }

    private static List<NodeSemanticEdge> collectCategoryDownstream(
            String categoryId,
            List<PortRef> outputs,
            NodeRecommendationRules rules
    ) {
        List<NodeSemanticEdge> edges = new ArrayList<>();
        if (rules == null || rules.sourceCategories == null || outputs == null || outputs.isEmpty()) {
            return edges;
        }
        for (PortRef port : outputs) {
            if (port.dataType() == null) {
                continue;
            }
            String typeId = port.dataType().getId();
            for (Map.Entry<String, NodeRecommendationRules.SourceCategoryRule> entry
                    : rules.sourceCategories.entrySet()) {
                if (!matchesCategoryPrefix(categoryId, entry.getKey())) {
                    continue;
                }
                NodeRecommendationRules.SourceCategoryRule categoryRule = entry.getValue();
                if (categoryRule == null || categoryRule.outputTypes == null) {
                    continue;
                }
                NodeRecommendationRules.PortDirectionRule typeRule = categoryRule.outputTypes.get(typeId);
                appendDirectionDownstream(edges, port.id(), typeRule, NodeSemanticEdgeKind.CATEGORY);
            }
        }
        return edges;
    }

    private static List<NodeSemanticEdge> collectCategoryUpstream(
            String categoryId,
            List<PortRef> inputs,
            NodeRecommendationRules rules
    ) {
        List<NodeSemanticEdge> edges = new ArrayList<>();
        if (rules == null || rules.sourceCategories == null || inputs == null || inputs.isEmpty()) {
            return edges;
        }
        for (PortRef port : inputs) {
            if (port.dataType() == null) {
                continue;
            }
            String typeId = port.dataType().getId();
            for (Map.Entry<String, NodeRecommendationRules.SourceCategoryRule> entry
                    : rules.sourceCategories.entrySet()) {
                if (!matchesCategoryPrefix(categoryId, entry.getKey())) {
                    continue;
                }
                NodeRecommendationRules.SourceCategoryRule categoryRule = entry.getValue();
                if (categoryRule == null || categoryRule.inputTypes == null) {
                    continue;
                }
                NodeRecommendationRules.PortDirectionRule typeRule = categoryRule.inputTypes.get(typeId);
                appendDirectionUpstream(edges, port.id(), typeRule, NodeSemanticEdgeKind.CATEGORY);
            }
        }
        return edges;
    }

    private static List<NodeSemanticEdge> collectTypeDownstream(
            List<PortRef> outputs,
            NodeRecommendationRules rules
    ) {
        List<NodeSemanticEdge> edges = new ArrayList<>();
        if (rules == null || rules.outputTypes == null || outputs == null || outputs.isEmpty()) {
            return edges;
        }
        for (PortRef port : outputs) {
            if (port.dataType() == null) {
                continue;
            }
            NodeRecommendationRules.OutputTypeRule typeRule =
                    rules.outputTypes.get(port.dataType().getId());
            if (typeRule == null) {
                continue;
            }
            for (NodeRecommendationRules.RuleEntry row : sortByOrder(typeRule.downstream)) {
                if (row == null || row.nodeId == null || row.nodeId.isBlank()) {
                    continue;
                }
                edges.add(new NodeSemanticEdge(
                        row.nodeId,
                        port.id(),
                        row.connectPortId,
                        row.reason,
                        row.order,
                        NodeSemanticEdgeKind.TYPE
                ));
            }
        }
        return edges;
    }

    private static List<NodeSemanticEdge> collectTypeUpstream(
            List<PortRef> inputs,
            NodeRecommendationRules rules
    ) {
        List<NodeSemanticEdge> edges = new ArrayList<>();
        if (rules == null || rules.outputTypes == null || inputs == null || inputs.isEmpty()) {
            return edges;
        }
        for (PortRef port : inputs) {
            if (port.dataType() == null) {
                continue;
            }
            NodeRecommendationRules.OutputTypeRule typeRule =
                    rules.outputTypes.get(port.dataType().getId());
            if (typeRule == null) {
                continue;
            }
            for (NodeRecommendationRules.RuleEntry row : sortByOrder(typeRule.upstream)) {
                if (row == null || row.nodeId == null || row.nodeId.isBlank()) {
                    continue;
                }
                edges.add(new NodeSemanticEdge(
                        row.nodeId,
                        row.connectPortId,
                        port.id(),
                        row.reason,
                        row.order,
                        NodeSemanticEdgeKind.TYPE
                ));
            }
        }
        return edges;
    }

    private static void appendDirectionDownstream(
            List<NodeSemanticEdge> into,
            String sourcePortId,
            NodeRecommendationRules.PortDirectionRule rule,
            NodeSemanticEdgeKind kind
    ) {
        if (rule == null) {
            return;
        }
        for (NodeRecommendationRules.RuleEntry row : sortByOrder(rule.downstream)) {
            if (row == null || row.nodeId == null || row.nodeId.isBlank()) {
                continue;
            }
            into.add(new NodeSemanticEdge(
                    row.nodeId,
                    sourcePortId,
                    row.connectPortId,
                    row.reason,
                    row.order,
                    kind
            ));
        }
    }

    private static void appendDirectionUpstream(
            List<NodeSemanticEdge> into,
            String targetPortId,
            NodeRecommendationRules.PortDirectionRule rule,
            NodeSemanticEdgeKind kind
    ) {
        if (rule == null) {
            return;
        }
        for (NodeRecommendationRules.RuleEntry row : sortByOrder(rule.upstream)) {
            if (row == null || row.nodeId == null || row.nodeId.isBlank()) {
                continue;
            }
            into.add(new NodeSemanticEdge(
                    row.nodeId,
                    row.connectPortId,
                    targetPortId,
                    row.reason,
                    row.order,
                    kind
            ));
        }
    }

    @SafeVarargs
    private static List<NodeSemanticEdge> mergeEdges(List<NodeSemanticEdge>... tiers) {
        Map<String, NodeSemanticEdge> best = new LinkedHashMap<>();
        for (List<NodeSemanticEdge> tier : tiers) {
            if (tier == null) {
                continue;
            }
            for (NodeSemanticEdge edge : tier) {
                if (edge == null || edge.targetNodeId() == null || edge.targetNodeId().isBlank()) {
                    continue;
                }
                String key = dedupeKey(edge);
                NodeSemanticEdge existing = best.get(key);
                if (existing == null || isBetter(edge, existing)) {
                    best.put(key, edge);
                }
            }
        }
        List<NodeSemanticEdge> merged = new ArrayList<>(best.values());
        merged.sort(Comparator
                .comparingInt((NodeSemanticEdge e) -> e.kind().ordinal())
                .thenComparingInt(NodeSemanticEdge::priority)
                .thenComparing(e -> e.targetNodeId(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(merged);
    }

    private static boolean isBetter(NodeSemanticEdge candidate, NodeSemanticEdge existing) {
        int kindCmp = Integer.compare(candidate.kind().ordinal(), existing.kind().ordinal());
        if (kindCmp != 0) {
            return kindCmp < 0;
        }
        return candidate.priority() < existing.priority();
    }

    private static String dedupeKey(NodeSemanticEdge edge) {
        return safe(edge.targetNodeId()).toLowerCase(Locale.ROOT)
                + '|'
                + safe(edge.sourcePortId()).toLowerCase(Locale.ROOT)
                + '|'
                + safe(edge.targetPortId()).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static List<NodeSemanticEdge> filterKind(List<NodeSemanticEdge> edges, NodeSemanticEdgeKind kind) {
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }
        List<NodeSemanticEdge> filtered = new ArrayList<>();
        for (NodeSemanticEdge edge : edges) {
            if (edge != null && edge.kind() == kind) {
                filtered.add(edge);
            }
        }
        return List.copyOf(filtered);
    }

    private static List<NodeSemanticEdge> filterPort(List<NodeSemanticEdge> edges, String portId) {
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }
        if (portId == null || portId.isBlank()) {
            return edges;
        }
        List<NodeSemanticEdge> filtered = new ArrayList<>();
        for (NodeSemanticEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            // Downstream: sourcePortId is this node's port; upstream: targetPortId is this node's port.
            if (portId.equalsIgnoreCase(edge.sourcePortId()) || portId.equalsIgnoreCase(edge.targetPortId())) {
                filtered.add(edge);
            }
        }
        return List.copyOf(filtered);
    }

    private static boolean matchesCategoryPrefix(String categoryId, String prefix) {
        if (categoryId == null || prefix == null) {
            return false;
        }
        String lowerCategory = categoryId.toLowerCase(Locale.ROOT);
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return lowerCategory.equals(lowerPrefix) || lowerCategory.startsWith(lowerPrefix + ".");
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

    private record PortRef(String id, NodeDataType dataType) {
    }

    private record PortSnapshot(List<PortRef> outputs, List<PortRef> inputs) {
        static final PortSnapshot EMPTY = new PortSnapshot(List.of(), List.of());

        PortSnapshot {
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
        }
    }
}

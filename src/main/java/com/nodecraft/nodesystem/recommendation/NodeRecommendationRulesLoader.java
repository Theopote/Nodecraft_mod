package com.nodecraft.nodesystem.recommendation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Loads {@code node_recommendations.json} and owns the shared rules revision counter
 * used by Suggested Connections and {@code NodeSemanticCatalog}.
 */
public final class NodeRecommendationRulesLoader {

    private static final String RESOURCE_PATH = "/nodecraft/node_recommendations.json";
    private static final Gson GSON = new Gson();
    private static final AtomicLong RULES_REVISION = new AtomicLong(0L);

    private NodeRecommendationRulesLoader() {
    }

    public static long getRulesRevision() {
        return RULES_REVISION.get();
    }

    /** Bump when rules are reloaded so catalog / recommendation caches invalidate together. */
    public static long bumpRulesRevision() {
        return RULES_REVISION.incrementAndGet();
    }

    public static NodeRecommendationRules load() {
        try (InputStream stream = openRulesStream()) {
            if (stream == null) {
                NodeCraft.LOGGER.warn("Node recommendation rules not found at {}", RESOURCE_PATH);
                return new NodeRecommendationRules();
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            normalizeStringRuleArrays(root);
            NodeRecommendationRules rules = GSON.fromJson(root, NodeRecommendationRules.class);
            if (rules == null) {
                return new NodeRecommendationRules();
            }

            validateAgainstRegistry(rules);
            return rules;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to load node recommendation rules: {}", e.getMessage(), e);
            return new NodeRecommendationRules();
        }
    }

    private static void normalizeStringRuleArrays(JsonObject root) {
        if (root.has("sourceCategories")) {
            JsonObject categories = root.getAsJsonObject("sourceCategories");
            for (String categoryKey : categories.keySet()) {
                JsonObject category = categories.getAsJsonObject(categoryKey);
                if (category.has("outputTypes")) {
                    normalizePortDirectionMap(category.getAsJsonObject("outputTypes"));
                }
                if (category.has("inputTypes")) {
                    normalizePortDirectionMap(category.getAsJsonObject("inputTypes"));
                }
            }
        }

        if (root.has("outputTypes")) {
            JsonObject outputTypes = root.getAsJsonObject("outputTypes");
            for (String typeKey : outputTypes.keySet()) {
                normalizeDirectionLists(outputTypes.getAsJsonObject(typeKey));
            }
        }

        if (root.has("sourceNodes")) {
            JsonObject sourceNodes = root.getAsJsonObject("sourceNodes");
            for (String nodeKey : sourceNodes.keySet()) {
                JsonObject nodeRule = sourceNodes.getAsJsonObject(nodeKey);
                if (nodeRule.has("outputs")) {
                    normalizePortDirectionMap(nodeRule.getAsJsonObject("outputs"));
                }
                if (nodeRule.has("inputs")) {
                    normalizePortDirectionMap(nodeRule.getAsJsonObject("inputs"));
                }
            }
        }
    }

    private static void normalizePortDirectionMap(JsonObject portMap) {
        for (String portKey : portMap.keySet()) {
            normalizeDirectionLists(portMap.getAsJsonObject(portKey));
        }
    }

    private static void normalizeDirectionLists(JsonObject directionContainer) {
        normalizeRuleArray(directionContainer, "downstream");
        normalizeRuleArray(directionContainer, "upstream");
    }

    private static void normalizeRuleArray(JsonObject container, String key) {
        if (!container.has(key)) {
            return;
        }
        JsonElement element = container.get(key);
        if (!element.isJsonArray()) {
            return;
        }
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement entry = array.get(i);
            if (!entry.isJsonPrimitive()) {
                continue;
            }
            JsonObject objectEntry = new JsonObject();
            objectEntry.addProperty("nodeId", entry.getAsString());
            objectEntry.addProperty("order", i * 10);
            array.set(i, objectEntry);
        }
    }

    private static InputStream openRulesStream() {
        InputStream stream = NodeRecommendationRulesLoader.class.getResourceAsStream(RESOURCE_PATH);
        if (stream != null) {
            return stream;
        }

        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_PATH.substring(1));
        if (stream != null) {
            return stream;
        }

        try {
            Path devPath = Path.of("src/main/resources/nodecraft/node_recommendations.json");
            if (Files.isRegularFile(devPath)) {
                return Files.newInputStream(devPath);
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("Unable to read dev recommendation rules from filesystem: {}", e.getMessage());
        }
        return null;
    }

    private static void validateAgainstRegistry(NodeRecommendationRules rules) {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (registry.getNodeCount() == 0) {
            return;
        }

        Set<String> knownIds = Set.copyOf(registry.getAllNodeIds());
        collectRuleNodeIds(rules, knownIds);
    }

    private static void collectRuleNodeIds(NodeRecommendationRules rules, Set<String> knownIds) {
        if (rules.sourceNodes != null) {
            for (NodeRecommendationRules.SourceNodeRule sourceRule : rules.sourceNodes.values()) {
                collectFromPortMap(sourceRule.outputs, knownIds);
                collectFromPortMap(sourceRule.inputs, knownIds);
            }
        }
        if (rules.sourceCategories != null) {
            for (NodeRecommendationRules.SourceCategoryRule categoryRule : rules.sourceCategories.values()) {
                if (categoryRule.outputTypes != null) {
                    for (NodeRecommendationRules.PortDirectionRule portRule : categoryRule.outputTypes.values()) {
                        collectFromDirectionRule(portRule, knownIds);
                    }
                }
                if (categoryRule.inputTypes != null) {
                    for (NodeRecommendationRules.PortDirectionRule portRule : categoryRule.inputTypes.values()) {
                        collectFromDirectionRule(portRule, knownIds);
                    }
                }
            }
        }
        if (rules.outputTypes != null) {
            for (NodeRecommendationRules.OutputTypeRule typeRule : rules.outputTypes.values()) {
                collectEntries(typeRule.downstream, knownIds);
                collectEntries(typeRule.upstream, knownIds);
            }
        }
    }

    private static void collectFromPortMap(
            Map<String, NodeRecommendationRules.PortDirectionRule> portMap,
            Set<String> knownIds) {
        if (portMap == null) {
            return;
        }
        for (NodeRecommendationRules.PortDirectionRule rule : portMap.values()) {
            collectFromDirectionRule(rule, knownIds);
        }
    }

    private static void collectFromDirectionRule(
            NodeRecommendationRules.PortDirectionRule rule,
            Set<String> knownIds) {
        if (rule == null) {
            return;
        }
        collectEntries(rule.downstream, knownIds);
        collectEntries(rule.upstream, knownIds);
    }

    private static void collectEntries(List<NodeRecommendationRules.RuleEntry> entries, Set<String> knownIds) {
        if (entries == null) {
            return;
        }
        for (NodeRecommendationRules.RuleEntry entry : entries) {
            if (entry == null || entry.nodeId == null || entry.nodeId.isBlank()) {
                continue;
            }
            String normalized = entry.nodeId.toLowerCase();
            if (!knownIds.contains(normalized)) {
                NodeCraft.LOGGER.warn("Node recommendation rules reference unknown node id: {}", normalized);
            }
        }
    }
}

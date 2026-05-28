package com.nodecraft.gui.ai;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds compact node schema metadata for AI prompt construction.
 */
public final class AiNodeSchemaCatalog {

    private static final List<String> ALWAYS_INCLUDE_TYPE_PREFIXES = List.of(
            "output.execute.apply_changes",
            "output.preview.",
            "input.numeric.",
            "math.scalar_math."
    );

        private static final List<String> DIVERSITY_CATEGORY_PREFIXES = List.of(
            "input.",
            "math.",
            "output."
        );

    private AiNodeSchemaCatalog() {
    }

    public record PortSchema(String id, String displayName, String dataType, boolean required, String description) {
    }

    public record ParamSchema(String name, String valueType) {
    }

    public record NodeSchema(
            String typeId,
            String displayName,
            String description,
            String category,
            List<PortSchema> inputs,
            List<PortSchema> outputs,
            List<ParamSchema> params
    ) {
    }

    public static List<NodeSchema> collectAll(NodeRegistry registry) {
        List<NodeSchema> schemas = new ArrayList<>();
        if (registry == null) {
            return schemas;
        }

        List<String> nodeIds = new ArrayList<>(registry.getAllNodeIds());
        nodeIds.sort(String::compareToIgnoreCase);

        for (String nodeId : nodeIds) {
            NodeInfo info = registry.getNodeInfo(nodeId);
            if (info == null) {
                continue;
            }

            try {
                INode node = registry.createNodeInstance(nodeId);
                List<PortSchema> inputs = convertPorts(node.getInputPorts());
                List<PortSchema> outputs = convertPorts(node.getOutputPorts());
                List<ParamSchema> params = extractParamSchema(node.getNodeState());

                schemas.add(new NodeSchema(
                        info.getId(),
                        info.getDisplayName(),
                        info.getDescription(),
                        info.getCategoryId(),
                        inputs,
                        outputs,
                        params
                ));
            } catch (Exception ignored) {
                // Skip nodes that cannot be instantiated in current runtime state.
            }
        }
        return schemas;
    }

    public static List<NodeSchema> selectRelevant(List<NodeSchema> allSchemas, String userPrompt, int limit) {
        if (allSchemas == null || allSchemas.isEmpty()) {
            return List.of();
        }
        int safeLimit = Math.max(1, limit);
        String prompt = userPrompt == null ? "" : userPrompt.toLowerCase(Locale.ROOT);
        Set<String> tokens = expandIntentTokens(prompt, tokenize(prompt));
        boolean generationIntent = hasGenerationIntent(prompt);
        boolean geometryIntent = hasGeometryIntent(prompt);
        boolean spatialIntent = hasSpatialIntent(prompt);

        List<NodeSchema> sorted = new ArrayList<>(allSchemas);
        sorted.sort(Comparator
                .comparingInt((NodeSchema schema) -> relevanceScore(schema, tokens, generationIntent, geometryIntent, spatialIntent))
                .reversed()
                .thenComparing(NodeSchema::typeId, String.CASE_INSENSITIVE_ORDER));

        List<NodeSchema> mustHave = new ArrayList<>();
        List<NodeSchema> scored = new ArrayList<>();
        for (NodeSchema schema : sorted) {
            if (isAlwaysIncludeSchema(schema)) {
                mustHave.add(schema);
            } else {
                scored.add(schema);
            }
        }

        List<NodeSchema> result = new ArrayList<>(safeLimit);
        for (NodeSchema schema : mustHave) {
            if (result.size() >= safeLimit) {
                return result;
            }
            result.add(schema);
        }

        // Ensure basic category diversity before consuming all remaining high-score slots.
        for (String categoryPrefix : DIVERSITY_CATEGORY_PREFIXES) {
            if (result.size() >= safeLimit) {
                break;
            }
            if (containsCategoryPrefix(result, categoryPrefix)) {
                continue;
            }

            NodeSchema candidate = findFirstByCategoryPrefix(scored, categoryPrefix);
            if (candidate != null && !result.contains(candidate)) {
                result.add(candidate);
            }
        }

        for (NodeSchema schema : scored) {
            if (result.size() >= safeLimit) {
                break;
            }
            if (result.contains(schema)) {
                continue;
            }
            result.add(schema);
        }

        return result;
    }

    private static boolean containsCategoryPrefix(List<NodeSchema> schemas, String categoryPrefix) {
        if (schemas == null || schemas.isEmpty() || categoryPrefix == null || categoryPrefix.isBlank()) {
            return false;
        }
        for (NodeSchema schema : schemas) {
            if (schema != null && safeLower(schema.category()).startsWith(safeLower(categoryPrefix))) {
                return true;
            }
        }
        return false;
    }

    private static NodeSchema findFirstByCategoryPrefix(List<NodeSchema> schemas, String categoryPrefix) {
        if (schemas == null || schemas.isEmpty() || categoryPrefix == null || categoryPrefix.isBlank()) {
            return null;
        }
        String prefix = safeLower(categoryPrefix);
        for (NodeSchema schema : schemas) {
            if (schema != null && safeLower(schema.category()).startsWith(prefix)) {
                return schema;
            }
        }
        return null;
    }

    private static boolean isAlwaysIncludeSchema(NodeSchema schema) {
        if (schema == null || schema.typeId() == null) {
            return false;
        }
        String typeId = schema.typeId().toLowerCase(Locale.ROOT);
        for (String prefix : ALWAYS_INCLUDE_TYPE_PREFIXES) {
            if (typeId.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static List<PortSchema> convertPorts(List<IPort> ports) {
        List<PortSchema> result = new ArrayList<>();
        if (ports == null) return result;
        for (IPort port : ports) {
            // Minified port schema: AI mostly needs ID and DataType for connectivity logic.
            // Display names and long descriptions are truncated to save tokens.
            result.add(new PortSchema(
                    port.getId(),
                    null, // Remove display name for prompt efficiency
                    port.getDataType().getId(),
                    port.isRequired(),
                    null  // Remove description unless specifically needed
            ));
        }
        return result;
    }

    private static List<ParamSchema> extractParamSchema(Object nodeState) {
        List<ParamSchema> params = new ArrayList<>();
        if (!(nodeState instanceof Map<?, ?> map)) return params;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String keyText)) continue;
            Object value = entry.getValue();
            String valueType = "any";
            if (value != null) {
                if (value instanceof Number) valueType = "number";
                else if (value instanceof Boolean) valueType = "boolean";
                else if (value instanceof String) valueType = "string";
                else valueType = value.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            }
            params.add(new ParamSchema(keyText, valueType));
        }
        return params;
    }

    private static int relevanceScore(
            NodeSchema schema,
            Set<String> tokens,
            boolean generationIntent,
            boolean geometryIntent,
            boolean spatialIntent
    ) {
        if (tokens.isEmpty()) {
            return schema.category().startsWith("output.") ? 2 : 1;
        }

        String typeId = safeLower(schema.typeId());
        String displayName = safeLower(schema.displayName());
        String description = safeLower(schema.description());
        String category = safeLower(schema.category());
        String haystack = typeId + " " + displayName + " " + description + " " + category;

        int score = 0;
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }

            if (typeId.contains(token)) {
                score += 5;
            }
            if (displayName.contains(token)) {
                score += 4;
            }
            if (category.contains(token)) {
                score += 4;
            }
            if (description.contains(token)) {
                score += 2;
            }
            if (haystack.contains(token)) {
                score += 1;
            }

            for (PortSchema input : schema.inputs()) {
                if (safeLower(input.id()).contains(token)
                        || safeLower(input.displayName()).contains(token)
                        || safeLower(input.description()).contains(token)
                        || safeLower(input.dataType()).contains(token)) {
                    score += 2;
                }
            }

            for (PortSchema output : schema.outputs()) {
                if (safeLower(output.id()).contains(token)
                        || safeLower(output.displayName()).contains(token)
                        || safeLower(output.description()).contains(token)
                        || safeLower(output.dataType()).contains(token)) {
                    score += 2;
                }
            }

            for (ParamSchema param : schema.params()) {
                if (safeLower(param.name()).contains(token) || safeLower(param.valueType()).contains(token)) {
                    score += 1;
                }
            }
        }

        if (schema.category().startsWith("output.")) {
            score += 1;
        }

        if (generationIntent) {
            if (category.startsWith("output.")) {
                score += 6;
            }
            if (typeId.contains("bake") || displayName.contains("bake") || category.contains("bake")) {
                score += 5;
            }
        }

        if (geometryIntent) {
            if (typeId.contains("sphere") || displayName.contains("sphere") || description.contains("sphere")) {
                score += 8;
            }
            if (category.contains("geometry") || typeId.contains("geometry") || displayName.contains("geometry")) {
                score += 4;
            }
        }

        if (spatialIntent) {
            if (typeId.contains("position") || displayName.contains("position") || description.contains("position")) {
                score += 4;
            }
            if (typeId.contains("offset") || displayName.contains("offset") || description.contains("offset")) {
                score += 3;
            }
        }

        return score;
    }

    private static Set<String> expandIntentTokens(String prompt, Set<String> baseTokens) {
        Set<String> tokens = new HashSet<>(baseTokens == null ? Set.of() : baseTokens);
        String text = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);

        if (containsAny(text, "球", "圆球", "sphere", "ball", "spherical")) {
            tokens.add("sphere");
            tokens.add("geometry");
            tokens.add("mesh");
        }

        if (containsAny(text, "玩家", "player", "角色", "entity")) {
            tokens.add("player");
            tokens.add("entity");
            tokens.add("position");
        }

        if (containsAny(text, "头上", "头顶", "上方", "above", "overhead")) {
            tokens.add("offset");
            tokens.add("position");
            tokens.add("translate");
        }

        if (containsAny(text, "生成", "放置", "输出", "烘焙", "bake", "spawn", "produce", "output", "create")) {
            tokens.add("output");
            tokens.add("bake");
            tokens.add("preview");
        }

        return tokens;
    }

    private static boolean hasGenerationIntent(String prompt) {
        return containsAny(prompt, "生成", "放置", "输出", "烘焙", "spawn", "produce", "output", "create", "bake");
    }

    private static boolean hasGeometryIntent(String prompt) {
        return containsAny(prompt, "几何", "模型", "球", "圆球", "sphere", "mesh", "geometry", "shape");
    }

    private static boolean hasSpatialIntent(String prompt) {
        return containsAny(prompt, "位置", "坐标", "头上", "头顶", "上方", "position", "offset", "above", "overhead");
    }

    private static boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank() || keywords == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String safeLower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }

        String[] split = text.split("[^a-zA-Z0-9_\\u4e00-\\u9fa5]+");
        for (String token : split) {
            if (token != null && token.length() >= 2) {
                tokens.add(token.toLowerCase(Locale.ROOT));
            }
        }
        return tokens;
    }
}

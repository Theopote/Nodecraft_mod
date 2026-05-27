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
        Set<String> tokens = tokenize(prompt);

        List<NodeSchema> sorted = new ArrayList<>(allSchemas);
        sorted.sort(Comparator
                .comparingInt((NodeSchema schema) -> relevanceScore(schema, tokens))
                .reversed()
                .thenComparing(NodeSchema::typeId, String.CASE_INSENSITIVE_ORDER));

        if (sorted.size() > safeLimit) {
            return new ArrayList<>(sorted.subList(0, safeLimit));
        }
        return sorted;
    }

    private static List<PortSchema> convertPorts(List<IPort> ports) {
        List<PortSchema> result = new ArrayList<>();
        if (ports == null) {
            return result;
        }
        for (IPort port : ports) {
            result.add(new PortSchema(
                    port.getId(),
                    port.getDisplayName(),
                    port.getDataType().getId(),
                    port.isRequired(),
                    port.getDescription()
            ));
        }
        return result;
    }

    private static List<ParamSchema> extractParamSchema(Object nodeState) {
        List<ParamSchema> params = new ArrayList<>();
        if (!(nodeState instanceof Map<?, ?> map)) {
            return params;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String keyText) || keyText.isBlank()) {
                continue;
            }
            Object value = entry.getValue();
            String valueType = value == null ? "any" : value.getClass().getSimpleName();
            params.add(new ParamSchema(keyText, valueType));
        }
        params.sort(Comparator.comparing(ParamSchema::name, String.CASE_INSENSITIVE_ORDER));
        return params;
    }

    private static int relevanceScore(NodeSchema schema, Set<String> tokens) {
        if (tokens.isEmpty()) {
            return schema.category().startsWith("output.") ? 2 : 1;
        }

        String haystack = (
                schema.typeId() + " "
                        + schema.displayName() + " "
                        + schema.description() + " "
                        + schema.category()
        ).toLowerCase(Locale.ROOT);

        int score = 0;
        for (String token : tokens) {
            if (haystack.contains(token)) {
                score += 2;
            }
            for (PortSchema input : schema.inputs()) {
                if (input.id().toLowerCase(Locale.ROOT).contains(token)
                        || input.displayName().toLowerCase(Locale.ROOT).contains(token)) {
                    score++;
                }
            }
        }

        if (schema.category().startsWith("output.")) {
            score += 1;
        }
        return score;
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

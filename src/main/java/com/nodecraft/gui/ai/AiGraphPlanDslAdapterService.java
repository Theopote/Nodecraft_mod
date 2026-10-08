package com.nodecraft.gui.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.gui.ai.model.AiPlanConnection;
import com.nodecraft.gui.ai.model.AiPlanNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AiGraphPlanDslAdapterService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_COMPACT = new GsonBuilder().create();

    private AiGraphPlanDslAdapterService() {
    }

    public static String toDslJson(AiGraphPlan plan) {
        return toDslJson(plan, false);
    }

    public static String toDslJsonCompact(AiGraphPlan plan) {
        return toDslJson(plan, true);
    }

    private static String toDslJson(AiGraphPlan plan, boolean compact) {
        JsonObject root = new JsonObject();
        root.addProperty("description", plan.summary());

        JsonArray nodesArray = new JsonArray();
        for (AiPlanNode node : safeNodes(plan)) {
            JsonObject nodeObj = new JsonObject();
            nodeObj.addProperty("id", node.ref());
            nodeObj.addProperty("type", node.typeId());

            JsonObject paramsObj = null;
            if (node.nodeState() instanceof Map<?, ?> stateMap) {
                paramsObj = GSON.toJsonTree(sanitizeForJson(stateMap)).getAsJsonObject();
            }
            nodeObj.add("params", paramsObj == null ? new JsonObject() : paramsObj);

            JsonObject posObj = new JsonObject();
            posObj.addProperty("x", node.offsetX());
            posObj.addProperty("y", node.offsetY());
            nodeObj.add("position", posObj);
            nodesArray.add(nodeObj);
        }
        root.add("nodes", nodesArray);

        JsonArray connectionsArray = new JsonArray();
        for (AiPlanConnection connection : safeConnections(plan)) {
            JsonObject connObj = new JsonObject();

            JsonObject fromObj = new JsonObject();
            fromObj.addProperty("nodeId", connection.sourceRef());
            fromObj.addProperty("port", connection.sourcePortId());

            JsonObject toObj = new JsonObject();
            toObj.addProperty("nodeId", connection.targetRef());
            toObj.addProperty("port", connection.targetPortId());

            connObj.add("from", fromObj);
            connObj.add("to", toObj);
            connectionsArray.add(connObj);
        }
        root.add("connections", connectionsArray);

        return compact ? GSON_COMPACT.toJson(root) : GSON.toJson(root);
    }

    public static AiGraphPlan fromDsl(AiGraphDslSupport.DslGraph dslGraph) {
        List<AiPlanNode> nodes = new ArrayList<>();
        List<AiPlanConnection> connections = new ArrayList<>();

        for (AiGraphDslSupport.DslNode node : dslGraph.nodes()) {
            float x = node.position() != null ? node.position().x() : 0.0f;
            float y = node.position() != null ? node.position().y() : 0.0f;
            Object state = node.params() == null ? null : new HashMap<>(node.params());
            nodes.add(new AiPlanNode(node.id(), node.type(), x, y, state));
        }

        for (AiGraphDslSupport.DslConnection connection : dslGraph.connections()) {
            connections.add(new AiPlanConnection(
                    connection.from().nodeId(),
                    connection.from().port(),
                    connection.to().nodeId(),
                    connection.to().port()
            ));
        }

        String summary = dslGraph.description() == null || dslGraph.description().isBlank()
                ? "AI JSON plan parsed and validated."
                : dslGraph.description();
        return new AiGraphPlan(summary, nodes, connections, List.of());
    }

    public static AiGraphPlan fromMockPlan(AiMockPlanService.MockPlan mockPlan) {
        List<AiPlanNode> nodes = new ArrayList<>();
        for (AiMockPlanService.MockNode node : mockPlan.nodes()) {
            nodes.add(new AiPlanNode(node.ref(), node.typeId(), node.offsetX(), node.offsetY(), node.nodeState()));
        }

        List<AiPlanConnection> connections = new ArrayList<>();
        for (AiMockPlanService.MockConnection connection : mockPlan.connections()) {
            connections.add(new AiPlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        String summary = mockPlan.summary() == null ? "" : mockPlan.summary();
        List<String> errors = mockPlan.validationErrors() == null ? List.of() : mockPlan.validationErrors();
        return new AiGraphPlan(summary, nodes, connections, errors);
    }

    private static List<AiPlanNode> safeNodes(AiGraphPlan plan) {
        return plan.nodes() == null ? List.of() : plan.nodes();
    }

    private static List<AiPlanConnection> safeConnections(AiGraphPlan plan) {
        return plan.connections() == null ? List.of() : plan.connections();
    }

    private static Object sanitizeForJson(Object value) {
        switch (value) {
            case null -> {
                return null;
            }
            case Double doubleValue -> {
                return Double.isFinite(doubleValue) ? doubleValue : 0.0d;
            }
            case Float floatValue -> {
                return Float.isFinite(floatValue) ? floatValue : 0.0f;
            }
            case Map<?, ?> mapValue -> {
                Map<String, Object> sanitizedMap = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                    if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                        continue;
                    }
                    sanitizedMap.put(key, sanitizeForJson(entry.getValue()));
                }
                return sanitizedMap;
            }
            case List<?> listValue -> {
                List<Object> sanitizedList = new ArrayList<>(listValue.size());
                for (Object item : listValue) {
                    sanitizedList.add(sanitizeForJson(item));
                }
                return sanitizedList;
            }
            default -> {
            }
        }

        return value;
    }
}

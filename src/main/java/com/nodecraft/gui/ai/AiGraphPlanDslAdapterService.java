package com.nodecraft.gui.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AiGraphPlanDslAdapterService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AiGraphPlanDslAdapterService() {
    }

    public record PlanNode(String ref, String typeId, float offsetX, float offsetY, Object nodeState) {
    }

    public record PlanConnection(String sourceRef, String sourcePortId, String targetRef, String targetPortId) {
    }

    public record GraphPlan(String summary, List<PlanNode> nodes, List<PlanConnection> connections, List<String> validationErrors) {
    }

    public static String toDslJson(GraphPlan plan) {
        JsonObject root = new JsonObject();
        root.addProperty("description", plan.summary());

        JsonArray nodesArray = new JsonArray();
        for (PlanNode node : safeNodes(plan)) {
            JsonObject nodeObj = new JsonObject();
            nodeObj.addProperty("id", node.ref());
            nodeObj.addProperty("type", node.typeId());

            JsonObject paramsObj = null;
            if (node.nodeState() instanceof Map<?, ?> stateMap) {
                paramsObj = GSON.toJsonTree(stateMap).getAsJsonObject();
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
        for (PlanConnection connection : safeConnections(plan)) {
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

        return GSON.toJson(root);
    }

    public static GraphPlan fromDsl(AiGraphDslSupport.DslGraph dslGraph) {
        List<PlanNode> nodes = new ArrayList<>();
        List<PlanConnection> connections = new ArrayList<>();

        for (AiGraphDslSupport.DslNode node : dslGraph.nodes()) {
            float x = node.position() != null ? node.position().x() : 0.0f;
            float y = node.position() != null ? node.position().y() : 0.0f;
            Object state = node.params() == null ? null : new HashMap<>(node.params());
            nodes.add(new PlanNode(node.id(), node.type(), x, y, state));
        }

        for (AiGraphDslSupport.DslConnection connection : dslGraph.connections()) {
            connections.add(new PlanConnection(
                    connection.from().nodeId(),
                    connection.from().port(),
                    connection.to().nodeId(),
                    connection.to().port()
            ));
        }

        String summary = dslGraph.description() == null || dslGraph.description().isBlank()
                ? "AI JSON plan parsed and validated."
                : dslGraph.description();
        return new GraphPlan(summary, nodes, connections, List.of());
    }

    public static GraphPlan fromMockPlan(AiMockPlanService.MockPlan mockPlan) {
        List<PlanNode> nodes = new ArrayList<>();
        for (AiMockPlanService.MockNode node : mockPlan.nodes()) {
            nodes.add(new PlanNode(node.ref(), node.typeId(), node.offsetX(), node.offsetY(), node.nodeState()));
        }

        List<PlanConnection> connections = new ArrayList<>();
        for (AiMockPlanService.MockConnection connection : mockPlan.connections()) {
            connections.add(new PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        String summary = mockPlan.summary() == null ? "" : mockPlan.summary();
        List<String> errors = mockPlan.validationErrors() == null ? List.of() : mockPlan.validationErrors();
        return new GraphPlan(summary, nodes, connections, errors);
    }

    private static List<PlanNode> safeNodes(GraphPlan plan) {
        return plan.nodes() == null ? List.of() : plan.nodes();
    }

    private static List<PlanConnection> safeConnections(GraphPlan plan) {
        return plan.connections() == null ? List.of() : plan.connections();
    }
}

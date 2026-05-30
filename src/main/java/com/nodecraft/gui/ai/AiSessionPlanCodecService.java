package com.nodecraft.gui.ai;

import com.nodecraft.gui.ai.AiGraphPlanDslAdapterService.GraphPlan;
import com.nodecraft.gui.components.ai.AiAssistantComponent;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiGraphPlan;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.List;

public final class AiSessionPlanCodecService {

    private AiSessionPlanCodecService() {
    }

    public static String serializePendingPlanToDsl(AiGraphPlan plan) {
        if (plan == null) {
            return "";
        }
        return AiPlanDslWorkflowService.toDslJson(toServiceGraphPlan(plan));
    }

    public static AiGraphPlan deserializePendingPlanFromDsl(String pendingPlanDslJson) {
        AiGraphDslSupport.ParseValidationResult parsed =
                AiGraphDslSupport.parseAndValidate(pendingPlanDslJson, NodeRegistry.getInstance());
        if (!parsed.isSuccess() || parsed.graph() == null) {
            return null;
        }
        return fromServiceGraphPlan(AiPlanDslWorkflowService.fromDsl(parsed.graph()));
    }

    private static GraphPlan toServiceGraphPlan(AiGraphPlan plan) {
        if (plan == null) {
            return new GraphPlan("", List.of(), List.of(), List.of());
        }

        List<AiGraphPlanDslAdapterService.PlanNode> nodes = new ArrayList<>(plan.nodes().size());
        for (AiAssistantComponent.AiPlanNode node : plan.nodes()) {
            nodes.add(new AiGraphPlanDslAdapterService.PlanNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    node.nodeState()
            ));
        }

        List<AiGraphPlanDslAdapterService.PlanConnection> connections = new ArrayList<>(plan.connections().size());
        for (AiAssistantComponent.AiPlanConnection connection : plan.connections()) {
            connections.add(new AiGraphPlanDslAdapterService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        return new GraphPlan(
                plan.summary(),
                nodes,
                connections,
                plan.validationErrors() == null ? List.of() : plan.validationErrors()
        );
    }

    private static AiGraphPlan fromServiceGraphPlan(GraphPlan plan) {
        List<AiAssistantComponent.AiPlanNode> nodes = new ArrayList<>();
        if (plan != null) {
            for (AiGraphPlanDslAdapterService.PlanNode node : plan.nodes()) {
                nodes.add(new AiAssistantComponent.AiPlanNode(node.ref(), node.typeId(), node.offsetX(), node.offsetY(), node.nodeState()));
            }
        }

        List<AiAssistantComponent.AiPlanConnection> connections = new ArrayList<>();
        if (plan != null) {
            for (AiGraphPlanDslAdapterService.PlanConnection connection : plan.connections()) {
                connections.add(new AiAssistantComponent.AiPlanConnection(
                        connection.sourceRef(),
                        connection.sourcePortId(),
                        connection.targetRef(),
                        connection.targetPortId()
                ));
            }
        }

        List<String> errors = plan == null || plan.validationErrors() == null ? List.of() : plan.validationErrors();
        String summary = plan == null ? "" : plan.summary();
        return new AiGraphPlan(summary, nodes, connections, errors);
    }
}

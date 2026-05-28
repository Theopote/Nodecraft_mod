package com.nodecraft.gui.ai;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class AiGraphApplyAdapterService {

    private AiGraphApplyAdapterService() {
    }

    public record PlanNode(String ref, String typeId, float offsetX, float offsetY, Object nodeState) {
    }

    public record PlanConnection(String sourceRef, String sourcePortId, String targetRef, String targetPortId) {
    }

    public record PatchPayload(
            List<AiGraphApplyService.ApplyNode> nodes,
            List<AiGraphApplyService.ApplyConnection> connections
    ) {
    }

    public static PatchPayload toPatchPayload(List<PlanNode> nodes, List<PlanConnection> connections) {
        List<AiGraphApplyService.ApplyNode> applyNodes = getApplyNodes(nodes);

        List<AiGraphApplyService.ApplyConnection> applyConnections = new ArrayList<>();
        if (connections != null) {
            applyConnections = new ArrayList<>(connections.size());
            for (PlanConnection connection : connections) {
                applyConnections.add(new AiGraphApplyService.ApplyConnection(
                        connection.sourceRef(),
                        connection.sourcePortId(),
                        connection.targetRef(),
                        connection.targetPortId()
                ));
            }
        }

        return new PatchPayload(applyNodes, applyConnections);
    }

    private static @NonNull List<AiGraphApplyService.ApplyNode> getApplyNodes(List<PlanNode> nodes) {
        List<AiGraphApplyService.ApplyNode> applyNodes = new ArrayList<>();
        if (nodes != null) {
            applyNodes = new ArrayList<>(nodes.size());
            for (PlanNode node : nodes) {
                applyNodes.add(new AiGraphApplyService.ApplyNode(
                        node.ref(),
                        node.typeId(),
                        node.offsetX(),
                        node.offsetY(),
                        node.nodeState()
                ));
            }
        }
        return applyNodes;
    }
}

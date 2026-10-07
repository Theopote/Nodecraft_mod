package com.nodecraft.gui.recommendation;

import com.nodecraft.gui.ai.AiNodeSchemaCatalog.NodeSchema;
import com.nodecraft.gui.ai.AiNodeSchemaCatalog.PortSchema;
import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.graph.NodeGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class NodeRecommendationConnector {

    private final NodePortIndex portIndex;
    private final NodeRecommendationRules rules;

    NodeRecommendationConnector(NodePortIndex portIndex, NodeRecommendationRules rules) {
        this.portIndex = portIndex;
        this.rules = rules;
    }

    NodePortIndex.CandidatePort pickBestCandidatePort(
            RecommendationDirection direction,
            NodeDataType sourceDataType,
            String nodeId,
            String preferredPortId) {
        List<NodePortIndex.CandidatePort> candidates = direction == RecommendationDirection.DOWNSTREAM
                ? portIndex.findDownstreamCandidates(sourceDataType)
                : portIndex.findUpstreamCandidates(sourceDataType);

        return candidates.stream()
                .filter(candidate -> candidate.nodeId().equalsIgnoreCase(nodeId))
                .filter(candidate -> preferredPortId == null || candidate.portId().equals(preferredPortId))
                .max(Comparator
                        .comparingInt((NodePortIndex.CandidatePort port) -> portScore(sourceDataType, port, preferredPortId))
                        .thenComparing(NodePortIndex.CandidatePort::portId))
                .orElseGet(() -> candidates.stream()
                        .filter(candidate -> candidate.nodeId().equalsIgnoreCase(nodeId))
                        .max(Comparator.comparingInt(port -> portScore(sourceDataType, port, null)))
                        .orElse(null));
    }

    private int portScore(NodeDataType sourceType, NodePortIndex.CandidatePort port, String preferredPortId) {
        int score = 0;
        if (preferredPortId != null && preferredPortId.equals(port.portId())) {
            score += 200;
        }
        if (port.required()) {
            score += 50;
        }
        if (sourceType != null && sourceType == port.dataType()) {
            score += 80;
        } else if (sourceType != null && NodeDataType.isConnectableTo(sourceType, port.dataType())) {
            score += 40;
        } else if (sourceType != null
                && TypeConversionRegistry.requiresExplicitConversion(sourceType, port.dataType())) {
            score += 20;
        }
        if (port.dataType() != NodeDataType.ANY) {
            score += 10;
        }
        return score;
    }

    NodeRecommendationApplyResult apply(
            ICanvasEditor editor,
            NodeGraph graph,
            NodeRecommendationContext context,
            NodeRecommendation recommendation) {
        if (editor == null || graph == null || context == null || recommendation == null) {
            return NodeRecommendationApplyResult.failure("Invalid recommendation apply context");
        }

        INode sourceNode = graph.getNode(context.sourceNodeId());
        if (sourceNode == null) {
            return NodeRecommendationApplyResult.failure("Source node no longer exists");
        }

        NodePosition sourcePos = editor.getNodePosition(context.sourceNodeId());
        float baseX = sourcePos != null ? (float) sourcePos.x : context.placementX();
        float baseY = sourcePos != null ? (float) sourcePos.y : context.placementY();

        NodeRecommendationRules.Offset offset = context.trigger() == RecommendationTrigger.PORT_DRAG
                ? rules.placement.portDragOffset
                : rules.placement.defaultOffset;
        float targetX = baseX + offset.dx();
        float targetY = baseY + offset.dy();

        if (context.trigger() == RecommendationTrigger.PORT_DRAG) {
            targetX = context.placementX();
            targetY = context.placementY();
        }

        return switch (recommendation.connectionPlan()) {
            case DIRECT -> applyDirect(editor, context, recommendation, targetX, targetY);
            case VIA_CONVERSION -> applyViaConversion(editor, context, recommendation, sourceNode, targetX, targetY);
            case MANUAL -> applyManual(editor, recommendation, targetX, targetY);
        };
    }

    private NodeRecommendationApplyResult applyDirect(
            ICanvasEditor editor,
            NodeRecommendationContext context,
            NodeRecommendation recommendation,
            float x,
            float y) {
        NodeGraph graph = editor.getCurrentGraph();
        float[] clear = findClearPlacement(editor, graph, x, y, List.of());
        INode created = editor.addNode(recommendation.nodeId(), clear[0], clear[1]);
        if (created == null) {
            return NodeRecommendationApplyResult.failure("Failed to create node: " + recommendation.displayName());
        }

        boolean connected = connectRecommendation(editor, context, recommendation, created.getId());
        if (!connected) {
            editor.setSelectedNodeId(created.getId());
            return NodeRecommendationApplyResult.success(
                    created.getId(),
                    List.of(created.getId()),
                    "Created " + recommendation.displayName() + " (manual wiring required)");
        }

        editor.setSelectedNodeId(created.getId());
        return NodeRecommendationApplyResult.success(
                created.getId(),
                List.of(created.getId()),
                "Created and connected " + recommendation.displayName());
    }

    private NodeRecommendationApplyResult applyManual(
            ICanvasEditor editor,
            NodeRecommendation recommendation,
            float x,
            float y) {
        NodeGraph graph = editor.getCurrentGraph();
        float[] clear = findClearPlacement(editor, graph, x, y, List.of());
        INode created = editor.addNode(recommendation.nodeId(), clear[0], clear[1]);
        if (created == null) {
            return NodeRecommendationApplyResult.failure("Failed to create node: " + recommendation.displayName());
        }
        editor.setSelectedNodeId(created.getId());
        return NodeRecommendationApplyResult.success(
                created.getId(),
                List.of(created.getId()),
                "Created " + recommendation.displayName());
    }

    private NodeRecommendationApplyResult applyViaConversion(
            ICanvasEditor editor,
            NodeRecommendationContext context,
            NodeRecommendation recommendation,
            INode sourceNode,
            float x,
            float y) {
        TypeConversionRegistry.ConversionSuggestion conversion = TypeConversionRegistry.getSuggestedConversion(
                recommendation.sourcePortType(),
                recommendation.connectPortType());
        if (conversion == null) {
            return applyManual(editor, recommendation, x, y);
        }

        // Horizontal chain: source → conversion → target.
        // PORT_DRAG: target at drop (x,y); conversion to the left by portDragOffset.
        // Selection: conversion at source+defaultOffset (x,y); target further right.
        float chainDx = rules.placement.portDragOffset != null ? rules.placement.portDragOffset.dx() : 220f;
        float chainDy = rules.placement.portDragOffset != null ? rules.placement.portDragOffset.dy() : 0f;
        float conversionX;
        float conversionY;
        float targetX;
        float targetY;
        if (context.trigger() == RecommendationTrigger.PORT_DRAG) {
            targetX = x;
            targetY = y;
            conversionX = targetX - chainDx;
            conversionY = targetY - chainDy;
        } else {
            conversionX = x;
            conversionY = y;
            targetX = conversionX + chainDx;
            targetY = conversionY + chainDy;
        }

        NodeGraph graph = editor.getCurrentGraph();
        // Prefer keeping the target near its preferred point; then clear conversion around that.
        float[] clearTarget = findClearPlacement(editor, graph, targetX, targetY, List.of());
        float[] clearConversion = findClearPlacement(
                editor, graph, conversionX, conversionY, List.of(clearTarget));

        INode conversionNode = editor.addNode(conversion.nodeId(), clearConversion[0], clearConversion[1]);
        if (conversionNode == null) {
            return applyManual(editor, recommendation, clearTarget[0], clearTarget[1]);
        }

        INode targetNode = editor.addNode(recommendation.nodeId(), clearTarget[0], clearTarget[1]);
        if (targetNode == null) {
            return NodeRecommendationApplyResult.success(
                    conversionNode.getId(),
                    List.of(conversionNode.getId()),
                    "Created conversion node only");
        }

        List<UUID> createdIds = new ArrayList<>();
        createdIds.add(conversionNode.getId());
        createdIds.add(targetNode.getId());

        connectSourceToNode(editor, context, sourceNode, conversionNode);
        connectNodeToTarget(editor, conversionNode, targetNode, recommendation.connectPortId());

        editor.setSelectedNodeId(targetNode.getId());
        return NodeRecommendationApplyResult.success(
                targetNode.getId(),
                createdIds,
                "Created conversion chain for " + recommendation.displayName());
    }

    private boolean connectRecommendation(
            ICanvasEditor editor,
            NodeRecommendationContext context,
            NodeRecommendation recommendation,
            UUID createdNodeId) {
        if (context.direction() == RecommendationDirection.DOWNSTREAM) {
            String sourcePortId = resolveSourcePortId(context, recommendation);
            String targetPortId = resolveTargetInputPort(recommendation.nodeId(), recommendation.connectPortId());
            if (sourcePortId == null || targetPortId == null) {
                return false;
            }
            return editor.connectPorts(
                    context.sourceNodeId(),
                    sourcePortId,
                    createdNodeId,
                    targetPortId);
        }

        String sourcePortId = resolveTargetOutputPort(recommendation.nodeId(), recommendation.connectPortId());
        String targetPortId = resolveSourcePortId(context, recommendation);
        if (sourcePortId == null || targetPortId == null) {
            return false;
        }
        return editor.connectPorts(
                createdNodeId,
                sourcePortId,
                context.sourceNodeId(),
                targetPortId);
    }

    private void connectSourceToNode(
            ICanvasEditor editor,
            NodeRecommendationContext context,
            INode sourceNode,
            INode targetNode) {
        String sourcePortId = context.sourcePortId();
        if (sourcePortId == null) {
            sourcePortId = NodeRecommendationPorts.resolveOutputPortId(sourceNode, null);
        }
        NodeDataType sourceType = NodeRecommendationPorts.resolvePortDataType(sourceNode, sourcePortId, true);
        String targetInputId = findCompatibleInputPort(targetNode, sourceType);
        if (sourcePortId != null && targetInputId != null) {
            editor.connectPorts(sourceNode.getId(), sourcePortId, targetNode.getId(), targetInputId);
        }
    }

    private void connectNodeToTarget(
            ICanvasEditor editor,
            INode sourceNode,
            INode targetNode,
            String preferredInputPortId) {
        String outputPortId = NodeRecommendationPorts.resolveOutputPortId(sourceNode, null);
        NodeDataType outputType = NodeRecommendationPorts.resolvePortDataType(sourceNode, outputPortId, true);
        String inputPortId = preferredInputPortId != null
                ? preferredInputPortId
                : findCompatibleInputPort(targetNode, outputType);
        if (outputPortId != null && inputPortId != null) {
            editor.connectPorts(sourceNode.getId(), outputPortId, targetNode.getId(), inputPortId);
        }
    }

    private String findCompatibleInputPort(INode node, NodeDataType outputType) {
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (IPort port : node.getInputPorts()) {
            if (outputType == null) {
                return port.getId();
            }
            if (!NodeDataType.isConnectableTo(outputType, port.getDataType())) {
                continue;
            }
            int score = port.getDataType() == outputType ? 100 : 50;
            if (port.isRequired()) {
                score += 20;
            }
            if (score > bestScore) {
                bestScore = score;
                best = port.getId();
            }
        }
        return best;
    }

    private String resolveSourcePortId(NodeRecommendationContext context, NodeRecommendation recommendation) {
        if (context.sourcePortId() != null && !context.sourcePortId().isBlank()) {
            return context.sourcePortId();
        }
        return recommendation.sourcePortId();
    }

    private String resolveTargetInputPort(String nodeId, String preferredPortId) {
        if (preferredPortId != null && !preferredPortId.isBlank()) {
            return preferredPortId;
        }
        return findFirstInputPort(nodeId);
    }

    private String resolveTargetOutputPort(String nodeId, String preferredPortId) {
        if (preferredPortId != null && !preferredPortId.isBlank()) {
            return preferredPortId;
        }
        return findFirstOutputPort(nodeId);
    }

    private String findFirstInputPort(String nodeTypeId) {
        for (NodeSchema schema : portIndex.schemas()) {
            if (!schema.typeId().equalsIgnoreCase(nodeTypeId)) {
                continue;
            }
            PortSchema best = null;
            for (PortSchema input : schema.inputs()) {
                if (best == null || (input.required() && !best.required())) {
                    best = input;
                }
            }
            return best != null ? best.id() : null;
        }
        return null;
    }

    private String findFirstOutputPort(String nodeTypeId) {
        for (NodeSchema schema : portIndex.schemas()) {
            if (!schema.typeId().equalsIgnoreCase(nodeTypeId)) {
                continue;
            }
            if (!schema.outputs().isEmpty()) {
                return schema.outputs().getFirst().id();
            }
        }
        return null;
    }

    /** Half-extents of the occupancy box used for simple placement collision (~120×80). */
    private static final float OCCUPANCY_HALF_W = 60f;
    private static final float OCCUPANCY_HALF_H = 40f;
    private static final float[] Y_LADDER = {0f, 80f, -80f, 160f, -160f};

    /**
     * Try preferred (x,y) then Y offsets to find a spot not overlapping existing nodes
     * or already-reserved placements in this apply call.
     */
    private static float[] findClearPlacement(
            ICanvasEditor editor,
            NodeGraph graph,
            float preferredX,
            float preferredY,
            List<float[]> reserved) {
        for (float dy : Y_LADDER) {
            float x = preferredX;
            float y = preferredY + dy;
            if (!isOccupied(editor, graph, x, y, reserved)) {
                return new float[] {x, y};
            }
        }
        return new float[] {preferredX, preferredY};
    }

    private static boolean isOccupied(
            ICanvasEditor editor,
            NodeGraph graph,
            float x,
            float y,
            List<float[]> reserved) {
        if (graph != null) {
            for (INode node : graph.getNodes()) {
                if (node == null) {
                    continue;
                }
                float nx;
                float ny;
                NodePosition pos = editor != null ? editor.getNodePosition(node.getId()) : null;
                if (pos != null) {
                    nx = pos.x;
                    ny = pos.y;
                } else {
                    nx = (float) node.getPositionX();
                    ny = (float) node.getPositionY();
                }
                if (Math.abs(nx - x) < OCCUPANCY_HALF_W && Math.abs(ny - y) < OCCUPANCY_HALF_H) {
                    return true;
                }
            }
        }
        if (reserved != null) {
            for (float[] point : reserved) {
                if (point == null || point.length < 2) {
                    continue;
                }
                if (Math.abs(point[0] - x) < OCCUPANCY_HALF_W
                        && Math.abs(point[1] - y) < OCCUPANCY_HALF_H) {
                    return true;
                }
            }
        }
        return false;
    }
}

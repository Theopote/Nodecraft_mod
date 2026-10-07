package com.nodecraft.gui.recommendation;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared label/tooltip formatting for Overlay and Port Drag popup.
 */
final class RecommendationUiPresentation {

    private RecommendationUiPresentation() {
    }

    static String formatLabel(NodeRecommendation recommendation) {
        if (recommendation == null) {
            return "";
        }
        String badges = formatBadges(recommendation);
        if (badges.isEmpty()) {
            return recommendation.displayName();
        }
        return recommendation.displayName() + " " + badges;
    }

    static String formatBadges(NodeRecommendation recommendation) {
        List<String> parts = new ArrayList<>(3);
        if (recommendation.connectionPlan() == NodeRecommendation.ConnectionPlan.VIA_CONVERSION) {
            parts.add("[Convert]");
        } else if (recommendation.connectionPlan() == NodeRecommendation.ConnectionPlan.MANUAL) {
            parts.add("[Manual]");
        }
        NodeEffect effect = resolveCandidateEffect(recommendation.nodeId());
        if (effect == NodeEffect.WORLD_WRITE) {
            parts.add("[World]");
        } else if (effect == NodeEffect.PREVIEW_WRITE) {
            parts.add("[Preview]");
        } else if (effect == NodeEffect.FILE_IO) {
            parts.add("[File]");
        }
        return String.join(" ", parts);
    }

    static String formatTooltip(NodeRecommendation recommendation, INode sourceNode) {
        if (recommendation == null) {
            return "";
        }
        StringBuilder tip = new StringBuilder();
        if (recommendation.reason() != null && !recommendation.reason().isBlank()) {
            tip.append(recommendation.reason());
        }
        String from = resolveSourcePortDisplay(recommendation, sourceNode);
        if (from != null && !from.isBlank()) {
            appendLine(tip, "From: " + from);
        }
        if (recommendation.connectPortId() != null && !recommendation.connectPortId().isBlank()) {
            appendLine(tip, "To: " + recommendation.connectPortId());
        }
        appendLine(tip, "Plan: " + planLabel(recommendation.connectionPlan()));
        if (recommendation.connectionPlan() == NodeRecommendation.ConnectionPlan.VIA_CONVERSION) {
            TypeConversionRegistry.ConversionSuggestion conversion =
                    TypeConversionRegistry.getSuggestedConversion(
                            recommendation.sourcePortType(),
                            recommendation.connectPortType());
            if (conversion != null) {
                appendLine(tip, "Will add: " + conversion.displayName());
            }
        }
        return tip.toString();
    }

    static String humanizePortId(String portId) {
        if (portId == null || portId.isBlank()) {
            return "";
        }
        String trimmed = portId;
        if (trimmed.startsWith("output_")) {
            trimmed = trimmed.substring("output_".length());
        } else if (trimmed.startsWith("input_")) {
            trimmed = trimmed.substring("input_".length());
        }
        String[] parts = trimmed.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    private static String resolveSourcePortDisplay(NodeRecommendation recommendation, INode sourceNode) {
        String portId = recommendation.sourcePortId();
        if (portId == null || portId.isBlank()) {
            return "";
        }
        if (sourceNode != null) {
            List<IPort> outputs = sourceNode.getOutputPorts();
            if (outputs != null) {
                for (IPort port : outputs) {
                    if (port != null && portId.equals(port.getId())
                            && port.getDisplayName() != null && !port.getDisplayName().isBlank()) {
                        return port.getDisplayName();
                    }
                }
            }
        }
        return humanizePortId(portId);
    }

    private static String planLabel(NodeRecommendation.ConnectionPlan plan) {
        if (plan == null) {
            return "Unknown";
        }
        return switch (plan) {
            case DIRECT -> "Direct connection";
            case VIA_CONVERSION -> "Adds conversion node";
            case MANUAL -> "Creates node without auto-wire";
        };
    }

    private static NodeEffect resolveCandidateEffect(String nodeId) {
        if (nodeId == null) {
            return NodeEffect.PURE;
        }
        NodeInfo info = NodeRegistry.getInstance().getNodeInfo(nodeId);
        Class<? extends INode> nodeClass = info != null ? info.getNodeClass() : null;
        return NodeEffectResolver.resolve(nodeClass, nodeId);
    }

    private static void appendLine(StringBuilder tip, String line) {
        if (!tip.isEmpty()) {
            tip.append('\n');
        }
        tip.append(line);
    }
}

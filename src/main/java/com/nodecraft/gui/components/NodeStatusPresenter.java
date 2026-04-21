package com.nodecraft.gui.components;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import imgui.ImVec4;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

final class NodeStatusPresenter {

    private NodeStatusPresenter() {
    }

    static String getCategoryNameForNode(String typeId) {
        if (typeId == null || typeId.isEmpty()) {
            return "Unknown";
        }

        String categoryId = "";
        int firstDotIndex = typeId.indexOf('.');
        if (firstDotIndex != -1) {
            categoryId = typeId.substring(0, firstDotIndex);
        }

        try {
            NodeRegistry registry = NodeRegistry.getInstance();
            if (registry != null) {
                NodeRegistry.NodeCategory category = registry.getCategory(categoryId);
                if (category != null) {
                    return category.getDisplayName();
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to resolve node category: {}", e.getMessage());
        }

        if (!categoryId.isEmpty()) {
            return formatSingleWord(categoryId);
        }

        String[] parts = typeId.split("\\.");
        return parts.length > 0 ? formatSingleWord(parts[0]) : "Uncategorized";
    }

    static String getNodeStatus(INode node) {
        if (node == null) {
            return "Unselected";
        }

        try {
            Method getErrorMethod = node.getClass().getMethod("getErrorState");
            Object errorState = getErrorMethod.invoke(node);
            if (errorState instanceof String error && !error.isEmpty()) {
                return "Error";
            }
            if (errorState instanceof Boolean error && error) {
                return "Error";
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }

        boolean hasInputPorts = !node.getInputPorts().isEmpty();
        if (hasInputPorts) {
            for (IPort port : node.getInputPorts()) {
                if (isRequiredInputPort(port) && !port.isConnected() && port.getValue() == null) {
                    return "Warning";
                }
            }
        }

        if (node instanceof BaseNode) {
            Object nodeState = node.getNodeState();
            if (nodeState instanceof Map<?, ?> stateMap && stateMap.containsKey("status")) {
                Object status = stateMap.get("status");
                if (status instanceof String statusString) {
                    if (statusString.equals("calculating")) {
                        return "Calculating";
                    }
                    if (statusString.equals("disabled")) {
                        return "Disabled";
                    }
                }
            }
        }

        return "Ready";
    }

    static String getNodeStatusMessage(String status) {
        return switch (status) {
            case "Error" -> "The node failed to evaluate and could not produce a valid result.";
            case "Warning" -> "The node has missing required inputs.";
            case "Calculating" -> "The node is currently evaluating.";
            case "Disabled" -> "The node is disabled and will not participate in execution.";
            case "Ready" -> "The node is ready.";
            case "Unselected" -> "No node is currently selected.";
            default -> "Unknown node status.";
        };
    }

    static ImVec4 getStatusColor(String status) {
        return switch (status) {
            case "Error" -> new ImVec4(1.0f, 0.3f, 0.3f, 1.0f);
            case "Warning" -> new ImVec4(1.0f, 0.9f, 0.3f, 1.0f);
            case "Calculating" -> new ImVec4(0.3f, 0.7f, 1.0f, 1.0f);
            case "Disabled" -> new ImVec4(0.5f, 0.5f, 0.5f, 1.0f);
            case "Ready" -> new ImVec4(0.3f, 0.9f, 0.3f, 1.0f);
            default -> new ImVec4(1.0f, 1.0f, 1.0f, 1.0f);
        };
    }

    private static String formatSingleWord(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }

        String[] parts = word.split("_");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                formatted.append(Character.toUpperCase(part.charAt(0)));
                formatted.append(part.substring(1).toLowerCase());
                formatted.append(" ");
            }
        }
        return formatted.toString().trim();
    }

    private static boolean isRequiredInputPort(IPort port) {
        if (port == null) {
            return false;
        }
        if (!port.isRequired()) {
            return false;
        }
        String displayName = port.getDisplayName();
        String description = port.getDescription();
        return !containsOptionalMarker(displayName) && !containsOptionalMarker(description);
    }

    private static boolean containsOptionalMarker(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("optional") || text.contains("可选");
    }
}

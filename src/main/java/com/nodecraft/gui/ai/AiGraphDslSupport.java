package com.nodecraft.gui.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Utility for parsing and validating AI-generated node graph JSON DSL.
 */
public final class AiGraphDslSupport {

    private static final Gson GSON = new GsonBuilder().create();

    private AiGraphDslSupport() {
    }

    public record DslPosition(float x, float y) {
    }

    public record DslNode(String id, String type, Map<String, Object> params, DslPosition position) {
    }

    public record DslEndpoint(String nodeId, String port) {
    }

    public record DslConnection(DslEndpoint from, DslEndpoint to) {
    }

    public record DslGraph(List<DslNode> nodes, List<DslConnection> connections, String description) {
    }

    public record ParseValidationResult(DslGraph graph, List<String> errors, List<String> warnings, String normalizedJson) {
        public boolean isSuccess() {
            return errors == null || errors.isEmpty();
        }
    }

    public static ParseValidationResult parseAndValidate(String modelResponse, NodeRegistry registry) {
        String jsonPayload = extractJsonPayload(modelResponse);
        if (jsonPayload.isBlank()) {
            List<String> errors = new ArrayList<>();
            errors.add("No JSON found in model response.");
            return new ParseValidationResult(null, errors, new ArrayList<>(), "");
        }
        return parseJsonPayload(jsonPayload, registry);
    }

    public static ParseValidationResult parseStructured(String jsonPayload, NodeRegistry registry) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            List<String> errors = new ArrayList<>();
            errors.add("Structured JSON payload is empty.");
            return new ParseValidationResult(null, errors, new ArrayList<>(), "");
        }
        return parseJsonPayload(jsonPayload, registry);
    }

    private static ParseValidationResult parseJsonPayload(String jsonPayload, NodeRegistry registry) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (registry == null) {
            errors.add("Node registry is unavailable.");
            return new ParseValidationResult(null, errors, warnings, "");
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(jsonPayload).getAsJsonObject();
        } catch (Exception e) {
            errors.add("Invalid JSON: " + e.getMessage());
            return new ParseValidationResult(null, errors, warnings, jsonPayload);
        }

        if (root.has("error")) {
            errors.add(root.get("error").getAsString());
            return new ParseValidationResult(null, errors, warnings, jsonPayload);
        }

        List<DslNode> nodes = parseNodes(root, errors);
        List<DslConnection> connections = parseConnections(root, errors);
        String description = root.has("description") ? root.get("description").getAsString() : "";

        DslGraph graph = new DslGraph(nodes, connections, description);
        validateGraph(graph, registry, errors, warnings);
        return new ParseValidationResult(graph, errors, warnings, GSON.toJson(root));
    }

    public static String extractJsonPayload(String response) {
        if (response == null || response.isBlank()) {
            return "";
        }

        String trimmed = response.trim();
        int fenceStart = trimmed.indexOf("```");
        if (fenceStart >= 0) {
            int jsonStart = trimmed.indexOf('{', fenceStart);
            int fenceEnd = trimmed.indexOf("```", fenceStart + 3);
            if (jsonStart >= 0 && fenceEnd > jsonStart) {
                String candidate = trimmed.substring(jsonStart, fenceEnd).trim();
                int objStart = candidate.indexOf('{');
                int objEnd = candidate.lastIndexOf('}');
                if (objStart >= 0 && objEnd > objStart) {
                    return candidate.substring(objStart, objEnd + 1);
                }
            }
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return trimmed.substring(start, end + 1);
    }

    private static List<DslNode> parseNodes(JsonObject root, List<String> errors) {
        List<DslNode> nodes = new ArrayList<>();
        if (!root.has("nodes") || !root.get("nodes").isJsonArray()) {
            errors.add("Missing or invalid 'nodes' array.");
            return nodes;
        }

        JsonArray nodeArray = root.getAsJsonArray("nodes");
        for (int i = 0; i < nodeArray.size(); i++) {
            JsonElement nodeElement = nodeArray.get(i);
            if (!nodeElement.isJsonObject()) {
                errors.add("nodes[" + i + "] must be an object.");
                continue;
            }
            JsonObject nodeObj = nodeElement.getAsJsonObject();

            String id = getString(nodeObj, "id");
            String type = getString(nodeObj, "type");
            if (id == null || id.isBlank()) {
                errors.add("nodes[" + i + "] is missing a valid 'id'.");
                continue;
            }
            if (type == null || type.isBlank()) {
                errors.add("nodes[" + i + "] is missing a valid 'type'.");
                continue;
            }

            Map<String, Object> params = new HashMap<>();
            if (nodeObj.has("params") && nodeObj.get("params").isJsonObject()) {
                params = GSON.fromJson(nodeObj.getAsJsonObject("params"), Map.class);
            }

            DslPosition position = new DslPosition(i * 220.0f, 0.0f);
            if (nodeObj.has("position") && nodeObj.get("position").isJsonObject()) {
                JsonObject posObj = nodeObj.getAsJsonObject("position");
                float x = getFloat(posObj, "x", position.x());
                float y = getFloat(posObj, "y", position.y());
                position = new DslPosition(x, y);
            }

            nodes.add(new DslNode(id, type.toLowerCase(Locale.ROOT), params, position));
        }

        return nodes;
    }

    private static List<DslConnection> parseConnections(JsonObject root, List<String> errors) {
        List<DslConnection> connections = new ArrayList<>();
        if (!root.has("connections")) {
            return connections;
        }
        if (!root.get("connections").isJsonArray()) {
            errors.add("'connections' must be an array when provided.");
            return connections;
        }

        JsonArray connArray = root.getAsJsonArray("connections");
        for (int i = 0; i < connArray.size(); i++) {
            JsonElement connElement = connArray.get(i);
            if (!connElement.isJsonObject()) {
                errors.add("connections[" + i + "] must be an object.");
                continue;
            }
            JsonObject connObj = connElement.getAsJsonObject();
            JsonObject fromObj = connObj.has("from") && connObj.get("from").isJsonObject()
                    ? connObj.getAsJsonObject("from")
                    : null;
            JsonObject toObj = connObj.has("to") && connObj.get("to").isJsonObject()
                    ? connObj.getAsJsonObject("to")
                    : null;

            if (fromObj == null || toObj == null) {
                errors.add("connections[" + i + "] must contain 'from' and 'to' objects.");
                continue;
            }

            DslEndpoint from = new DslEndpoint(getString(fromObj, "nodeId"), getString(fromObj, "port"));
            DslEndpoint to = new DslEndpoint(getString(toObj, "nodeId"), getString(toObj, "port"));

            if (isBlank(from.nodeId()) || isBlank(from.port()) || isBlank(to.nodeId()) || isBlank(to.port())) {
                errors.add("connections[" + i + "] has empty endpoint fields.");
                continue;
            }
            connections.add(new DslConnection(from, to));
        }

        return connections;
    }

    private static void validateGraph(DslGraph graph, NodeRegistry registry, List<String> errors, List<String> warnings) {
        if (graph.nodes().isEmpty()) {
            errors.add("Graph must contain at least one node.");
            return;
        }

        Set<String> nodeIds = new HashSet<>();
        Map<String, INode> instances = new HashMap<>();

        for (DslNode node : graph.nodes()) {
            if (!nodeIds.add(node.id())) {
                errors.add("Duplicate node id: " + node.id());
                continue;
            }

            if (registry.getNodeInfo(node.type()) == null) {
                errors.add("Unknown node type: " + node.type());
                continue;
            }

            try {
                instances.put(node.id(), registry.createNodeInstance(node.type()));
            } catch (Exception e) {
                errors.add("Failed to instantiate node type '" + node.type() + "': " + e.getMessage());
            }
        }

        boolean hasOutputNode = false;
        for (DslNode node : graph.nodes()) {
            if (registry.getNodeInfo(node.type()) != null
                    && registry.getNodeInfo(node.type()).getCategoryId().startsWith("output.")) {
                hasOutputNode = true;
                break;
            }
        }
        if (!hasOutputNode) {
            warnings.add("Graph has no output.* category node. This is allowed for partial sub-graphs, but it may not produce visible output until connected.");
        }

        for (DslConnection connection : graph.connections()) {
            INode fromNode = instances.get(connection.from().nodeId());
            INode toNode = instances.get(connection.to().nodeId());
            if (fromNode == null || toNode == null) {
                errors.add("Connection references unknown node: "
                        + connection.from().nodeId() + " -> " + connection.to().nodeId());
                continue;
            }

            IPort outPort = findPort(fromNode.getOutputPorts(), connection.from().port());
            IPort inPort = findPort(toNode.getInputPorts(), connection.to().port());
            if (outPort == null) {
                errors.add("Output port not found: " + connection.from().nodeId() + "." + connection.from().port());
                continue;
            }
            if (inPort == null) {
                errors.add("Input port not found: " + connection.to().nodeId() + "." + connection.to().port());
                continue;
            }

            NodeDataType outType = outPort.getDataType();
            NodeDataType inType = inPort.getDataType();
            if (!NodeDataType.isConnectableTo(outType, inType)) {
                String reason = NodeDataType.getConnectabilityRejectionReason(outType, inType);
                errors.add("Type mismatch on connection " + connection.from().nodeId() + "." + outPort.getId()
                        + " -> " + connection.to().nodeId() + "." + inPort.getId()
                        + " : " + reason);
            }
        }
    }

    private static IPort findPort(List<IPort> ports, String portId) {
        if (ports == null || portId == null) {
            return null;
        }
        for (IPort port : ports) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static float getFloat(JsonObject obj, String key, float defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsFloat();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

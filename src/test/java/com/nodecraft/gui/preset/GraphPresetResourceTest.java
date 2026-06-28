package com.nodecraft.gui.preset;

import com.google.gson.Gson;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphPresetResourceTest {

    private static final Gson GSON = new Gson();
    private static final List<String> RESOURCE_PATHS = List.of(
            "/nodecraft/graph_presets.json",
            "/nodecraft/graph_presets_updated.json");

    @BeforeAll
    static void initializeRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void builtinGraphPresetsReferenceCurrentNodesAndPorts() {
        for (String resourcePath : RESOURCE_PATHS) {
            validateResource(resourcePath);
        }
    }

    private static void validateResource(String resourcePath) {
        GraphPresetRules rules = loadRules(resourcePath);
        NodeRegistry registry = NodeRegistry.getInstance();
        Map<String, INode> nodeCache = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (GraphPresetRules.PresetCategory category : rules.categories) {
            if (category == null || category.presets == null) {
                continue;
            }
            for (GraphPresetRules.GraphPresetDefinition preset : category.presets) {
                if (preset == null || !"composite".equalsIgnoreCase(preset.kind)) {
                    continue;
                }
                Map<String, String> typeByRef = new LinkedHashMap<>();
                if (preset.nodes != null) {
                    for (GraphPresetRules.PresetNode node : preset.nodes) {
                        if (node == null || node.ref == null || node.typeId == null) {
                            errors.add(preset.id + " contains an incomplete node entry");
                            continue;
                        }
                        if (registry.getNodeInfo(node.typeId) == null) {
                            errors.add(preset.id + " references missing node type " + node.typeId);
                            continue;
                        }
                        typeByRef.put(node.ref, node.typeId);
                        nodeCache.computeIfAbsent(node.typeId, registry::createNodeInstance);
                    }
                }
                if (preset.connections != null) {
                    for (GraphPresetRules.PresetConnection connection : preset.connections) {
                        validateConnection(preset, connection, typeByRef, nodeCache, errors);
                    }
                }
            }
        }

        assertFalse(rules.categories.isEmpty(), "expected at least one built-in preset category in " + resourcePath);
        assertTrue(errors.isEmpty(), resourcePath + System.lineSeparator() + String.join(System.lineSeparator(), errors));
    }

    private static GraphPresetRules loadRules(String resourcePath) {
        try (InputStream stream = GraphPresetResourceTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "Missing " + resourcePath);
            GraphPresetRules rules = GSON.fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8),
                    GraphPresetRules.class);
            assertNotNull(rules, "Failed to parse " + resourcePath);
            return rules;
        } catch (Exception e) {
            throw new AssertionError("Failed to load " + resourcePath, e);
        }
    }

    private static void validateConnection(
            GraphPresetRules.GraphPresetDefinition preset,
            GraphPresetRules.PresetConnection connection,
            Map<String, String> typeByRef,
            Map<String, INode> nodeCache,
            List<String> errors) {
        if (connection == null) {
            return;
        }

        String sourceTypeId = typeByRef.get(connection.fromRef);
        String targetTypeId = typeByRef.get(connection.toRef);
        if (sourceTypeId == null || targetTypeId == null) {
            errors.add(preset.id + " references unknown connection nodes "
                    + connection.fromRef + " -> " + connection.toRef);
            return;
        }

        INode sourceNode = nodeCache.get(sourceTypeId);
        INode targetNode = nodeCache.get(targetTypeId);
        IPort sourcePort = findPort(sourceNode.getOutputPorts(), connection.fromPort);
        IPort targetPort = findPort(targetNode.getInputPorts(), connection.toPort);

        if (sourcePort == null) {
            errors.add(preset.id + " references missing output port "
                    + sourceTypeId + "." + connection.fromPort);
            return;
        }
        if (targetPort == null) {
            errors.add(preset.id + " references missing input port "
                    + targetTypeId + "." + connection.toPort);
            return;
        }
        if (!NodeDataType.isConnectableTo(sourcePort.getDataType(), targetPort.getDataType())) {
            errors.add(preset.id + " connects incompatible ports "
                    + sourceTypeId + "." + connection.fromPort + " (" + sourcePort.getDataType() + ") -> "
                    + targetTypeId + "." + connection.toPort + " (" + targetPort.getDataType() + ")");
        }
    }

    private static IPort findPort(List<IPort> ports, String portId) {
        if (portId == null) {
            return null;
        }
        for (IPort port : ports) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }
}

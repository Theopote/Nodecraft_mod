package com.nodecraft.gui.preset;

import com.google.gson.GsonBuilder;
import com.nodecraft.gui.ai.AiNodeSchemaCatalog;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dumps the live node schema (ports + default state) to build/node-schema-dump.json.
 * Not an assertion test — a development aid for authoring presets.
 */
class NodeSchemaDumpTest {

    @Test
    void dumpSchema() throws IOException {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String id : registry.getAllNodeIds()) {
            INode node = registry.createNodeInstance(id);
            Map<String, Object> entry = new LinkedHashMap<>();
            List<String> inputs = new ArrayList<>();
            for (IPort port : node.getInputPorts()) {
                inputs.add(port.getId() + ":" + port.getDataType().getId() + (port.isRequired() ? "*" : ""));
            }
            List<String> outputs = new ArrayList<>();
            for (IPort port : node.getOutputPorts()) {
                outputs.add(port.getId() + ":" + port.getDataType().getId());
            }
            entry.put("inputs", inputs);
            entry.put("outputs", outputs);
            Object state = node.getNodeState();
            entry.put("state", state instanceof Map<?, ?> m ? m : null);
            out.put(id, entry);
        }
        Path target = Path.of("build", "node-schema-dump.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(out), StandardCharsets.UTF_8);
        System.out.println("Wrote " + target.toAbsolutePath() + " entries=" + out.size());
    }

    @Test
    void dumpSemanticHints() throws IOException {
        List<AiNodeSchemaCatalog.NodeSchema> schemas = AiNodeSchemaCatalog.collectAll(NodeRegistry.getInstance());
        Path target = Path.of("build", "node-schema-hints.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(schemas), StandardCharsets.UTF_8);
        System.out.println("Wrote " + target.toAbsolutePath() + " entries=" + schemas.size());
    }
}

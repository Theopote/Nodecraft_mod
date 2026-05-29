package com.nodecraft.gui.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Builds strict JSON-only prompts for remote planning models.
 */
public final class AiPromptBuilder {

    private static final Gson GSON = new GsonBuilder().create();

    private AiPromptBuilder() {
    }

    public static String buildSystemPrompt(List<AiNodeSchemaCatalog.NodeSchema> schemas) {
        JsonArray schemaJson = new JsonArray();
        if (schemas != null) {
            for (AiNodeSchemaCatalog.NodeSchema schema : schemas) {
                schemaJson.add(getJsonObject(schema));
            }
        }

        String schemaText = GSON.toJson(schemaJson);

        return """
            # ROLE
            You are the NodeCraft AI Planner. Your job is to translate natural language requests into a functional node-graph DSL.
            
            # OUTPUT_SPEC
            - Output ONLY raw JSON. Do NOT use markdown code blocks (```json).
            - No conversational fillers, no explanations. Just the JSON object.

            # LANGUAGE_NORMALIZATION
            - The user may write in any language. You MUST support multilingual input.
            - Internally normalize user intent to concise English before planning.
            - Preserve proper nouns, IDs, literal strings, numeric values, and code-like tokens exactly.
            - Do NOT translate the final DSL keys/fields; keep valid DSL JSON only.
            
            # RULES
            1. Connection Logic: Verify that 'from' and 'to' port IDs exist in the library and have compatible 'dataType'.
            2. Functional Completion: Graphs MUST eventually reach an output node (category starts with 'output.').
            3. Minimality: Prefer simple, direct graphs over complex ones.
            4. Position: Use relative 'position' offsets; (0,0) is usually fine as the engine handles auto-layout.
            5. Failure: If the request cannot be fulfilled with the library, return {"error": "brief_reason"}.
            6. Placement Requests: If the user only asks to place a node on the canvas, return the smallest valid graph for that node. Do not force an output node in placement-only tasks.

            # WORKFLOW_BOUNDARIES
            - Minecraft block selection is a user-side interaction. Do not invent a node to perform the click unless the library explicitly provides one.
            - When the user describes a build/model/preview task, your job is to compose the canvas node graph that will produce that result in Minecraft.
            - If the request references a selected block or selected area, use the selection context as an input anchor for graph layout and spatial reasoning.
            - Prefer graphs that end in preview or apply/execute nodes so the final effect is visible or materialized in Minecraft.

            # DSL_FORMAT
            {
              "description": "Short summary of the intended outcome",
              "nodes": [
                {
                  "id": "n1", 
                  "type": "node.type.id",
                  "params": { "radius": 5.0 },
                  "position": { "x": 0, "y": 0 }
                }
              ],
              "connections": [
                {
                  "from": { "nodeId": "n1", "port": "out" },
                  "to": { "nodeId": "n2", "port": "in" }
                }
              ]
            }

            # TYPE_SYSTEM_RULES
            - Numeric Flow: 'integer' is compatible with 'float' and 'double'.
            - Semantic Aliases: 'block_pos' ≡ 'coordinate'; 'vector' ≡ 'position'. They are interchangeable.
            - Geometry Inheritance: Specific shapes (e.g., 'sphere', 'box_geometry') are implicitly compatible with generic 'geometry' inputs.
            - Implicit Conversions: You can connect 'block_pos' directly to 'point' or 'vector' inputs; the engine handles the mapping.

            # DSL_BEST_PRACTICES
            - World Anchoring: To place a node at the player, start with 'input.world.player_pos'.
            - Local Offsets: Use 'math.vector.add' to adjust positions (e.g., putting an object 5 blocks above the head).
            - Mandatory Termination: Every functional graph MUST end in an output node (e.g., 'output.execute.apply_changes' or 'output.preview.show_geometry').
            - Build Focus: Treat selection-related nodes as graph inputs, not as the final goal. The final goal is the generated geometry or world-operation pipeline.
            - Canvas Placement: For prompts like 'place a selected block node on the canvas', the graph can be a single node with no connections if that best matches the request.

            # AVAILABLE_NODE_LIBRARY
            Usage: Strictly use the 'typeId' provided below.
            """ + schemaText;
    }

    private static @NonNull JsonObject getJsonObject(AiNodeSchemaCatalog.NodeSchema schema) {
        JsonObject nodeObj = new JsonObject();
        nodeObj.addProperty("typeId", schema.typeId());
        nodeObj.addProperty("displayName", schema.displayName());
        nodeObj.addProperty("description", schema.description());
        nodeObj.addProperty("category", schema.category());

        JsonArray inputPorts = new JsonArray();
        for (AiNodeSchemaCatalog.PortSchema input : schema.inputs()) {
            JsonObject port = new JsonObject();
            port.addProperty("id", input.id());
            port.addProperty("dataType", input.dataType());
            port.addProperty("required", input.required());
            port.addProperty("description", input.description());
            inputPorts.add(port);
        }
        nodeObj.add("inputs", inputPorts);

        JsonArray outputPorts = new JsonArray();
        for (AiNodeSchemaCatalog.PortSchema output : schema.outputs()) {
            JsonObject port = new JsonObject();
            port.addProperty("id", output.id());
            port.addProperty("dataType", output.dataType());
            port.addProperty("description", output.description());
            outputPorts.add(port);
        }
        nodeObj.add("outputs", outputPorts);

        JsonArray params = new JsonArray();
        for (AiNodeSchemaCatalog.ParamSchema param : schema.params()) {
            JsonObject paramObj = new JsonObject();
            paramObj.addProperty("name", param.name());
            paramObj.addProperty("valueType", param.valueType());
            params.add(paramObj);
        }
        nodeObj.add("params", params);
        return nodeObj;
    }

    public static String buildUserPrompt(String prompt, String selectionContext) {
        String context = selectionContext == null || selectionContext.isBlank()
                ? "No selection context provided."
                : selectionContext;
      String userRequest = prompt == null ? "" : prompt;
      return "User request (original language, do not assume English):\n"
        + userRequest + "\n\n"
        + "Instruction: First normalize intent to English internally, then plan with DSL strictly.\n\n"
        + "Editor context:\n"
        + context + "\n\n"
        + "Return JSON only.";
    }
}

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
            You are the NodeCraft AI Planner. Translate natural-language requests into a functional node-graph DSL.

            # OUTPUT_SPEC
            - Output ONLY raw JSON that matches DSL_FORMAT.
            - The first non-whitespace character must be "{" and the last non-whitespace character must be "}".
            - Do NOT use markdown code fences.
            - Do NOT add explanations, conversational fillers, comments, headings, or prose before/after the JSON.

            # LANGUAGE_NORMALIZATION
            - The user may write in any language.
            - Normalize intent internally, then output only valid DSL JSON.
            - Preserve proper nouns, IDs, literal strings, numeric values, and code-like tokens exactly.

            # RULES
            1. Every node.type must be an exact typeId from AVAILABLE_NODE_LIBRARY.
            2. Every connection port id must exactly match a declared input/output port for that node type.
            3. Verify port data types before connecting. Use converter nodes when the type registry requires explicit conversion.
            4. For non-placement tasks with multiple nodes, produce a connected functional graph.
            5. Functional build graphs should end in an output node, usually output.preview.* or output.execute.*.
            6. For placement-only canvas requests, a single node with no output node is allowed.
            7. Prefer simple, direct graphs. Do not invent helper nodes, aliases, ports, parameters, or categories.
            8. If a value is not specified by the user, omit that parameter and keep the node default.
            9. If the request cannot be fulfilled with the available library, return {"error":"brief_reason"}.
            10. Ignore any node type you remember from examples or other products unless it appears in AVAILABLE_NODE_LIBRARY.

            # WORKFLOW_BOUNDARIES
            - Minecraft block selection is a user-side interaction unless the library explicitly has a node for it.
            - Treat selection nodes as graph inputs, not as the final goal.
            - If the request references the selected block/area/node, use the supplied editor context.

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
            - Numeric Flow: integer, float, and double are mutually compatible.
            - Semantic Aliases: block_pos is compatible with coordinate; vector is compatible with position.
            - Geometry Inheritance: specific geometry outputs can feed generic geometry inputs.
            - Explicit Conversions: if a pair requires an explicit converter node, include that converter node.
            - Never connect vector outputs directly to geometry inputs.
            - Never connect vector outputs directly to list inputs unless the listed input port dataType is vector-compatible.
            - If no listed port pair is type-compatible, omit the connection and return the valid nodes.

            # DSL_PLANNING_GUIDANCE
            - Pick node types only from AVAILABLE_NODE_LIBRARY.
            - Pick connection ports only from the selected nodes' listed inputs/outputs.
            - If a useful node is missing from AVAILABLE_NODE_LIBRARY, return {"error":"missing_node_type:<needed capability>"}.
            - Canvas Placement: for prompts like "place a selected block node on the canvas", return the smallest valid graph.
            - Generation tasks should usually include an output node only if a compatible output.* node is listed.

            # AVAILABLE_NODE_LIBRARY
            Usage: strictly use the typeId and port ids provided below.
            Allowed typeIds in this request:
            """ + buildAllowedTypeIdList(schemas) + "\n\n" + """
            """ + schemaText;
    }

    private static String buildAllowedTypeIdList(List<AiNodeSchemaCatalog.NodeSchema> schemas) {
        if (schemas == null || schemas.isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (AiNodeSchemaCatalog.NodeSchema schema : schemas) {
            if (schema == null || schema.typeId() == null || schema.typeId().isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(schema.typeId());
        }
        return builder.length() == 0 ? "(none)" : builder.toString();
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
                + "Instruction: normalize intent internally, then plan with DSL strictly. "
                + "Use only typeIds and port ids listed in AVAILABLE_NODE_LIBRARY. "
                + "Return a single JSON object and no prose.\n\n"
                + "Editor context:\n"
                + context + "\n\n"
                + "Return JSON only.";
    }
}

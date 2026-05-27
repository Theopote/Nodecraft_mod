package com.nodecraft.gui.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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

                schemaJson.add(nodeObj);
            }
        }

        String schemaText = GSON.toJson(schemaJson);

        return "You are NodeCraft AI planner. Convert user request into a strict node-graph JSON DSL.\\n"
                + "Rules:\\n"
                + "1) Output JSON only. No markdown, no explanation.\\n"
                + "2) Use only nodes and ports listed in AVAILABLE_NODES.\\n"
                + "3) Connections must use compatible port data types.\\n"
                + "4) Graph must include at least one output.* category node.\\n"
                + "5) If impossible, return {\\\"error\\\":\\\"reason\\\"}.\\n"
                + "6) Prefer minimal graph that satisfies user intent.\\n"
                + "JSON format:\\n"
                + "{\\n"
                + "  \\\"description\\\": \\\"...\\\",\\n"
                + "  \\\"nodes\\\": [{\\\"id\\\":\\\"n1\\\",\\\"type\\\":\\\"...\\\",\\\"params\\\":{},\\\"position\\\":{\\\"x\\\":0,\\\"y\\\":0}}],\\n"
                + "  \\\"connections\\\": [{\\\"from\\\":{\\\"nodeId\\\":\\\"n1\\\",\\\"port\\\":\\\"...\\\"},\\\"to\\\":{\\\"nodeId\\\":\\\"n2\\\",\\\"port\\\":\\\"...\\\"}}]\\n"
                + "}\\n"
                + "AVAILABLE_NODES:\\n"
                + schemaText;
    }

    public static String buildUserPrompt(String prompt, String selectionContext) {
        String context = selectionContext == null || selectionContext.isBlank()
                ? "No selection context provided."
                : selectionContext;
        return "User request:\\n"
                + prompt + "\\n\\n"
                + "Editor context:\\n"
                + context + "\\n\\n"
                + "Return JSON only.";
    }
}

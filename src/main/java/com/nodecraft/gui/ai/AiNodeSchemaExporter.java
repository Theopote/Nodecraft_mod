package com.nodecraft.gui.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Exports the runtime node registry schema used by the AI planner.
 */
public final class AiNodeSchemaExporter {

    public static final int SCHEMA_EXPORT_VERSION = 1;
    public static final String JSON_FILE_NAME = "ai-node-schema.json";
    public static final String MARKDOWN_FILE_NAME = "ai-node-schema.md";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new GsonBuilder().create();

    private AiNodeSchemaExporter() {
    }

    public static ExportSnapshot snapshot(NodeRegistry registry) {
        List<AiNodeSchemaCatalog.NodeSchema> schemas = AiNodeSchemaCatalog.collectAll(registry);
        return snapshot(schemas);
    }

    public static ExportSnapshot snapshot(List<AiNodeSchemaCatalog.NodeSchema> schemas) {
        List<AiNodeSchemaCatalog.NodeSchema> safeSchemas = schemas == null ? List.of() : List.copyOf(schemas);
        String revision = computeRevision(safeSchemas);
        return new ExportSnapshot(
            SCHEMA_EXPORT_VERSION,
            revision,
            safeSchemas.size(),
            safeSchemas,
            toJson(safeSchemas, revision),
            toMarkdown(safeSchemas, revision)
        );
    }

    public static void writeLatestExports(NodeRegistry registry, Path outputDirectory) {
        if (registry == null || outputDirectory == null) {
            return;
        }
        try {
            ExportSnapshot snapshot = snapshot(registry);
            Files.createDirectories(outputDirectory);
            Path jsonPath = outputDirectory.resolve(JSON_FILE_NAME);
            Path markdownPath = outputDirectory.resolve(MARKDOWN_FILE_NAME);
            Files.writeString(jsonPath, snapshot.json(), StandardCharsets.UTF_8);
            Files.writeString(markdownPath, snapshot.markdown(), StandardCharsets.UTF_8);
            NodeCraft.LOGGER.info(
                "AI node schema exported: nodes={}, revision={}, json={}, markdown={}",
                snapshot.nodeCount(),
                snapshot.revision(),
                jsonPath.toAbsolutePath(),
                markdownPath.toAbsolutePath()
            );
        } catch (IOException e) {
            NodeCraft.LOGGER.warn("Failed to export AI node schema to {}", outputDirectory.toAbsolutePath(), e);
        } catch (Exception e) {
            NodeCraft.LOGGER.warn("Unexpected failure while exporting AI node schema", e);
        }
    }

    public static String computeRevision(List<AiNodeSchemaCatalog.NodeSchema> schemas) {
        JsonObject canonical = buildCanonicalJson(schemas == null ? List.of() : schemas);
        String compact = COMPACT_GSON.toJson(canonical);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(compact.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(compact.hashCode());
        }
    }

    public static String toJson(List<AiNodeSchemaCatalog.NodeSchema> schemas, String revision) {
        JsonObject root = buildCanonicalJson(schemas == null ? List.of() : schemas);
        root.addProperty("revision", revision == null || revision.isBlank() ? computeRevision(schemas) : revision);
        return GSON.toJson(root);
    }

    public static String toMarkdown(List<AiNodeSchemaCatalog.NodeSchema> schemas, String revision) {
        List<AiNodeSchemaCatalog.NodeSchema> safeSchemas = schemas == null ? List.of() : schemas;
        String resolvedRevision = revision == null || revision.isBlank() ? computeRevision(safeSchemas) : revision;
        StringBuilder builder = new StringBuilder();
        builder.append("# NodeCraft AI Node Schema\n\n");
        builder.append("- Schema export version: ").append(SCHEMA_EXPORT_VERSION).append('\n');
        builder.append("- Revision: ").append(resolvedRevision).append('\n');
        builder.append("- Node count: ").append(safeSchemas.size()).append("\n\n");
        builder.append("| Type ID | Category | Inputs | Outputs | Params |\n");
        builder.append("|---|---|---|---|---|\n");
        for (AiNodeSchemaCatalog.NodeSchema schema : safeSchemas) {
            builder.append("| `").append(escapeMarkdown(schema.typeId())).append("` | `")
                .append(escapeMarkdown(schema.category())).append("` | ")
                .append(formatPorts(schema.inputs())).append(" | ")
                .append(formatPorts(schema.outputs())).append(" | ")
                .append(formatParams(schema.params())).append(" |\n");
        }
        return builder.toString();
    }

    private static JsonObject buildCanonicalJson(List<AiNodeSchemaCatalog.NodeSchema> schemas) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaExportVersion", SCHEMA_EXPORT_VERSION);
        root.addProperty("nodeCount", schemas == null ? 0 : schemas.size());
        JsonArray nodes = new JsonArray();
        if (schemas != null) {
            for (AiNodeSchemaCatalog.NodeSchema schema : schemas) {
                nodes.add(toJsonObject(schema));
            }
        }
        root.add("nodes", nodes);
        return root;
    }

    private static JsonObject toJsonObject(AiNodeSchemaCatalog.NodeSchema schema) {
        JsonObject node = new JsonObject();
        node.addProperty("typeId", nullToEmpty(schema.typeId()));
        node.addProperty("displayName", nullToEmpty(schema.displayName()));
        node.addProperty("description", nullToEmpty(schema.description()));
        node.addProperty("category", nullToEmpty(schema.category()));
        node.add("inputs", portsToJson(schema.inputs()));
        node.add("outputs", portsToJson(schema.outputs()));
        node.add("params", paramsToJson(schema.params()));
        return node;
    }

    private static JsonArray portsToJson(List<AiNodeSchemaCatalog.PortSchema> ports) {
        JsonArray array = new JsonArray();
        if (ports == null) {
            return array;
        }
        for (AiNodeSchemaCatalog.PortSchema port : ports) {
            JsonObject item = new JsonObject();
            item.addProperty("id", nullToEmpty(port.id()));
            item.addProperty("displayName", nullToEmpty(port.displayName()));
            item.addProperty("dataType", nullToEmpty(port.dataType()));
            item.addProperty("required", port.required());
            item.addProperty("description", nullToEmpty(port.description()));
            array.add(item);
        }
        return array;
    }

    private static JsonArray paramsToJson(List<AiNodeSchemaCatalog.ParamSchema> params) {
        JsonArray array = new JsonArray();
        if (params == null) {
            return array;
        }
        for (AiNodeSchemaCatalog.ParamSchema param : params) {
            JsonObject item = new JsonObject();
            item.addProperty("name", nullToEmpty(param.name()));
            item.addProperty("valueType", nullToEmpty(param.valueType()));
            array.add(item);
        }
        return array;
    }

    private static String formatPorts(List<AiNodeSchemaCatalog.PortSchema> ports) {
        if (ports == null || ports.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (AiNodeSchemaCatalog.PortSchema port : ports) {
            if (builder.length() > 0) {
                builder.append("<br>");
            }
            builder.append('`').append(escapeMarkdown(port.id())).append(':')
                .append(escapeMarkdown(port.dataType())).append('`');
        }
        return builder.toString();
    }

    private static String formatParams(List<AiNodeSchemaCatalog.ParamSchema> params) {
        if (params == null || params.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (AiNodeSchemaCatalog.ParamSchema param : params) {
            if (builder.length() > 0) {
                builder.append("<br>");
            }
            builder.append('`').append(escapeMarkdown(param.name())).append(':')
                .append(escapeMarkdown(param.valueType())).append('`');
        }
        return builder.toString();
    }

    private static String escapeMarkdown(String value) {
        return nullToEmpty(value).replace("|", "\\|").replace("\n", " ");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record ExportSnapshot(
        int schemaExportVersion,
        String revision,
        int nodeCount,
        List<AiNodeSchemaCatalog.NodeSchema> schemas,
        String json,
        String markdown
    ) {
    }
}

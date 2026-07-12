package com.nodecraft.nodesystem.nodes.output.export;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.ExportPathUtil;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "output.export.export_data",
    displayName = "Export CSV / JSON",
    description = "Exports list/coordinates data to CSV or JSON file.",
    category = "output.export",
    order = 3
)
public class ExportDataNode extends BaseNode {

    public enum ExportFormat {
        CSV,
        JSON
    }

    @NodeProperty(displayName = "Format", category = "Export", order = 1)
    private ExportFormat format = ExportFormat.CSV;

    @NodeProperty(displayName = "Pretty JSON", category = "Export", order = 2)
    private boolean prettyJson = true;

    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String INPUT_DATA_ID = "input_data";
    private static final String INPUT_BLOCKS_ID = "input_blocks";
    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_FORMAT_ID = "input_format";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_FORMAT_ID = "output_format";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ExportDataNode() {
        super(UUID.randomUUID(), "output.export.export_data");
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Export trigger", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_DATA_ID, "Data", "List data to export", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks", "Optional block list to export", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Output file path", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_FORMAT_ID, "Format", "Optional format override (csv/json)", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "True when export succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Resolved output path", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Exported row/item count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FORMAT_ID, "Format", "Resolved format", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when export fails", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (inputValues.get(INPUT_TRIGGER_ID) == null) {
            publish(false, "", 0, resolvedFormat().name().toLowerCase(), "trigger not connected");
            return;
        }

        List<?> data = resolveData();
        if (data.isEmpty()) {
            publish(false, "", 0, resolvedFormat().name().toLowerCase(), "empty data");
            return;
        }

        ExportFormat fmt = resolvedFormat();
        String rawPath = inputValues.get(INPUT_PATH_ID) instanceof String text && !text.isBlank()
            ? text.trim()
            : "nodecraft_export." + fmt.name().toLowerCase();
        Path outputPath = null;
        try {
            String extension = fmt == ExportFormat.JSON ? ".json" : ".csv";
            String defaultFileName = "nodecraft_export" + extension;
            outputPath = ExportPathUtil.resolve(rawPath, defaultFileName, extension);
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            String content = fmt == ExportFormat.CSV ? toCsv(data) : toJson(data, prettyJson, 0);
            Files.writeString(outputPath, content, StandardCharsets.UTF_8);
            publish(true, outputPath.toString(), data.size(), fmt.name().toLowerCase(), "");
        } catch (Exception e) {
            String resolvedPath = outputPath != null ? outputPath.toString() : rawPath;
            publish(false, resolvedPath, 0, fmt.name().toLowerCase(), e.getMessage() != null ? e.getMessage() : "export failed");
        }
    }

    private List<?> resolveData() {
        Object blocksObj = inputValues.get(INPUT_BLOCKS_ID);
        if (blocksObj instanceof BlockPosList blocks && !blocks.isEmpty()) {
            List<Map<String, Object>> rows = new ArrayList<>(blocks.size());
            for (BlockPos b : blocks) {
                rows.add(Map.of("x", b.getX(), "y", b.getY(), "z", b.getZ()));
            }
            return rows;
        }

        Object dataObj = inputValues.get(INPUT_DATA_ID);
        if (dataObj instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private ExportFormat resolvedFormat() {
        Object value = inputValues.get(INPUT_FORMAT_ID);
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase();
            if ("json".equals(normalized)) return ExportFormat.JSON;
            if ("csv".equals(normalized)) return ExportFormat.CSV;
        }
        return format;
    }

    private String toCsv(List<?> data) {
        StringBuilder sb = new StringBuilder();
        boolean mapRows = !data.isEmpty() && data.get(0) instanceof Map<?, ?>;
        if (mapRows) {
            List<String> headers = collectHeaders(data);
            sb.append(String.join(",", headers)).append('\n');
            for (Object item : data) {
                Map<?, ?> map = item instanceof Map<?, ?> m ? m : Map.of("value", item);
                for (int i = 0; i < headers.size(); i++) {
                    if (i > 0) sb.append(',');
                    Object value = map.get(headers.get(i));
                    sb.append(csvEscape(stringify(value)));
                }
                sb.append('\n');
            }
            return sb.toString();
        }

        sb.append("index,value\n");
        for (int i = 0; i < data.size(); i++) {
            sb.append(i).append(',').append(csvEscape(stringify(data.get(i)))).append('\n');
        }
        return sb.toString();
    }

    private List<String> collectHeaders(List<?> data) {
        List<String> headers = new ArrayList<>();
        for (Object item : data) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            for (Object key : map.keySet()) {
                String text = String.valueOf(key);
                if (!headers.contains(text)) {
                    headers.add(text);
                }
            }
        }
        if (headers.isEmpty()) {
            headers.add("value");
        }
        return headers;
    }

    private String csvEscape(String text) {
        if (text == null) return "";
        boolean quote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (!quote) return text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String toJson(Object value, boolean pretty, int depth) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return String.valueOf(value);
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isFinite(d)) return String.valueOf(d);
            return "null";
        }
        if (value instanceof String text) {
            return "\"" + escapeJson(text) + "\"";
        }
        if (value instanceof BlockPos b) {
            return "{\"x\":" + b.getX() + ",\"y\":" + b.getY() + ",\"z\":" + b.getZ() + "}";
        }
        if (value instanceof Vector3d v) {
            return "{\"x\":" + v.x + ",\"y\":" + v.y + ",\"z\":" + v.z + "}";
        }
        if (value instanceof PointData p) {
            return toJson(p.getPosition(), pretty, depth);
        }
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = "\"" + escapeJson(String.valueOf(e.getKey())) + "\"";
                parts.add(key + ":" + (pretty ? " " : "") + toJson(e.getValue(), pretty, depth + 1));
            }
            return wrapObject(parts, pretty, depth);
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> parts = new ArrayList<>();
            for (Object item : iterable) {
                parts.add(toJson(item, pretty, depth + 1));
            }
            return wrapArray(parts, pretty, depth);
        }
        return "\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    private String wrapObject(List<String> parts, boolean pretty, int depth) {
        if (!pretty) {
            return "{" + String.join(",", parts) + "}";
        }
        if (parts.isEmpty()) return "{}";
        String indent = "  ".repeat(depth + 1);
        String closeIndent = "  ".repeat(depth);
        return "{\n" + indent + String.join(",\n" + indent, parts) + "\n" + closeIndent + "}";
    }

    private String wrapArray(List<String> parts, boolean pretty, int depth) {
        if (!pretty) {
            return "[" + String.join(",", parts) + "]";
        }
        if (parts.isEmpty()) return "[]";
        String indent = "  ".repeat(depth + 1);
        String closeIndent = "  ".repeat(depth);
        return "[\n" + indent + String.join(",\n" + indent, parts) + "\n" + closeIndent + "]";
    }

    private String escapeJson(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\f", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private String stringify(Object value) {
        if (value == null) return "";
        if (value instanceof BlockPos b) return b.getX() + "," + b.getY() + "," + b.getZ();
        if (value instanceof Vector3d v) return v.x + "," + v.y + "," + v.z;
        if (value instanceof PointData p) return stringify(p.getPosition());
        return String.valueOf(value);
    }

    private void publish(boolean success, String path, int count, String formatText, String error) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_FORMAT_ID, formatText);
        outputValues.put(OUTPUT_ERROR_ID, error != null ? error : "");
    }
}


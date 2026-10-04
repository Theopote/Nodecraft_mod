package com.nodecraft.nodesystem.nodes.output.export;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.ExportDataEncoder;
import com.nodecraft.nodesystem.util.ExportPathUtil;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.FILE_IO,
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
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "EXEC pulse to export", NodeDataType.EXEC, this, false, false));
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
        ExportFormat fmt = resolvedFormat();
        if (!Boolean.TRUE.equals(inputValues.get(INPUT_TRIGGER_ID))) {
            publish(false, "", 0, fmt.name().toLowerCase(), "");
            return;
        }

        List<?> data = resolveData();
        if (data.isEmpty()) {
            publish(false, "", 0, fmt.name().toLowerCase(), "empty data");
            return;
        }
        if (data.size() > GenerationLimits.MAX_EXPORT_ROWS) {
            publish(false, "", 0, fmt.name().toLowerCase(), "rows exceed MAX_EXPORT_ROWS");
            return;
        }

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

            ExportDataEncoder.Result encoded = fmt == ExportFormat.CSV
                ? ExportDataEncoder.encodeCsv(data)
                : ExportDataEncoder.encodeJson(data, prettyJson);
            if (!encoded.valid()) {
                publish(false, outputPath.toString(), 0, fmt.name().toLowerCase(), encoded.error());
                return;
            }

            Files.writeString(outputPath, encoded.text(), StandardCharsets.UTF_8);
            publish(true, outputPath.toString(), encoded.count(), fmt.name().toLowerCase(), "");
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
            if ("json".equals(normalized)) {
                return ExportFormat.JSON;
            }
            if ("csv".equals(normalized)) {
                return ExportFormat.CSV;
            }
        }
        return format;
    }

    private void publish(boolean success, String path, int count, String formatText, String error) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_FORMAT_ID, formatText);
        outputValues.put(OUTPUT_ERROR_ID, error != null ? error : "");
    }
}

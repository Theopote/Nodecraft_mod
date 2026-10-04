package com.nodecraft.nodesystem.nodes.output.export;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.ExportBounds;
import com.nodecraft.nodesystem.util.ExportPathUtil;
import com.nodecraft.nodesystem.util.StructureExportPreflight;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtString;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Exports placements into a stable NodeCraft NBT structure file with palette metadata.
 */
@NodeInfo(
    effect = NodeEffect.FILE_IO,
    id = "output.export.export_schematic",
    displayName = "Export Schematic",
    description = "Exports placements to a NodeCraft NBT structure file",
    category = "output.export",
    order = 0
)
public class ExportSchematicNode extends BaseCustomUINode {

    private static final int FORMAT_VERSION = 2;

    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String INPUT_BLOCKS_ID = "input_blocks";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";
    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_NAME_ID = "input_name";
    private static final String INPUT_AUTHOR_ID = "input_author";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_FORMAT_ID = "output_format";
    private static final String OUTPUT_VERSION_ID = "output_version";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ExportSchematicNode() {
        super(UUID.randomUUID(), "output.export.export_schematic");

        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "EXEC pulse to export", NodeDataType.EXEC, this, false, false));
        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Uniform block type when exporting plain block coordinates", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Per-position block assignments", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Output path such as schematics/out.nbt", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_NAME_ID, "Name", "Optional schematic name stored in metadata", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_AUTHOR_ID, "Author", "Optional author stored in metadata", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether export succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Resolved output path", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Block Count", "Number of exported blocks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FORMAT_ID, "Format", "Export format id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VERSION_ID, "Format Version", "Export format version", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when export fails", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!Boolean.TRUE.equals(inputValues.get(INPUT_TRIGGER_ID))) {
            publishOutputs(false, "", 0, "");
            return;
        }

        String rawPath = getInputString(INPUT_PATH_ID, "nodecraft_export.nbt");
        String defaultBlock = getInputString(INPUT_BLOCK_TYPE_ID, "minecraft:stone");
        String name = getInputString(INPUT_NAME_ID, StructureExportPreflight.deriveNameFromPath(rawPath));
        String author = getInputString(INPUT_AUTHOR_ID, "nodecraft");

        StructureExportPreflight.Result prepared = StructureExportPreflight.prepare(
            inputValues.get(INPUT_PLACEMENTS_ID),
            inputValues.get(INPUT_BLOCKS_ID),
            defaultBlock,
            StructureExportPreflight.DenseMode.NONE,
            name,
            author,
            ""
        );
        if (!prepared.valid() || prepared.prepared() == null) {
            publishOutputs(false, "", 0, prepared.error());
            return;
        }

        Path outputPath = null;
        try {
            outputPath = ExportPathUtil.resolve(rawPath, "nodecraft_export.nbt", ".nbt");
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            NbtCompound root = buildExportNbt(prepared.prepared());
            NbtIo.write(root, outputPath);
            publishOutputs(true, outputPath.toString(), prepared.prepared().placements().size(), "");
        } catch (Exception e) {
            String resolvedPath = outputPath != null ? outputPath.toString() : rawPath;
            publishOutputs(false, resolvedPath, 0, e.getMessage() != null ? e.getMessage() : "export failed");
        }
    }

    private NbtCompound buildExportNbt(StructureExportPreflight.PreparedStructureExport prepared) {
        ExportBounds bounds = prepared.bounds();
        StructureExportPreflight.StructurePalette palette = prepared.palette();

        NbtCompound root = new NbtCompound();
        root.put("format", NbtString.of("nodecraft:structure"));
        root.put("format_version", NbtInt.of(FORMAT_VERSION));
        root.put("name", NbtString.of(prepared.metadata().name()));
        root.put("author", NbtString.of(prepared.metadata().author()));
        root.put("created_at_epoch_ms", NbtLong.of(Instant.now().toEpochMilli()));
        root.put("block_count", NbtInt.of(prepared.placements().size()));
        root.put("size", createIntList(bounds.sizeXInt(), bounds.sizeYInt(), bounds.sizeZInt()));
        root.put("origin", createIntList(bounds.minX(), bounds.minY(), bounds.minZ()));
        root.put("palette", createPaletteList(palette));
        root.put("blocks", createBlocksList(prepared, bounds, palette));
        return root;
    }

    private NbtList createPaletteList(StructureExportPreflight.StructurePalette palette) {
        NbtList entries = new NbtList();
        for (StructureExportPreflight.PaletteEntry entry : palette.entries()) {
            NbtCompound paletteEntry = new NbtCompound();
            paletteEntry.put("block", NbtString.of(entry.blockId()));
            BlockStateData stateData = entry.stateData();
            if (stateData != null && !stateData.isEmpty()) {
                NbtCompound state = new NbtCompound();
                stateData.forEach((property, value) -> state.put(property, NbtString.of(value)));
                paletteEntry.put("state", state);
            }
            entries.add(paletteEntry);
        }
        return entries;
    }

    private NbtList createBlocksList(
        StructureExportPreflight.PreparedStructureExport prepared,
        ExportBounds bounds,
        StructureExportPreflight.StructurePalette palette
    ) {
        NbtList blocks = new NbtList();
        for (BlockPlacementData placement : prepared.placements()) {
            var pos = placement.pos();
            NbtCompound entry = new NbtCompound();
            entry.put("pos", createIntList(
                pos.getX() - bounds.minX(),
                pos.getY() - bounds.minY(),
                pos.getZ() - bounds.minZ()
            ));
            entry.put("palette_index", NbtInt.of(palette.indexFor(placement)));
            entry.put("block", NbtString.of(placement.blockId()));

            BlockStateData stateData = placement.stateData();
            if (stateData != null && !stateData.isEmpty()) {
                NbtCompound state = new NbtCompound();
                stateData.forEach((key, value) -> state.put(key, NbtString.of(value)));
                entry.put("state", state);
            }
            blocks.add(entry);
        }
        return blocks;
    }

    private NbtList createIntList(int... values) {
        NbtList list = new NbtList();
        for (int value : values) {
            list.add(NbtInt.of(value));
        }
        return list;
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }

    private void publishOutputs(boolean success, String path, int count, String error) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_FORMAT_ID, "nodecraft:structure");
        outputValues.put(OUTPUT_VERSION_ID, FORMAT_VERSION);
        outputValues.put(OUTPUT_ERROR_ID, error != null ? error : "");
    }

    @Override
    protected float calculateUIHeight() {
        return 0f;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return false;
    }
}

package com.nodecraft.nodesystem.nodes.output.export;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.ExportBounds;
import com.nodecraft.nodesystem.util.ExportPathUtil;
import com.nodecraft.nodesystem.util.MinecraftFormatVersion;
import com.nodecraft.nodesystem.util.StructureExportPreflight;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Exports placements to a Sponge schematic file that WorldEdit can import.
 */
@NodeInfo(
    effect = NodeEffect.FILE_IO,
    id = "output.export.export_worldedit",
    displayName = "Export WorldEdit",
    description = "Exports placements to a Sponge schematic file for WorldEdit",
    category = "output.export",
    order = 2
)
public class ExportWorldEditNode extends BaseCustomUINode {

    private static final int SPONGE_SCHEM_VERSION = 2;

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

    public ExportWorldEditNode() {
        super(UUID.randomUUID(), "output.export.export_worldedit");

        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "EXEC pulse to export", NodeDataType.EXEC, this, false, false));
        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Uniform block type when exporting plain block coordinates", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Per-position block assignments", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Output path such as schematics/out.schem", NodeDataType.STRING, this));
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

        String rawPath = getInputString(INPUT_PATH_ID, "nodecraft_export.schem");
        String defaultBlock = getInputString(INPUT_BLOCK_TYPE_ID, "minecraft:stone");
        String name = getInputString(INPUT_NAME_ID, StructureExportPreflight.deriveNameFromPath(rawPath));
        String author = getInputString(INPUT_AUTHOR_ID, "nodecraft");

        StructureExportPreflight.Result prepared = StructureExportPreflight.prepare(
            inputValues.get(INPUT_PLACEMENTS_ID),
            inputValues.get(INPUT_BLOCKS_ID),
            defaultBlock,
            StructureExportPreflight.DenseMode.WORLD_EDIT,
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
            outputPath = ExportPathUtil.resolve(rawPath, "nodecraft_export.schem", ".schem");
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            NbtCompound root = buildSpongeSchematicNbt(prepared.prepared());
            NbtIo.write(root, outputPath);
            publishOutputs(true, outputPath.toString(), prepared.prepared().placements().size(), "");
        } catch (Exception e) {
            String resolvedPath = outputPath != null ? outputPath.toString() : rawPath;
            publishOutputs(false, resolvedPath, 0, e.getMessage() != null ? e.getMessage() : "export failed");
        }
    }

    private NbtCompound buildSpongeSchematicNbt(StructureExportPreflight.PreparedStructureExport prepared) {
        ExportBounds bounds = prepared.bounds();
        int width = bounds.sizeXInt();
        int height = bounds.sizeYInt();
        int length = bounds.sizeZInt();

        int[] paletteIndices = StructureExportPreflight.buildDenseIndices(
            prepared,
            StructureExportPreflight.IndexOrder.WORLD_EDIT
        );
        Map<String, Integer> spongePalette = buildSpongePalette(prepared);

        NbtCompound root = new NbtCompound();
        root.put("Version", NbtInt.of(SPONGE_SCHEM_VERSION));
        root.put("DataVersion", NbtInt.of(MinecraftFormatVersion.dataVersion()));
        root.put("Width", NbtShort.of((short) width));
        root.put("Height", NbtShort.of((short) height));
        root.put("Length", NbtShort.of((short) length));
        root.put("Offset", new NbtIntArray(new int[]{0, 0, 0}));
        root.put("PaletteMax", NbtInt.of(spongePalette.size()));

        NbtCompound paletteNbt = new NbtCompound();
        spongePalette.forEach((key, index) -> paletteNbt.put(key, NbtInt.of(index)));
        root.put("Palette", paletteNbt);
        root.put("BlockData", new NbtByteArray(encodeVarInts(paletteIndices)));
        root.put("BlockEntities", new NbtList());

        NbtCompound metadata = new NbtCompound();
        metadata.put("Name", NbtString.of(prepared.metadata().name()));
        metadata.put("Author", NbtString.of(prepared.metadata().author()));
        metadata.put("RequiredMods", new NbtList());
        metadata.put("Date", NbtString.of(java.time.Instant.now().toString()));
        root.put("Metadata", metadata);
        return root;
    }

    /**
     * Remap shared palette (air@0) into Sponge state-string keys with matching indices.
     */
    private Map<String, Integer> buildSpongePalette(StructureExportPreflight.PreparedStructureExport prepared) {
        LinkedHashMap<String, Integer> sponge = new LinkedHashMap<>();
        // Index 0 air
        sponge.put("minecraft:air", 0);
        for (BlockPlacementData placement : prepared.placements()) {
            String key = StructureExportPreflight.spongeKeyFor(placement);
            if (!sponge.containsKey(key)) {
                sponge.put(key, prepared.palette().indexFor(placement));
            }
        }
        return sponge;
    }

    private byte[] encodeVarInts(int[] values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(values.length * 2);
        for (int value : values) {
            int remaining = value;
            while ((remaining & -128) != 0) {
                output.write((remaining & 127) | 128);
                remaining >>>= 7;
            }
            output.write(remaining & 127);
        }
        return output.toByteArray();
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }

    private void publishOutputs(boolean success, String path, int count, String error) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_FORMAT_ID, "worldedit:schem");
        outputValues.put(OUTPUT_VERSION_ID, SPONGE_SCHEM_VERSION);
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

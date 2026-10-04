package com.nodecraft.nodesystem.nodes.output.export;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.ExportBounds;
import com.nodecraft.nodesystem.util.ExportPathUtil;
import com.nodecraft.nodesystem.util.MinecraftFormatVersion;
import com.nodecraft.nodesystem.util.StructureExportPreflight;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtString;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Exports placements to a single-region Litematic-compatible NBT structure.
 */
@NodeInfo(
    effect = NodeEffect.FILE_IO,
    id = "output.export.export_litematic",
    displayName = "Export Litematic",
    description = "Exports placements to a single-region Litematic file",
    category = "output.export",
    order = 1
)
public class ExportLitematicNode extends BaseCustomUINode {

    private static final int LITEMATIC_VERSION = 6;
    private static final int LITEMATIC_SUB_VERSION = 1;

    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String INPUT_BLOCKS_ID = "input_blocks";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";
    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_NAME_ID = "input_name";
    private static final String INPUT_AUTHOR_ID = "input_author";
    private static final String INPUT_DESCRIPTION_ID = "input_description";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_FORMAT_ID = "output_format";
    private static final String OUTPUT_VERSION_ID = "output_version";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ExportLitematicNode() {
        super(UUID.randomUUID(), "output.export.export_litematic");

        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "EXEC pulse to export", NodeDataType.EXEC, this, false, false));
        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Uniform block type when exporting plain block coordinates", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Per-position block assignments", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Output path such as schematics/out.litematic", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_NAME_ID, "Name", "Optional litematic name stored in metadata", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_AUTHOR_ID, "Author", "Optional author stored in metadata", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_DESCRIPTION_ID, "Description", "Optional description stored in metadata", NodeDataType.STRING, this));

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

        String rawPath = getInputString(INPUT_PATH_ID, "nodecraft_export.litematic");
        String defaultBlock = getInputString(INPUT_BLOCK_TYPE_ID, "minecraft:stone");
        String name = getInputString(INPUT_NAME_ID, StructureExportPreflight.deriveNameFromPath(rawPath));
        String author = getInputString(INPUT_AUTHOR_ID, "nodecraft");
        String description = getInputString(INPUT_DESCRIPTION_ID, "Exported by NodeCraft");

        StructureExportPreflight.Result prepared = StructureExportPreflight.prepare(
            inputValues.get(INPUT_PLACEMENTS_ID),
            inputValues.get(INPUT_BLOCKS_ID),
            defaultBlock,
            StructureExportPreflight.DenseMode.LITEMATIC,
            name,
            author,
            description
        );
        if (!prepared.valid() || prepared.prepared() == null) {
            publishOutputs(false, "", 0, prepared.error());
            return;
        }

        Path outputPath = null;
        try {
            outputPath = ExportPathUtil.resolve(rawPath, "nodecraft_export.litematic", ".litematic");
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            NbtCompound root = buildLitematicNbt(prepared.prepared());
            NbtIo.write(root, outputPath);
            publishOutputs(true, outputPath.toString(), prepared.prepared().placements().size(), "");
        } catch (Exception e) {
            String resolvedPath = outputPath != null ? outputPath.toString() : rawPath;
            publishOutputs(false, resolvedPath, 0, e.getMessage() != null ? e.getMessage() : "export failed");
        }
    }

    private NbtCompound buildLitematicNbt(StructureExportPreflight.PreparedStructureExport prepared) {
        ExportBounds bounds = prepared.bounds();
        int sizeX = bounds.sizeXInt();
        int sizeY = bounds.sizeYInt();
        int sizeZ = bounds.sizeZInt();
        int totalVolume = Math.toIntExact(bounds.checkedVolume());

        int[] indices = StructureExportPreflight.buildDenseIndices(
            prepared,
            StructureExportPreflight.IndexOrder.LITEMATIC
        );
        NbtList paletteEntries = createPaletteList(prepared.palette());

        NbtCompound root = new NbtCompound();
        root.put("Version", NbtInt.of(LITEMATIC_VERSION));
        root.put("SubVersion", NbtInt.of(LITEMATIC_SUB_VERSION));
        root.put("MinecraftDataVersion", NbtInt.of(MinecraftFormatVersion.dataVersion()));
        root.put("Metadata", createMetadata(
            prepared.metadata(),
            prepared.placements().size(),
            totalVolume,
            sizeX,
            sizeY,
            sizeZ
        ));
        root.put(
            "Regions",
            createRegions(
                paletteEntries,
                packBlockStates(indices, Math.max(2, bitsForPalette(prepared.palette().size()))),
                sizeX,
                sizeY,
                sizeZ
            )
        );
        return root;
    }

    private NbtList createPaletteList(StructureExportPreflight.StructurePalette palette) {
        NbtList entries = new NbtList();
        for (StructureExportPreflight.PaletteEntry entry : palette.entries()) {
            NbtCompound blockState = new NbtCompound();
            blockState.put("Name", NbtString.of(entry.blockId()));
            BlockStateData stateData = entry.stateData();
            if (stateData != null && !stateData.isEmpty()) {
                NbtCompound properties = new NbtCompound();
                stateData.forEach((property, value) -> properties.put(property, NbtString.of(value)));
                blockState.put("Properties", properties);
            }
            entries.add(blockState);
        }
        return entries;
    }

    private NbtCompound createMetadata(
        StructureExportPreflight.Metadata metadata,
        int totalBlocks,
        int totalVolume,
        int sizeX,
        int sizeY,
        int sizeZ
    ) {
        long now = Instant.now().toEpochMilli();
        NbtCompound compound = new NbtCompound();
        compound.put("Name", NbtString.of(metadata.name()));
        compound.put("Author", NbtString.of(metadata.author()));
        compound.put("Description", NbtString.of(metadata.description()));
        compound.put("TimeCreated", NbtLong.of(now));
        compound.put("TimeModified", NbtLong.of(now));
        compound.put("RegionCount", NbtInt.of(1));
        compound.put("TotalBlocks", NbtInt.of(totalBlocks));
        compound.put("TotalVolume", NbtInt.of(totalVolume));
        compound.put("EnclosingSize", createVectorCompound(sizeX, sizeY, sizeZ));
        return compound;
    }

    private NbtCompound createRegions(NbtList paletteEntries, long[] packedStates, int sizeX, int sizeY, int sizeZ) {
        NbtCompound regions = new NbtCompound();
        NbtCompound region = new NbtCompound();
        region.put("Position", createVectorCompound(0, 0, 0));
        region.put("Size", createVectorCompound(sizeX, sizeY, sizeZ));
        region.put("BlockStatePalette", paletteEntries);
        region.put("BlockStates", new NbtLongArray(packedStates));
        region.put("Entities", new NbtList());
        region.put("TileEntities", new NbtList());
        region.put("PendingBlockTicks", new NbtList());
        region.put("PendingFluidTicks", new NbtList());
        regions.put("main", region);
        return regions;
    }

    private NbtCompound createVectorCompound(int x, int y, int z) {
        NbtCompound vector = new NbtCompound();
        vector.put("x", NbtInt.of(x));
        vector.put("y", NbtInt.of(y));
        vector.put("z", NbtInt.of(z));
        return vector;
    }

    private int bitsForPalette(int paletteSize) {
        int bits = 0;
        int size = Math.max(1, paletteSize - 1);
        while (size > 0) {
            bits++;
            size >>= 1;
        }
        return Math.max(2, bits);
    }

    private long[] packBlockStates(int[] indices, int bitsPerEntry) {
        int valuesPerLong = 64 / bitsPerEntry;
        int longCount = Math.max(1, (indices.length + valuesPerLong - 1) / valuesPerLong);
        long[] packed = new long[longCount];
        long mask = (1L << bitsPerEntry) - 1L;

        for (int i = 0; i < indices.length; i++) {
            int longIndex = i / valuesPerLong;
            int bitOffset = (i % valuesPerLong) * bitsPerEntry;
            packed[longIndex] |= ((long) indices[i] & mask) << bitOffset;
        }
        return packed;
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }

    private void publishOutputs(boolean success, String path, int count, String error) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_FORMAT_ID, "litematic");
        outputValues.put(OUTPUT_VERSION_ID, LITEMATIC_VERSION);
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

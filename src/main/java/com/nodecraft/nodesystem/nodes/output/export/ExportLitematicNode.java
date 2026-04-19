package com.nodecraft.nodesystem.nodes.output.export;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exports placements to a single-region Litematic-compatible NBT structure.
 */
@NodeInfo(
    id = "output.export.export_litematic",
    displayName = "Export Litematic",
    description = "Exports placements to a single-region Litematic file",
    category = "output.export",
    order = 1
)
public class ExportLitematicNode extends BaseCustomUINode {

    private static final int LITEMATIC_VERSION = 6;
    private static final int LITEMATIC_SUB_VERSION = 1;
    private static final int MINECRAFT_DATA_VERSION = 3700;

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

    public ExportLitematicNode() {
        super(UUID.randomUUID(), "output.export.export_litematic");

        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Export trigger", NodeDataType.ANY, this));
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
    }

    @Override
    public String getDescription() {
        return "Exports placements to a single-region Litematic file";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (inputValues.get(INPUT_TRIGGER_ID) == null) {
            publishOutputs(false, "", 0);
            return;
        }

        String defaultBlock = getInputString(INPUT_BLOCK_TYPE_ID, "minecraft:stone");
        String rawPath = getInputString(INPUT_PATH_ID, "nodecraft_export.litematic");
        String name = getInputString(INPUT_NAME_ID, deriveNameFromPath(rawPath));
        String author = getInputString(INPUT_AUTHOR_ID, "nodecraft");
        String description = getInputString(INPUT_DESCRIPTION_ID, "Exported by NodeCraft");

        List<BlockPlacementData> placements = resolvePlacements(defaultBlock);
        if (placements.isEmpty()) {
            publishOutputs(false, "", 0);
            return;
        }

        try {
            Path outputPath = normalizeOutputPath(rawPath);
            Files.createDirectories(outputPath.getParent());

            NbtCompound root = buildLitematicNbt(placements, name, author, description);
            NbtIo.write(root, outputPath);

            publishOutputs(true, outputPath.toString(), placements.size());
        } catch (Exception e) {
            publishOutputs(false, e.getMessage() != null ? e.getMessage() : rawPath, 0);
        }
    }

    private List<BlockPlacementData> resolvePlacements(String defaultBlock) {
        Object placementsObj = inputValues.get(INPUT_PLACEMENTS_ID);
        Object blocksObj = inputValues.get(INPUT_BLOCKS_ID);

        List<BlockPlacementData> resolved = new ArrayList<>();
        if (placementsObj instanceof List<?> placementList && !placementList.isEmpty()) {
            for (Object entry : placementList) {
                if (entry instanceof BlockPlacementData placement
                    && placement.pos() != null
                    && placement.blockId() != null
                    && !placement.blockId().isBlank()) {
                    resolved.add(new BlockPlacementData(placement.pos(), placement.blockId(), placement.stateData()));
                }
            }
            return resolved;
        }

        if (blocksObj instanceof BlockPosList blocks && !blocks.isEmpty()) {
            for (BlockPos pos : blocks) {
                resolved.add(new BlockPlacementData(pos, defaultBlock));
            }
        }
        return resolved;
    }

    private NbtCompound buildLitematicNbt(List<BlockPlacementData> placements, String name, String author, String description) {
        Bounds bounds = Bounds.fromPlacements(placements);
        Palette palette = Palette.fromPlacements(placements);

        int sizeX = bounds.sizeX();
        int sizeY = bounds.sizeY();
        int sizeZ = bounds.sizeZ();
        int totalVolume = sizeX * sizeY * sizeZ;

        int[] indices = new int[totalVolume];
        for (BlockPlacementData placement : placements) {
            BlockPos pos = placement.pos();
            int x = pos.getX() - bounds.minX();
            int y = pos.getY() - bounds.minY();
            int z = pos.getZ() - bounds.minZ();
            int linearIndex = x + z * sizeX + y * sizeX * sizeZ;
            indices[linearIndex] = palette.indexByKey().getOrDefault(Palette.keyFor(placement), 0);
        }

        NbtCompound root = new NbtCompound();
        root.put("Version", NbtInt.of(LITEMATIC_VERSION));
        root.put("SubVersion", NbtInt.of(LITEMATIC_SUB_VERSION));
        root.put("MinecraftDataVersion", NbtInt.of(MINECRAFT_DATA_VERSION));
        root.put("Metadata", createMetadata(name, author, description, placements.size(), totalVolume, sizeX, sizeY, sizeZ));
        root.put("Regions", createRegions(bounds, palette.paletteEntries(), packBlockStates(indices, Math.max(2, bitsForPalette(palette.size()))), sizeX, sizeY, sizeZ));
        return root;
    }

    private NbtCompound createMetadata(String name, String author, String description, int totalBlocks, int totalVolume, int sizeX, int sizeY, int sizeZ) {
        long now = Instant.now().toEpochMilli();

        NbtCompound metadata = new NbtCompound();
        metadata.put("Name", NbtString.of(name));
        metadata.put("Author", NbtString.of(author));
        metadata.put("Description", NbtString.of(description));
        metadata.put("TimeCreated", NbtLong.of(now));
        metadata.put("TimeModified", NbtLong.of(now));
        metadata.put("RegionCount", NbtInt.of(1));
        metadata.put("TotalBlocks", NbtInt.of(totalBlocks));
        metadata.put("TotalVolume", NbtInt.of(totalVolume));
        metadata.put("EnclosingSize", createVectorCompound(sizeX, sizeY, sizeZ));
        return metadata;
    }

    private NbtCompound createRegions(Bounds bounds, NbtList paletteEntries, long[] packedStates, int sizeX, int sizeY, int sizeZ) {
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

    private Path normalizeOutputPath(String rawPath) {
        String resolved = (rawPath == null || rawPath.isBlank()) ? "nodecraft_export.litematic" : rawPath.trim();
        if (!resolved.toLowerCase().endsWith(".litematic")) {
            resolved = resolved + ".litematic";
        }

        Path path = Path.of(resolved);
        if (!path.isAbsolute()) {
            path = path.toAbsolutePath();
        }

        Path parent = path.getParent();
        if (parent == null) {
            parent = Path.of("").toAbsolutePath();
            path = parent.resolve(path.getFileName());
        }
        return path.normalize();
    }

    private String deriveNameFromPath(String rawPath) {
        String candidate = (rawPath == null || rawPath.isBlank()) ? "nodecraft_export" : rawPath.trim();
        String fileName = Path.of(candidate).getFileName() != null ? Path.of(candidate).getFileName().toString() : "nodecraft_export";
        int suffixIndex = fileName.lastIndexOf('.');
        return suffixIndex > 0 ? fileName.substring(0, suffixIndex) : fileName;
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }

    private void publishOutputs(boolean success, String path, int count) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_FORMAT_ID, "litematic");
        outputValues.put(OUTPUT_VERSION_ID, LITEMATIC_VERSION);
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

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Bounds fromPlacements(List<BlockPlacementData> placements) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;

            for (BlockPlacementData placement : placements) {
                BlockPos pos = placement.pos();
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        int sizeX() {
            return maxX - minX + 1;
        }

        int sizeY() {
            return maxY - minY + 1;
        }

        int sizeZ() {
            return maxZ - minZ + 1;
        }
    }

    private record Palette(NbtList paletteEntries, Map<String, Integer> indexByKey, int size) {
        static Palette fromPlacements(List<BlockPlacementData> placements) {
            LinkedHashMap<String, Integer> indexByKey = new LinkedHashMap<>();
            NbtList entries = new NbtList();

            for (BlockPlacementData placement : placements) {
                String key = keyFor(placement);
                if (indexByKey.containsKey(key)) {
                    continue;
                }

                int nextIndex = indexByKey.size();
                indexByKey.put(key, nextIndex);

                NbtCompound blockState = new NbtCompound();
                blockState.put("Name", NbtString.of(placement.blockId()));
                if (placement.stateData() != null && !placement.stateData().isEmpty()) {
                    NbtCompound properties = new NbtCompound();
                    placement.stateData().forEach((property, value) -> properties.put(property, NbtString.of(value)));
                    blockState.put("Properties", properties);
                }
                entries.add(blockState);
            }

            return new Palette(entries, Map.copyOf(indexByKey), indexByKey.size());
        }

        static String keyFor(BlockPlacementData placement) {
            StringBuilder builder = new StringBuilder(placement.blockId());
            if (placement.stateData() != null && !placement.stateData().isEmpty()) {
                builder.append('|');
                placement.stateData().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> builder.append(entry.getKey()).append('=').append(entry.getValue()).append(';'));
            }
            return builder.toString();
        }
    }
}

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
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.NbtIo;
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
 * Exports placements into a stable NodeCraft NBT structure file with palette metadata.
 */
@NodeInfo(
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

    public ExportSchematicNode() {
        super(UUID.randomUUID(), "output.export.export_schematic");

        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Export trigger", NodeDataType.ANY, this));
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
    }

    @Override
    public String getDescription() {
        return "Exports placements to a NodeCraft NBT structure file";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (inputValues.get(INPUT_TRIGGER_ID) == null) {
            publishOutputs(false, "", 0);
            return;
        }

        String defaultBlock = getInputString(INPUT_BLOCK_TYPE_ID, "minecraft:stone");
        String rawPath = getInputString(INPUT_PATH_ID, "nodecraft_export.nbt");
        String name = getInputString(INPUT_NAME_ID, deriveNameFromPath(rawPath));
        String author = getInputString(INPUT_AUTHOR_ID, "nodecraft");

        List<BlockPlacementData> placements = resolvePlacements(defaultBlock);
        if (placements.isEmpty()) {
            publishOutputs(false, "", 0);
            return;
        }

        try {
            Path outputPath = normalizeOutputPath(rawPath);
            Files.createDirectories(outputPath.getParent());

            NbtCompound root = buildExportNbt(placements, name, author);
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

    private NbtCompound buildExportNbt(List<BlockPlacementData> placements, String name, String author) {
        Bounds bounds = Bounds.fromPlacements(placements);
        Palette palette = Palette.fromPlacements(placements);

        NbtCompound root = new NbtCompound();
        root.put("format", NbtString.of("nodecraft:structure"));
        root.put("format_version", NbtInt.of(FORMAT_VERSION));
        root.put("name", NbtString.of(name));
        root.put("author", NbtString.of(author));
        root.put("created_at_epoch_ms", NbtLong.of(Instant.now().toEpochMilli()));
        root.put("block_count", NbtInt.of(placements.size()));
        root.put("size", createIntList(bounds.sizeX(), bounds.sizeY(), bounds.sizeZ()));
        root.put("origin", createIntList(bounds.minX(), bounds.minY(), bounds.minZ()));
        root.put("palette", palette.paletteEntries());
        root.put("blocks", createBlocksList(placements, bounds, palette.indexByKey()));
        return root;
    }

    private NbtList createBlocksList(List<BlockPlacementData> placements, Bounds bounds, Map<String, Integer> indexByKey) {
        NbtList blocks = new NbtList();
        for (BlockPlacementData placement : placements) {
            BlockPos pos = placement.pos();
            String paletteKey = Palette.keyFor(placement);

            NbtCompound entry = new NbtCompound();
            entry.put("pos", createIntList(
                pos.getX() - bounds.minX(),
                pos.getY() - bounds.minY(),
                pos.getZ() - bounds.minZ()
            ));
            entry.put("palette_index", NbtInt.of(indexByKey.getOrDefault(paletteKey, 0)));
            entry.put("block", NbtString.of(placement.blockId()));

            if (placement.stateData() != null && !placement.stateData().isEmpty()) {
                NbtCompound state = new NbtCompound();
                placement.stateData().forEach((key, value) -> state.put(key, NbtString.of(value)));
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

    private Path normalizeOutputPath(String rawPath) {
        String resolved = (rawPath == null || rawPath.isBlank()) ? "nodecraft_export.nbt" : rawPath.trim();
        if (!resolved.toLowerCase().endsWith(".nbt")) {
            resolved = resolved + ".nbt";
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
        outputValues.put(OUTPUT_FORMAT_ID, "nodecraft:structure");
        outputValues.put(OUTPUT_VERSION_ID, FORMAT_VERSION);
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

    private record Palette(NbtList paletteEntries, Map<String, Integer> indexByKey) {
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

                NbtCompound paletteEntry = new NbtCompound();
                paletteEntry.put("block", NbtString.of(placement.blockId()));
                if (placement.stateData() != null && !placement.stateData().isEmpty()) {
                    NbtCompound state = new NbtCompound();
                    placement.stateData().forEach((property, value) -> state.put(property, NbtString.of(value)));
                    paletteEntry.put("state", state);
                }
                entries.add(paletteEntry);
            }

            return new Palette(entries, Map.copyOf(indexByKey));
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

package com.nodecraft.nodesystem.nodes.output.export;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.ExportPathUtil;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Exports placements to a Sponge schematic file that WorldEdit can import.
 */
@NodeInfo(
    id = "output.export.export_worldedit",
    displayName = "Export WorldEdit",
    description = "Exports placements to a Sponge schematic file for WorldEdit",
    category = "output.export",
    order = 2
)
public class ExportWorldEditNode extends BaseCustomUINode {

    private static final int SPONGE_SCHEM_VERSION = 2;
    private static final int MINECRAFT_DATA_VERSION = 3700;

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

        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Export trigger", NodeDataType.ANY, this));
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
        if (inputValues.get(INPUT_TRIGGER_ID) == null) {
            publishOutputs(false, "", 0, "trigger not connected");
            return;
        }

        String defaultBlock = getInputString(INPUT_BLOCK_TYPE_ID, "minecraft:stone");
        String rawPath = getInputString(INPUT_PATH_ID, "nodecraft_export.schem");
        String name = getInputString(INPUT_NAME_ID, deriveNameFromPath(rawPath));
        String author = getInputString(INPUT_AUTHOR_ID, "nodecraft");

        List<BlockPlacementData> placements = resolvePlacements(defaultBlock);
        if (placements.isEmpty()) {
            publishOutputs(false, "", 0, "empty placements");
            return;
        }

        Path outputPath = null;
        try {
            outputPath = ExportPathUtil.resolve(rawPath, "nodecraft_export.schem", ".schem");
            Files.createDirectories(outputPath.getParent());

            NbtCompound root = buildSpongeSchematicNbt(placements, name, author);
            NbtIo.write(root, outputPath);

            publishOutputs(true, outputPath.toString(), placements.size(), "");
        } catch (Exception e) {
            String resolvedPath = outputPath != null ? outputPath.toString() : rawPath;
            publishOutputs(false, resolvedPath, 0, e.getMessage() != null ? e.getMessage() : "export failed");
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

    private NbtCompound buildSpongeSchematicNbt(List<BlockPlacementData> placements, String name, String author) {
        Bounds bounds = Bounds.fromPlacements(placements);
        Palette palette = Palette.fromPlacements(placements);

        int width = bounds.sizeX();
        int height = bounds.sizeY();
        int length = bounds.sizeZ();
        int volume = width * height * length;

        int[] paletteIndices = new int[volume];
        for (BlockPlacementData placement : placements) {
            BlockPos pos = placement.pos();
            int x = 0;
            if (pos != null) {
                x = pos.getX() - bounds.minX();
            }
            int y = 0;
            if (pos != null) {
                y = pos.getY() - bounds.minY();
            }
            int z = 0;
            if (pos != null) {
                z = pos.getZ() - bounds.minZ();
            }
            int linearIndex = x + y * width + z * width * height;
            paletteIndices[linearIndex] = palette.indexByState().getOrDefault(Palette.keyFor(placement), 0);
        }

        NbtCompound root = new NbtCompound();
        root.put("Version", NbtInt.of(SPONGE_SCHEM_VERSION));
        root.put("DataVersion", NbtInt.of(MINECRAFT_DATA_VERSION));
        root.put("Width", NbtShort.of((short) width));
        root.put("Height", NbtShort.of((short) height));
        root.put("Length", NbtShort.of((short) length));
        root.put("Offset", new NbtIntArray(new int[]{0, 0, 0}));
        root.put("PaletteMax", NbtInt.of(palette.indexByState().size()));
        root.put("Palette", palette.paletteNbt());
        root.put("BlockData", new NbtByteArray(encodeVarInts(paletteIndices)));
        root.put("BlockEntities", new NbtList());

        NbtCompound metadata = new NbtCompound();
        metadata.put("Name", NbtString.of(name));
        metadata.put("Author", NbtString.of(author));
        metadata.put("RequiredMods", new NbtList());
        metadata.put("Date", NbtString.of(java.time.Instant.now().toString()));
        root.put("Metadata", metadata);

        return root;
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
                if (pos != null) {
                    minX = Math.min(minX, pos.getX());
                }
                if (pos != null) {
                    minY = Math.min(minY, pos.getY());
                }
                if (pos != null) {
                    minZ = Math.min(minZ, pos.getZ());
                }
                if (pos != null) {
                    maxX = Math.max(maxX, pos.getX());
                }
                if (pos != null) {
                    maxY = Math.max(maxY, pos.getY());
                }
                if (pos != null) {
                    maxZ = Math.max(maxZ, pos.getZ());
                }
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

    private record Palette(NbtCompound paletteNbt, Map<String, Integer> indexByState) {
        static Palette fromPlacements(List<BlockPlacementData> placements) {
            LinkedHashMap<String, Integer> indexByState = new LinkedHashMap<>();
            NbtCompound palette = new NbtCompound();

            for (BlockPlacementData placement : placements) {
                String key = keyFor(placement);
                if (indexByState.containsKey(key)) {
                    continue;
                }

                int nextIndex = indexByState.size();
                indexByState.put(key, nextIndex);
                palette.put(key, NbtInt.of(nextIndex));
            }

            return new Palette(palette, Map.copyOf(indexByState));
        }

        static String keyFor(BlockPlacementData placement) {
            if (placement.stateData() == null || Objects.requireNonNull(placement.stateData()).isEmpty()) {
                return placement.blockId();
            }

            StringBuilder builder = new StringBuilder(placement.blockId()).append('[');
            Objects.requireNonNull(placement.stateData()).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEachOrdered(entry -> {
                    if (builder.charAt(builder.length() - 1) != '[') {
                        builder.append(',');
                    }
                    builder.append(entry.getKey()).append('=').append(entry.getValue());
                });
            builder.append(']');
            return builder.toString();
        }
    }
}

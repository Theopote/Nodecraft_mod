package com.nodecraft.nodesystem.nodes.utilities.fileio;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.ImportPathUtil;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "utilities.fileio.import_vox",
    displayName = "Import VOX",
    description = "Imports MagicaVoxel .vox files as block coordinates and placements",
    category = "utilities.fileio",
    order = 1
)
public class ImportVoxNode extends BaseNode {

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";
    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_MAX_VOXELS_ID = "input_max_voxels";
    private static final String INPUT_ALLOW_EXTERNAL_PATHS_ID = "input_allow_external_paths";
    private static final String INPUT_Z_UP_ID = "input_z_up_to_minecraft_y";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_COLORS_ID = "output_colors";
    private static final String OUTPUT_COLOR_INDICES_ID = "output_color_indices";
    private static final String OUTPUT_SIZE_X_ID = "output_size_x";
    private static final String OUTPUT_SIZE_Y_ID = "output_size_y";
    private static final String OUTPUT_SIZE_Z_ID = "output_size_z";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VERSION_ID = "output_version";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Default Block", category = "Import", order = 1)
    private String defaultBlockType = "minecraft:stone";

    @NodeProperty(displayName = "Max Voxels", category = "Safety", order = 2)
    private int maxVoxels = 262_144;

    @NodeProperty(displayName = "Allow External Paths", category = "Safety", order = 3)
    private boolean allowExternalPaths = false;

    @NodeProperty(displayName = "Z Up To Minecraft Y", category = "Transform", order = 4)
    private boolean zUpToMinecraftY = true;

    public ImportVoxNode() {
        super(UUID.randomUUID(), "utilities.fileio.import_vox");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Path to a MagicaVoxel .vox file", NodeDataType.FILE_PATH, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Block id assigned to imported voxels", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Minecraft-space origin offset for imported voxels", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_MAX_VOXELS_ID, "Max Voxels", "Maximum voxels to import", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ALLOW_EXTERNAL_PATHS_ID, "Allow External Paths", "Permit paths outside common NodeCraft asset locations", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_Z_UP_ID, "Z Up To Y", "Map VOX Z-up coordinates to Minecraft Y-up coordinates", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Resolved VOX path", NodeDataType.FILE_PATH, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Imported block coordinate list", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Imported voxels as block placements", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COLORS_ID, "Voxel Colors", "Voxel colors aligned with the block list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COLOR_INDICES_ID, "Color Indices", "MagicaVoxel palette indices aligned with the block list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_X_ID, "Size X", "VOX model X size", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Y_ID, "Size Y", "VOX model Y size", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Z_ID, "Size Z", "VOX model Z size", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Voxel Count", "Number of imported voxels", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VERSION_ID, "VOX Version", "VOX file format version", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when import succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why import failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String rawPath = getInputString(INPUT_PATH_ID, "");
        if (rawPath.isBlank()) {
            publishEmpty("", "Missing VOX path");
            return;
        }

        Path resolvedPath;
        try {
            resolvedPath = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            publishEmpty(rawPath, "Invalid path: " + e.getMessage());
            return;
        }

        boolean allowExternal = getInputBoolean(INPUT_ALLOW_EXTERNAL_PATHS_ID, allowExternalPaths);
        if (!allowExternal && !ImportPathUtil.isAllowedDefaultPath(resolvedPath, ImportPathUtil.ImportKind.VOX)) {
            publishEmpty(resolvedPath.toString(), "External VOX paths are disabled");
            return;
        }
        if (!Files.isRegularFile(resolvedPath)) {
            publishEmpty(resolvedPath.toString(), "VOX file does not exist");
            return;
        }
        if (!resolvedPath.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".vox")) {
            publishEmpty(resolvedPath.toString(), "File extension is not .vox");
            return;
        }

        try {
            int resolvedMaxVoxels = Math.max(1, getInputInteger(INPUT_MAX_VOXELS_ID, maxVoxels));
            String blockType = getInputString(INPUT_BLOCK_TYPE_ID, defaultBlockType);
            if (blockType.isBlank()) {
                blockType = "minecraft:stone";
            }
            BlockPos origin = getInputOrigin();
            boolean zUp = getInputBoolean(INPUT_Z_UP_ID, zUpToMinecraftY);

            MagicaVoxelVoxReader.VoxModel model = MagicaVoxelVoxReader.read(resolvedPath, resolvedMaxVoxels);
            ImportResult importResult = toOutputs(model, blockType, origin, zUp);

            outputValues.put(OUTPUT_PATH_ID, resolvedPath.toString());
            outputValues.put(OUTPUT_BLOCKS_ID, importResult.blocks());
            outputValues.put(OUTPUT_PLACEMENTS_ID, importResult.placements());
            outputValues.put(OUTPUT_COLORS_ID, importResult.colors());
            outputValues.put(OUTPUT_COLOR_INDICES_ID, importResult.colorIndices());
            outputValues.put(OUTPUT_SIZE_X_ID, model.sizeX());
            outputValues.put(OUTPUT_SIZE_Y_ID, model.sizeY());
            outputValues.put(OUTPUT_SIZE_Z_ID, model.sizeZ());
            outputValues.put(OUTPUT_COUNT_ID, model.voxels().size());
            outputValues.put(OUTPUT_VERSION_ID, model.version());
            outputValues.put(OUTPUT_VALID_ID, true);
            outputValues.put(OUTPUT_ERROR_ID, "");
        } catch (Exception e) {
            publishEmpty(resolvedPath.toString(), e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private ImportResult toOutputs(MagicaVoxelVoxReader.VoxModel model, String blockType, BlockPos origin, boolean zUp) {
        BlockPosList blocks = new BlockPosList();
        List<BlockPlacementData> placements = new ArrayList<>(model.voxels().size());
        List<ColorData> colors = new ArrayList<>(model.voxels().size());
        List<Integer> colorIndices = new ArrayList<>(model.voxels().size());

        for (MagicaVoxelVoxReader.Voxel voxel : model.voxels()) {
            BlockPos pos = toMinecraftPos(voxel, origin, zUp);
            blocks.add(pos);
            placements.add(new BlockPlacementData(pos, blockType));
            colors.add(voxel.color());
            colorIndices.add(voxel.colorIndex());
        }

        return new ImportResult(blocks, List.copyOf(placements), List.copyOf(colors), List.copyOf(colorIndices));
    }

    private BlockPos toMinecraftPos(MagicaVoxelVoxReader.Voxel voxel, BlockPos origin, boolean zUp) {
        int x = origin.getX() + voxel.x();
        int y = origin.getY() + (zUp ? voxel.z() : voxel.y());
        int z = origin.getZ() + (zUp ? voxel.y() : voxel.z());
        return new BlockPos(x, y, z);
    }

    private BlockPos getInputOrigin() {
        Object value = inputValues.get(INPUT_ORIGIN_ID);
        if (value instanceof BlockPos pos) {
            return pos.toImmutable();
        }
        return BlockPos.ORIGIN;
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return value instanceof String text ? text.trim() : fallback;
    }

    private int getInputInteger(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean getInputBoolean(String portId, boolean fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private void publishEmpty(String path, String error) {
        outputValues.put(OUTPUT_PATH_ID, path != null ? path : "");
        outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_PLACEMENTS_ID, List.of());
        outputValues.put(OUTPUT_COLORS_ID, List.of());
        outputValues.put(OUTPUT_COLOR_INDICES_ID, List.of());
        outputValues.put(OUTPUT_SIZE_X_ID, 0);
        outputValues.put(OUTPUT_SIZE_Y_ID, 0);
        outputValues.put(OUTPUT_SIZE_Z_ID, 0);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VERSION_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error != null ? error : "");
    }

    private record ImportResult(BlockPosList blocks, List<BlockPlacementData> placements, List<ColorData> colors, List<Integer> colorIndices) {
    }
}

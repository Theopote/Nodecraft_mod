package com.nodecraft.nodesystem.nodes.utilities.fileio;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.ImportAccessPolicy;
import com.nodecraft.nodesystem.util.ImportPathUtil;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.FILE_IO,
    id = "utilities.fileio.import_vox",
    displayName = "Import VOX",
    description = "Imports MagicaVoxel .vox structure as block coordinates, colors, and palette indices",
    category = "utilities.fileio",
    order = 2
)
public class ImportVoxNode extends BaseNode {

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_Z_UP_ID = "input_z_up_to_minecraft_y";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COLORS_ID = "output_colors";
    private static final String OUTPUT_COLOR_INDICES_ID = "output_color_indices";
    private static final String OUTPUT_SIZE_X_ID = "output_size_x";
    private static final String OUTPUT_SIZE_Y_ID = "output_size_y";
    private static final String OUTPUT_SIZE_Z_ID = "output_size_z";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VERSION_ID = "output_version";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Z Up To Minecraft Y", category = "Transform", order = 1)
    private boolean zUpToMinecraftY = true;

    public ImportVoxNode() {
        super(UUID.randomUUID(), "utilities.fileio.import_vox");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Path to a MagicaVoxel .vox file", NodeDataType.FILE_PATH, this));
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Minecraft-space origin offset for imported voxels", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_Z_UP_ID, "Z Up To Y", "Map VOX Z-up coordinates to Minecraft Y-up coordinates", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Resolved VOX path", NodeDataType.FILE_PATH, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Imported block coordinate list", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COLORS_ID, "Voxel Colors", "Voxel colors aligned with the block list", NodeDataType.COLOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COLOR_INDICES_ID, "Color Indices", "MagicaVoxel palette indices aligned with the block list", NodeDataType.INTEGER_LIST, this));
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
        String rawPath = getInputString(INPUT_PATH_ID);
        if (rawPath == null || rawPath.isBlank()) {
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

        if (!ImportAccessPolicy.current().allows(resolvedPath, ImportPathUtil.ImportKind.VOX)) {
            publishEmpty(resolvedPath.toString(), "VOX path is outside the import allowlist");
            return;
        }
        if (!Files.isRegularFile(resolvedPath)) {
            publishEmpty(resolvedPath.toString(), "VOX file does not exist");
            return;
        }
        if (!resolvedPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".vox")) {
            publishEmpty(resolvedPath.toString(), "File extension is not .vox");
            return;
        }

        BlockPos origin = OptionalPortDrive.resolveOptionalBlockPos(this, INPUT_ORIGIN_ID, BlockPos.ORIGIN);
        if (origin == null) {
            publishEmpty(resolvedPath.toString(), "Origin is connected but null or invalid");
            return;
        }

        Boolean zUp = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_Z_UP_ID, zUpToMinecraftY);
        if (zUp == null) {
            publishEmpty(resolvedPath.toString(), "Z Up To Y is connected but null or invalid");
            return;
        }

        try {
            long fileBytes = Files.size(resolvedPath);
            if (fileBytes > GenerationLimits.MAX_VOX_FILE_BYTES) {
                publishEmpty(
                    resolvedPath.toString(),
                    "VOX file exceeds max bytes " + GenerationLimits.MAX_VOX_FILE_BYTES
                );
                return;
            }

            MagicaVoxelVoxReader.VoxModel model = MagicaVoxelVoxReader.read(
                resolvedPath,
                GenerationLimits.MAX_IMPORTED_VOXELS
            );
            ImportResult importResult = toOutputs(model, origin, zUp);

            outputValues.put(OUTPUT_PATH_ID, resolvedPath.toString());
            outputValues.put(OUTPUT_BLOCKS_ID, importResult.blocks());
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

    private ImportResult toOutputs(MagicaVoxelVoxReader.VoxModel model, BlockPos origin, boolean zUp) {
        BlockPosList blocks = new BlockPosList();
        List<ColorData> colors = new ArrayList<>(model.voxels().size());
        List<Integer> colorIndices = new ArrayList<>(model.voxels().size());

        for (MagicaVoxelVoxReader.Voxel voxel : model.voxels()) {
            blocks.add(toMinecraftPos(voxel, origin, zUp));
            colors.add(voxel.color());
            colorIndices.add(voxel.colorIndex());
        }

        return new ImportResult(blocks, List.copyOf(colors), List.copyOf(colorIndices));
    }

    private BlockPos toMinecraftPos(MagicaVoxelVoxReader.Voxel voxel, BlockPos origin, boolean zUp) {
        int x = origin.getX() + voxel.x();
        int y = origin.getY() + (zUp ? voxel.z() : voxel.y());
        int z = origin.getZ() + (zUp ? voxel.y() : voxel.z());
        return new BlockPos(x, y, z);
    }

    private @Nullable String getInputString(String portId) {
        Object value = inputValues.get(portId);
        return value instanceof String text ? text.trim() : null;
    }

    private void publishEmpty(String path, String error) {
        outputValues.put(OUTPUT_PATH_ID, path != null ? path : "");
        outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
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

    public boolean isZUpToMinecraftY() {
        return zUpToMinecraftY;
    }

    public void setZUpToMinecraftY(boolean zUpToMinecraftY) {
        this.zUpToMinecraftY = zUpToMinecraftY;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("zUpToMinecraftY", zUpToMinecraftY);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("zUpToMinecraftY") instanceof Boolean value) {
            setZUpToMinecraftY(value);
        }
    }

    private record ImportResult(BlockPosList blocks, List<ColorData> colors, List<Integer> colorIndices) {
    }
}

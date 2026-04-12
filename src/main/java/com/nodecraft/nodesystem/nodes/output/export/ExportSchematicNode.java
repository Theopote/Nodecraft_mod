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
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 导出结构节点：将方块坐标列表（及可选按位置方块类型）导出为 NBT 结构文件。
 * 格式兼容简单结构数据，便于保存或与结构方块等配合使用。
 */
@NodeInfo(
    id = "output.export.export_schematic",
    displayName = "导出结构",
    description = "将方块列表导出为 NBT 结构文件（.nbt）",
    category = "output.export",
    order = 0
)
public class ExportSchematicNode extends BaseCustomUINode {

    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String INPUT_BLOCKS_ID = "input_blocks";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";
    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_PATH_ID = "input_path";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public ExportSchematicNode() {
        super(UUID.randomUUID(), "output.export.export_schematic");
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "触发导出", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks", "方块坐标列表", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "统一方块类型（当无 Placements 时）", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "按位置方块（可选，优先于 Blocks+Block Type）", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "保存路径（如 schematics/out.nbt）", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "是否导出成功", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "实际写入路径", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Block Count", "导出的方块数", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "将方块列表导出为 NBT 结构文件（.nbt）";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        String outPath = "";
        int count = 0;

        Object triggerObj = inputValues.get(INPUT_TRIGGER_ID);
        Object pathObj = inputValues.get(INPUT_PATH_ID);
        Object placementsObj = inputValues.get(INPUT_PLACEMENTS_ID);
        Object blocksObj = inputValues.get(INPUT_BLOCKS_ID);
        Object blockTypeObj = inputValues.get(INPUT_BLOCK_TYPE_ID);

        String path = pathObj instanceof String ? (String) pathObj : "nodecraft_export.nbt";
        String defaultBlock = blockTypeObj instanceof String && !((String) blockTypeObj).isEmpty() ? (String) blockTypeObj : "minecraft:stone";

        if (triggerObj == null) {
            outputValues.put(OUTPUT_SUCCESS_ID, false);
            outputValues.put(OUTPUT_PATH_ID, "");
            outputValues.put(OUTPUT_COUNT_ID, 0);
            return;
        }

        List<BlockPlacementData> toExport = new ArrayList<>();
        if (placementsObj instanceof List && !((List<?>) placementsObj).isEmpty()) {
            for (Object e : (List<?>) placementsObj) {
                if (e instanceof BlockPlacementData bpd && bpd.pos() != null && bpd.blockId() != null) {
                    toExport.add(bpd);
                }
            }
        } else if (blocksObj instanceof BlockPosList && !((BlockPosList) blocksObj).isEmpty()) {
            BlockPosList list = (BlockPosList) blocksObj;
            for (BlockPos pos : list) {
                toExport.add(new BlockPlacementData(pos, defaultBlock));
            }
        }

        if (toExport.isEmpty()) {
            outputValues.put(OUTPUT_SUCCESS_ID, false);
            outputValues.put(OUTPUT_PATH_ID, "");
            outputValues.put(OUTPUT_COUNT_ID, 0);
            return;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPlacementData bpd : toExport) {
            BlockPos p = bpd.pos();
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        int sizeX = maxX - minX + 1, sizeY = maxY - minY + 1, sizeZ = maxZ - minZ + 1;

        NbtCompound root = new NbtCompound();
        NbtList sizeList = new NbtList();
        sizeList.add(NbtInt.of(sizeX));
        sizeList.add(NbtInt.of(sizeY));
        sizeList.add(NbtInt.of(sizeZ));
        root.put("size", sizeList);
        NbtList originList = new NbtList();
        originList.add(NbtInt.of(minX));
        originList.add(NbtInt.of(minY));
        originList.add(NbtInt.of(minZ));
        root.put("origin", originList);
        NbtList blocksList = new NbtList();
        for (BlockPlacementData bpd : toExport) {
            NbtCompound entry = new NbtCompound();
            entry.put("x", NbtInt.of(bpd.pos().getX()));
            entry.put("y", NbtInt.of(bpd.pos().getY()));
            entry.put("z", NbtInt.of(bpd.pos().getZ()));
            entry.put("block", NbtString.of(bpd.blockId()));
            if (bpd.stateData() != null && !bpd.stateData().isEmpty()) {
                NbtCompound state = new NbtCompound();
                bpd.stateData().forEach((key, value) -> state.put(key, NbtString.of(value)));
                entry.put("state", state);
            }
            blocksList.add(entry);
        }
        root.put("blocks", blocksList);

        try {
            Path resolve = Path.of(path).toAbsolutePath();
            NbtIo.write(root, resolve);
            outPath = resolve.toString();
            count = toExport.size();
            success = true;
        } catch (Exception e) {
            outPath = e.getMessage() != null ? e.getMessage() : path;
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PATH_ID, outPath);
        outputValues.put(OUTPUT_COUNT_ID, count);
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

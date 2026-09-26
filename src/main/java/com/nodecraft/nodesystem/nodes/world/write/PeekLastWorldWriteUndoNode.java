package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.CONTEXT_READ,
    id = "world.write.peek_last_undo",
    displayName = "Peek Last World Write Undo",
    description = "Inspects the latest world.write undo record and outputs affected count and region bounds",
    category = "world.write",
    order = 16
)
public class PeekLastWorldWriteUndoNode extends BaseNode {
    private static final String OUTPUT_HAS_RECORD_ID = "output_has_record";
    private static final String OUTPUT_RECORDED_COUNT_ID = "output_recorded_count";
    private static final String OUTPUT_REMAINING_HISTORY_ID = "output_remaining_history";
    private static final String OUTPUT_MIN_POS_ID = "output_min_pos";
    private static final String OUTPUT_MAX_POS_ID = "output_max_pos";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    public PeekLastWorldWriteUndoNode() {
        super(UUID.randomUUID(), "world.write.peek_last_undo");
        addOutputPort(new BasePort(OUTPUT_HAS_RECORD_ID, "Has Record", "Whether world.write undo history has at least one record", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RECORDED_COUNT_ID, "Recorded Count", "Affected block count of latest undo record", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMAINING_HISTORY_ID, "Remaining History", "Current world.write undo history size", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_MIN_POS_ID, "Min Position", "Minimum corner of latest undo record bounds", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_MAX_POS_ID, "Max Position", "Maximum corner of latest undo record bounds", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Affected Region", "Axis-aligned bounds of latest undo record", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether peek succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why peek failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null || context.getWorld() == null) {
            outputValues.put(OUTPUT_HAS_RECORD_ID, false);
            outputValues.put(OUTPUT_RECORDED_COUNT_ID, 0);
            outputValues.put(OUTPUT_REMAINING_HISTORY_ID, 0);
            outputValues.put(OUTPUT_MIN_POS_ID, null);
            outputValues.put(OUTPUT_MAX_POS_ID, null);
            outputValues.put(OUTPUT_REGION_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Missing execution world");
            return;
        }

        WorldWriteHistoryService service = WorldWriteHistoryService.getInstance();
        UUID actorId = WorldWriteHistoryService.resolveActorId(context.getPlayer());
        WorldWriteHistoryService.UndoRecord record = service.peek(actorId, context.getWorld());
        boolean hasRecord = record != null && record.size() > 0;
        int count = hasRecord ? record.size() : 0;
        int remaining = service.size(actorId, context.getWorld());
        BlockPos minPos = null;
        BlockPos maxPos = null;
        RegionData region = null;

        if (hasRecord) {
            List<BlockPos> positions = record.getPositions();
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : positions) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            minPos = new BlockPos(minX, minY, minZ);
            maxPos = new BlockPos(maxX, maxY, maxZ);
            region = new RegionData(minPos, maxPos);
        }

        outputValues.put(OUTPUT_HAS_RECORD_ID, hasRecord);
        outputValues.put(OUTPUT_RECORDED_COUNT_ID, count);
        outputValues.put(OUTPUT_REMAINING_HISTORY_ID, remaining);
        outputValues.put(OUTPUT_MIN_POS_ID, minPos);
        outputValues.put(OUTPUT_MAX_POS_ID, maxPos);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }
}

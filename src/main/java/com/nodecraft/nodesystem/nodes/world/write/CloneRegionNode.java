package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.clone_region",
    displayName = "Clone Region",
    description = "Clones a source region to a destination block position",
    category = "world.write",
    order = 4
)
public class CloneRegionNode extends BaseNode {

    private static final String INPUT_SOURCE_REGION_ID = "input_source_region";
    private static final String INPUT_DESTINATION_POS_ID = "input_destination_pos";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_INCLUDE_ENTITIES_ID = "input_include_entities";
    private static final String INPUT_INCLUDE_AIR_ID = "input_include_air";
    private static final String INPUT_CLONE_MODE_ID = "input_clone_mode";
    private static final String INPUT_NOTIFY_ID = "input_notify";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";

    private static final String OUTPUT_CLONED_BLOCKS_ID = "output_cloned_blocks";
    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_FAILURE_COUNT_ID = "output_failure_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_DESTINATION_REGION_ID = "output_destination_region";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_HIT_LIMIT_ID = WorldWriteUtils.OUTPUT_HIT_LIMIT_ID;
    private static final String OUTPUT_COMPLETE_ID = WorldWriteUtils.OUTPUT_COMPLETE_ID;
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    private boolean notifyUpdate = true;
    @NodeProperty(
        displayName = "Include Entities",
        category = "Clone",
        order = 3,
        readOnly = true,
        description = "Entity cloning is not implemented yet"
    )
    private boolean includeEntities = false;
    private boolean includeAir = true;
    private CloneMode cloneMode = CloneMode.NORMAL;
    private int maxBlocks = 32768;
    @NodeProperty(displayName = "Record Undo", category = "Execution", order = 1)
    private boolean recordUndo = true;

    public enum CloneMode {
        NORMAL("Normal", "Copy source region as-is"),
        FORCE("Force", "Force clone even with immovable blocks"),
        MOVE("Move", "Clone then clear the source region to air"),
        MASKED("Masked", "Clone non-air blocks only");

        private final String displayName;
        private final String description;

        CloneMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public CloneRegionNode() {
        super(UUID.randomUUID(), "world.write.clone_region");

        addInputPort(new BasePort(INPUT_SOURCE_REGION_ID, "Source Region", "Region to clone", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_DESTINATION_POS_ID, "Destination Position", "Destination min corner", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_INCLUDE_ENTITIES_ID, "Include Entities", "Reserved; entity clone is not implemented", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_INCLUDE_AIR_ID, "Include Air", "Whether to clone air blocks", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_CLONE_MODE_ID, "Clone Mode", "0=Normal, 1=Force, 2=Move, 3=Masked", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", "Whether neighbor and listener updates should fire", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "User budget hard-capped by MAX_WORLD_WRITE_BLOCKS", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_CLONED_BLOCKS_ID, "Cloned Blocks", "Number of blocks cloned", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", "Successful placements", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FAILURE_COUNT_ID, "Failure Count", "Failed placements", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", "Attempted placements", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_DESTINATION_REGION_ID, "Destination Region", "Destination region bounds", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the clone fully succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "True when Max Blocks budget stopped early", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete", "False when Hit Limit or per-cell failures", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why clone did not run or the first failure reason", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Clones a source region to a destination block position";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(0, 0, 0, 0, null, false, false, false, false, "Trigger is connected but null or invalid.");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(0, 0, 0, 0, null, false, true, false, true, "Not triggered");
            return;
        }

        Boolean includeEntitiesValue = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_INCLUDE_ENTITIES_ID, includeEntities);
        Boolean includeAirValue = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_INCLUDE_AIR_ID, includeAir);
        Boolean notify = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_NOTIFY_ID, notifyUpdate);
        Integer blockLimit = WorldWriteUtils.resolveUserBudgetExactInteger(
            this, INPUT_MAX_BLOCKS_ID, maxBlocks, GenerationLimits.MAX_WORLD_WRITE_BLOCKS);
        if (includeEntitiesValue == null || includeAirValue == null || notify == null) {
            publish(0, 0, 0, 0, null, false, false, false, false, "Boolean drive is connected but null or invalid.");
            return;
        }
        if (blockLimit == null) {
            publish(0, 0, 0, 0, null, false, false, false, false,
                "Max Blocks must be an exact INTEGER between 1 and " + GenerationLimits.MAX_WORLD_WRITE_BLOCKS + ".");
            return;
        }

        CloneMode cloneModeValue = resolveCloneMode();
        if (cloneModeValue == null) {
            publish(0, 0, 0, 0, null, false, false, false, false, "Clone Mode must be an exact INTEGER 0–3.");
            return;
        }

        Object sourceRegionObj = inputValues.get(INPUT_SOURCE_REGION_ID);
        BlockPos destinationPos = WorldWriteUtils.requireBlockPos(inputValues.get(INPUT_DESTINATION_POS_ID));
        if (!(sourceRegionObj instanceof RegionData sourceRegion) || !sourceRegion.isComplete()) {
            publish(0, 0, 0, 0, null, false, false, false, false, "Invalid source region");
            return;
        }
        if (destinationPos == null) {
            publish(0, 0, 0, 0, null, false, false, false, false, "Invalid destination position (BLOCK_POS required).");
            return;
        }
        if (context == null || context.getWorld() == null) {
            publish(0, 0, 0, 0, null, false, false, false, false, "Missing execution world");
            return;
        }

        BlockPos sourceMinCorner = sourceRegion.getMinCorner();
        BlockPos sourceMaxCorner = sourceRegion.getMaxCorner();
        long volume = WorldWriteUtils.volume(sourceMinCorner, sourceMaxCorner);
        if (volume < 0L) {
            publish(0, 0, 0, 0, null, false, false, false, false, "Region volume overflow");
            return;
        }
        if (volume > blockLimit) {
            publish(0, 0, 0, 0, null, false, false, false, false,
                "Region volume " + volume + " exceeds Max Blocks " + blockLimit + ".");
            return;
        }

        BlockPos destMaxCorner = getBlockPos(destinationPos, sourceMaxCorner, sourceMinCorner);
        RegionData destinationRegion = new RegionData(destinationPos, destMaxCorner);
        if (checkRegionsOverlap(sourceRegion, destinationRegion) && cloneModeValue != CloneMode.MOVE) {
            publish(0, 0, 0, 0, destinationRegion, false, false, false, false,
                "Source and destination regions overlap; use MOVE mode");
            return;
        }

        if (includeEntitiesValue) {
            publish(0, 0, 0, 0, destinationRegion, false, false, false, false,
                "Include entities is not implemented yet");
            return;
        }

        WorldWriteTransaction tx = new WorldWriteTransaction(WorldWriteUtils.worldKey(context.getWorld()));
        Map<BlockPos, BlockState> blocksToCopy = new HashMap<>();
        int totalCount = 0;

        for (BlockPos pos : BlockPos.iterate(sourceMinCorner, sourceMaxCorner)) {
            totalCount++;
            BlockPos immutablePos = pos.toImmutable();
            try {
                BlockState blockState = context.getWorld().getBlockState(immutablePos);
                boolean isAir = context.getWorld().isAir(immutablePos);
                if (isAir && !includeAirValue) {
                    continue;
                }
                if (cloneModeValue == CloneMode.MASKED && isAir) {
                    continue;
                }
                blocksToCopy.put(getPos(destinationPos, immutablePos, sourceMinCorner), blockState);
            } catch (Exception e) {
                tx.recordFailure();
            }
        }

        int flags = WorldWriteUtils.flags(notify);
        for (Map.Entry<BlockPos, BlockState> entry : blocksToCopy.entrySet()) {
            BlockPos pos = entry.getKey();
            try {
                BlockSnapshot before = WorldWriteTransaction.captureCurrent(context, pos);
                if (before == null) {
                    tx.recordFailure();
                    continue;
                }
                boolean blockSuccess = context.getWorld().setBlockState(pos, entry.getValue(), flags);
                if (blockSuccess) {
                    tx.recordSuccess(before);
                } else {
                    tx.recordFailure();
                }
            } catch (Exception e) {
                tx.recordFailure();
            }
        }

        if (cloneModeValue == CloneMode.MOVE) {
            BlockState airState = Blocks.AIR.getDefaultState();
            for (BlockPos pos : BlockPos.iterate(sourceMinCorner, sourceMaxCorner)) {
                BlockPos immutablePos = pos.toImmutable();
                if (destinationRegion.contains(immutablePos)) {
                    continue;
                }
                try {
                    BlockSnapshot before = WorldWriteTransaction.captureCurrent(context, immutablePos);
                    if (before == null) {
                        tx.recordFailure();
                        continue;
                    }
                    boolean clearSuccess = context.getWorld().setBlockState(immutablePos, airState, flags);
                    if (clearSuccess) {
                        tx.recordSuccess(before);
                    } else {
                        tx.recordFailure();
                    }
                } catch (Exception e) {
                    tx.recordFailure();
                }
            }
        }

        tx.pushIfNeeded(context, recordUndo);
        String error = tx.failureCount() > 0 ? "Partial write: " + tx.failureCount() + " failure(s)" : "";
        publish(
            tx.successCount(),
            tx.successCount(),
            tx.failureCount(),
            totalCount,
            destinationRegion,
            tx.failureCount() == 0,
            true,
            tx.hitLimit(),
            tx.isComplete(),
            error
        );
    }

    private @Nullable CloneMode resolveCloneMode() {
        Object raw = inputValues.get(INPUT_CLONE_MODE_ID);
        if (raw == null) {
            return cloneMode;
        }
        Integer index = StrictIntegerUtils.requireExactInteger(raw);
        if (index == null || index < 0 || index >= CloneMode.values().length) {
            return null;
        }
        return CloneMode.values()[index];
    }

    private static @NotNull BlockPos getPos(BlockPos destinationPos, BlockPos immutablePos, BlockPos sourceMinCorner) {
        return new BlockPos(
            destinationPos.getX() + (immutablePos.getX() - sourceMinCorner.getX()),
            destinationPos.getY() + (immutablePos.getY() - sourceMinCorner.getY()),
            destinationPos.getZ() + (immutablePos.getZ() - sourceMinCorner.getZ())
        );
    }

    private static @NotNull BlockPos getBlockPos(BlockPos destinationPos, BlockPos sourceMaxCorner, BlockPos sourceMinCorner) {
        int width = sourceMaxCorner.getX() - sourceMinCorner.getX() + 1;
        int height = sourceMaxCorner.getY() - sourceMinCorner.getY() + 1;
        int depth = sourceMaxCorner.getZ() - sourceMinCorner.getZ() + 1;
        return new BlockPos(
            destinationPos.getX() + width - 1,
            destinationPos.getY() + height - 1,
            destinationPos.getZ() + depth - 1
        );
    }

    private static boolean checkRegionsOverlap(RegionData region1, RegionData region2) {
        if (!region1.isComplete() || !region2.isComplete()) {
            return false;
        }
        BlockPos min1 = region1.getMinCorner();
        BlockPos max1 = region1.getMaxCorner();
        BlockPos min2 = region2.getMinCorner();
        BlockPos max2 = region2.getMaxCorner();
        return !(max1.getX() < min2.getX() || min1.getX() > max2.getX()
            || max1.getY() < min2.getY() || min1.getY() > max2.getY()
            || max1.getZ() < min2.getZ() || min1.getZ() > max2.getZ());
    }

    private void publish(
        int clonedBlocks,
        int successCount,
        int failureCount,
        int totalCount,
        @Nullable RegionData destinationRegion,
        boolean success,
        boolean valid,
        boolean hitLimit,
        boolean complete,
        String error
    ) {
        outputValues.put(OUTPUT_CLONED_BLOCKS_ID, clonedBlocks);
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_FAILURE_COUNT_ID, failureCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_DESTINATION_REGION_ID, destinationRegion);
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_COMPLETE_ID, complete);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isNotifyUpdate() { return notifyUpdate; }
    public void setNotifyUpdate(boolean notifyUpdate) { this.notifyUpdate = notifyUpdate; markDirty(); }
    public boolean isIncludeEntities() { return includeEntities; }
    public void setIncludeEntities(boolean includeEntities) { this.includeEntities = includeEntities; markDirty(); }
    public boolean isIncludeAir() { return includeAir; }
    public void setIncludeAir(boolean includeAir) { this.includeAir = includeAir; markDirty(); }
    public CloneMode getCloneMode() { return cloneMode; }
    public void setCloneMode(CloneMode cloneMode) { this.cloneMode = cloneMode; markDirty(); }
    public int getMaxBlocks() { return maxBlocks; }
    public void setMaxBlocks(int maxBlocks) {
        this.maxBlocks = Math.max(1, Math.min(maxBlocks, GenerationLimits.MAX_WORLD_WRITE_BLOCKS));
        markDirty();
    }
    public boolean isRecordUndo() { return recordUndo; }
    public void setRecordUndo(boolean recordUndo) { this.recordUndo = recordUndo; markDirty(); }
    public boolean isTrigger() { return trigger; }
    public void setTrigger(boolean trigger) { this.trigger = trigger; markDirty(); }
}

package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.apply_redstone_power",
    displayName = "Apply Redstone Power",
    description = "Places a temporary redstone power source next to a target block",
    category = "world.write",
    order = 11
)
public class ApplyRedstonePowerNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_POWER_LEVEL_ID = "input_power_level";
    private static final String INPUT_DURATION_ID = "input_duration";
    private static final String INPUT_ONLY_IF_AIR_ID = "input_only_if_air";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_BLOCK_TYPE_ID = "output_block_type";
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    private int powerLevel = 15;
    private int duration = 1;
    private boolean onlyIfAir = true;

    public ApplyRedstonePowerNode() {
        super(UUID.randomUUID(), "world.write.apply_redstone_power");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Target block position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_POWER_LEVEL_ID, "Power Level", "Requested redstone power level 0..15", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_DURATION_ID, "Duration", "Duration in ticks (>=1)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ONLY_IF_AIR_ID, "Only If Air", "Only place temporary support/source blocks into air", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether a pulse source was placed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_TYPE_ID, "Block Type", "Registry id of the target block", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why the pulse was not placed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(false, "", false, "Trigger is connected but null or invalid.");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(false, "", true, "Not triggered");
            return;
        }

        Boolean onlyAir = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_ONLY_IF_AIR_ID, onlyIfAir);
        if (onlyAir == null) {
            publish(false, "", false, "Only If Air is connected but null or invalid.");
            return;
        }

        Integer resolvedPower = StrictIntegerUtils.requireExactInteger(
            inputValues.get(INPUT_POWER_LEVEL_ID) != null ? inputValues.get(INPUT_POWER_LEVEL_ID) : powerLevel);
        Integer resolvedDuration = StrictIntegerUtils.requireExactInteger(
            inputValues.get(INPUT_DURATION_ID) != null ? inputValues.get(INPUT_DURATION_ID) : duration);
        if (resolvedPower == null || resolvedPower < 0 || resolvedPower > 15) {
            publish(false, "", false, "Power Level must be an exact INTEGER between 0 and 15.");
            return;
        }
        if (resolvedDuration == null || resolvedDuration < 1) {
            publish(false, "", false, "Duration must be an exact INTEGER >= 1.");
            return;
        }

        BlockPos targetPos = WorldWriteUtils.requireBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        if (targetPos == null) {
            publish(false, "", false, "Invalid coordinate (BLOCK_POS required).");
            return;
        }
        if (context == null || context.getWorld() == null) {
            publish(false, "", false, "Missing execution world");
            return;
        }
        BlockPos supportPos = targetPos.up();
        BlockPos sourcePos = supportPos.up();
        if (!WorldWriteUtils.isChunkLoaded(context, targetPos)
            || !WorldWriteUtils.isChunkLoaded(context, supportPos)
            || !WorldWriteUtils.isChunkLoaded(context, sourcePos)) {
            publish(false, "", false, WorldWriteUtils.UNLOADED_CHUNK_ERROR);
            return;
        }

        try {
            String blockType = Registries.BLOCK.getId(context.getWorld().getBlockState(targetPos).getBlock()).toString();

            BlockState previousSupportState = context.getWorld().getBlockState(supportPos);
            BlockState previousSourceState = context.getWorld().getBlockState(sourcePos);

            if (onlyAir && (!previousSupportState.isAir() || !previousSourceState.isAir())) {
                publish(false, blockType, true, "Temporary redstone positions are not air");
                return;
            }

            BlockState supportState = previousSupportState.isAir() ? Blocks.STONE.getDefaultState() : previousSupportState;
            BlockState sourceState = resolvedPower >= 15
                ? Blocks.REDSTONE_BLOCK.getDefaultState()
                : Blocks.REDSTONE_WIRE.getDefaultState().with(RedstoneWireBlock.POWER, resolvedPower);

            // Expected current states after placement (for restore guard)
            BlockState expectedSupport = supportState;
            BlockState expectedSource = sourceState;

            context.getWorld().setBlockState(supportPos, supportState, 3);
            context.getWorld().setBlockState(sourcePos, sourceState, 3);
            context.getWorld().updateNeighborsAlways(targetPos, context.getWorld().getBlockState(targetPos).getBlock(), (WireOrientation) null);
            context.getWorld().updateNeighborsAlways(sourcePos, sourceState.getBlock(), (WireOrientation) null);

            RedstonePulseService pulseService = RedstonePulseService.getInstance();
            pulseService.ensureRegistered();
            pulseService.enqueue(
                context.getWorld(),
                supportPos, previousSupportState, expectedSupport,
                sourcePos, previousSourceState, expectedSource,
                resolvedDuration
            );
            publish(true, blockType, true, "");
        } catch (Exception e) {
            publish(false, "", true, "World write failed");
        }
    }

    private void publish(boolean success, String blockType, boolean valid, String error) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_BLOCK_TYPE_ID, blockType == null ? "" : blockType);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public int getPowerLevel() { return powerLevel; }
    public void setPowerLevel(int powerLevel) { this.powerLevel = Math.max(0, Math.min(15, powerLevel)); markDirty(); }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = Math.max(1, duration); markDirty(); }
    public boolean isOnlyIfAir() { return onlyIfAir; }
    public void setOnlyIfAir(boolean onlyIfAir) { this.onlyIfAir = onlyIfAir; markDirty(); }
    public boolean isTrigger() { return trigger; }
    public void setTrigger(boolean trigger) { this.trigger = trigger; markDirty(); }
}

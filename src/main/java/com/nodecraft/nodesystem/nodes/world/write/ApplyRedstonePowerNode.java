package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
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
    category = "world.write"
)
public class ApplyRedstonePowerNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_POWER_LEVEL_ID = "input_power_level";
    private static final String INPUT_DURATION_ID = "input_duration";
    private static final String INPUT_PLAY_SOUND_ID = "input_play_sound";
    private static final String INPUT_ONLY_IF_AIR_ID = "input_only_if_air";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_BLOCK_TYPE_ID = "output_block_type";
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    private int powerLevel = 15;
    private int duration = 1;
    private boolean onlyIfAir = true;

    public ApplyRedstonePowerNode() {
        super(UUID.randomUUID(), "world.write.apply_redstone_power");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Target block position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "When connected, false prevents this write from running", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_POWER_LEVEL_ID, "Power Level", "Requested redstone power level", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_DURATION_ID, "Duration", "Duration in ticks", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLAY_SOUND_ID, "Play Sound", "Reserved for future sound toggling", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_ONLY_IF_AIR_ID, "Only If Air", "Only place temporary support/source blocks into air", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether a pulse source was placed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_TYPE_ID, "Block Type", "Registry id of the target block", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why the pulse was not placed", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Places a temporary redstone power source next to a target block";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        String blockType = "";
        String error = "";

        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        int resolvedPower = inputValues.get(INPUT_POWER_LEVEL_ID) instanceof Number value ? Math.max(0, Math.min(15, value.intValue())) : powerLevel;
        int resolvedDuration = inputValues.get(INPUT_DURATION_ID) instanceof Number value ? Math.max(1, value.intValue()) : duration;
        boolean onlyIfAirValue = inputValues.get(INPUT_ONLY_IF_AIR_ID) instanceof Boolean value ? value : onlyIfAir;

        BlockPos targetPos = WorldWriteUtils.resolveBlockPos(coordinateObj);
        if (!WorldWriteUtils.shouldRun(inputValues)) {
            error = "Not triggered";
        } else if (context == null || context.getWorld() == null) {
            error = "Missing execution world";
        } else if (targetPos == null) {
            error = "Invalid coordinate";
        } else {
            try {
                blockType = Registries.BLOCK.getId(context.getWorld().getBlockState(targetPos).getBlock()).toString();
                BlockPos supportPos = targetPos.up();
                BlockPos sourcePos = supportPos.up();

                BlockState previousSupportState = context.getWorld().getBlockState(supportPos);
                BlockState previousSourceState = context.getWorld().getBlockState(sourcePos);

                if (onlyIfAirValue && (!previousSupportState.isAir() || !previousSourceState.isAir())) {
                    error = "Temporary redstone positions are not air";
                    outputValues.put(OUTPUT_SUCCESS_ID, false);
                    outputValues.put(OUTPUT_BLOCK_TYPE_ID, blockType);
                    outputValues.put(OUTPUT_ERROR_ID, error);
                    return;
                }

                BlockState supportState = previousSupportState.isAir() ? Blocks.STONE.getDefaultState() : previousSupportState;
                BlockState sourceState = resolvedPower >= 15
                    ? Blocks.REDSTONE_BLOCK.getDefaultState()
                    : Blocks.REDSTONE_WIRE.getDefaultState().with(RedstoneWireBlock.POWER, resolvedPower);

                context.getWorld().setBlockState(supportPos, supportState, 3);
                context.getWorld().setBlockState(sourcePos, sourceState, 3);
                context.getWorld().updateNeighborsAlways(targetPos, context.getWorld().getBlockState(targetPos).getBlock(), (WireOrientation) null);
                context.getWorld().updateNeighborsAlways(sourcePos, sourceState.getBlock(), (WireOrientation) null);

                RedstonePulseService pulseService = RedstonePulseService.getInstance();
                pulseService.ensureRegistered();
                pulseService.enqueue(context.getWorld(), supportPos, previousSupportState, sourcePos, previousSourceState, resolvedDuration);
                success = true;
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_BLOCK_TYPE_ID, blockType);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    public int getPowerLevel() {
        return powerLevel;
    }

    public void setPowerLevel(int powerLevel) {
        this.powerLevel = Math.max(0, Math.min(15, powerLevel));
        markDirty();
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = Math.max(1, duration);
        markDirty();
    }

    public boolean isOnlyIfAir() {
        return onlyIfAir;
    }

    public void setOnlyIfAir(boolean onlyIfAir) {
        this.onlyIfAir = onlyIfAir;
        markDirty();
    }

    @Override
    public @Nullable Object getNodeState() {
        return new Object[]{powerLevel, duration, onlyIfAir};
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof int[] values && values.length >= 2) {
            powerLevel = values[0];
            duration = values[1];
            return;
        }
        if (state instanceof Object[] values && values.length >= 2) {
            if (values[0] instanceof Number power) {
                powerLevel = Math.max(0, Math.min(15, power.intValue()));
            }
            if (values[1] instanceof Number ticks) {
                duration = Math.max(1, ticks.intValue());
            }
            if (values.length >= 3 && values[2] instanceof Boolean value) {
                onlyIfAir = value;
            }
        }
    }
}

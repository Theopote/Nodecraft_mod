package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.simulate_right_click",
    displayName = "Simulate Right Click",
    description = "Simulates a server-side right click on a block",
    category = "world.write",
    order = 12
)
public class SimulateRightClickNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_PLAYER_ID = "input_player";
    private static final String INPUT_ITEM_IN_HAND_ID = "input_item_in_hand";
    private static final String INPUT_PLAY_SOUND_ID = "input_play_sound";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_BLOCK_TYPE_ID = "output_block_type";
    private static final String OUTPUT_INTERACTION_RESULT_ID = "output_interaction_result";
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    private boolean playSound = true;

    public SimulateRightClickNode() {
        super(UUID.randomUUID(), "world.write.simulate_right_click");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Target block position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_PLAYER_ID, "Player", "Optional server player executor", NodeDataType.PLAYER, this));
        addInputPort(new BasePort(INPUT_ITEM_IN_HAND_ID, "Item in Hand", "Optional item stack to use", NodeDataType.ITEM_STACK, this));
        addInputPort(new BasePort(INPUT_PLAY_SOUND_ID, "Play Sound", "Whether to sync interaction listeners", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the interaction was accepted", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_TYPE_ID, "Block Type", "Registry id of the target block", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_INTERACTION_RESULT_ID, "Interaction Result", "Action result name", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why the interaction did not run", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(false, "", "PASS", false, "Trigger is connected but null or invalid.");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(false, "", "PASS", true, "Not triggered");
            return;
        }

        Boolean syncListeners = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_PLAY_SOUND_ID, playSound);
        if (syncListeners == null) {
            publish(false, "", "PASS", false, "Play Sound is connected but null or invalid.");
            return;
        }

        BlockPos pos = WorldWriteUtils.requireBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        if (pos == null) {
            publish(false, "", "PASS", false, "Invalid coordinate (BLOCK_POS required).");
            return;
        }
        if (context == null || context.getWorld() == null) {
            publish(false, "", "PASS", false, "Missing execution world");
            return;
        }
        if (!WorldWriteUtils.isChunkLoaded(context, pos)) {
            publish(false, "", "PASS", false, WorldWriteUtils.UNLOADED_CHUNK_ERROR);
            return;
        }

        Object playerObj = inputValues.get(INPUT_PLAYER_ID);
        ServerPlayerEntity player = playerObj instanceof ServerPlayerEntity provided ? provided : context.getPlayer();
        if (player == null) {
            publish(false, "", "PASS", true, "Missing server player");
            return;
        }

        try {
            String blockType = Registries.BLOCK.getId(context.getWorld().getBlockState(pos).getBlock()).toString();
            Object itemInHandObj = inputValues.get(INPUT_ITEM_IN_HAND_ID);
            ItemStack stack = itemInHandObj instanceof ItemStack providedStack
                ? providedStack.copy()
                : player.getMainHandStack().copy();
            BlockHitResult hitResult = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
            ActionResult result = player.interactionManager.interactBlock(
                player, context.getWorld(), stack, Hand.MAIN_HAND, hitResult);
            boolean success = result.isAccepted();
            if (syncListeners && success) {
                context.getWorld().updateListeners(pos, context.getWorld().getBlockState(pos), context.getWorld().getBlockState(pos), 3);
            }
            publish(success, blockType, String.valueOf(result), true, success ? "" : "Interaction not accepted");
        } catch (Exception e) {
            publish(false, "", "ERROR", true, "World write failed");
        }
    }

    private void publish(boolean success, String blockType, String interactionResult, boolean valid, String error) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_BLOCK_TYPE_ID, blockType == null ? "" : blockType);
        outputValues.put(OUTPUT_INTERACTION_RESULT_ID, interactionResult);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isPlaySound() { return playSound; }
    public void setPlaySound(boolean playSound) { this.playSound = playSound; markDirty(); }
    public boolean isTrigger() { return trigger; }
    public void setTrigger(boolean trigger) { this.trigger = trigger; markDirty(); }
}

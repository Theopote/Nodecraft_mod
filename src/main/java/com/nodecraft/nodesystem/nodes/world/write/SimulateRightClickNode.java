package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
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
    category = "world.write"
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
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    private boolean playSound = true;

    public SimulateRightClickNode() {
        super(UUID.randomUUID(), "world.write.simulate_right_click");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Target block position", NodeDataType.COORDINATE, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "When connected, false prevents this interaction from running", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_PLAYER_ID, "Player", "Optional server player executor", NodeDataType.PLAYER, this));
        addInputPort(new BasePort(INPUT_ITEM_IN_HAND_ID, "Item in Hand", "Optional item stack to use", NodeDataType.ITEM_STACK, this));
        addInputPort(new BasePort(INPUT_PLAY_SOUND_ID, "Play Sound", "Whether to sync interaction listeners", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the interaction was accepted", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_TYPE_ID, "Block Type", "Registry id of the target block", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_INTERACTION_RESULT_ID, "Interaction Result", "Action result name", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why the interaction did not run", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Simulates a server-side right click on a block";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        String blockType = "";
        String interactionResult = "PASS";
        String error = "";

        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        Object playerObj = inputValues.get(INPUT_PLAYER_ID);
        Object itemInHandObj = inputValues.get(INPUT_ITEM_IN_HAND_ID);
        boolean syncListeners = inputValues.get(INPUT_PLAY_SOUND_ID) instanceof Boolean value ? value : playSound;

        BlockPos pos = WorldWriteUtils.resolveBlockPos(coordinateObj);
        if (!WorldWriteUtils.shouldRun(inputValues)) {
            error = "Not triggered";
        } else if (context == null || context.getWorld() == null) {
            error = "Missing execution world";
        } else if (pos == null) {
            error = "Invalid coordinate";
        } else {
            ServerPlayerEntity player = playerObj instanceof ServerPlayerEntity provided ? provided : context.getPlayer();
            if (player != null) {
                try {
                    blockType = Registries.BLOCK.getId(context.getWorld().getBlockState(pos).getBlock()).toString();
                    ItemStack stack = itemInHandObj instanceof ItemStack providedStack ? providedStack.copy() : player.getMainHandStack().copy();
                    BlockHitResult hitResult = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
                    ActionResult result = player.interactionManager.interactBlock(player, context.getWorld(), stack, Hand.MAIN_HAND, hitResult);
                    success = result.isAccepted();
                    interactionResult = String.valueOf(result);

                    if (syncListeners && success) {
                        context.getWorld().updateListeners(pos, context.getWorld().getBlockState(pos), context.getWorld().getBlockState(pos), 3);
                    }
                } catch (Exception e) {
                    interactionResult = "ERROR: " + e.getMessage();
                    error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                }
            } else {
                error = "Missing server player";
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_BLOCK_TYPE_ID, blockType);
        outputValues.put(OUTPUT_INTERACTION_RESULT_ID, interactionResult);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    public boolean isPlaySound() {
        return playSound;
    }

    public void setPlaySound(boolean playSound) {
        this.playSound = playSound;
        markDirty();
    }

    @Override
    public @Nullable Object getNodeState() {
        return playSound;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof Boolean value) {
            playSound = value;
        }
    }
}

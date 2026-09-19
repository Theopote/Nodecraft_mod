package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.query.get_light_level",
    displayName = "Get Light Level",
    description = "Gets the combined, sky, and block light values for a block position",
    category = "world.query",
    order = 0
)
public class GetLightLevelNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    private static final String OUTPUT_LIGHT_LEVEL_ID = "output_light_level";
    private static final String OUTPUT_SKY_LIGHT_ID = "output_sky_light";
    private static final String OUTPUT_BLOCK_LIGHT_ID = "output_block_light";
    private static final String OUTPUT_IS_DAY_ID = "output_is_day";
    private static final String OUTPUT_CAN_SEE_SKY_ID = "output_can_see_sky";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetLightLevelNode() {
        super(UUID.randomUUID(), "world.query.get_light_level");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Block position to query", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_LIGHT_LEVEL_ID, "Light Level", "Combined light level", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SKY_LIGHT_ID, "Sky Light", "Sky light level", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_LIGHT_ID, "Block Light", "Block light level", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_IS_DAY_ID, "Is Day", "Whether the world currently counts as daytime", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_CAN_SEE_SKY_ID, "Can See Sky", "Whether the queried block can see the sky", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the light query succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when the light query fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Gets the combined, sky, and block light values for a block position";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        int lightLevel = 0;
        int skyLight = 0;
        int blockLight = 0;
        boolean isDay = false;
        boolean canSeeSky = false;
        boolean valid = false;
        String error = "";

        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        if (context == null || context.getWorld() == null) {
            error = "Execution context or world is missing.";
        } else if (!(coordinateObj instanceof BlockPos pos)) {
            error = "Coordinate input must be a block position.";
        } else {
            try {
                lightLevel = context.getWorld().getLightLevel(pos);
                skyLight = context.getWorld().getLightLevel(LightType.SKY, pos);
                blockLight = context.getWorld().getLightLevel(LightType.BLOCK, pos);
                isDay = context.getWorld().isDay();
                canSeeSky = context.getWorld().isSkyVisible(pos);
                valid = true;
            } catch (Exception e) {
                error = "Error getting light level at " + pos + ": " + e.getMessage();
                NodeCraft.LOGGER.warn(error);
            }
        }

        outputValues.put(OUTPUT_LIGHT_LEVEL_ID, lightLevel);
        outputValues.put(OUTPUT_SKY_LIGHT_ID, skyLight);
        outputValues.put(OUTPUT_BLOCK_LIGHT_ID, blockLight);
        outputValues.put(OUTPUT_IS_DAY_ID, isDay);
        outputValues.put(OUTPUT_CAN_SEE_SKY_ID, canSeeSky);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }
}

package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.query.get_fluid_level",
    displayName = "Get Fluid Level",
    description = "Gets the fluid state, type, and level for a block position",
    category = "world.query",
    order = 7
)
public class GetFluidLevelNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    private static final String OUTPUT_FLUID_LEVEL_ID = "output_fluid_level";
    private static final String OUTPUT_FLUID_TYPE_ID = "output_fluid_type";
    private static final String OUTPUT_IS_SOURCE_ID = "output_is_source";
    private static final String OUTPUT_IS_FLOWING_ID = "output_is_flowing";
    private static final String OUTPUT_IS_WATER_ID = "output_is_water";
    private static final String OUTPUT_IS_LAVA_ID = "output_is_lava";
    private static final String OUTPUT_HAS_FLUID_ID = "output_has_fluid";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetFluidLevelNode() {
        super(UUID.randomUUID(), "world.query.get_fluid_level");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Block position to query", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_FLUID_LEVEL_ID, "Fluid Level", "Fluid level reported by the fluid state", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FLUID_TYPE_ID, "Fluid Type", "Fluid registry id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_SOURCE_ID, "Is Source", "Whether the fluid is a still source", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_FLOWING_ID, "Is Flowing", "Whether the fluid is flowing", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_WATER_ID, "Is Water", "Whether the fluid is tagged as water", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_LAVA_ID, "Is Lava", "Whether the fluid is tagged as lava", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HAS_FLUID_ID, "Has Fluid", "Whether any fluid is present", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the fluid query succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when the fluid query fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Gets the fluid state, type, and level for a block position";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        int fluidLevel = 0;
        String fluidType = "";
        boolean isSource = false;
        boolean isFlowing = false;
        boolean isWater = false;
        boolean isLava = false;
        boolean hasFluid = false;
        boolean valid = false;
        String error = "";

        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        if (context == null || context.getWorld() == null) {
            error = "Execution context or world is missing.";
        } else if (!(coordinateObj instanceof BlockPos pos)) {
            error = "Coordinate input must be a block position.";
        } else {
            try {
                FluidState fluidState = context.getWorld().getFluidState(pos);
                hasFluid = !fluidState.isEmpty();
                valid = true;

                if (hasFluid) {
                    fluidType = Registries.FLUID.getId(fluidState.getFluid()).toString();
                    fluidLevel = fluidState.getLevel();
                    isSource = fluidState.isStill();
                    isFlowing = !isSource;
                    isWater = fluidState.isIn(FluidTags.WATER);
                    isLava = fluidState.isIn(FluidTags.LAVA);
                }
            } catch (Exception e) {
                error = "Error getting fluid level at " + pos + ": " + e.getMessage();
                NodeCraft.LOGGER.warn(error);
            }
        }

        outputValues.put(OUTPUT_FLUID_LEVEL_ID, fluidLevel);
        outputValues.put(OUTPUT_FLUID_TYPE_ID, fluidType);
        outputValues.put(OUTPUT_IS_SOURCE_ID, isSource);
        outputValues.put(OUTPUT_IS_FLOWING_ID, isFlowing);
        outputValues.put(OUTPUT_IS_WATER_ID, isWater);
        outputValues.put(OUTPUT_IS_LAVA_ID, isLava);
        outputValues.put(OUTPUT_HAS_FLUID_ID, hasFluid);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }
}

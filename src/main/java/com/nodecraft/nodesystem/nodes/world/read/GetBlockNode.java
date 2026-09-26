package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.read.get_block",
    displayName = "Get Block",
    description = "Reads block state, type, light, fluid, and block-entity presence at a block position.",
    category = "world.read",
    order = 0
)
public class GetBlockNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    private static final String OUTPUT_BLOCK_ID = "output_block";
    private static final String OUTPUT_BLOCK_TYPE_ID = "output_block_type";
    private static final String OUTPUT_IS_AIR_ID = "output_is_air";
    private static final String OUTPUT_IS_SOLID_ID = "output_is_solid";
    private static final String OUTPUT_LIGHT_LEVEL_ID = "output_light_level";
    private static final String OUTPUT_HAS_BLOCK_ENTITY_ID = "output_has_block_entity";
    private static final String OUTPUT_FLUID_TYPE_ID = "output_fluid_type";
    private static final String OUTPUT_HAS_FLUID_ID = "output_has_fluid";
    private static final String OUTPUT_IS_REPLACEABLE_ID = "output_is_replaceable";
    private static final String OUTPUT_LUMINANCE_ID = "output_luminance";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetBlockNode() {
        super(UUID.randomUUID(), "world.read.get_block");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Block position to read", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_ID, "Block State", "Minecraft BlockState object", NodeDataType.BLOCK_INFO, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_TYPE_ID, "Block Type", "Block registry id", NodeDataType.BLOCK_TYPE, this));
        addOutputPort(new BasePort(OUTPUT_IS_AIR_ID, "Is Air", "Whether the block is air", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_SOLID_ID, "Is Solid", "Whether the block is solid at this position", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_LIGHT_LEVEL_ID, "Light Level", "World light level at the position", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HAS_BLOCK_ENTITY_ID, "Has Block Entity", "Whether a block entity exists at the position", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_FLUID_TYPE_ID, "Fluid Type", "Fluid registry id at the position", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_HAS_FLUID_ID, "Has Fluid", "Whether any fluid is present", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_IS_REPLACEABLE_ID, "Is Replaceable", "Whether the block state is replaceable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_LUMINANCE_ID, "Luminance", "Block state's emitted light value", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether block read succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when block read fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Reads block state, type, light, fluid, and block-entity presence at a block position.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPos pos = WorldReadUtils.requireBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        if (pos == null) {
            writeFailure("Coordinate input must be a block position.");
            return;
        }
        if (context == null || context.getWorld() == null) {
            writeFailure("Execution context or world is missing.");
            return;
        }

        try {
            BlockState blockState = context.getWorld().getBlockState(pos);
            FluidState fluidState = context.getWorld().getFluidState(pos);
            boolean hasFluid = !fluidState.isEmpty();

            outputValues.put(OUTPUT_BLOCK_ID, blockState);
            outputValues.put(OUTPUT_BLOCK_TYPE_ID, WorldReadUtils.blockId(blockState));
            outputValues.put(OUTPUT_IS_AIR_ID, blockState.isAir());
            outputValues.put(OUTPUT_IS_SOLID_ID, blockState.isSolidBlock(context.getWorld(), pos));
            outputValues.put(OUTPUT_LIGHT_LEVEL_ID, context.getWorld().getLightLevel(pos));
            outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, context.getWorld().getBlockEntity(pos) != null);
            outputValues.put(OUTPUT_FLUID_TYPE_ID, hasFluid ? Registries.FLUID.getId(fluidState.getFluid()).toString() : "");
            outputValues.put(OUTPUT_HAS_FLUID_ID, hasFluid);
            outputValues.put(OUTPUT_IS_REPLACEABLE_ID, blockState.isReplaceable());
            outputValues.put(OUTPUT_LUMINANCE_ID, blockState.getLuminance());
            outputValues.put(OUTPUT_VALID_ID, true);
            outputValues.put(OUTPUT_ERROR_ID, "");
        } catch (Exception e) {
            String error = "Error getting block at " + pos + ": " + e.getMessage();
            NodeCraft.LOGGER.warn(error);
            writeFailure(error);
        }
    }

    private void writeFailure(String error) {
        outputValues.put(OUTPUT_BLOCK_ID, null);
        outputValues.put(OUTPUT_BLOCK_TYPE_ID, "");
        outputValues.put(OUTPUT_IS_AIR_ID, true);
        outputValues.put(OUTPUT_IS_SOLID_ID, false);
        outputValues.put(OUTPUT_LIGHT_LEVEL_ID, 0);
        outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, false);
        outputValues.put(OUTPUT_FLUID_TYPE_ID, "");
        outputValues.put(OUTPUT_HAS_FLUID_ID, false);
        outputValues.put(OUTPUT_IS_REPLACEABLE_ID, false);
        outputValues.put(OUTPUT_LUMINANCE_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

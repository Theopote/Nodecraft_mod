package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.NodeDataType;
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
        BlockState blockState = null;
        String blockType = "";
        boolean isAir = true;
        boolean isSolid = false;
        int lightLevel = 0;
        boolean hasBlockEntity = false;
        String fluidType = "";
        boolean hasFluid = false;
        boolean isReplaceable = false;
        int luminance = 0;
        boolean valid = false;
        String error = "";

        BlockPos pos = WorldReadUtils.resolveBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        if (context == null || context.getWorld() == null) {
            error = "Execution context or world is missing.";
        } else if (pos == null) {
            error = "Coordinate input must resolve to a block position.";
        } else {
            try {
                blockState = context.getWorld().getBlockState(pos);
                blockType = WorldReadUtils.blockId(blockState);
                isAir = blockState.isAir();
                isSolid = blockState.isSolidBlock(context.getWorld(), pos);
                lightLevel = context.getWorld().getLightLevel(pos);
                hasBlockEntity = context.getWorld().getBlockEntity(pos) != null;
                FluidState fluidState = context.getWorld().getFluidState(pos);
                hasFluid = !fluidState.isEmpty();
                fluidType = hasFluid ? Registries.FLUID.getId(fluidState.getFluid()).toString() : "";
                isReplaceable = blockState.isReplaceable();
                luminance = blockState.getLuminance();
                valid = true;
            } catch (Exception e) {
                error = "Error getting block at " + pos + ": " + e.getMessage();
                NodeCraft.LOGGER.warn(error);
            }
        }

        outputValues.put(OUTPUT_BLOCK_ID, blockState);
        outputValues.put(OUTPUT_BLOCK_TYPE_ID, blockType);
        outputValues.put(OUTPUT_IS_AIR_ID, isAir);
        outputValues.put(OUTPUT_IS_SOLID_ID, isSolid);
        outputValues.put(OUTPUT_LIGHT_LEVEL_ID, lightLevel);
        outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, hasBlockEntity);
        outputValues.put(OUTPUT_FLUID_TYPE_ID, fluidType);
        outputValues.put(OUTPUT_HAS_FLUID_ID, hasFluid);
        outputValues.put(OUTPUT_IS_REPLACEABLE_ID, isReplaceable);
        outputValues.put(OUTPUT_LUMINANCE_ID, luminance);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }
}

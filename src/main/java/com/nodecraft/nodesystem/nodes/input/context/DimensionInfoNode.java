package com.nodecraft.nodesystem.nodes.input.context;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.minecraft.PlayerAccessor;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Reads the current dimension state from the active player context.
 */
@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "input.context.dimension_info",
    displayName = "Dimension Info",
    description = "Gets the current dimension and basic dimension traits from the active Minecraft world.",
    category = "input.context",
    order = 2
)
public class DimensionInfoNode extends BaseNode {

    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_DIMENSION_ID = "output_dimension_id";
    private static final String OUTPUT_IS_OVERWORLD_ID = "output_is_overworld";
    private static final String OUTPUT_IS_NETHER_ID = "output_is_nether";
    private static final String OUTPUT_IS_END_ID = "output_is_end";
    private static final String OUTPUT_HAS_SKYLIGHT_ID = "output_has_skylight";
    private static final String OUTPUT_HAS_CEILING_ID = "output_has_ceiling";

    private static final String OVERWORLD_ID = "minecraft:overworld";
    private static final String NETHER_ID = "minecraft:the_nether";
    private static final String END_ID = "minecraft:the_end";

    private final String description = "Gets information about the player's current dimension.";

    public DimensionInfoNode() {
        super(UUID.randomUUID(), "input.context.dimension_info");

        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether world context was available", NodeDataType.BOOLEAN, this));

        IPort dimensionIdOutput = new BasePort(
            OUTPUT_DIMENSION_ID,
            "Dimension ID",
            "The dimension identifier",
            NodeDataType.STRING,
            this
        );
        addOutputPort(dimensionIdOutput);

        IPort isOverworldOutput = new BasePort(
            OUTPUT_IS_OVERWORLD_ID,
            "Is Overworld",
            "Whether the dimension is the overworld",
            NodeDataType.BOOLEAN,
            this
        );
        addOutputPort(isOverworldOutput);

        IPort isNetherOutput = new BasePort(
            OUTPUT_IS_NETHER_ID,
            "Is Nether",
            "Whether the dimension is the nether",
            NodeDataType.BOOLEAN,
            this
        );
        addOutputPort(isNetherOutput);

        IPort isEndOutput = new BasePort(
            OUTPUT_IS_END_ID,
            "Is End",
            "Whether the dimension is the end",
            NodeDataType.BOOLEAN,
            this
        );
        addOutputPort(isEndOutput);

        IPort hasSkylightOutput = new BasePort(
            OUTPUT_HAS_SKYLIGHT_ID,
            "Has Skylight",
            "Whether the dimension has skylight",
            NodeDataType.BOOLEAN,
            this
        );
        addOutputPort(hasSkylightOutput);

        IPort hasCeilingOutput = new BasePort(
            OUTPUT_HAS_CEILING_ID,
            "Has Ceiling",
            "Whether the dimension has a ceiling",
            NodeDataType.BOOLEAN,
            this
        );
        addOutputPort(hasCeilingOutput);

        invalidateContext();
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!ContextReadUtils.isLiveContextAvailable(context)) {
            invalidateContext();
            return;
        }

        PlayerAccessor playerAccessor = context.getPlayerAccessor();
        World world = context.getWorld();
        updateOutputs(playerAccessor, world);
    }

    private void updateOutputs(PlayerAccessor playerAccessor, World world) {
        String dimensionId = playerAccessor.getPlayerDimension();

        boolean isOverworld = OVERWORLD_ID.equals(dimensionId);
        boolean isNether = NETHER_ID.equals(dimensionId);
        boolean isEnd = END_ID.equals(dimensionId);

        DimensionType dimensionType = world.getDimension();
        boolean hasSkylight = dimensionType.hasSkyLight();
        boolean hasCeiling = dimensionType.hasCeiling();

        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_DIMENSION_ID, dimensionId);
        outputValues.put(OUTPUT_IS_OVERWORLD_ID, isOverworld);
        outputValues.put(OUTPUT_IS_NETHER_ID, isNether);
        outputValues.put(OUTPUT_IS_END_ID, isEnd);
        outputValues.put(OUTPUT_HAS_SKYLIGHT_ID, hasSkylight);
        outputValues.put(OUTPUT_HAS_CEILING_ID, hasCeiling);
    }

    private void invalidateContext() {
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_DIMENSION_ID, "");
        outputValues.put(OUTPUT_IS_OVERWORLD_ID, false);
        outputValues.put(OUTPUT_IS_NETHER_ID, false);
        outputValues.put(OUTPUT_IS_END_ID, false);
        outputValues.put(OUTPUT_HAS_SKYLIGHT_ID, false);
        outputValues.put(OUTPUT_HAS_CEILING_ID, false);
    }

    @Override
    public Object getNodeState() {
        return null;
    }

    @Override
    public void setNodeState(Object state) {
        // Stateless.
    }
}

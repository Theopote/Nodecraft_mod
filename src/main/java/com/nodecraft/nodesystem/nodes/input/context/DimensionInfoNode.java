package com.nodecraft.nodesystem.nodes.input.context;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.minecraft.PlayerAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Reads the current dimension state from the active player context.
 */
@NodeInfo(
    id = "input.context.dimension_info",
    displayName = "Dimension Info",
    description = "Gets the current dimension and basic dimension traits from the active Minecraft world.",
    category = "input.context"
)
public class DimensionInfoNode extends BaseNode {

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

        resetOutputs();
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null) {
            resetOutputs();
            return;
        }

        PlayerAccessor playerAccessor = context.getPlayerAccessor();
        if (playerAccessor == null) {
            resetOutputs();
            return;
        }

        updateOutputs(playerAccessor);
    }

    private void updateOutputs(PlayerAccessor playerAccessor) {
        String dimensionId = playerAccessor.getPlayerDimension();

        boolean isOverworld = OVERWORLD_ID.equals(dimensionId);
        boolean isNether = NETHER_ID.equals(dimensionId);
        boolean isEnd = END_ID.equals(dimensionId);

        // These traits are inferred from the known vanilla dimensions.
        boolean hasSkylight = isOverworld || isEnd;
        boolean hasCeiling = isNether;

        outputValues.put(OUTPUT_DIMENSION_ID, dimensionId);
        outputValues.put(OUTPUT_IS_OVERWORLD_ID, isOverworld);
        outputValues.put(OUTPUT_IS_NETHER_ID, isNether);
        outputValues.put(OUTPUT_IS_END_ID, isEnd);
        outputValues.put(OUTPUT_HAS_SKYLIGHT_ID, hasSkylight);
        outputValues.put(OUTPUT_HAS_CEILING_ID, hasCeiling);
    }

    private void resetOutputs() {
        outputValues.put(OUTPUT_DIMENSION_ID, OVERWORLD_ID);
        outputValues.put(OUTPUT_IS_OVERWORLD_ID, true);
        outputValues.put(OUTPUT_IS_NETHER_ID, false);
        outputValues.put(OUTPUT_IS_END_ID, false);
        outputValues.put(OUTPUT_HAS_SKYLIGHT_ID, true);
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

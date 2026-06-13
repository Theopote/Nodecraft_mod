package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "world.read.biome_at_player",
    displayName = "Biome At Player",
    description = "Gets the biome at the player's current position",
    category = "world.read",
    order = 4
)
public class BiomeAtPlayerNode extends BaseNode {

    private static final String OUTPUT_BIOME_ID = "output_biome_id";
    private static final String OUTPUT_IS_OCEAN_ID = "output_is_ocean";
    private static final String OUTPUT_IS_SNOWY_ID = "output_is_snowy";
    private static final String OUTPUT_IS_HOT_ID = "output_is_hot";
    private static final String OUTPUT_HAS_PRECIPITATION_ID = "output_has_precipitation";
    private static final String OUTPUT_PLAYER_POSITION_ID = "output_player_position";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public BiomeAtPlayerNode() {
        super(UUID.randomUUID(), "world.read.biome_at_player");

        IPort biomeIdOutput = new BasePort(OUTPUT_BIOME_ID, "Biome ID", "The biome registry id", NodeDataType.STRING, this);
        addOutputPort(biomeIdOutput);

        IPort isOceanOutput = new BasePort(OUTPUT_IS_OCEAN_ID, "Looks Ocean", "Whether the biome id looks like an ocean biome", NodeDataType.BOOLEAN, this);
        addOutputPort(isOceanOutput);

        IPort isSnowyOutput = new BasePort(OUTPUT_IS_SNOWY_ID, "Looks Snowy", "Whether the biome id looks snowy or frozen", NodeDataType.BOOLEAN, this);
        addOutputPort(isSnowyOutput);

        IPort isHotOutput = new BasePort(OUTPUT_IS_HOT_ID, "Looks Hot", "Whether the biome id looks hot", NodeDataType.BOOLEAN, this);
        addOutputPort(isHotOutput);

        IPort hasPrecipitationOutput = new BasePort(OUTPUT_HAS_PRECIPITATION_ID, "Estimated Precipitation", "Whether the biome id suggests precipitation", NodeDataType.BOOLEAN, this);
        addOutputPort(hasPrecipitationOutput);

        addOutputPort(new BasePort(OUTPUT_PLAYER_POSITION_ID, "Player Position", "Player block position used for the biome query", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether player biome read succeeded", NodeDataType.BOOLEAN, this));

        resetOutputs();
    }

    @Override
    public String getDescription() {
        return "Gets the biome at the player's current position";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null || context.getWorld() == null || context.getPlayer() == null) {
            resetOutputs();
            return;
        }

        try {
            var biomeEntry = context.getWorld().getBiome(context.getPlayer().getBlockPos());
            String biomeId = biomeEntry.getKey()
                .map(RegistryKey::getValue)
                .map(Object::toString)
                .orElse("minecraft:unknown");

            boolean isOcean = biomeId.contains("ocean");
            boolean isSnowy = biomeId.contains("snowy") || biomeId.contains("frozen") || biomeId.contains("ice");
            boolean isHot = biomeId.contains("desert") || biomeId.contains("savanna") || biomeId.contains("badlands") || biomeId.contains("jungle");
            boolean hasPrecipitation = !biomeId.contains("desert") && !biomeId.contains("nether");

            outputValues.put(OUTPUT_BIOME_ID, biomeId);
            outputValues.put(OUTPUT_IS_OCEAN_ID, isOcean);
            outputValues.put(OUTPUT_IS_SNOWY_ID, isSnowy);
            outputValues.put(OUTPUT_IS_HOT_ID, isHot);
            outputValues.put(OUTPUT_HAS_PRECIPITATION_ID, hasPrecipitation);
            outputValues.put(OUTPUT_PLAYER_POSITION_ID, context.getPlayer().getBlockPos());
            outputValues.put(OUTPUT_VALID_ID, true);
        } catch (Exception e) {
            resetOutputs();
        }
    }

    private void resetOutputs() {
        outputValues.put(OUTPUT_BIOME_ID, "minecraft:unknown");
        outputValues.put(OUTPUT_IS_OCEAN_ID, false);
        outputValues.put(OUTPUT_IS_SNOWY_ID, false);
        outputValues.put(OUTPUT_IS_HOT_ID, false);
        outputValues.put(OUTPUT_HAS_PRECIPITATION_ID, false);
        outputValues.put(OUTPUT_PLAYER_POSITION_ID, BlockPos.ORIGIN);
        outputValues.put(OUTPUT_VALID_ID, false);
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

package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.registry.RegistryKey;
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

    public BiomeAtPlayerNode() {
        super(UUID.randomUUID(), "world.read.biome_at_player");

        IPort biomeIdOutput = new BasePort(OUTPUT_BIOME_ID, "Biome ID", "The biome registry id", NodeDataType.STRING, this);
        addOutputPort(biomeIdOutput);

        IPort isOceanOutput = new BasePort(OUTPUT_IS_OCEAN_ID, "Is Ocean", "Whether the biome is an ocean biome", NodeDataType.BOOLEAN, this);
        addOutputPort(isOceanOutput);

        IPort isSnowyOutput = new BasePort(OUTPUT_IS_SNOWY_ID, "Is Snowy", "Whether the biome is snowy or frozen", NodeDataType.BOOLEAN, this);
        addOutputPort(isSnowyOutput);

        IPort isHotOutput = new BasePort(OUTPUT_IS_HOT_ID, "Is Hot", "Whether the biome is hot", NodeDataType.BOOLEAN, this);
        addOutputPort(isHotOutput);

        IPort hasPrecipitationOutput = new BasePort(OUTPUT_HAS_PRECIPITATION_ID, "Has Precipitation", "Whether the biome receives precipitation", NodeDataType.BOOLEAN, this);
        addOutputPort(hasPrecipitationOutput);

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

package com.nodecraft.nodesystem.nodes.world.read;

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
    id = "world.read.get_biome",
    displayName = "Get Biome",
    description = "Gets the biome registry id and basic climate data for a block position",
    category = "world.read",
    order = 3
)
public class GetBiomeNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    private static final String OUTPUT_BIOME_ID = "output_biome";
    private static final String OUTPUT_BIOME_NAME_ID = "output_biome_name";
    private static final String OUTPUT_BIOME_TEMP_ID = "output_biome_temperature";
    private static final String OUTPUT_IS_OCEAN_ID = "output_is_ocean";
    private static final String OUTPUT_DOWNFALL_ID = "output_downfall";

    public GetBiomeNode() {
        super(UUID.randomUUID(), "world.read.get_biome");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Block position to query", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_BIOME_ID, "Biome", "Biome object", NodeDataType.BIOME, this));
        addOutputPort(new BasePort(OUTPUT_BIOME_NAME_ID, "Biome Name", "Biome registry id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_BIOME_TEMP_ID, "Temperature", "Biome base temperature", NodeDataType.FLOAT, this));
        addOutputPort(new BasePort(OUTPUT_IS_OCEAN_ID, "Is Ocean", "Whether the biome id looks like an ocean biome", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_DOWNFALL_ID, "Downfall", "Biome downfall value", NodeDataType.FLOAT, this));
    }

    @Override
    public String getDescription() {
        return "Gets the biome registry id and basic climate data for a block position";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object biomeObj = null;
        String biomeName = "";
        float temperature = 0.0f;
        boolean isOcean = false;
        float downfall = 0.0f;

        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        if (context != null && context.getWorld() != null && coordinateObj instanceof BlockPos pos) {
            try {
                var biomeEntry = context.getWorld().getBiome(pos);
                var biome = biomeEntry.value();
                biomeObj = biome;
                biomeName = biomeEntry.getKey()
                    .map(RegistryKey::getValue)
                    .map(Object::toString)
                    .orElse("minecraft:unknown");
                temperature = biome.getTemperature();
                isOcean = biomeName.contains("ocean");
                downfall = biomeName.contains("desert") || biomeName.contains("nether") ? 0.0f : 0.5f;
            } catch (Exception e) {
                System.err.println("Error getting biome at " + pos + ": " + e.getMessage());
            }
        }

        outputValues.put(OUTPUT_BIOME_ID, biomeObj);
        outputValues.put(OUTPUT_BIOME_NAME_ID, biomeName);
        outputValues.put(OUTPUT_BIOME_TEMP_ID, temperature);
        outputValues.put(OUTPUT_IS_OCEAN_ID, isOcean);
        outputValues.put(OUTPUT_DOWNFALL_ID, downfall);
    }
}

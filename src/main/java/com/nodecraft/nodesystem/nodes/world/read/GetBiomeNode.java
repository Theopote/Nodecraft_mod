package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.core.NodeCraft;
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
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetBiomeNode() {
        super(UUID.randomUUID(), "world.read.get_biome");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Block position to query", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_BIOME_ID, "Biome", "Biome object", NodeDataType.BIOME, this));
        addOutputPort(new BasePort(OUTPUT_BIOME_NAME_ID, "Biome Name", "Biome registry id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_BIOME_TEMP_ID, "Temperature", "Biome base temperature", NodeDataType.FLOAT, this));
        addOutputPort(new BasePort(OUTPUT_IS_OCEAN_ID, "Looks Ocean", "Whether the biome id looks like an ocean biome", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_DOWNFALL_ID, "Estimated Downfall", "Estimated downfall inferred from biome id", NodeDataType.FLOAT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether biome read succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when biome read fails", NodeDataType.STRING, this));
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
        boolean valid = false;
        String error = "";

        BlockPos pos = WorldReadUtils.resolveBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        if (context == null || context.getWorld() == null) {
            error = "Execution context or world is missing.";
        } else if (pos == null) {
            error = "Coordinate input must resolve to a block position.";
        } else {
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
                valid = true;
            } catch (Exception e) {
                error = "Error getting biome at " + pos + ": " + e.getMessage();
                NodeCraft.LOGGER.warn(error);
            }
        }

        outputValues.put(OUTPUT_BIOME_ID, biomeObj);
        outputValues.put(OUTPUT_BIOME_NAME_ID, biomeName);
        outputValues.put(OUTPUT_BIOME_TEMP_ID, temperature);
        outputValues.put(OUTPUT_IS_OCEAN_ID, isOcean);
        outputValues.put(OUTPUT_DOWNFALL_ID, downfall);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }
}

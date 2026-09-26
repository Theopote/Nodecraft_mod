package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.read.get_biome",
    displayName = "Get Biome",
    description = "Gets the biome registry id and base temperature for a block position",
    category = "world.read",
    order = 3
)
public class GetBiomeNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";

    private static final String OUTPUT_BIOME_ID = "output_biome";
    private static final String OUTPUT_BIOME_NAME_ID = "output_biome_name";
    private static final String OUTPUT_BIOME_TEMP_ID = "output_biome_temperature";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetBiomeNode() {
        super(UUID.randomUUID(), "world.read.get_biome");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Block position to query", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_BIOME_ID, "Biome", "Biome object", NodeDataType.BIOME, this));
        addOutputPort(new BasePort(OUTPUT_BIOME_NAME_ID, "Biome Name", "Biome registry id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_BIOME_TEMP_ID, "Temperature", "Biome base temperature", NodeDataType.FLOAT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether biome read succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when biome read fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Gets the biome registry id and base temperature for a block position";
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
            var biomeEntry = context.getWorld().getBiome(pos);
            var biome = biomeEntry.value();
            String biomeName = biomeEntry.getKey()
                .map(RegistryKey::getValue)
                .map(Object::toString)
                .orElse("minecraft:unknown");

            outputValues.put(OUTPUT_BIOME_ID, biome);
            outputValues.put(OUTPUT_BIOME_NAME_ID, biomeName);
            outputValues.put(OUTPUT_BIOME_TEMP_ID, biome.getTemperature());
            outputValues.put(OUTPUT_VALID_ID, true);
            outputValues.put(OUTPUT_ERROR_ID, "");
        } catch (Exception e) {
            String error = "Error getting biome at " + pos + ": " + e.getMessage();
            NodeCraft.LOGGER.warn(error);
            writeFailure(error);
        }
    }

    private void writeFailure(String error) {
        outputValues.put(OUTPUT_BIOME_ID, null);
        outputValues.put(OUTPUT_BIOME_NAME_ID, "");
        outputValues.put(OUTPUT_BIOME_TEMP_ID, 0.0f);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

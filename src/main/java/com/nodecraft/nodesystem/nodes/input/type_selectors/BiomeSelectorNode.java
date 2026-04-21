package com.nodecraft.nodesystem.nodes.input.type_selectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "input.type_selectors.biome_selector",
    displayName = "Biome Selector",
    description = "Selects a biome id for biome-aware generation workflows.",
    category = "input.type_selectors",
    order = 3
)
public class BiomeSelectorNode extends BaseNode {

    private static final String OUTPUT_BIOME_ID = "output_biome_id";
    private static final String OUTPUT_NAMESPACE = "output_namespace";
    private static final String OUTPUT_BIOME_PATH = "output_biome_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";

    @NodeProperty(displayName = "Selected Biome", category = "Selection", order = 1)
    private String selectedBiome = "minecraft:plains";

    public BiomeSelectorNode() {
        super(UUID.randomUUID(), "input.type_selectors.biome_selector");
        addOutputPort(new BasePort(OUTPUT_BIOME_ID, "Biome", "Selected biome id", NodeDataType.BIOME, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "Biome namespace", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_BIOME_PATH, "Biome Path", "Biome path", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether biome is non-minecraft namespace", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Selects a biome id for biome-aware generation workflows.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Identifier id = Identifier.tryParse(selectedBiome);
        if (id == null) {
            id = Identifier.of("minecraft", "plains");
            selectedBiome = id.toString();
        }
        outputValues.put(OUTPUT_BIOME_ID, id.toString());
        outputValues.put(OUTPUT_NAMESPACE, id.getNamespace());
        outputValues.put(OUTPUT_BIOME_PATH, id.getPath());
        outputValues.put(OUTPUT_IS_MODDED, !"minecraft".equals(id.getNamespace()));
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("selectedBiome", selectedBiome);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("selectedBiome") instanceof String biome) {
            selectedBiome = biome;
        }
    }
}


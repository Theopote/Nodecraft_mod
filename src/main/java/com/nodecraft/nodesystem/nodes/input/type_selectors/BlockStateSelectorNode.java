package com.nodecraft.nodesystem.nodes.input.type_selectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockStateData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "input.type_selectors.block_state_selector",
    displayName = "Block State Selector",
    description = "Builds block-state key/value data from a compact properties string.",
    category = "input.type_selectors",
    order = 4
)
public class BlockStateSelectorNode extends BaseNode {

    private static final String OUTPUT_BLOCK_ID = "output_block_id";
    private static final String OUTPUT_BLOCK_STATE = "output_block_state";
    private static final String OUTPUT_HAS_PROPERTIES = "output_has_properties";

    @NodeProperty(displayName = "Block ID", category = "Selection", order = 1)
    private String blockId = "minecraft:stone";

    @NodeProperty(displayName = "State Properties", category = "Selection", order = 2,
        description = "Comma separated key=value pairs, e.g. facing=north,waterlogged=false")
    private String stateProperties = "";

    public BlockStateSelectorNode() {
        super(UUID.randomUUID(), "input.type_selectors.block_state_selector");
        addOutputPort(new BasePort(OUTPUT_BLOCK_ID, "Block Type", "Selected block type id", NodeDataType.BLOCK_TYPE, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_STATE, "Block State", "Parsed block state data", NodeDataType.BLOCK_STATE_DATA, this));
        addOutputPort(new BasePort(OUTPUT_HAS_PROPERTIES, "Has Properties", "True when state map has at least one key", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Builds block-state key/value data from a compact properties string.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockStateData stateData = parseStateProperties(stateProperties);
        outputValues.put(OUTPUT_BLOCK_ID, normalizeBlockId(blockId));
        outputValues.put(OUTPUT_BLOCK_STATE, stateData);
        outputValues.put(OUTPUT_HAS_PROPERTIES, !stateData.isEmpty());
    }

    private static String normalizeBlockId(String value) {
        if (value == null || value.isBlank()) {
            return "minecraft:stone";
        }
        return value.contains(":") ? value.trim() : "minecraft:" + value.trim();
    }

    private static BlockStateData parseStateProperties(String text) {
        BlockStateData state = new BlockStateData();
        if (text == null || text.isBlank()) {
            return state;
        }
        String[] pairs = text.split(",");
        for (String pair : pairs) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            String value = kv[1].trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                state.put(key, value);
            }
        }
        return state;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("blockId", blockId);
        state.put("stateProperties", stateProperties);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("blockId") instanceof String value) {
            blockId = value;
        }
        if (map.get("stateProperties") instanceof String value) {
            stateProperties = value;
        }
    }
}


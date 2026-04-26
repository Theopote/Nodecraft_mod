package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "math.list.chunk",
    displayName = "Chunk List",
    description = "Splits a list into fixed-size chunks.",
    category = "math.list",
    order = 33
)
public class ChunkListNode extends BaseNode {

    @NodeProperty(displayName = "Drop Remainder", category = "Chunk", order = 1)
    private boolean dropRemainder = false;

    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_SIZE_ID = "input_size";

    private static final String OUTPUT_CHUNKS_ID = "output_chunks";
    private static final String OUTPUT_CHUNK_COUNT_ID = "output_chunk_count";
    private static final String OUTPUT_REMAINDER_ID = "output_remainder";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ChunkListNode() {
        super(UUID.randomUUID(), "math.list.chunk");

        addInputPort(new BasePort(INPUT_LIST_ID, "List", "Input list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_SIZE_ID, "Size", "Chunk size", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_CHUNKS_ID, "Chunks", "List of chunk lists", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_CHUNK_COUNT_ID, "Chunk Count", "Number of chunks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMAINDER_ID, "Remainder", "Trailing items not included in chunks", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether chunking succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Chunk List";
    }

    @Override
    public String getDescription() {
        return "Splits a list into fixed-size chunks.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listObj = inputValues.get(INPUT_LIST_ID);
        Object sizeObj = inputValues.get(INPUT_SIZE_ID);
        if (!(listObj instanceof List<?> list) || !(sizeObj instanceof Number sizeNumber)) {
            outputValues.put(OUTPUT_CHUNKS_ID, List.of());
            outputValues.put(OUTPUT_CHUNK_COUNT_ID, 0);
            outputValues.put(OUTPUT_REMAINDER_ID, List.of());
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        int size = sizeNumber.intValue();
        if (size <= 0) {
            outputValues.put(OUTPUT_CHUNKS_ID, List.of());
            outputValues.put(OUTPUT_CHUNK_COUNT_ID, 0);
            outputValues.put(OUTPUT_REMAINDER_ID, List.of());
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        List<Object> chunks = new ArrayList<>();
        List<Object> remainder = new ArrayList<>();

        for (int i = 0; i < list.size(); i += size) {
            int end = Math.min(i + size, list.size());
            List<Object> chunk = new ArrayList<>(list.subList(i, end));
            if (chunk.size() < size && dropRemainder) {
                remainder.addAll(chunk);
            } else if (chunk.size() < size) {
                chunks.add(chunk);
                remainder.addAll(chunk);
            } else {
                chunks.add(chunk);
            }
        }

        outputValues.put(OUTPUT_CHUNKS_ID, chunks);
        outputValues.put(OUTPUT_CHUNK_COUNT_ID, chunks.size());
        outputValues.put(OUTPUT_REMAINDER_ID, remainder);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("dropRemainder", dropRemainder);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object dropRemainderValue = map.get("dropRemainder");
        if (dropRemainderValue instanceof Boolean value) {
            dropRemainder = value;
        }
    }
}

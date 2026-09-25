package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.shuffle_list",
    displayName = "Shuffle List",
    description = "Deterministically reorders a list using Seed (preserves element type T).",
    category = "math.list"
)
public class ShuffleListNode extends BaseNode {

    private long seed = 0;

    private static final String LIST_T = "T";
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String OUTPUT_LIST_ID = "output_list";

    public ShuffleListNode() {
        super(UUID.randomUUID(), "math.list.shuffle_list");

        addInputPort(new BasePort(INPUT_LIST_ID, "List", "The list to shuffle", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Random seed (0 is a valid deterministic seed)",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LIST_ID, "Shuffled", "The shuffled list", NodeDataType.LIST, this)
                .bindListType(LIST_T));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object inputObj = inputValues.get(INPUT_LIST_ID);
        Object seedObj = inputValues.get(INPUT_SEED_ID);

        List<Object> resultList = new ArrayList<>();
        if (inputObj instanceof List<?> inputList) {
            resultList.addAll(inputList);
            long actualSeed = seed;
            if (seedObj instanceof Number number) {
                actualSeed = number.longValue();
            }
            if (!resultList.isEmpty()) {
                Collections.shuffle(resultList, new Random(actualSeed));
            }
        }
        outputValues.put(OUTPUT_LIST_ID, resultList);
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        if (this.seed != seed) {
            this.seed = seed;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("seed", getSeed());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }
        Object seedObj = stateMap.get("seed");
        if (seedObj instanceof Number number) {
            setSeed(number.longValue());
        }
        // Legacy preserveInput ignored.
    }
}

package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.sequence.repeat",
    displayName = "Repeat Item",
    description = "Repeats a single item Count times as a LIST. A list item is repeated as one element, never tiled.",
    category = "math.sequence"
)
public class RepeatNode extends BaseNode {

    private int defaultCount = 3;

    private static final String INPUT_DATA_ID = "input_data";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_LENGTH_ID = "output_length";

    public RepeatNode() {
        super(UUID.randomUUID(), "math.sequence.repeat");

        IPort dataInput = new BasePort(INPUT_DATA_ID, "Item",
                "The item to repeat (lists are one element, not tiled)", NodeDataType.ANY, this);
        addInputPort(dataInput);

        IPort countInput = new BasePort(INPUT_COUNT_ID, "Count",
                "Number of times to repeat", NodeDataType.INTEGER, this);
        addInputPort(countInput);

        IPort resultOutput = new BasePort(OUTPUT_RESULT_ID, "Result",
                "The repeated item as a list", NodeDataType.LIST, this);
        addOutputPort(resultOutput);

        IPort lengthOutput = new BasePort(OUTPUT_LENGTH_ID, "Length",
                "Length of the resulting list", NodeDataType.INTEGER, this);
        addOutputPort(lengthOutput);
    }

    @Override
    public String getDisplayName() {
        return "Repeat Item";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object dataObj = inputValues.get(INPUT_DATA_ID);
        Object countObj = inputValues.get(INPUT_COUNT_ID);

        int count = defaultCount;
        if (countObj instanceof Number number) {
            count = number.intValue();
        }
        count = GenerationLimits.clampNonNegativeCount(count);

        List<Object> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(dataObj);
        }

        outputValues.put(OUTPUT_RESULT_ID, result);
        outputValues.put(OUTPUT_LENGTH_ID, result.size());
    }

    public int getDefaultCount() {
        return defaultCount;
    }

    public void setDefaultCount(int count) {
        int resolved = GenerationLimits.clampNonNegativeCount(count);
        if (this.defaultCount != resolved) {
            this.defaultCount = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("defaultCount", getDefaultCount());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }
        Object count = stateMap.get("defaultCount");
        if (count instanceof Number number) {
            setDefaultCount(number.intValue());
        }
    }
}

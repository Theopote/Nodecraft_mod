package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.RandomInputResolver;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
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
public class RepeatNode extends SequenceGenerationNode {

    private static final String LIST_T = "T";

    private int defaultCount = 3;

    private static final String INPUT_DATA_ID = "input_data";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_LENGTH_ID = "output_length";

    public RepeatNode() {
        super(UUID.randomUUID(), "math.sequence.repeat");

        IPort dataInput = new BasePort(INPUT_DATA_ID, "Item",
                "The item to repeat (lists are one element, not tiled)", NodeDataType.ANY, this)
                .bindListElementType(LIST_T);
        addInputPort(dataInput);

        IPort countInput = new BasePort(INPUT_COUNT_ID, "Count",
                "Number of times to repeat", NodeDataType.INTEGER, this);
        addInputPort(countInput);

        IPort resultOutput = new BasePort(OUTPUT_RESULT_ID, "Result",
                "The repeated item as a list", NodeDataType.LIST, this)
                .bindListType(LIST_T);
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
        RandomInputResolver.IntegerResolveResult countResult = RandomInputResolver.resolveCount(
                resolveValue(INPUT_COUNT_ID),
                defaultCount,
                isDriven(INPUT_COUNT_ID)
        );
        if (!countResult.valid()) {
            emitRepeatFailure("invalid_input");
            return;
        }

        Object dataObj;
        if (isDriven(INPUT_DATA_ID)) {
            dataObj = resolveValue(INPUT_DATA_ID);
            if (dataObj == null) {
                emitRepeatFailure("invalid_input");
                return;
            }
        } else {
            dataObj = null;
        }

        int count = countResult.value();
        List<Object> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(dataObj);
        }

        outputValues.put(OUTPUT_RESULT_ID, result);
        outputValues.put(OUTPUT_LENGTH_ID, result.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void emitRepeatFailure(String error) {
        outputValues.put(OUTPUT_RESULT_ID, Collections.emptyList());
        outputValues.put(OUTPUT_LENGTH_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
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
        if (count instanceof Integer integer) {
            setDefaultCount(integer);
        }
    }
}

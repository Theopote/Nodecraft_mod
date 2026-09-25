package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.sort_text",
    displayName = "Sort Text",
    description = "Sorts a STRING_LIST ascending or descending.",
    category = "math.list",
    order = 34
)
public class SortTextNode extends BaseNode {

    @NodeProperty(displayName = "Descending", category = "Sort", order = 1)
    private boolean descending = false;

    private static final String INPUT_LIST_ID = "input_list";
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SortTextNode() {
        super(UUID.randomUUID(), "math.list.sort_text");
        addInputPort(new BasePort(INPUT_LIST_ID, "Texts", "String list to sort", NodeDataType.STRING_LIST, this));
        addOutputPort(new BasePort(OUTPUT_LIST_ID, "Texts", "Sorted string list", NodeDataType.STRING_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether all elements were strings",
                NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object input = inputValues.get(INPUT_LIST_ID);
        if (!(input instanceof List<?> list)) {
            writeInvalid();
            return;
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof String text)) {
                writeInvalid();
                return;
            }
            values.add(text);
        }
        values.sort(descending ? Comparator.reverseOrder() : Comparator.naturalOrder());
        outputValues.put(OUTPUT_LIST_ID, values);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_LIST_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    public boolean isDescending() {
        return descending;
    }

    public void setDescending(boolean descending) {
        if (this.descending != descending) {
            this.descending = descending;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("descending", descending);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map && map.get("descending") instanceof Boolean value) {
            setDescending(value);
        }
    }
}

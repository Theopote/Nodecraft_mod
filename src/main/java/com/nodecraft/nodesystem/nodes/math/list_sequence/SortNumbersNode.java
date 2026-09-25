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
    id = "math.list.sort_numbers",
    displayName = "Sort Numbers",
    description = "Sorts a DOUBLE_LIST ascending or descending.",
    category = "math.list",
    order = 33
)
public class SortNumbersNode extends BaseNode {

    @NodeProperty(displayName = "Descending", category = "Sort", order = 1)
    private boolean descending = false;

    private static final String INPUT_LIST_ID = "input_list";
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SortNumbersNode() {
        super(UUID.randomUUID(), "math.list.sort_numbers");
        addInputPort(new BasePort(INPUT_LIST_ID, "Numbers", "Double list to sort", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_LIST_ID, "Numbers", "Sorted double list", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether all elements were finite numbers",
                NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object input = inputValues.get(INPUT_LIST_ID);
        if (!(input instanceof List<?> list)) {
            writeInvalid();
            return;
        }
        List<Double> values = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                writeInvalid();
                return;
            }
            values.add(number.doubleValue());
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

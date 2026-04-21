package com.nodecraft.nodesystem.nodes.input.numeric;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "input.numeric.range",
    displayName = "Range Input",
    description = "Defines a numeric interval and outputs min/max/span plus a range object.",
    category = "input.numeric",
    order = 8
)
public class RangeInputNode extends BaseNode {

    private static final String OUTPUT_RANGE_ID = "output_range";
    private static final String OUTPUT_MIN_ID = "output_min";
    private static final String OUTPUT_MAX_ID = "output_max";
    private static final String OUTPUT_SPAN_ID = "output_span";

    @NodeProperty(displayName = "Min", category = "Value", order = 1)
    private double min = 0.0d;

    @NodeProperty(displayName = "Max", category = "Value", order = 2)
    private double max = 1.0d;

    public RangeInputNode() {
        super(UUID.randomUUID(), "input.numeric.range");
        addOutputPort(new BasePort(OUTPUT_RANGE_ID, "Range", "Numeric range object", NodeDataType.NUMERIC_RANGE, this));
        addOutputPort(new BasePort(OUTPUT_MIN_ID, "Min", "Lower bound", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_MAX_ID, "Max", "Upper bound", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SPAN_ID, "Span", "Range span (max - min)", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDescription() {
        return "Defines a numeric interval and outputs min/max/span plus a range object.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        double resolvedMin = Math.min(min, max);
        double resolvedMax = Math.max(min, max);
        NumericRangeData range = new NumericRangeData(resolvedMin, resolvedMax);
        outputValues.put(OUTPUT_RANGE_ID, range);
        outputValues.put(OUTPUT_MIN_ID, range.min());
        outputValues.put(OUTPUT_MAX_ID, range.max());
        outputValues.put(OUTPUT_SPAN_ID, range.span());
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("min", min);
        state.put("max", max);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("min") instanceof Number n) {
            min = n.doubleValue();
        }
        if (map.get("max") instanceof Number n) {
            max = n.doubleValue();
        }
    }
}


package com.nodecraft.nodesystem.nodes.flow.loop;

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
    id = "flow.loop.accumulator",
    displayName = "Accumulator",
    description = "Accumulates list values into a single result.",
    category = "flow.loop",
    order = 1
)
public class AccumulatorNode extends BaseNode {

    public enum Operation {
        SUM,
        PRODUCT,
        MIN,
        MAX,
        CONCAT,
        COUNT
    }

    @NodeProperty(displayName = "Operation", category = "Accumulator", order = 1)
    private Operation operation = Operation.SUM;

    @NodeProperty(displayName = "Ignore Nulls", category = "Accumulator", order = 2)
    private boolean ignoreNulls = true;

    @NodeProperty(displayName = "Concat Separator", category = "Accumulator", order = 3)
    private String separator = "";

    private static final String INPUT_VALUES_ID = "input_values";
    private static final String INPUT_INITIAL_ID = "input_initial";

    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_RUNNING_RESULTS_ID = "output_running_results";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public AccumulatorNode() {
        super(UUID.randomUUID(), "flow.loop.accumulator");

        addInputPort(new BasePort(INPUT_VALUES_ID, "Values", "Values to accumulate", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_INITIAL_ID, "Initial", "Optional initial accumulator value", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Accumulated result", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_RUNNING_RESULTS_ID, "Running Results", "Per-step accumulated values", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of processed values", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether accumulation succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Accumulator";
    }

    @Override
    public String getDescription() {
        return "Accumulates list values into a single result.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valuesObj = inputValues.get(INPUT_VALUES_ID);
        Object initialObj = inputValues.get(INPUT_INITIAL_ID);

        if (!(valuesObj instanceof List<?> values)) {
            outputValues.put(OUTPUT_RESULT_ID, null);
            outputValues.put(OUTPUT_RUNNING_RESULTS_ID, List.of());
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Operation op = operation == null ? Operation.SUM : operation;
        List<Object> running = new ArrayList<>();
        int processedCount = 0;

        switch (op) {
            case SUM -> {
                Double initial = asFiniteDouble(initialObj);
                double acc = initial != null ? initial : 0.0d;
                for (Object value : values) {
                    if (value == null && ignoreNulls) {
                        continue;
                    }
                    Double numeric = asFiniteDouble(value);
                    if (numeric == null) {
                        continue;
                    }
                    acc += numeric;
                    running.add(acc);
                    processedCount++;
                }
                outputValues.put(OUTPUT_RESULT_ID, acc);
                outputValues.put(OUTPUT_RUNNING_RESULTS_ID, running);
                outputValues.put(OUTPUT_COUNT_ID, processedCount);
                outputValues.put(OUTPUT_VALID_ID, true);
            }
            case PRODUCT -> {
                Double initial = asFiniteDouble(initialObj);
                double acc = initial != null ? initial : 1.0d;
                for (Object value : values) {
                    if (value == null && ignoreNulls) {
                        continue;
                    }
                    Double numeric = asFiniteDouble(value);
                    if (numeric == null) {
                        continue;
                    }
                    acc *= numeric;
                    running.add(acc);
                    processedCount++;
                }
                outputValues.put(OUTPUT_RESULT_ID, acc);
                outputValues.put(OUTPUT_RUNNING_RESULTS_ID, running);
                outputValues.put(OUTPUT_COUNT_ID, processedCount);
                outputValues.put(OUTPUT_VALID_ID, true);
            }
            case MIN -> {
                Double acc = asFiniteDouble(initialObj);
                for (Object value : values) {
                    if (value == null && ignoreNulls) {
                        continue;
                    }
                    Double numeric = asFiniteDouble(value);
                    if (numeric == null) {
                        continue;
                    }
                    acc = acc == null ? numeric : Math.min(acc, numeric);
                    running.add(acc);
                    processedCount++;
                }
                outputValues.put(OUTPUT_RESULT_ID, acc);
                outputValues.put(OUTPUT_RUNNING_RESULTS_ID, running);
                outputValues.put(OUTPUT_COUNT_ID, processedCount);
                outputValues.put(OUTPUT_VALID_ID, acc != null);
            }
            case MAX -> {
                Double acc = asFiniteDouble(initialObj);
                for (Object value : values) {
                    if (value == null && ignoreNulls) {
                        continue;
                    }
                    Double numeric = asFiniteDouble(value);
                    if (numeric == null) {
                        continue;
                    }
                    acc = acc == null ? numeric : Math.max(acc, numeric);
                    running.add(acc);
                    processedCount++;
                }
                outputValues.put(OUTPUT_RESULT_ID, acc);
                outputValues.put(OUTPUT_RUNNING_RESULTS_ID, running);
                outputValues.put(OUTPUT_COUNT_ID, processedCount);
                outputValues.put(OUTPUT_VALID_ID, acc != null);
            }
            case CONCAT -> {
                StringBuilder builder = new StringBuilder();
                if (initialObj != null) {
                    builder.append(initialObj);
                }
                for (Object value : values) {
                    if (value == null && ignoreNulls) {
                        continue;
                    }
                    if (!builder.isEmpty() && !separator.isEmpty()) {
                        builder.append(separator);
                    }
                    builder.append(String.valueOf(value));
                    running.add(builder.toString());
                    processedCount++;
                }
                outputValues.put(OUTPUT_RESULT_ID, builder.toString());
                outputValues.put(OUTPUT_RUNNING_RESULTS_ID, running);
                outputValues.put(OUTPUT_COUNT_ID, processedCount);
                outputValues.put(OUTPUT_VALID_ID, true);
            }
            case COUNT -> {
                int count = 0;
                for (Object value : values) {
                    if (value == null && ignoreNulls) {
                        continue;
                    }
                    count++;
                    running.add(count);
                }
                outputValues.put(OUTPUT_RESULT_ID, count);
                outputValues.put(OUTPUT_RUNNING_RESULTS_ID, running);
                outputValues.put(OUTPUT_COUNT_ID, count);
                outputValues.put(OUTPUT_VALID_ID, true);
            }
        }
    }

    private Double asFiniteDouble(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double numeric = number.doubleValue();
        if (!Double.isFinite(numeric)) {
            return null;
        }
        return numeric;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("operation", operation == null ? Operation.SUM.name() : operation.name());
        state.put("ignoreNulls", ignoreNulls);
        state.put("separator", separator);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }

        Object operationName = map.get("operation");
        if (operationName instanceof String name) {
            try {
                operation = Operation.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                operation = Operation.SUM;
            }
        }

        Object ignoreNullsValue = map.get("ignoreNulls");
        if (ignoreNullsValue instanceof Boolean value) {
            ignoreNulls = value;
        }

        Object separatorValue = map.get("separator");
        if (separatorValue instanceof String value) {
            separator = value;
        }
    }
}

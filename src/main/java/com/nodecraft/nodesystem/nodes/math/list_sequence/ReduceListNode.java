package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "math.list.reduce",
    displayName = "Reduce List",
    description = "Reduces a list into a single value using a selected operation.",
    category = "math.list",
    order = 32
)
public class ReduceListNode extends BaseNode {

    public enum Operation {
        SUM,
        PRODUCT,
        MIN,
        MAX,
        AVERAGE,
        CONCAT,
        COUNT
    }

    @NodeProperty(displayName = "Operation", category = "Reduce", order = 1)
    private Operation operation = Operation.SUM;

    @NodeProperty(displayName = "Ignore Non-Numeric", category = "Reduce", order = 2)
    private boolean ignoreNonNumeric = true;

    @NodeProperty(displayName = "Ignore Nulls", category = "Reduce", order = 3)
    private boolean ignoreNulls = true;

    @NodeProperty(displayName = "Separator", category = "Reduce", order = 4)
    private String separator = "";

    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_INITIAL_ID = "input_initial";

    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ReduceListNode() {
        super(UUID.randomUUID(), "math.list.reduce");

        addInputPort(new BasePort(INPUT_LIST_ID, "List", "Input list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_INITIAL_ID, "Initial", "Optional initial value", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Reduced value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of consumed values", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether reduction succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Reduce List";
    }

    @Override
    public String getDescription() {
        return "Reduces a list into a single value using a selected operation.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listObj = inputValues.get(INPUT_LIST_ID);
        Object initialObj = inputValues.get(INPUT_INITIAL_ID);
        if (!(listObj instanceof List<?> list)) {
            outputValues.put(OUTPUT_RESULT_ID, null);
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Operation op = operation == null ? Operation.SUM : operation;
        switch (op) {
            case CONCAT -> reduceConcat(list, initialObj);
            case COUNT -> reduceCount(list);
            default -> reduceNumeric(list, initialObj, op);
        }
    }

    private void reduceConcat(List<?> list, Object initialObj) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        if (initialObj != null) {
            builder.append(initialObj);
        }

        for (Object item : list) {
            if (item == null) {
                if (!ignoreNulls) {
                    outputValues.put(OUTPUT_RESULT_ID, null);
                    outputValues.put(OUTPUT_COUNT_ID, 0);
                    outputValues.put(OUTPUT_VALID_ID, false);
                    return;
                }
                continue;
            }
            if (builder.length() > 0 && separator != null && !separator.isEmpty()) {
                builder.append(separator);
            }
            builder.append(item);
            count++;
        }

        outputValues.put(OUTPUT_RESULT_ID, builder.toString());
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void reduceCount(List<?> list) {
        int count = 0;
        for (Object item : list) {
            if (item == null && !ignoreNulls) {
                outputValues.put(OUTPUT_RESULT_ID, 0);
                outputValues.put(OUTPUT_COUNT_ID, 0);
                outputValues.put(OUTPUT_VALID_ID, false);
                return;
            }
            if (item != null) {
                count++;
            }
        }

        outputValues.put(OUTPUT_RESULT_ID, count);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void reduceNumeric(List<?> list, Object initialObj, Operation op) {
        Double initial = toDouble(initialObj);
        boolean hasValue = initial != null && Double.isFinite(initial);
        double result;
        int count = 0;
        double sumForAverage = 0.0d;

        if (op == Operation.PRODUCT) {
            result = hasValue ? initial : 1.0d;
            if (hasValue) {
                count++;
            }
        } else if (op == Operation.MIN || op == Operation.MAX) {
            result = hasValue ? initial : 0.0d;
        } else {
            result = hasValue ? initial : 0.0d;
            if (hasValue) {
                count++;
                sumForAverage += initial;
            }
        }

        for (Object item : list) {
            if (item == null) {
                if (!ignoreNulls) {
                    setInvalidNumeric();
                    return;
                }
                continue;
            }
            Double value = toDouble(item);
            if (value == null || !Double.isFinite(value)) {
                if (!ignoreNonNumeric) {
                    setInvalidNumeric();
                    return;
                }
                continue;
            }

            if (op == Operation.MIN) {
                result = (count == 0 && !hasValue) ? value : Math.min(result, value);
            } else if (op == Operation.MAX) {
                result = (count == 0 && !hasValue) ? value : Math.max(result, value);
            } else if (op == Operation.SUM || op == Operation.AVERAGE) {
                result += value;
            } else if (op == Operation.PRODUCT) {
                result *= value;
            }
            count++;
            if (op == Operation.AVERAGE) {
                sumForAverage += value;
            }
        }

        if (count == 0 && !hasValue) {
            setInvalidNumeric();
            return;
        }

        if (op == Operation.AVERAGE) {
            outputValues.put(OUTPUT_RESULT_ID, sumForAverage / count);
        } else {
            outputValues.put(OUTPUT_RESULT_ID, result);
        }
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void setInvalidNumeric() {
        outputValues.put(OUTPUT_RESULT_ID, Double.NaN);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("operation", operation != null ? operation.name() : Operation.SUM.name());
        state.put("ignoreNonNumeric", ignoreNonNumeric);
        state.put("ignoreNulls", ignoreNulls);
        state.put("separator", separator);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object operationValue = map.get("operation");
        if (operationValue instanceof String text) {
            try {
                operation = Operation.valueOf(text);
            } catch (IllegalArgumentException ignored) {
                operation = Operation.SUM;
            }
        }
        Object ignoreNonNumericValue = map.get("ignoreNonNumeric");
        if (ignoreNonNumericValue instanceof Boolean value) {
            ignoreNonNumeric = value;
        }
        Object ignoreNullsValue = map.get("ignoreNulls");
        if (ignoreNullsValue instanceof Boolean value) {
            ignoreNulls = value;
        }
        Object separatorValue = map.get("separator");
        if (separatorValue instanceof String text) {
            separator = text;
        }
    }
}


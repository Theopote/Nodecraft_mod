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
    id = "math.list.map_list",
    displayName = "Map List",
    description = "Applies a scalar operation to each numeric item in a list.",
    category = "math.list",
    order = 31
)
public class MapListNode extends BaseNode {

    public enum Operation {
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE,
        POWER,
        MIN,
        MAX,
        CLAMP,
        ABS,
        FLOOR,
        CEIL,
        ROUND,
        SIGN
    }

    @NodeProperty(displayName = "Operation", category = "Map", order = 1)
    private Operation operation = Operation.ADD;

    @NodeProperty(displayName = "Ignore Non-Numeric", category = "Map", order = 2)
    private boolean ignoreNonNumeric = true;

    @NodeProperty(displayName = "Ignore Nulls", category = "Map", order = 3)
    private boolean ignoreNulls = true;

    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_MIN_ID = "input_min";
    private static final String INPUT_MAX_ID = "input_max";

    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_CHANGED_COUNT_ID = "output_changed_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MapListNode() {
        super(UUID.randomUUID(), "math.list.map_list");

        addInputPort(new BasePort(INPUT_LIST_ID, "List", "Input list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Operand value for scalar operations", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MIN_ID, "Min", "Clamp minimum (for CLAMP operation)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MAX_ID, "Max", "Clamp maximum (for CLAMP operation)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_LIST_ID, "Mapped", "Mapped list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_CHANGED_COUNT_ID, "Changed Count", "Number of mapped numeric entries", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether mapping completed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Map List";
    }

    @Override
    public String getDescription() {
        return "Applies a scalar operation to each numeric item in a list.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listObj = inputValues.get(INPUT_LIST_ID);
        if (!(listObj instanceof List<?> inputList)) {
            outputValues.put(OUTPUT_LIST_ID, List.of());
            outputValues.put(OUTPUT_CHANGED_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double operand = getFiniteOrDefault(inputValues.get(INPUT_VALUE_ID), 0.0d);
        double min = getFiniteOrDefault(inputValues.get(INPUT_MIN_ID), 0.0d);
        double max = getFiniteOrDefault(inputValues.get(INPUT_MAX_ID), 1.0d);
        if (min > max) {
            double tmp = min;
            min = max;
            max = tmp;
        }

        List<Object> mapped = new ArrayList<>(inputList.size());
        int changedCount = 0;
        for (Object item : inputList) {
            if (item == null) {
                if (!ignoreNulls) {
                    outputValues.put(OUTPUT_LIST_ID, List.of());
                    outputValues.put(OUTPUT_CHANGED_COUNT_ID, 0);
                    outputValues.put(OUTPUT_VALID_ID, false);
                    return;
                }
                mapped.add(null);
                continue;
            }

            Double value = toDouble(item);
            if (value == null || !Double.isFinite(value)) {
                if (!ignoreNonNumeric) {
                    outputValues.put(OUTPUT_LIST_ID, List.of());
                    outputValues.put(OUTPUT_CHANGED_COUNT_ID, 0);
                    outputValues.put(OUTPUT_VALID_ID, false);
                    return;
                }
                mapped.add(item);
                continue;
            }

            mapped.add(applyOperation(value, operand, min, max));
            changedCount++;
        }

        outputValues.put(OUTPUT_LIST_ID, mapped);
        outputValues.put(OUTPUT_CHANGED_COUNT_ID, changedCount);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private double applyOperation(double input, double operand, double min, double max) {
        Operation op = operation == null ? Operation.ADD : operation;
        return switch (op) {
            case ADD -> input + operand;
            case SUBTRACT -> input - operand;
            case MULTIPLY -> input * operand;
            case DIVIDE -> Math.abs(operand) <= 1.0e-12d ? Double.NaN : input / operand;
            case POWER -> Math.pow(input, operand);
            case MIN -> Math.min(input, operand);
            case MAX -> Math.max(input, operand);
            case CLAMP -> Math.max(min, Math.min(max, input));
            case ABS -> Math.abs(input);
            case FLOOR -> Math.floor(input);
            case CEIL -> Math.ceil(input);
            case ROUND -> (double) Math.round(input);
            case SIGN -> input > 0.0d ? 1.0d : (input < 0.0d ? -1.0d : 0.0d);
        };
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

    private double getFiniteOrDefault(Object value, double fallback) {
        Double parsed = toDouble(value);
        if (parsed == null || !Double.isFinite(parsed)) {
            return fallback;
        }
        return parsed;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("operation", operation != null ? operation.name() : Operation.ADD.name());
        state.put("ignoreNonNumeric", ignoreNonNumeric);
        state.put("ignoreNulls", ignoreNulls);
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
                operation = Operation.ADD;
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
    }
}

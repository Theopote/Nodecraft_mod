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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.map_numbers",
    displayName = "Map Numbers",
    description = "Applies a scalar operation to each value in a DOUBLE_LIST.",
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

    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_MIN_ID = "input_min";
    private static final String INPUT_MAX_ID = "input_max";

    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MapListNode() {
        super(UUID.randomUUID(), "math.list.map_numbers");

        addInputPort(new BasePort(INPUT_LIST_ID, "Numbers", "Input double list", NodeDataType.DOUBLE_LIST, this));
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Operand value for scalar operations", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MIN_ID, "Min", "Clamp minimum (for CLAMP operation)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MAX_ID, "Max", "Clamp maximum (for CLAMP operation)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_LIST_ID, "Numbers", "Mapped double list", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of mapped entries", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether mapping completed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Map Numbers";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listObj = inputValues.get(INPUT_LIST_ID);
        if (!(listObj instanceof List<?> inputList)) {
            writeInvalid();
            return;
        }

        Double operand = toFiniteDouble(inputValues.get(INPUT_VALUE_ID));
        Double minObj = toFiniteDouble(inputValues.get(INPUT_MIN_ID));
        Double maxObj = toFiniteDouble(inputValues.get(INPUT_MAX_ID));
        double min = minObj != null ? minObj : 0.0d;
        double max = maxObj != null ? maxObj : 1.0d;
        if (min > max) {
            double tmp = min;
            min = max;
            max = tmp;
        }

        Operation op = operation == null ? Operation.ADD : operation;
        boolean needsOperand = op != Operation.ABS && op != Operation.FLOOR && op != Operation.CEIL
                && op != Operation.ROUND && op != Operation.SIGN && op != Operation.CLAMP;
        if (needsOperand && operand == null) {
            writeInvalid();
            return;
        }
        double operandValue = operand != null ? operand : 0.0d;

        List<Double> mapped = new ArrayList<>(inputList.size());
        for (Object item : inputList) {
            Double value = toFiniteDouble(item);
            if (value == null) {
                writeInvalid();
                return;
            }

            double mappedValue = applyOperation(value, operandValue, min, max);
            if (!Double.isFinite(mappedValue)) {
                writeInvalid();
                return;
            }
            mapped.add(mappedValue);
        }

        outputValues.put(OUTPUT_LIST_ID, mapped);
        outputValues.put(OUTPUT_COUNT_ID, mapped.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_LIST_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
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

    private Double toFiniteDouble(Object value) {
        if (value instanceof Number number) {
            double parsed = number.doubleValue();
            return Double.isFinite(parsed) ? parsed : null;
        }
        return null;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("operation", operation != null ? operation.name() : Operation.ADD.name());
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
                setOperation(Operation.valueOf(text));
            } catch (IllegalArgumentException ignored) {
                setOperation(Operation.ADD);
            }
        }
        // Legacy ignoreNonNumeric / ignoreNulls are ignored.
    }

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation value) {
        Operation resolved = value != null ? value : Operation.ADD;
        if (operation != resolved) {
            operation = resolved;
            markDirty();
        }
    }
}

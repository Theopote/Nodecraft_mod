package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.ScalarMathOps;
import com.nodecraft.nodesystem.math.ScalarResult;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictDoubleUtils;
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
        addInputPort(new BasePort(INPUT_MIN_ID, "Min", "Clamp minimum (undriven=0; reversed bounds auto-normalized)",
                NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MAX_ID, "Max", "Clamp maximum (undriven=1; reversed bounds auto-normalized)",
                NodeDataType.DOUBLE, this));

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

        Operation op = operation == null ? Operation.ADD : operation;
        Double operand = resolveOperand(op);
        if (operand == null && needsOperand(op)) {
            writeInvalid();
            return;
        }
        double operandValue = operand != null ? operand : 0.0d;

        Double minBound = resolveClampBound(INPUT_MIN_ID, 0.0d);
        Double maxBound = resolveClampBound(INPUT_MAX_ID, 1.0d);
        if (op == Operation.CLAMP && (minBound == null || maxBound == null)) {
            writeInvalid();
            return;
        }
        double min = minBound != null ? minBound : 0.0d;
        double max = maxBound != null ? maxBound : 1.0d;

        List<Double> mapped = new ArrayList<>(inputList.size());
        for (Object item : inputList) {
            Double value = toFiniteDouble(item);
            if (value == null) {
                writeInvalid();
                return;
            }

            ScalarResult mappedResult = applyOperation(op, value, operandValue, min, max);
            if (!mappedResult.valid()) {
                writeInvalid();
                return;
            }
            mapped.add(mappedResult.value());
        }

        outputValues.put(OUTPUT_LIST_ID, mapped);
        outputValues.put(OUTPUT_COUNT_ID, mapped.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private @Nullable Double resolveOperand(Operation op) {
        if (!needsOperand(op)) {
            return null;
        }
        return toFiniteDouble(resolveValue(INPUT_VALUE_ID));
    }

    private @Nullable Double resolveClampBound(String portId, double defaultValue) {
        if (!isDriven(portId)) {
            return defaultValue;
        }
        return StrictDoubleUtils.requireExactFiniteDouble(resolveValue(portId));
    }

    private boolean needsOperand(Operation op) {
        return op != Operation.ABS && op != Operation.FLOOR && op != Operation.CEIL
                && op != Operation.ROUND && op != Operation.SIGN && op != Operation.CLAMP;
    }

    private boolean isDriven(String portId) {
        return OptionalPortDrive.isConnected(this, portId) || inputValues.containsKey(portId);
    }

    private @Nullable Object resolveValue(String portId) {
        if (OptionalPortDrive.isConnected(this, portId)) {
            return getInput(portId);
        }
        return inputValues.get(portId);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_LIST_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private ScalarResult applyOperation(Operation op, double input, double operand, double min, double max) {
        return switch (op) {
            case ADD -> ScalarMathOps.add(input, operand);
            case SUBTRACT -> ScalarMathOps.sub(input, operand);
            case MULTIPLY -> ScalarMathOps.mul(input, operand);
            case DIVIDE -> ScalarMathOps.div(input, operand);
            case POWER -> ScalarMathOps.pow(input, operand);
            case MIN -> ScalarMathOps.min(input, operand);
            case MAX -> ScalarMathOps.max(input, operand);
            case CLAMP -> ScalarMathOps.clamp(input, min, max);
            case ABS -> ScalarMathOps.abs(input);
            case FLOOR -> ScalarMathOps.floor(input);
            case CEIL -> ScalarMathOps.ceil(input);
            case ROUND -> ScalarMathOps.round(input);
            case SIGN -> ScalarMathOps.sign(input);
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

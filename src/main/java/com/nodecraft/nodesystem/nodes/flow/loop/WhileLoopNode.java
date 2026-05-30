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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "flow.loop.while",
    displayName = "While Loop",
    description = "Processes input values while condition remains true, with iteration safety limits.",
    category = "flow.loop",
    order = 2
)
public class WhileLoopNode extends BaseNode {

    private static final int MIN_ITERATIONS = 1;
    private static final int MAX_ITERATIONS_CAP = 100000;

    @NodeProperty(displayName = "Max Iterations", category = "Loop", order = 1)
    private int maxIterations = 256;

    @NodeProperty(displayName = "Default Condition", category = "Loop", order = 2)
    private boolean defaultCondition = true;

    private static final String INPUT_VALUES_ID = "input_values";
    private static final String INPUT_CONDITION_ID = "input_condition";
    private static final String INPUT_MAX_ITERATIONS_ID = "input_max_iterations";

    private static final String OUTPUT_VALUES_ID = "output_values";
    private static final String OUTPUT_ITERATIONS_ID = "output_iterations";
    private static final String OUTPUT_TERMINATED_BY_CONDITION_ID = "output_terminated_by_condition";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public WhileLoopNode() {
        super(UUID.randomUUID(), "flow.loop.while");

        addInputPort(new BasePort(INPUT_VALUES_ID, "Values", "Values to process while condition is true", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_CONDITION_ID, "Condition", "Boolean or boolean-list condition", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_MAX_ITERATIONS_ID, "Max Iterations", "Optional iteration cap override", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_VALUES_ID, "Values", "Values processed before loop terminated", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_ITERATIONS_ID, "Iterations", "Number of iterations executed", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TERMINATED_BY_CONDITION_ID, "Terminated By Condition", "Whether loop stopped because condition became false", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "Whether loop stopped because max iterations was reached", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether loop input shape was valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valuesObj = inputValues.get(INPUT_VALUES_ID);
        Object conditionObj = inputValues.get(INPUT_CONDITION_ID);
        int limit = resolveLimit(inputValues.get(INPUT_MAX_ITERATIONS_ID));

        if (!(valuesObj instanceof List<?> values)) {
            outputValues.put(OUTPUT_VALUES_ID, List.of());
            outputValues.put(OUTPUT_ITERATIONS_ID, 0);
            outputValues.put(OUTPUT_TERMINATED_BY_CONDITION_ID, false);
            outputValues.put(OUTPUT_HIT_LIMIT_ID, false);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        List<Object> output = new ArrayList<>();
        int iterations = 0;
        boolean terminatedByCondition = false;

        for (int i = 0; i < values.size() && i < limit; i++) {
            boolean condition = resolveConditionAt(conditionObj, i);
            if (!condition) {
                terminatedByCondition = true;
                break;
            }
            output.add(values.get(i));
            iterations++;
        }

        boolean hitLimit = values.size() > iterations && iterations >= limit && !terminatedByCondition;

        outputValues.put(OUTPUT_VALUES_ID, output);
        outputValues.put(OUTPUT_ITERATIONS_ID, iterations);
        outputValues.put(OUTPUT_TERMINATED_BY_CONDITION_ID, terminatedByCondition);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private int resolveLimit(Object inputLimit) {
        int resolved = maxIterations;
        if (inputLimit instanceof Number number) {
            resolved = number.intValue();
        }
        return clampMaxIterations(resolved);
    }

    private boolean resolveConditionAt(Object conditionObj, int index) {
        if (conditionObj instanceof List<?> list) {
            if (index >= list.size()) {
                return false;
            }
            Object entry = list.get(index);
            return coerceBoolean(entry);
        }
        if (conditionObj == null) {
            return defaultCondition;
        }
        return coerceBoolean(conditionObj);
    }

    private boolean coerceBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.isEmpty()) {
                return false;
            }
            return switch (normalized.toLowerCase(Locale.ROOT)) {
                case "true", "yes", "1", "on" -> true;
                default -> false;
            };
        }
        return value != null;
    }

    private static int clampMaxIterations(int value) {
        if (value < MIN_ITERATIONS) {
            return MIN_ITERATIONS;
        }
        return Math.min(value, MAX_ITERATIONS_CAP);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("maxIterations", maxIterations);
        state.put("defaultCondition", defaultCondition);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }

        Object maxIterationsObj = map.get("maxIterations");
        if (maxIterationsObj instanceof Number number) {
            maxIterations = clampMaxIterations(number.intValue());
        }

        Object defaultConditionObj = map.get("defaultCondition");
        if (defaultConditionObj instanceof Boolean value) {
            defaultCondition = value;
        }
    }
}


package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "math.scalar_math.clamp",
    displayName = "Clamp",
    description = "Restricts a value to the specified minimum and maximum values.",
    category = "math.scalar_math",
    order = 10
)
public class ClampNode extends BaseNode {

    private double defaultMin = 0.0;
    private double defaultMax = 1.0;

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_MIN_ID = "input_min";
    private static final String INPUT_MAX_ID = "input_max";
    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ClampNode() {
        super(UUID.randomUUID(), "math.scalar_math.clamp");
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Value to clamp", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MIN_ID, "Min", "Minimum allowed value", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MAX_ID, "Max", "Maximum allowed value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "The clamped value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the value input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Restricts a value to the specified minimum and maximum values.";
    }

    @Override
    public String getDisplayName() {
        return "Clamp";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valueObj = inputValues.get(INPUT_VALUE_ID);
        Object minObj = inputValues.get(INPUT_MIN_ID);
        Object maxObj = inputValues.get(INPUT_MAX_ID);

        if (!(valueObj instanceof Number valueNumber)) {
            outputValues.put(OUTPUT_RESULT_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double value = valueNumber.doubleValue();
        if (!Double.isFinite(value)) {
            outputValues.put(OUTPUT_RESULT_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double min = minObj instanceof Number number ? number.doubleValue() : defaultMin;
        double max = maxObj instanceof Number number ? number.doubleValue() : defaultMax;
        if (min > max) {
            double temp = min;
            min = max;
            max = temp;
        }

        outputValues.put(OUTPUT_RESULT_ID, Math.max(min, Math.min(max, value)));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public double getDefaultMin() {
        return defaultMin;
    }

    public void setDefaultMin(double min) {
        this.defaultMin = min;
        markDirty();
    }

    public double getDefaultMax() {
        return defaultMax;
    }

    public void setDefaultMax(double max) {
        this.defaultMax = max;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("defaultMin", getDefaultMin());
        state.put("defaultMax", getDefaultMax());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            Object obj = stateMap.get("defaultMin");
            if (obj instanceof Number number) {
                setDefaultMin(number.doubleValue());
            }
            obj = stateMap.get("defaultMax");
            if (obj instanceof Number number) {
                setDefaultMax(number.doubleValue());
            }
        }
    }
}

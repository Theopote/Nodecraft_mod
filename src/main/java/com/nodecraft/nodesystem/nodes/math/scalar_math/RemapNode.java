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
    id = "math.scalar_math.remap",
    displayName = "Remap",
    description = "Maps a value from an input range to an output range.",
    category = "math.scalar_math",
    order = 11
)
public class RemapNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_IN_MIN_ID = "input_in_min";
    private static final String INPUT_IN_MAX_ID = "input_in_max";
    private static final String INPUT_OUT_MIN_ID = "input_out_min";
    private static final String INPUT_OUT_MAX_ID = "input_out_max";
    private static final String INPUT_CLAMP_ID = "input_clamp";
    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_VALID_ID = "output_valid";

    private double defaultInMin = 0.0;
    private double defaultInMax = 1.0;
    private double defaultOutMin = 0.0;
    private double defaultOutMax = 1.0;
    private boolean defaultClamp = true;

    public RemapNode() {
        super(UUID.randomUUID(), "math.scalar_math.remap");
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Value to remap", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_IN_MIN_ID, "In Min", "Input range minimum", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_IN_MAX_ID, "In Max", "Input range maximum", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OUT_MIN_ID, "Out Min", "Output range minimum", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OUT_MAX_ID, "Out Max", "Output range maximum", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CLAMP_ID, "Clamp", "Clamp result to output range", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "The remapped value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the value input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Maps a value from an input range to an output range.";
    }

    @Override
    public String getDisplayName() {
        return "Remap";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valueObj = inputValues.get(INPUT_VALUE_ID);
        Object inMinObj = inputValues.get(INPUT_IN_MIN_ID);
        Object inMaxObj = inputValues.get(INPUT_IN_MAX_ID);
        Object outMinObj = inputValues.get(INPUT_OUT_MIN_ID);
        Object outMaxObj = inputValues.get(INPUT_OUT_MAX_ID);
        Object clampObj = inputValues.get(INPUT_CLAMP_ID);

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

        double inMin = inMinObj instanceof Number number ? number.doubleValue() : defaultInMin;
        double inMax = inMaxObj instanceof Number ? ((Number) inMaxObj).doubleValue() : defaultInMax;
        double outMin = outMinObj instanceof Number ? ((Number) outMinObj).doubleValue() : defaultOutMin;
        double outMax = outMaxObj instanceof Number ? ((Number) outMaxObj).doubleValue() : defaultOutMax;
        boolean clamp = clampObj instanceof Boolean ? (Boolean) clampObj : defaultClamp;

        double result;
        if (Math.abs(inMax - inMin) < 1e-10) {
            result = (outMin + outMax) / 2.0;
        } else {
            double normalizedValue = (value - inMin) / (inMax - inMin);
            result = outMin + normalizedValue * (outMax - outMin);
        }

        if (clamp) {
            double minOut = Math.min(outMin, outMax);
            double maxOut = Math.max(outMin, outMax);
            result = Math.max(minOut, Math.min(maxOut, result));
        }

        outputValues.put(OUTPUT_RESULT_ID, result);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public double getDefaultInMin() {
        return defaultInMin;
    }

    public void setDefaultInMin(double value) {
        this.defaultInMin = value;
        markDirty();
    }

    public double getDefaultInMax() {
        return defaultInMax;
    }

    public void setDefaultInMax(double value) {
        this.defaultInMax = value;
        markDirty();
    }

    public double getDefaultOutMin() {
        return defaultOutMin;
    }

    public void setDefaultOutMin(double value) {
        this.defaultOutMin = value;
        markDirty();
    }

    public double getDefaultOutMax() {
        return defaultOutMax;
    }

    public void setDefaultOutMax(double value) {
        this.defaultOutMax = value;
        markDirty();
    }

    public boolean getDefaultClamp() {
        return defaultClamp;
    }

    public void setDefaultClamp(boolean value) {
        this.defaultClamp = value;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("defaultInMin", getDefaultInMin());
        state.put("defaultInMax", getDefaultInMax());
        state.put("defaultOutMin", getDefaultOutMin());
        state.put("defaultOutMax", getDefaultOutMax());
        state.put("defaultClamp", getDefaultClamp());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            Object obj = stateMap.get("defaultInMin");
            if (obj instanceof Number) {
                setDefaultInMin(((Number) obj).doubleValue());
            }
            obj = stateMap.get("defaultInMax");
            if (obj instanceof Number) {
                setDefaultInMax(((Number) obj).doubleValue());
            }
            obj = stateMap.get("defaultOutMin");
            if (obj instanceof Number) {
                setDefaultOutMin(((Number) obj).doubleValue());
            }
            obj = stateMap.get("defaultOutMax");
            if (obj instanceof Number) {
                setDefaultOutMax(((Number) obj).doubleValue());
            }
            obj = stateMap.get("defaultClamp");
            if (obj instanceof Boolean) {
                setDefaultClamp((Boolean) obj);
            }
        }
    }
}

package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.ScalarMathOps;
import com.nodecraft.nodesystem.math.ScalarResult;
import com.nodecraft.nodesystem.util.NumericDomainResolver;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.scalar_math.remap",
    displayName = "Remap",
    description = "Maps a value from a source domain to a target domain.",
    category = "math.scalar_math",
    order = 11
)
public class RemapNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_SOURCE_ID = "input_source";
    private static final String INPUT_TARGET_ID = "input_target";
    private static final String INPUT_CLAMP_ID = "input_clamp";
    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_VALID_ID = "output_valid";

    private double defaultSourceStart = 0.0;
    private double defaultSourceEnd = 1.0;
    private double defaultTargetStart = 0.0;
    private double defaultTargetEnd = 1.0;
    private boolean defaultClamp = true;

    public RemapNode() {
        super(UUID.randomUUID(), "math.scalar_math.remap");
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Value to remap", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SOURCE_ID, "Source", "Source domain (Start→End)", NodeDataType.NUMERIC_RANGE, this));
        addInputPort(new BasePort(INPUT_TARGET_ID, "Target", "Target domain (Start→End)", NodeDataType.NUMERIC_RANGE, this));
        addInputPort(new BasePort(INPUT_CLAMP_ID, "Clamp", "Clamp result to target bounds", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "The remapped value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether inputs are finite and source domain is non-degenerate", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Maps a value from a source domain to a target domain. Supports reversed domains (e.g. 0→1 to 100→0).";
    }

    @Override
    public String getDisplayName() {
        return "Remap";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valueObj = inputValues.get(INPUT_VALUE_ID);
        Object clampObj = inputValues.get(INPUT_CLAMP_ID);

        if (!(valueObj instanceof Number valueNumber)) {
            outputValues.put(OUTPUT_RESULT_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        NumericRangeData source = NumericDomainResolver.resolveDomain(
            inputValues.get(INPUT_SOURCE_ID), defaultSourceStart, defaultSourceEnd);
        NumericRangeData target = NumericDomainResolver.resolveDomain(
            inputValues.get(INPUT_TARGET_ID), defaultTargetStart, defaultTargetEnd);
        boolean clamp = clampObj instanceof Boolean ? (Boolean) clampObj : defaultClamp;

        ScalarResult result = ScalarMathOps.remap(valueNumber.doubleValue(), source, target, clamp);
        outputValues.put(OUTPUT_RESULT_ID, result.value());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
    }

    public double getDefaultSourceStart() {
        return defaultSourceStart;
    }

    public void setDefaultSourceStart(double value) {
        this.defaultSourceStart = value;
        markDirty();
    }

    public double getDefaultSourceEnd() {
        return defaultSourceEnd;
    }

    public void setDefaultSourceEnd(double value) {
        this.defaultSourceEnd = value;
        markDirty();
    }

    public double getDefaultTargetStart() {
        return defaultTargetStart;
    }

    public void setDefaultTargetStart(double value) {
        this.defaultTargetStart = value;
        markDirty();
    }

    public double getDefaultTargetEnd() {
        return defaultTargetEnd;
    }

    public void setDefaultTargetEnd(double value) {
        this.defaultTargetEnd = value;
        markDirty();
    }

    /** @deprecated Use {@link #getDefaultSourceStart()}. */
    @Deprecated
    public double getDefaultInMin() {
        return defaultSourceStart;
    }

    /** @deprecated Use {@link #setDefaultSourceStart(double)}. */
    @Deprecated
    public void setDefaultInMin(double value) {
        setDefaultSourceStart(value);
    }

    /** @deprecated Use {@link #getDefaultSourceEnd()}. */
    @Deprecated
    public double getDefaultInMax() {
        return defaultSourceEnd;
    }

    /** @deprecated Use {@link #setDefaultSourceEnd(double)}. */
    @Deprecated
    public void setDefaultInMax(double value) {
        setDefaultSourceEnd(value);
    }

    /** @deprecated Use {@link #getDefaultTargetStart()}. */
    @Deprecated
    public double getDefaultOutMin() {
        return defaultTargetStart;
    }

    /** @deprecated Use {@link #setDefaultTargetStart(double)}. */
    @Deprecated
    public void setDefaultOutMin(double value) {
        setDefaultTargetStart(value);
    }

    /** @deprecated Use {@link #getDefaultTargetEnd()}. */
    @Deprecated
    public double getDefaultOutMax() {
        return defaultTargetEnd;
    }

    /** @deprecated Use {@link #setDefaultTargetEnd(double)}. */
    @Deprecated
    public void setDefaultOutMax(double value) {
        setDefaultTargetEnd(value);
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
        state.put("defaultSourceStart", getDefaultSourceStart());
        state.put("defaultSourceEnd", getDefaultSourceEnd());
        state.put("defaultTargetStart", getDefaultTargetStart());
        state.put("defaultTargetEnd", getDefaultTargetEnd());
        state.put("defaultClamp", getDefaultClamp());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            Object obj = stateMap.get("defaultSourceStart");
            if (obj instanceof Number) {
                setDefaultSourceStart(((Number) obj).doubleValue());
            } else if (stateMap.get("defaultInMin") instanceof Number n) {
                setDefaultSourceStart(n.doubleValue());
            }
            obj = stateMap.get("defaultSourceEnd");
            if (obj instanceof Number) {
                setDefaultSourceEnd(((Number) obj).doubleValue());
            } else if (stateMap.get("defaultInMax") instanceof Number n) {
                setDefaultSourceEnd(n.doubleValue());
            }
            obj = stateMap.get("defaultTargetStart");
            if (obj instanceof Number) {
                setDefaultTargetStart(((Number) obj).doubleValue());
            } else if (stateMap.get("defaultOutMin") instanceof Number n) {
                setDefaultTargetStart(n.doubleValue());
            }
            obj = stateMap.get("defaultTargetEnd");
            if (obj instanceof Number) {
                setDefaultTargetEnd(((Number) obj).doubleValue());
            } else if (stateMap.get("defaultOutMax") instanceof Number n) {
                setDefaultTargetEnd(n.doubleValue());
            }
            obj = stateMap.get("defaultClamp");
            if (obj instanceof Boolean) {
                setDefaultClamp((Boolean) obj);
            }
        }
    }
}

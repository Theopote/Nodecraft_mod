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
    id = "math.scalar_math.clamp",
    displayName = "Clamp",
    description = "Restricts a value to a domain's bounds (uses lower..upper, direction ignored).",
    category = "math.scalar_math",
    order = 10
)
public class ClampNode extends BaseNode {

    private double defaultStart = 0.0;
    private double defaultEnd = 1.0;

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_DOMAIN_ID = "input_domain";
    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ClampNode() {
        super(UUID.randomUUID(), "math.scalar_math.clamp");
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Value to clamp", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DOMAIN_ID, "Domain", "Allowed bounds (lower..upper)", NodeDataType.NUMERIC_RANGE, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "The clamped value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the value input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Restricts a value to a domain's bounds (uses lower..upper, direction ignored).";
    }

    @Override
    public String getDisplayName() {
        return "Clamp";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valueObj = inputValues.get(INPUT_VALUE_ID);

        if (!(valueObj instanceof Number valueNumber)) {
            outputValues.put(OUTPUT_RESULT_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        NumericRangeData domain = NumericDomainResolver.resolveDomain(
            inputValues.get(INPUT_DOMAIN_ID), defaultStart, defaultEnd);
        ScalarResult result = ScalarMathOps.clamp(valueNumber.doubleValue(), domain);
        outputValues.put(OUTPUT_RESULT_ID, result.value());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
    }

    public double getDefaultStart() {
        return defaultStart;
    }

    public void setDefaultStart(double start) {
        this.defaultStart = start;
        markDirty();
    }

    public double getDefaultEnd() {
        return defaultEnd;
    }

    public void setDefaultEnd(double end) {
        this.defaultEnd = end;
        markDirty();
    }

    /** @deprecated Use {@link #getDefaultStart()}. */
    @Deprecated
    public double getDefaultMin() {
        return defaultStart;
    }

    /** @deprecated Use {@link #setDefaultStart(double)}. */
    @Deprecated
    public void setDefaultMin(double min) {
        setDefaultStart(min);
    }

    /** @deprecated Use {@link #getDefaultEnd()}. */
    @Deprecated
    public double getDefaultMax() {
        return defaultEnd;
    }

    /** @deprecated Use {@link #setDefaultEnd(double)}. */
    @Deprecated
    public void setDefaultMax(double max) {
        setDefaultEnd(max);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("defaultStart", getDefaultStart());
        state.put("defaultEnd", getDefaultEnd());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            Object obj = stateMap.get("defaultStart");
            if (obj instanceof Number number) {
                setDefaultStart(number.doubleValue());
            } else if (stateMap.get("defaultMin") instanceof Number n) {
                setDefaultStart(n.doubleValue());
            }
            obj = stateMap.get("defaultEnd");
            if (obj instanceof Number number) {
                setDefaultEnd(number.doubleValue());
            } else if (stateMap.get("defaultMax") instanceof Number n) {
                setDefaultEnd(n.doubleValue());
            }
        }
    }
}

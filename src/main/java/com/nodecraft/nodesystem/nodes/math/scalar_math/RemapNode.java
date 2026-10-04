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
    private static final String OUTPUT_ERROR_ID = "output_error";

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
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
                "Whether inputs are finite and source domain is non-degenerate", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
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
        Double value = ScalarMathPorts.requireExactFinite(inputValues.get(INPUT_VALUE_ID));
        if (value == null) {
            writeInvalid(ScalarMathPorts.ERROR_INVALID_INPUT);
            return;
        }

        NumericRangeData source = NumericDomainResolver.resolveOptionalDomain(
            this, INPUT_SOURCE_ID, defaultSourceStart, defaultSourceEnd);
        NumericRangeData target = NumericDomainResolver.resolveOptionalDomain(
            this, INPUT_TARGET_ID, defaultTargetStart, defaultTargetEnd);
        if (source == null || target == null) {
            writeInvalid(ScalarMathPorts.ERROR_INVALID_DOMAIN);
            return;
        }

        Boolean clamp = ScalarMathPorts.resolveOptionalBoolean(this, INPUT_CLAMP_ID, defaultClamp);
        if (clamp == null) {
            writeInvalid(ScalarMathPorts.ERROR_INVALID_INPUT);
            return;
        }

        ScalarResult result = ScalarMathOps.remap(value, source, target, clamp);
        if (!result.valid()) {
            String error = source.start() == source.end()
                ? ScalarMathPorts.ERROR_DEGENERATE_DOMAIN
                : ScalarMathPorts.ERROR_NON_FINITE_RESULT;
            writeInvalid(error);
            return;
        }
        outputValues.put(OUTPUT_RESULT_ID, result.value());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_RESULT_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
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
            if (obj instanceof Number number) {
                setDefaultSourceStart(number.doubleValue());
            }
            obj = stateMap.get("defaultSourceEnd");
            if (obj instanceof Number number) {
                setDefaultSourceEnd(number.doubleValue());
            }
            obj = stateMap.get("defaultTargetStart");
            if (obj instanceof Number number) {
                setDefaultTargetStart(number.doubleValue());
            }
            obj = stateMap.get("defaultTargetEnd");
            if (obj instanceof Number number) {
                setDefaultTargetEnd(number.doubleValue());
            }
            obj = stateMap.get("defaultClamp");
            if (obj instanceof Boolean bool) {
                setDefaultClamp(bool);
            }
        }
    }
}

package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.ScalarMathOps;
import com.nodecraft.nodesystem.math.ScalarResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.scalar_math.power",
    displayName = "Power (^)",
    description = "Computes Base raised to Exponent.",
    category = "math.scalar_math",
    order = 5
)
public class PowerNode extends BaseNode {

    private static final String INPUT_BASE_ID = "input_base";
    private static final String INPUT_EXPONENT_ID = "input_exponent";
    private static final String OUTPUT_POWER_ID = "output_power";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public PowerNode() {
        super(UUID.randomUUID(), "math.scalar_math.power");
        addInputPort(new BasePort(INPUT_BASE_ID, "Base", "The base value", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_EXPONENT_ID, "Exponent", "The exponent value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_POWER_ID, "Power", "Result of Base ^ Exponent", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the power result is finite", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Computes Base raised to Exponent.";
    }

    @Override
    public String getDisplayName() {
        return "Power (^)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Double base = ScalarMathPorts.requireExactFinite(inputValues.get(INPUT_BASE_ID));
        Double exponent = ScalarMathPorts.requireExactFinite(inputValues.get(INPUT_EXPONENT_ID));
        if (base == null || exponent == null) {
            publish(ScalarResult.invalid(), ScalarMathPorts.ERROR_INVALID_INPUT);
            return;
        }
        ScalarResult result = ScalarMathOps.pow(base, exponent);
        publish(result, result.valid() ? "" : ScalarMathPorts.ERROR_NON_FINITE_RESULT);
    }

    private void publish(ScalarResult result, String error) {
        outputValues.put(OUTPUT_POWER_ID, result.value());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

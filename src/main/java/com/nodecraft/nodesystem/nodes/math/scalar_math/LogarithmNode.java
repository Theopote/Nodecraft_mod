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
    id = "math.scalar_math.logarithm",
    displayName = "Logarithm (log)",
    description = "Computes the logarithm of Number using Base.",
    category = "math.scalar_math",
    order = 6
)
public class LogarithmNode extends BaseNode {

    private static final String INPUT_NUMBER_ID = "input_number";
    private static final String INPUT_BASE_ID = "input_base";
    private static final String OUTPUT_LOGARITHM_ID = "output_logarithm";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public LogarithmNode() {
        super(UUID.randomUUID(), "math.scalar_math.logarithm");
        addInputPort(new BasePort(INPUT_NUMBER_ID, "Number", "The number", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_BASE_ID, "Base", "The base, defaults to e", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_LOGARITHM_ID, "Logarithm", "Result of log base B of A", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether inputs define a valid logarithm", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Computes the logarithm of Number using Base.";
    }

    @Override
    public String getDisplayName() {
        return "Logarithm (log)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Double number = ScalarMathPorts.requireExactFinite(inputValues.get(INPUT_NUMBER_ID));
        Double base;
        if (ScalarMathPorts.isPortDriven(this, INPUT_BASE_ID)) {
            base = ScalarMathPorts.requireExactFinite(getInput(INPUT_BASE_ID));
        } else {
            base = Math.E;
        }
        if (number == null || base == null) {
            publish(ScalarResult.invalid(), ScalarMathPorts.ERROR_INVALID_INPUT);
            return;
        }
        ScalarResult result = ScalarMathOps.log(number, base);
        publish(result, result.valid() ? "" : ScalarMathPorts.ERROR_INVALID_DOMAIN);
    }

    private void publish(ScalarResult result, String error) {
        outputValues.put(OUTPUT_LOGARITHM_ID, result.value());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

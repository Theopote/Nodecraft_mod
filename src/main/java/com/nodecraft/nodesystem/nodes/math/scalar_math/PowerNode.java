package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
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

    public PowerNode() {
        super(UUID.randomUUID(), "math.scalar_math.power");
        addInputPort(new BasePort(INPUT_BASE_ID, "Base", "The base value", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_EXPONENT_ID, "Exponent", "The exponent value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_POWER_ID, "Power", "Result of Base ^ Exponent", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the power result is finite", NodeDataType.BOOLEAN, this));
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
        Object valBase = inputValues.get(INPUT_BASE_ID);
        Object valExponent = inputValues.get(INPUT_EXPONENT_ID);

        if (!(valBase instanceof Number baseNumber) || !(valExponent instanceof Number exponentNumber)) {
            outputValues.put(OUTPUT_POWER_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double base = baseNumber.doubleValue();
        double exponent = exponentNumber.doubleValue();
        if (!Double.isFinite(base) || !Double.isFinite(exponent)) {
            outputValues.put(OUTPUT_POWER_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double result = Math.pow(base, exponent);
        outputValues.put(OUTPUT_POWER_ID, result);
        outputValues.put(OUTPUT_VALID_ID, Double.isFinite(result));
    }
}

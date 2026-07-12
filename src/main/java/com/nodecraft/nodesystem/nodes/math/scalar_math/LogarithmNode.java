package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
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

    public LogarithmNode() {
        super(UUID.randomUUID(), "math.scalar_math.logarithm");
        addInputPort(new BasePort(INPUT_NUMBER_ID, "Number", "The number", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_BASE_ID, "Base", "The base, defaults to e", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_LOGARITHM_ID, "Logarithm", "Result of log base B of A", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether inputs define a valid logarithm", NodeDataType.BOOLEAN, this));
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
        Object valNumber = inputValues.get(INPUT_NUMBER_ID);
        Object valBase = inputValues.getOrDefault(INPUT_BASE_ID, Math.E);

        if (!(valNumber instanceof Number numberValue) || !(valBase instanceof Number baseNumber)) {
            outputValues.put(OUTPUT_LOGARITHM_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double number = numberValue.doubleValue();
        double base = baseNumber.doubleValue();
        if (!Double.isFinite(number) || !Double.isFinite(base)
            || number <= 0.0d || base <= 0.0d || Math.abs(base - 1.0d) < 1.0e-10d) {
            outputValues.put(OUTPUT_LOGARITHM_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_LOGARITHM_ID, Math.log(number) / Math.log(base));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

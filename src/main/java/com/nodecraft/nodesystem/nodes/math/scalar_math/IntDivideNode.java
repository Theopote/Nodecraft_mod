package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.scalar_math.int_divide",
    displayName = "Integer Divide",
    description = "Performs floor-style integer division A / B and returns quotient and remainder.",
    category = "math.scalar_math",
    order = 18
)
public class IntDivideNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";

    private static final String OUTPUT_QUOTIENT_ID = "output_quotient";
    private static final String OUTPUT_REMAINDER_ID = "output_remainder";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public IntDivideNode() {
        super(UUID.randomUUID(), "math.scalar_math.int_divide");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Dividend", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Divisor", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_QUOTIENT_ID, "Quotient", "Floor-division quotient", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMAINDER_ID, "Remainder", "Remainder using floorMod", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether division succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Integer Divide";
    }

    @Override
    public String getDescription() {
        return "Performs floor-style integer division A / B and returns quotient and remainder.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object aObj = inputValues.get(INPUT_A_ID);
        Object bObj = inputValues.get(INPUT_B_ID);

        if (!(aObj instanceof Number aNum) || !(bObj instanceof Number bNum)) {
            outputValues.put(OUTPUT_QUOTIENT_ID, 0);
            outputValues.put(OUTPUT_REMAINDER_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        int a = aNum.intValue();
        int b = bNum.intValue();
        if (b == 0) {
            outputValues.put(OUTPUT_QUOTIENT_ID, 0);
            outputValues.put(OUTPUT_REMAINDER_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_QUOTIENT_ID, Math.floorDiv(a, b));
        outputValues.put(OUTPUT_REMAINDER_ID, Math.floorMod(a, b));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}


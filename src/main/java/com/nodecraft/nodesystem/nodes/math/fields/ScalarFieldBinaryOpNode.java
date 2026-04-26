package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.fields.scalar_binary_op",
    displayName = "Scalar Field Binary Op",
    description = "Combines two scalar fields with a basic arithmetic operation.",
    category = "math.fields",
    order = 4
)
public class ScalarFieldBinaryOpNode extends BaseNode {

    public enum ScalarBinaryOp {
        ADD,
        SUB,
        MUL,
        DIV,
        MIN,
        MAX,
        POW
    }

    @NodeProperty(displayName = "Operation", category = "Field", order = 1)
    private ScalarBinaryOp operation = ScalarBinaryOp.ADD;

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String OUTPUT_FIELD_ID = "output_field";

    public ScalarFieldBinaryOpNode() {
        super(UUID.randomUUID(), "math.fields.scalar_binary_op");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Left scalar field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Right scalar field", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Combined scalar field", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public String getDescription() {
        return "Combines two scalar fields with a basic arithmetic operation.";
    }

    @Override
    public String getDisplayName() {
        return "Scalar Field Binary Op";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object aObj = inputValues.get(INPUT_A_ID);
        Object bObj = inputValues.get(INPUT_B_ID);
        if (!(aObj instanceof ScalarFieldData a) || !(bObj instanceof ScalarFieldData b)) {
            outputValues.put(OUTPUT_FIELD_ID, null);
            return;
        }

        ScalarBinaryOp op = operation == null ? ScalarBinaryOp.ADD : operation;
        ScalarFieldData field = switch (op) {
            case ADD -> point -> a.sampleScalar(point) + b.sampleScalar(point);
            case SUB -> point -> a.sampleScalar(point) - b.sampleScalar(point);
            case MUL -> point -> a.sampleScalar(point) * b.sampleScalar(point);
            case DIV -> point -> safeDiv(a.sampleScalar(point), b.sampleScalar(point));
            case MIN -> point -> Math.min(a.sampleScalar(point), b.sampleScalar(point));
            case MAX -> point -> Math.max(a.sampleScalar(point), b.sampleScalar(point));
            case POW -> point -> safePow(a.sampleScalar(point), b.sampleScalar(point));
        };

        outputValues.put(OUTPUT_FIELD_ID, field);
    }

    private static double safeDiv(double x, double y) {
        if (y == 0.0d) {
            return x >= 0.0d ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        return x / y;
    }

    private static double safePow(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return Double.NaN;
        }
        return Math.pow(x, y);
    }
}

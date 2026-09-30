package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.FieldMath;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.fields.scalar_binary_op",
    displayName = "Combine Scalar Fields",
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
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ScalarFieldBinaryOpNode() {
        super(UUID.randomUUID(), "math.fields.scalar_binary_op");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Left scalar field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Right scalar field", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Combined scalar field", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the combined field was constructed",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Combines two scalar fields with a basic arithmetic operation.";
    }

    @Override
    public String getDisplayName() {
        return "Combine Scalar Fields";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object aObj = inputValues.get(INPUT_A_ID);
        Object bObj = inputValues.get(INPUT_B_ID);
        if (!(aObj instanceof ScalarFieldData a) || !(bObj instanceof ScalarFieldData b)) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_FIELD);
            return;
        }

        ScalarBinaryOp op = operation == null ? ScalarBinaryOp.ADD : operation;
        FieldMath.ScalarCombineOp combineOp = FieldMath.ScalarCombineOp.valueOf(op.name());
        ScalarFieldData field = point -> FieldMath.combineScalars(
                a.sampleScalar(point), b.sampleScalar(point), combineOp);

        outputValues.put(OUTPUT_FIELD_ID, field);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_FIELD_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.fields.vector_binary_op",
    displayName = "Combine Vector Fields",
    description = "Combines two vector fields component-wise or via cross product.",
    category = "math.fields",
    order = 7
)
public class VectorFieldBinaryOpNode extends BaseNode {

    public enum VectorBinaryOp {
        ADD,
        SUB,
        MUL_COMPONENT,
        CROSS
    }

    @NodeProperty(displayName = "Operation", category = "Field", order = 1)
    private VectorBinaryOp operation = VectorBinaryOp.ADD;

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String OUTPUT_FIELD_ID = "output_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public VectorFieldBinaryOpNode() {
        super(UUID.randomUUID(), "math.fields.vector_binary_op");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Left vector field", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Right vector field", NodeDataType.VECTOR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Combined vector field", NodeDataType.VECTOR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the combined field was constructed",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Combines two vector fields component-wise or via cross product.";
    }

    @Override
    public String getDisplayName() {
        return "Combine Vector Fields";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object aObj = inputValues.get(INPUT_A_ID);
        Object bObj = inputValues.get(INPUT_B_ID);
        if (!(aObj instanceof VectorFieldData a) || !(bObj instanceof VectorFieldData b)) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_FIELD);
            return;
        }

        VectorBinaryOp op = operation == null ? VectorBinaryOp.ADD : operation;
        VectorFieldData field = (point, dest) -> {
            a.sampleVector(point, dest);
            double ax = dest.x;
            double ay = dest.y;
            double az = dest.z;
            b.sampleVector(point, dest);
            switch (op) {
                case ADD -> dest.add(ax, ay, az);
                case SUB -> dest.set(ax - dest.x, ay - dest.y, az - dest.z);
                case MUL_COMPONENT -> dest.set(ax * dest.x, ay * dest.y, az * dest.z);
                case CROSS -> dest.set(
                        ay * dest.z - az * dest.y,
                        az * dest.x - ax * dest.z,
                        ax * dest.y - ay * dest.x);
            }
        };

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

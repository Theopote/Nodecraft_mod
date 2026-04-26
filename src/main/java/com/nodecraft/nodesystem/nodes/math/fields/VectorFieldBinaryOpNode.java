package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "math.fields.vector_binary_op",
    displayName = "Vector Field Binary Op",
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

    private final Vector3d tmpA = new Vector3d();
    private final Vector3d tmpB = new Vector3d();

    public VectorFieldBinaryOpNode() {
        super(UUID.randomUUID(), "math.fields.vector_binary_op");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Left vector field", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Right vector field", NodeDataType.VECTOR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Combined vector field", NodeDataType.VECTOR_FIELD, this));
    }

    @Override
    public String getDescription() {
        return "Combines two vector fields component-wise or via cross product.";
    }

    @Override
    public String getDisplayName() {
        return "Vector Field Binary Op";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object aObj = inputValues.get(INPUT_A_ID);
        Object bObj = inputValues.get(INPUT_B_ID);
        if (!(aObj instanceof VectorFieldData a) || !(bObj instanceof VectorFieldData b)) {
            outputValues.put(OUTPUT_FIELD_ID, null);
            return;
        }

        VectorBinaryOp op = operation == null ? VectorBinaryOp.ADD : operation;
        VectorFieldData field = (point, dest) -> {
            a.sampleVector(point, tmpA);
            b.sampleVector(point, tmpB);
            switch (op) {
                case ADD -> dest.set(tmpA).add(tmpB);
                case SUB -> dest.set(tmpA).sub(tmpB);
                case MUL_COMPONENT -> dest.set(tmpA.x * tmpB.x, tmpA.y * tmpB.y, tmpA.z * tmpB.z);
                case CROSS -> dest.set(tmpA).cross(tmpB);
            }
        };

        outputValues.put(OUTPUT_FIELD_ID, field);
    }
}

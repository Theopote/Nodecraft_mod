package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.fields.vector_constant",
    displayName = "Vector Field Constant",
    description = "Builds a vector field that returns a constant vector everywhere.",
    category = "math.fields",
    order = 5
)
public class VectorFieldConstantNode extends BaseNode {

    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_Z_ID = "input_z";
    private static final String OUTPUT_FIELD_ID = "output_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public VectorFieldConstantNode() {
        super(UUID.randomUUID(), "math.fields.vector_constant");

        addInputPort(new BasePort(INPUT_X_ID, "X", "Constant vector X", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Constant vector Y", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Z_ID, "Z", "Constant vector Z", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Vector field F(p) = v", NodeDataType.VECTOR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the field was constructed",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Builds a vector field that returns a constant vector everywhere.";
    }

    @Override
    public String getDisplayName() {
        return "Vector Field Constant";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Double x = FieldSampleUtils.resolveOptionalFiniteDouble(this, INPUT_X_ID, 0.0d);
        Double y = FieldSampleUtils.resolveOptionalFiniteDouble(this, INPUT_Y_ID, 0.0d);
        Double z = FieldSampleUtils.resolveOptionalFiniteDouble(this, INPUT_Z_ID, 0.0d);
        if (x == null || y == null || z == null) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_INPUT);
            return;
        }

        final double vx = x;
        final double vy = y;
        final double vz = z;
        VectorFieldData field = (point, dest) -> dest.set(vx, vy, vz);
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

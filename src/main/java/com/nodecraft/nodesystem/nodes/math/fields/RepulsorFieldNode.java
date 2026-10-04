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
    id = "math.fields.repulsor_field",
    displayName = "Repulsor Field",
    description = "Inverts a vector field direction (repulsion) with optional strength scaling.",
    category = "math.fields",
    order = 12
)
public class RepulsorFieldNode extends BaseNode {

    @NodeProperty(displayName = "Strength", category = "Repulsor", order = 1)
    private double strength = 1.0d;

    private static final String INPUT_FIELD_ID = "input_field";
    private static final String INPUT_STRENGTH_ID = "input_strength";
    private static final String OUTPUT_FIELD_ID = "output_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public RepulsorFieldNode() {
        super(UUID.randomUUID(), "math.fields.repulsor_field");

        addInputPort(new BasePort(INPUT_FIELD_ID, "Field", "Input attractor/vector field", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Repulsion scale override", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Repulsor vector field output", NodeDataType.VECTOR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the field was constructed",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Inverts a vector field direction (repulsion) with optional strength scaling.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object fieldObj = inputValues.get(INPUT_FIELD_ID);
        if (!(fieldObj instanceof VectorFieldData field)) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_FIELD);
            return;
        }

        Double effectiveStrength = FieldSampleUtils.resolveOptionalFiniteDouble(this, INPUT_STRENGTH_ID, strength);
        if (effectiveStrength == null) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_INPUT);
            return;
        }

        final double strengthFinal = effectiveStrength;
        VectorFieldData repulsor = (point, dest) -> {
            field.sampleVector(point, dest);
            dest.mul(-strengthFinal);
        };

        outputValues.put(OUTPUT_FIELD_ID, repulsor);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_FIELD_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

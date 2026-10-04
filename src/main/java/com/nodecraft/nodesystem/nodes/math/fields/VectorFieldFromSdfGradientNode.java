package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.fields.vector_from_sdf_gradient",
    displayName = "Vector Field From SDF Gradient",
    description = "Builds a vector field from central-difference gradients of an SDF (normalized direction).",
    category = "math.fields",
    order = 6
)
public class VectorFieldFromSdfGradientNode extends BaseNode {

    @NodeProperty(displayName = "Step", category = "SDF", order = 1, description = "Finite difference step size")
    private double step = 0.25d;

    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String OUTPUT_FIELD_ID = "output_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public VectorFieldFromSdfGradientNode() {
        super(UUID.randomUUID(), "math.fields.vector_from_sdf_gradient");

        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Signed distance field input", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Finite difference step size", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Vector field aligned with SDF gradient",
                NodeDataType.VECTOR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the field was constructed",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Builds a vector field from central-difference gradients of an SDF (normalized direction).";
    }

    @Override
    public String getDisplayName() {
        return "Vector Field From SDF Gradient";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_FIELD);
            return;
        }

        Double h = FieldSampleUtils.resolveOptionalPositiveDouble(this, INPUT_STEP_ID, step);
        if (h == null) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_INPUT);
            return;
        }

        final double stepSize = h;
        VectorFieldData field = (point, dest) ->
                FieldSampleUtils.sampleSdfGradientDirection(sdf, point, stepSize, dest);

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

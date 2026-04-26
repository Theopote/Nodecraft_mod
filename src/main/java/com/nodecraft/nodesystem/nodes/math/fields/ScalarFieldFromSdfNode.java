package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.fields.scalar_from_sdf",
    displayName = "Scalar Field From SDF",
    description = "Wraps a signed distance field as a scalar field using its distance value.",
    category = "math.fields",
    order = 2
)
public class ScalarFieldFromSdfNode extends BaseNode {

    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String OUTPUT_FIELD_ID = "output_field";

    public ScalarFieldFromSdfNode() {
        super(UUID.randomUUID(), "math.fields.scalar_from_sdf");
        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Signed distance field input", NodeDataType.SDF, this));
        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Scalar field f(p) = sdf.distance(p)", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public String getDescription() {
        return "Wraps a signed distance field as a scalar field using its distance value.";
    }

    @Override
    public String getDisplayName() {
        return "Scalar Field From SDF";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            outputValues.put(OUTPUT_FIELD_ID, null);
            return;
        }

        ScalarFieldData field = sdf::sampleDistance;
        outputValues.put(OUTPUT_FIELD_ID, field);
    }
}

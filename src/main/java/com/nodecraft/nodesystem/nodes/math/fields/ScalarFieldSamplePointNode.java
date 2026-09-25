package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.fields.scalar_sample_point",
    displayName = "Scalar Field Sample Point",
    description = "Samples a scalar field at a point.",
    category = "math.fields",
    order = 8
)
public class ScalarFieldSamplePointNode extends BaseNode {

    private static final String INPUT_FIELD_ID = "input_field";
    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ScalarFieldSamplePointNode() {
        super(UUID.randomUUID(), "math.fields.scalar_sample_point");

        addInputPort(new BasePort(INPUT_FIELD_ID, "Field", "Scalar field input", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", "Query point", NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "Sampled scalar", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when sample is finite", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Samples a scalar field at a point.";
    }

    @Override
    public String getDisplayName() {
        return "Scalar Field Sample Point";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object fieldObj = inputValues.get(INPUT_FIELD_ID);
        Vector3d p = FieldSampleUtils.resolvePoint(inputValues.get(INPUT_POINT_ID));
        if (!(fieldObj instanceof ScalarFieldData field) || p == null) {
            outputValues.put(OUTPUT_VALUE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        FieldSampleUtils.ScalarSample sample = FieldSampleUtils.sampleScalar(field, p);
        outputValues.put(OUTPUT_VALUE_ID, sample.value());
        outputValues.put(OUTPUT_VALID_ID, sample.valid());
    }
}

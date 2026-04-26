package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "math.fields.vector_sample_point",
    displayName = "Vector Field Sample Point",
    description = "Samples a vector field at a point.",
    category = "math.fields",
    order = 10
)
public class VectorFieldSamplePointNode extends BaseNode {

    private static final String INPUT_FIELD_ID = "input_field";
    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public VectorFieldSamplePointNode() {
        super(UUID.randomUUID(), "math.fields.vector_sample_point");

        addInputPort(new BasePort(INPUT_FIELD_ID, "Field", "Vector field input", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", "Query point", NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector", "Sampled vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when sampling succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Samples a vector field at a point.";
    }

    @Override
    public String getDisplayName() {
        return "Vector Field Sample Point";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object fieldObj = inputValues.get(INPUT_FIELD_ID);
        Vector3d p = FieldSampleUtils.resolvePoint(inputValues.get(INPUT_POINT_ID));
        if (!(fieldObj instanceof VectorFieldData field) || p == null) {
            outputValues.put(OUTPUT_VECTOR_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d out = new Vector3d();
        field.sampleVector(p, out);
        outputValues.put(OUTPUT_VECTOR_ID, out);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

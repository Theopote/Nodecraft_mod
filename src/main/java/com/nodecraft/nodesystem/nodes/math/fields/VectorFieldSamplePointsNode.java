package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.fields.vector_sample_points",
    displayName = "Vector Field Sample Points",
    description = "Samples a vector field for each query point and outputs a vector list.",
    category = "math.fields",
    order = 11
)
public class VectorFieldSamplePointsNode extends BaseNode {

    private static final String INPUT_FIELD_ID = "input_field";
    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_VECTORS_ID = "output_vectors";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public VectorFieldSamplePointsNode() {
        super(UUID.randomUUID(), "math.fields.vector_sample_points");

        addInputPort(new BasePort(INPUT_FIELD_ID, "Field", "Vector field input", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Query point list", NodeDataType.POINT_LIST, this));

        addOutputPort(new BasePort(OUTPUT_VECTORS_ID, "Vectors", "Vector samples aligned with resolved points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of resolved samples", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when field exists and at least one point resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Samples a vector field for each query point and outputs a vector list.";
    }

    @Override
    public String getDisplayName() {
        return "Vector Field Sample Points";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object fieldObj = inputValues.get(INPUT_FIELD_ID);
        if (!(fieldObj instanceof VectorFieldData field)) {
            writeInvalid();
            return;
        }

        List<Vector3d> points = FieldSampleUtils.resolvePointList(inputValues.get(INPUT_POINTS_ID));
        if (points.isEmpty()) {
            writeInvalid();
            return;
        }

        List<Vector3d> vectors = new ArrayList<>(points.size());
        Vector3d tmp = new Vector3d();
        for (Vector3d p : points) {
            field.sampleVector(p, tmp);
            vectors.add(new Vector3d(tmp));
        }

        outputValues.put(OUTPUT_VECTORS_ID, List.copyOf(vectors));
        outputValues.put(OUTPUT_COUNT_ID, vectors.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_VECTORS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

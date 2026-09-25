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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.fields.scalar_sample_points",
    displayName = "Scalar Field Sample Points",
    description = "Samples a scalar field for each query point and outputs a value list.",
    category = "math.fields",
    order = 9
)
public class ScalarFieldSamplePointsNode extends BaseNode {

    private static final String INPUT_FIELD_ID = "input_field";
    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_VALUES_ID = "output_values";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ScalarFieldSamplePointsNode() {
        super(UUID.randomUUID(), "math.fields.scalar_sample_points");

        addInputPort(new BasePort(INPUT_FIELD_ID, "Field", "Scalar field input", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Query point list", NodeDataType.POINT_LIST, this));

        addOutputPort(new BasePort(OUTPUT_VALUES_ID, "Values", "Scalar samples aligned with resolved points", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of resolved samples", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when all samples are finite", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Samples a scalar field for each query point and outputs a value list.";
    }

    @Override
    public String getDisplayName() {
        return "Scalar Field Sample Points";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object fieldObj = inputValues.get(INPUT_FIELD_ID);
        if (!(fieldObj instanceof ScalarFieldData field)) {
            writeInvalid();
            return;
        }

        List<Vector3d> points = FieldSampleUtils.resolvePointList(inputValues.get(INPUT_POINTS_ID));
        if (points.isEmpty()) {
            writeInvalid();
            return;
        }

        List<Double> values = new ArrayList<>(points.size());
        for (Vector3d p : points) {
            FieldSampleUtils.ScalarSample sample = FieldSampleUtils.sampleScalar(field, p);
            if (!sample.valid()) {
                writeInvalid();
                return;
            }
            values.add(sample.value());
        }

        outputValues.put(OUTPUT_VALUES_ID, Collections.unmodifiableList(values));
        outputValues.put(OUTPUT_COUNT_ID, values.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_VALUES_ID, Collections.emptyList());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

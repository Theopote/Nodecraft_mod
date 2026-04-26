package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@NodeInfo(
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
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Query point list", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_VALUES_ID, "Values", "Scalar samples aligned with resolved points", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of resolved samples", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when field exists and at least one point resolved", NodeDataType.BOOLEAN, this));
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
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        if (!(fieldObj instanceof ScalarFieldData field) || !(pointsObj instanceof Collection<?> collection)) {
            writeInvalid();
            return;
        }

        List<Double> values = new ArrayList<>();
        for (Object entry : collection) {
            Vector3d p = FieldSampleUtils.resolvePoint(entry);
            if (p == null) {
                continue;
            }
            values.add(field.sampleScalar(p));
        }

        if (values.isEmpty()) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_VALUES_ID, List.copyOf(values));
        outputValues.put(OUTPUT_COUNT_ID, values.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_VALUES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

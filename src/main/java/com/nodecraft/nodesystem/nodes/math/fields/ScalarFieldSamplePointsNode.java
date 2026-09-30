package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
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
    private static final String OUTPUT_ERROR_ID = "output_error";

    /** Package-visible for budget contract tests; production uses {@link GenerationLimits#MAX_FIELD_SAMPLE_POINTS}. */
    int samplePointLimit = GenerationLimits.MAX_FIELD_SAMPLE_POINTS;

    public ScalarFieldSamplePointsNode() {
        super(UUID.randomUUID(), "math.fields.scalar_sample_points");

        addInputPort(new BasePort(INPUT_FIELD_ID, "Field", "Scalar field input", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Query point list", NodeDataType.POINT_LIST, this));

        addOutputPort(new BasePort(OUTPUT_VALUES_ID, "Values",
                "Scalar samples aligned with input points", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of samples (matches input point count)",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when all samples are finite",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
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
            writeInvalid(FieldSampleUtils.ERROR_INVALID_FIELD);
            return;
        }

        FieldSampleUtils.PointListResult pointsResult =
                FieldSampleUtils.resolvePointListStrict(inputValues.get(INPUT_POINTS_ID));
        if (!pointsResult.valid()) {
            writeInvalid(pointsResult.error());
            return;
        }
        List<Vector3d> points = pointsResult.points();

        if (points != null && points.size() > samplePointLimit) {
            writeInvalid(FieldSampleUtils.ERROR_OUTPUT_BUDGET_EXCEEDED);
            return;
        }

        if (points != null && points.isEmpty()) {
            outputValues.put(OUTPUT_VALUES_ID, Collections.emptyList());
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, true);
            outputValues.put(OUTPUT_ERROR_ID, "");
            return;
        }

        List<Double> values = null;
        if (points != null) {
            values = new ArrayList<>(points.size());
        }
        if (points != null) {
            for (Vector3d p : points) {
                FieldSampleUtils.ScalarSample sample = FieldSampleUtils.sampleScalar(field, p);
                if (!sample.valid()) {
                    writeInvalid(FieldSampleUtils.ERROR_INVALID_INPUT);
                    return;
                }
                values.add(sample.value());
            }
        }

        if (values != null) {
            outputValues.put(OUTPUT_VALUES_ID, Collections.unmodifiableList(values));
        }
        if (values != null) {
            outputValues.put(OUTPUT_COUNT_ID, values.size());
        }
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_VALUES_ID, Collections.emptyList());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

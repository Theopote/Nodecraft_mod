package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PointUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.sdf_sample_points",
    displayName = "SDF Sample Points",
    description = "Samples signed distance for each query point and outputs distance and inside lists",
    category = "geometry.sdf",
    order = 18
)
public class SdfSamplePointsNode extends AbstractSdfNode {
    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_INSIDE_ID = "output_inside";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public SdfSamplePointsNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_sample_points");
        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Signed distance field input", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Query point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances",
            "Signed distance list aligned with input points", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_INSIDE_ID, "Inside",
            "Boolean list where true means distance <= 0", NodeDataType.BOOLEAN_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of query points", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs("True when SDF and every query point are valid");
    }

    @Override
    public String getDescription() {
        return "Samples signed distance for each query point and outputs distance and inside lists";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            writeFailure("SDF input is required");
            return;
        }
        if (!(pointsObj instanceof Collection<?> collection)) {
            writeFailure("Points must be a POINT_LIST");
            return;
        }
        if (collection.isEmpty()) {
            putEmptyListOutputs(OUTPUT_DISTANCES_ID, OUTPUT_INSIDE_ID);
            putIntOutputs(0, OUTPUT_COUNT_ID);
            markSuccess();
            return;
        }

        List<Vector3d> points = PointUtils.resolveStrictPointListBounded(
            pointsObj, GenerationLimits.MAX_SDF_SAMPLE_POINTS);
        if (points == null) {
            writeFailure("Every entry in Points must be a finite PointData within MAX_SDF_SAMPLE_POINTS (no silent drop)");
            return;
        }

        List<Double> distances = new ArrayList<>(points.size());
        List<Boolean> inside = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            double d = sdf.sampleDistance(point);
            if (!Double.isFinite(d)) {
                writeFailure("Sampled distance is not finite");
                return;
            }
            distances.add(d);
            inside.add(d <= 0.0d);
        }

        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
        outputValues.put(OUTPUT_INSIDE_ID, List.copyOf(inside));
        putIntOutputs(distances.size(), OUTPUT_COUNT_ID);
        markSuccess();
    }

    private void writeFailure(String error) {
        putEmptyListOutputs(OUTPUT_DISTANCES_ID, OUTPUT_INSIDE_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }
}

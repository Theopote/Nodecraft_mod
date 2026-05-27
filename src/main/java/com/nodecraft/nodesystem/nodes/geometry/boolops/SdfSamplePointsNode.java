package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.boolean.sdf_sample_points",
    displayName = "SDF Sample Points",
    description = "Samples signed distance for each query point and outputs distance and inside lists",
    category = "geometry.boolean",
    order = 18
)
public class SdfSamplePointsNode extends BaseNode {
    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_POINTS_ID = "input_points";

    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_INSIDE_ID = "output_inside";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SdfSamplePointsNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_sample_points");

        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Signed distance field input", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Query point list", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances", "Signed distance list aligned with input points", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_INSIDE_ID, "Inside", "Boolean list where true means distance <= 0", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of resolved query points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when SDF and at least one query point are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Samples signed distance for each query point and outputs distance and inside lists";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf) || !(pointsObj instanceof Collection<?> collection)) {
            writeInvalid();
            return;
        }

        List<Double> distances = new ArrayList<>();
        List<Boolean> inside = new ArrayList<>();
        for (Object entry : collection) {
            Vector3d point = resolvePoint(entry);
            if (point == null) {
                continue;
            }
            double d = sdf.sampleDistance(point);
            distances.add(d);
            inside.add(d <= 0.0d);
        }

        if (distances.isEmpty()) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
        outputValues.put(OUTPUT_INSIDE_ID, List.copyOf(inside));
        outputValues.put(OUTPUT_COUNT_ID, distances.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_DISTANCES_ID, List.of());
        outputValues.put(OUTPUT_INSIDE_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }
}

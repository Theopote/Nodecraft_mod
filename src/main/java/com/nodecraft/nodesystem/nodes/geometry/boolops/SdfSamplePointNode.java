package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.sdf_sample_point",
    displayName = "SDF Sample Point",
    description = "Samples signed distance at a query point and reports inside/outside state",
    category = "geometry.sdf",
    order = 17
)
public class SdfSamplePointNode extends AbstractSdfNode {
    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_INSIDE_ID = "output_inside";

    public SdfSamplePointNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_sample_point");
        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Signed distance field input", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", "Query point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance", "Signed distance at query point", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_INSIDE_ID, "Inside", "True when distance <= 0", NodeDataType.BOOLEAN, this));
        addValidAndErrorOutputs("True when sampling succeeded");
    }

    @Override
    public String getDescription() {
        return "Samples signed distance at a query point and reports inside/outside state";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        Vector3d point = resolveOptionalPoint(INPUT_POINT_ID, null);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf) || point == null) {
            writeFailure("Valid SDF and finite query point are required");
            return;
        }

        double d = sdf.sampleDistance(point);
        if (!Double.isFinite(d)) {
            writeFailure("Sampled distance is not finite");
            return;
        }

        outputValues.put(OUTPUT_DISTANCE_ID, d);
        outputValues.put(OUTPUT_INSIDE_ID, d <= 0.0d);
        markSuccess();
    }

    private void writeFailure(String error) {
        putDoubleOutputs(0.0d, OUTPUT_DISTANCE_ID);
        outputValues.put(OUTPUT_INSIDE_ID, false);
        markInvalid(error);
    }
}

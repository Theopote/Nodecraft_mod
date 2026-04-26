package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "math.fields.vector_from_sdf_gradient",
    displayName = "Vector Field From SDF Gradient",
    description = "Builds a vector field from central-difference gradients of an SDF (normalized direction).",
    category = "math.fields",
    order = 6
)
public class VectorFieldFromSdfGradientNode extends BaseNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Step", category = "SDF", order = 1, description = "Finite difference step size")
    private double step = 0.25d;

    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String OUTPUT_FIELD_ID = "output_field";

    public VectorFieldFromSdfGradientNode() {
        super(UUID.randomUUID(), "math.fields.vector_from_sdf_gradient");

        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Signed distance field input", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Finite difference step size", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Vector field aligned with SDF gradient", NodeDataType.VECTOR_FIELD, this));
    }

    @Override
    public String getDescription() {
        return "Builds a vector field from central-difference gradients of an SDF (normalized direction).";
    }

    @Override
    public String getDisplayName() {
        return "Vector Field From SDF Gradient";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        double h = Math.max(1.0e-4d, getInputDouble(INPUT_STEP_ID, step));
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            outputValues.put(OUTPUT_FIELD_ID, null);
            return;
        }

        VectorFieldData field = (point, dest) -> {
            Vector3d p = new Vector3d(point);
            double gx = sdf.sampleDistance(new Vector3d(p.x + h, p.y, p.z)) - sdf.sampleDistance(new Vector3d(p.x - h, p.y, p.z));
            double gy = sdf.sampleDistance(new Vector3d(p.x, p.y + h, p.z)) - sdf.sampleDistance(new Vector3d(p.x, p.y - h, p.z));
            double gz = sdf.sampleDistance(new Vector3d(p.x, p.y, p.z + h)) - sdf.sampleDistance(new Vector3d(p.x, p.y, p.z - h));
            dest.set(gx, gy, gz);
            if (dest.lengthSquared() <= EPS) {
                dest.set(0.0d, 0.0d, 0.0d);
                return;
            }
            dest.normalize();
        };

        outputValues.put(OUTPUT_FIELD_ID, field);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}

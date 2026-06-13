package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "world.terrain.flow_direction_field",
    displayName = "Flow Direction Field",
    description = "Computes downslope flow direction and slope magnitude from a height field.",
    category = "world.terrain",
    order = 6
)
public class FlowDirectionFieldNode extends BaseNode {

    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";
    private static final String INPUT_STEP_ID = "input_step";

    private static final String OUTPUT_FLOW_FIELD_ID = "output_flow_field";
    private static final String OUTPUT_SLOPE_FIELD_ID = "output_slope_field";

    @NodeProperty(displayName = "Step", category = "Sampling", order = 1)
    private double step = 1.0d;

    public FlowDirectionFieldNode() {
        super(UUID.randomUUID(), "world.terrain.flow_direction_field");

        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field", "Input elevation field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Finite difference step size", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FLOW_FIELD_ID, "Flow Field", "Normalized downslope direction", NodeDataType.VECTOR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_SLOPE_FIELD_ID, "Slope Field", "Slope magnitude", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object heightObj = inputValues.get(INPUT_HEIGHT_FIELD_ID);
        if (!(heightObj instanceof ScalarFieldData heightField)) {
            outputValues.put(OUTPUT_FLOW_FIELD_ID, null);
            outputValues.put(OUTPUT_SLOPE_FIELD_ID, null);
            return;
        }

        double resolvedStep = Math.max(1.0e-4d, getInputDouble(INPUT_STEP_ID, step));
        double invTwoStep = 1.0d / (2.0d * resolvedStep);

        ScalarFieldData slopeField = point -> {
            double gradX = sampleGradientX(heightField, point.x, point.y, point.z, resolvedStep, invTwoStep);
            double gradZ = sampleGradientZ(heightField, point.x, point.y, point.z, resolvedStep, invTwoStep);
            return Math.sqrt(gradX * gradX + gradZ * gradZ);
        };

        VectorFieldData flowField = (point, dest) -> {
            double gradX = sampleGradientX(heightField, point.x, point.y, point.z, resolvedStep, invTwoStep);
            double gradZ = sampleGradientZ(heightField, point.x, point.y, point.z, resolvedStep, invTwoStep);

            double dx = -gradX;
            double dz = -gradZ;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len <= 1.0e-9d) {
                dest.set(0.0d, 0.0d, 0.0d);
                return;
            }
            dest.set(dx / len, 0.0d, dz / len);
        };

        outputValues.put(OUTPUT_FLOW_FIELD_ID, flowField);
        outputValues.put(OUTPUT_SLOPE_FIELD_ID, slopeField);
    }

    private double sampleGradientX(ScalarFieldData field, double x, double y, double z, double stepSize, double invTwoStep) {
        double left = field.sampleScalar(new Vector3d(x - stepSize, y, z));
        double right = field.sampleScalar(new Vector3d(x + stepSize, y, z));
        return (right - left) * invTwoStep;
    }

    private double sampleGradientZ(ScalarFieldData field, double x, double y, double z, double stepSize, double invTwoStep) {
        double down = field.sampleScalar(new Vector3d(x, y, z - stepSize));
        double up = field.sampleScalar(new Vector3d(x, y, z + stepSize));
        return (up - down) * invTwoStep;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}

package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "world.terrain.orogenic_uplift_field",
    displayName = "Orogenic Uplift Field",
    description = "Converts boundary intensity into mountain uplift potential.",
    category = "world.terrain",
    order = 3
)
public class OrogenicUpliftFieldNode extends BaseNode {

    private static final String INPUT_BOUNDARY_FIELD_ID = "input_boundary_field";
    private static final String INPUT_STRENGTH_ID = "input_strength";
    private static final String INPUT_FALLOFF_ID = "input_falloff";

    private static final String OUTPUT_UPLIFT_FIELD_ID = "output_uplift_field";

    @NodeProperty(displayName = "Strength", category = "Uplift", order = 1)
    private double strength = 1.2d;

    @NodeProperty(displayName = "Falloff", category = "Uplift", order = 2)
    private double falloff = 2.0d;

    public OrogenicUpliftFieldNode() {
        super(UUID.randomUUID(), "world.terrain.orogenic_uplift_field");

        addInputPort(new BasePort(INPUT_BOUNDARY_FIELD_ID, "Boundary Field", "Plate boundary intensity field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Overall uplift multiplier", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_FALLOFF_ID, "Falloff", "Nonlinear contrast; >1 sharpens mountain belts", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_UPLIFT_FIELD_ID, "Uplift Field", "Mountain uplift contribution field", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object boundaryObj = inputValues.get(INPUT_BOUNDARY_FIELD_ID);
        if (!(boundaryObj instanceof ScalarFieldData boundaryField)) {
            outputValues.put(OUTPUT_UPLIFT_FIELD_ID, null);
            return;
        }

        double resolvedStrength = Math.max(0.0d, getInputDouble(INPUT_STRENGTH_ID, strength));
        double resolvedFalloff = Math.max(0.1d, getInputDouble(INPUT_FALLOFF_ID, falloff));

        ScalarFieldData upliftField = point -> {
            double boundary = clamp01(boundaryField.sampleScalar(point));
            double shaped = Math.pow(boundary, resolvedFalloff);
            return shaped * resolvedStrength;
        };

        outputValues.put(OUTPUT_UPLIFT_FIELD_ID, upliftField);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}

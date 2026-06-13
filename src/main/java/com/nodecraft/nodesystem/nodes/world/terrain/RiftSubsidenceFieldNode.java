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
    id = "world.terrain.rift_subsidence_field",
    displayName = "Rift Subsidence Field",
    description = "Builds rift/trench subsidence strength from boundary intensity.",
    category = "world.terrain",
    order = 4
)
public class RiftSubsidenceFieldNode extends BaseNode {

    private static final String INPUT_BOUNDARY_FIELD_ID = "input_boundary_field";
    private static final String INPUT_STRENGTH_ID = "input_strength";
    private static final String INPUT_WIDTH_ID = "input_width";

    private static final String OUTPUT_SUBSIDENCE_FIELD_ID = "output_subsidence_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Strength", category = "Subsidence", order = 1)
    private double strength = 0.8d;

    @NodeProperty(displayName = "Width", category = "Subsidence", order = 2)
    private double width = 1.0d;

    public RiftSubsidenceFieldNode() {
        super(UUID.randomUUID(), "world.terrain.rift_subsidence_field");

        addInputPort(new BasePort(INPUT_BOUNDARY_FIELD_ID, "Boundary Field", "Plate boundary intensity field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Overall subsidence multiplier", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_WIDTH_ID, "Width", "Boundary spread width; larger gives broader basins", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_SUBSIDENCE_FIELD_ID, "Subsidence Field", "Rift basin/trench subsidence contribution", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the field was created", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when field creation failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object boundaryObj = inputValues.get(INPUT_BOUNDARY_FIELD_ID);
        if (!(boundaryObj instanceof ScalarFieldData boundaryField)) {
            outputValues.put(OUTPUT_SUBSIDENCE_FIELD_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Missing boundary field input.");
            return;
        }

        double resolvedStrength = Math.max(0.0d, getInputDouble(INPUT_STRENGTH_ID, strength));
        double resolvedWidth = Math.max(0.05d, getInputDouble(INPUT_WIDTH_ID, width));

        ScalarFieldData subsidenceField = point -> {
            double boundary = clamp01(boundaryField.sampleScalar(point));
            double shaped = Math.pow(boundary, resolvedWidth);
            return shaped * resolvedStrength;
        };

        outputValues.put(OUTPUT_SUBSIDENCE_FIELD_ID, subsidenceField);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}

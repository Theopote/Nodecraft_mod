package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.terrain.orogenic_uplift_field",
    displayName = "Orogenic Uplift Field",
    description = "Converts boundary intensity into mountain uplift potential. "
        + "Validates inputs at process time; lazy samples may propagate NaN to materialize consumers.",
    category = "world.terrain",
    order = 2
)
public class OrogenicUpliftFieldNode extends BaseNode {

    private static final String INPUT_BOUNDARY_FIELD_ID = "input_boundary_field";
    private static final String INPUT_STRENGTH_ID = "input_strength";
    private static final String INPUT_FALLOFF_ID = "input_falloff";

    private static final String OUTPUT_UPLIFT_FIELD_ID = "output_uplift_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

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
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the uplift field was created", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when field creation failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object boundaryObj = inputValues.get(INPUT_BOUNDARY_FIELD_ID);
        if (!(boundaryObj instanceof ScalarFieldData boundaryField)) {
            publishInvalid("Missing boundary field input.");
            return;
        }

        Double resolvedStrengthRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_STRENGTH_ID, strength);
        if (resolvedStrengthRaw == null) {
            publishInvalid("Strength must be a finite DOUBLE.");
            return;
        }

        Double resolvedFalloffRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_FALLOFF_ID, falloff);
        if (resolvedFalloffRaw == null) {
            publishInvalid("Falloff must be a finite DOUBLE.");
            return;
        }

        double resolvedStrength = Math.max(0.0d, resolvedStrengthRaw);
        double resolvedFalloff = Math.max(0.1d, resolvedFalloffRaw);

        ScalarFieldData upliftField = point -> {
            double boundarySample = boundaryField.sampleScalar(point);
            if (!Double.isFinite(boundarySample)) {
                return Double.NaN;
            }
            double boundary = clamp01(boundarySample);
            double shaped = Math.pow(boundary, resolvedFalloff);
            return shaped * resolvedStrength;
        };

        outputValues.put(OUTPUT_UPLIFT_FIELD_ID, upliftField);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishInvalid(String error) {
        outputValues.put(OUTPUT_UPLIFT_FIELD_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}

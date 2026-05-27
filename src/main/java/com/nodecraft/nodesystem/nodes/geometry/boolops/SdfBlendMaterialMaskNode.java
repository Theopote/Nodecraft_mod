package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.boolean.sdf_blend_material_mask",
    displayName = "SDF Blend Material Mask",
    description = "Maps SDF distance values to smooth 0..1 blend weights and inside/outside booleans",
    category = "geometry.boolean",
    order = 22
)
public class SdfBlendMaterialMaskNode extends BaseNode {

    @NodeProperty(displayName = "Center", category = "Mask", order = 1)
    private double center = 0.0d;

    @NodeProperty(displayName = "Half Width", category = "Mask", order = 2)
    private double halfWidth = 1.0d;

    @NodeProperty(displayName = "Invert", category = "Mask", order = 3)
    private boolean invert = false;

    private static final String INPUT_DISTANCES_ID = "input_distances";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_HALF_WIDTH_ID = "input_half_width";

    private static final String OUTPUT_WEIGHTS_ID = "output_weights";
    private static final String OUTPUT_INSIDE_ID = "output_inside";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SdfBlendMaterialMaskNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_blend_material_mask");

        addInputPort(new BasePort(INPUT_DISTANCES_ID, "Distances", "SDF distance list (typically from SDF Sample Points)", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Distance center where mask weight is 0.5", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HALF_WIDTH_ID, "Half Width", "Half of transition band width (> 0)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_WEIGHTS_ID, "Weights", "0..1 smooth blend weights", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_INSIDE_ID, "Inside", "Boolean inside/outside classification (distance <= center)", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of mapped samples", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when at least one distance value was mapped", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Maps SDF distance values to smooth 0..1 blend weights and inside/outside booleans";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object distancesObj = inputValues.get(INPUT_DISTANCES_ID);
        if (!(distancesObj instanceof List<?> distanceList)) {
            writeInvalid();
            return;
        }

        double resolvedCenter = resolveDouble(inputValues.get(INPUT_CENTER_ID), center);
        double resolvedHalfWidth = Math.max(1.0e-6d, Math.abs(resolveDouble(inputValues.get(INPUT_HALF_WIDTH_ID), halfWidth)));

        List<Double> weights = new ArrayList<>(distanceList.size());
        List<Boolean> inside = new ArrayList<>(distanceList.size());

        for (Object entry : distanceList) {
            if (!(entry instanceof Number number)) {
                continue;
            }
            double distance = number.doubleValue();
            double x = (distance - resolvedCenter) / resolvedHalfWidth;
            double t = 0.5d + 0.5d * x;
            double weight = smoothstep01(t);
            if (invert) {
                weight = 1.0d - weight;
            }
            weights.add(weight);
            inside.add(distance <= resolvedCenter);
        }

        if (weights.isEmpty()) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_WEIGHTS_ID, List.copyOf(weights));
        outputValues.put(OUTPUT_INSIDE_ID, List.copyOf(inside));
        outputValues.put(OUTPUT_COUNT_ID, weights.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_WEIGHTS_ID, List.of());
        outputValues.put(OUTPUT_INSIDE_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static double smoothstep01(double v) {
        double t = Math.max(0.0d, Math.min(1.0d, v));
        return t * t * (3.0d - 2.0d * t);
    }

    private double resolveDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}

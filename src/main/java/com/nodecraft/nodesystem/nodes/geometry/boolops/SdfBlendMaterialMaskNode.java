package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.sdf_blend_material_mask",
    displayName = "SDF Blend Material Mask",
    description = "Maps SDF distance values to smooth 0..1 blend weights and inside/outside booleans",
    category = "geometry.sdf",
    order = 22
)
public class SdfBlendMaterialMaskNode extends AbstractSdfNode {

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

    public SdfBlendMaterialMaskNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_blend_material_mask");
        addInputPort(new BasePort(INPUT_DISTANCES_ID, "Distances",
            "SDF distance list (typically from SDF Sample Points)", NodeDataType.DOUBLE_LIST, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center",
            "Distance center where mask weight is 0.5", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HALF_WIDTH_ID, "Half Width",
            "Half of transition band width (> 0)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_WEIGHTS_ID, "Weights",
            "0..1 smooth blend weights", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_INSIDE_ID, "Inside",
            "Boolean inside/outside classification (distance <= center)", NodeDataType.BOOLEAN_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of mapped samples", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs("True when every distance value was mapped");
    }

    @Override
    public String getDescription() {
        return "Maps SDF distance values to smooth 0..1 blend weights and inside/outside booleans";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object distancesObj = inputValues.get(INPUT_DISTANCES_ID);
        if (!(distancesObj instanceof List<?> distanceList)) {
            writeFailure("Distances must be a DOUBLE_LIST");
            return;
        }

        Double resolvedCenter = resolveFiniteDouble(INPUT_CENTER_ID, center);
        Double resolvedHalfWidth = resolvePositiveDouble(INPUT_HALF_WIDTH_ID, halfWidth);
        if (resolvedCenter == null) {
            writeFailure("Center must be finite");
            return;
        }
        if (resolvedHalfWidth == null) {
            writeFailure("Half Width must be finite and > 0");
            return;
        }

        if (distanceList.isEmpty()) {
            putEmptyListOutputs(OUTPUT_WEIGHTS_ID, OUTPUT_INSIDE_ID);
            putIntOutputs(0, OUTPUT_COUNT_ID);
            markSuccess();
            return;
        }

        List<Double> weights = new ArrayList<>(distanceList.size());
        List<Boolean> inside = new ArrayList<>(distanceList.size());

        for (Object entry : distanceList) {
            if (!(entry instanceof Number number)) {
                writeFailure("Every Distances entry must be a finite number (no silent drop)");
                return;
            }
            double distance = number.doubleValue();
            if (!Double.isFinite(distance)) {
                writeFailure("Every Distances entry must be a finite number (no silent drop)");
                return;
            }
            double x = (distance - resolvedCenter) / resolvedHalfWidth;
            double t = 0.5d + 0.5d * x;
            double weight = smoothstep01(t);
            if (invert) {
                weight = 1.0d - weight;
            }
            weights.add(weight);
            inside.add(distance <= resolvedCenter);
        }

        outputValues.put(OUTPUT_WEIGHTS_ID, List.copyOf(weights));
        outputValues.put(OUTPUT_INSIDE_ID, List.copyOf(inside));
        putIntOutputs(weights.size(), OUTPUT_COUNT_ID);
        markSuccess();
    }

    private void writeFailure(String error) {
        putEmptyListOutputs(OUTPUT_WEIGHTS_ID, OUTPUT_INSIDE_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }

    private static double smoothstep01(double v) {
        double t = Math.max(0.0d, Math.min(1.0d, v));
        return t * t * (3.0d - 2.0d * t);
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of(
            "center", center,
            "halfWidth", halfWidth,
            "invert", invert
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("center") instanceof Number value) center = value.doubleValue();
        if (map.get("halfWidth") instanceof Number value) halfWidth = value.doubleValue();
        if (map.get("invert") instanceof Boolean value) invert = value;
    }
}

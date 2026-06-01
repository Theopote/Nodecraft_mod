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
    id = "world.terrain.river_mask_field",
    displayName = "River Mask Field",
    description = "Creates a river-channel mask field from flow accumulation.",
    category = "world.terrain",
    order = 6
)
public class RiverMaskFieldNode extends BaseNode {

    private static final String INPUT_ACCUMULATION_FIELD_ID = "input_accumulation_field";
    private static final String INPUT_THRESHOLD_ID = "input_threshold";
    private static final String INPUT_MIN_ORDER_ID = "input_min_order";

    private static final String OUTPUT_RIVER_MASK_FIELD_ID = "output_river_mask_field";

    @NodeProperty(displayName = "Threshold", category = "River", order = 1)
    private double threshold = 0.62d;

    @NodeProperty(displayName = "Min Order", category = "River", order = 2)
    private int minOrder = 2;

    public RiverMaskFieldNode() {
        super(UUID.randomUUID(), "world.terrain.river_mask_field");

        addInputPort(new BasePort(INPUT_ACCUMULATION_FIELD_ID, "Accumulation Field", "Drainage accumulation input", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_THRESHOLD_ID, "Threshold", "Base river extraction threshold", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MIN_ORDER_ID, "Min Order", "Higher values keep only stronger channels", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_RIVER_MASK_FIELD_ID, "River Mask Field", "0..1 river channel mask", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object accumulationObj = inputValues.get(INPUT_ACCUMULATION_FIELD_ID);
        if (!(accumulationObj instanceof ScalarFieldData accumulationField)) {
            outputValues.put(OUTPUT_RIVER_MASK_FIELD_ID, null);
            return;
        }

        double resolvedThreshold = Math.max(0.0d, getInputDouble(INPUT_THRESHOLD_ID, threshold));
        int resolvedMinOrder = Math.max(1, getInputInt(INPUT_MIN_ORDER_ID, minOrder));
        double orderScale = 1.0d + (resolvedMinOrder - 1) * 0.25d;
        double effectiveThreshold = resolvedThreshold * orderScale;

        ScalarFieldData maskField = point -> {
            double accumulation = accumulationField.sampleScalar(point);
            if (accumulation <= effectiveThreshold) {
                return 0.0d;
            }

            double normalized = (accumulation - effectiveThreshold) / Math.max(1.0e-9d, effectiveThreshold);
            return clamp01(normalized);
        };

        outputValues.put(OUTPUT_RIVER_MASK_FIELD_ID, maskField);
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}

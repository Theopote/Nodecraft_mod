package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BooleanSdfData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "geometry.boolean.sdf_boolean",
    displayName = "SDF Boolean",
    description = "Combines two SDF inputs with union/intersection/difference and optional smooth blending",
    category = "geometry.boolean",
    order = 21
)
public class SdfBooleanNode extends BaseNode {

    @NodeProperty(displayName = "Operation", category = "SDF", order = 1)
    private String operation = "UNION";

    @NodeProperty(displayName = "Smooth K", category = "SDF", order = 2)
    private double smoothK = 0.0d;

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String INPUT_SMOOTH_K_ID = "input_smooth_k";

    private static final String OUTPUT_SDF_ID = "output_sdf";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SdfBooleanNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_boolean");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Left SDF operand", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Right SDF operand", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_SMOOTH_K_ID, "Smooth K", "Blend radius (0 = hard boolean)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF", "Combined SDF output", NodeDataType.SDF, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when both input SDFs are connected", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Combines two SDF inputs with union/intersection/difference and optional smooth blending";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object leftObj = inputValues.get(INPUT_A_ID);
        Object rightObj = inputValues.get(INPUT_B_ID);
        if (!(leftObj instanceof SignedDistanceFieldData left) || !(rightObj instanceof SignedDistanceFieldData right)) {
            outputValues.put(OUTPUT_SDF_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double resolvedSmoothK = getInputDouble(INPUT_SMOOTH_K_ID, smoothK);
        BooleanSdfData.Operation op = parseOperation(operation);
        SignedDistanceFieldData out = new BooleanSdfData(left, right, op, resolvedSmoothK);
        outputValues.put(OUTPUT_SDF_ID, out);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private BooleanSdfData.Operation parseOperation(String raw) {
        if (raw == null) {
            return BooleanSdfData.Operation.UNION;
        }
        try {
            return BooleanSdfData.Operation.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BooleanSdfData.Operation.UNION;
        }
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}

package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BooleanSdfData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.util.SdfExpressionLimits;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.sdf_boolean",
    displayName = "SDF Boolean",
    description = "Combines two SDF inputs with union/intersection/difference and optional smooth blending",
    category = "geometry.sdf",
    order = 14
)
public class SdfBooleanNode extends AbstractSdfNode {

    @NodeProperty(displayName = "Operation", category = "SDF", order = 1)
    private BooleanSdfData.Operation operation = BooleanSdfData.Operation.UNION;

    @NodeProperty(displayName = "Smooth K", category = "SDF", order = 2)
    private double smoothK = 0.0d;

    private boolean operationCorrupt;

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String INPUT_SMOOTH_K_ID = "input_smooth_k";
    private static final String OUTPUT_SDF_ID = "output_sdf";

    public SdfBooleanNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_boolean");
        addInputPort(new BasePort(INPUT_A_ID, "A", "Left SDF operand", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Right SDF operand", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_SMOOTH_K_ID, "Smooth K", "Blend radius (0 = hard boolean)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF", "Combined SDF output", NodeDataType.SDF, this));
        addValidAndErrorOutputs("True when both input SDFs are valid");
    }

    @Override
    public String getDescription() {
        return "Combines two SDF inputs with union/intersection/difference and optional smooth blending";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object leftObj = inputValues.get(INPUT_A_ID);
        Object rightObj = inputValues.get(INPUT_B_ID);
        if (!(leftObj instanceof SignedDistanceFieldData left)
            || !(rightObj instanceof SignedDistanceFieldData right)) {
            writeFailure("Both SDF inputs A and B are required");
            return;
        }

        Double resolvedSmoothK = resolveNonNegativeDouble(INPUT_SMOOTH_K_ID, smoothK);
        if (resolvedSmoothK == null) {
            writeFailure("Smooth K must be finite and >= 0");
            return;
        }
        if (operationCorrupt || operation == null) {
            writeFailure("Operation must be UNION, INTERSECTION, or DIFFERENCE");
            return;
        }
        if (!SdfExpressionLimits.canWrapBinary(left, right)) {
            writeFailure(sdfBudgetError());
            return;
        }

        SignedDistanceFieldData out = new BooleanSdfData(left, right, operation, resolvedSmoothK);
        outputValues.put(OUTPUT_SDF_ID, out);
        markSuccess();
    }

    public BooleanSdfData.Operation getOperation() {
        return operation;
    }

    public void setOperation(BooleanSdfData.Operation operation) {
        if (operation == null) {
            return;
        }
        this.operation = operation;
        this.operationCorrupt = false;
        markDirty();
    }

    private void writeFailure(String error) {
        putNullOutputs(OUTPUT_SDF_ID);
        markInvalid(error);
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of("operation", operation == null ? "" : operation.name(), "smoothK", smoothK);
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("operation") instanceof String value) {
            BooleanSdfData.Operation parsed = parseOperation(value);
            if (parsed != null) {
                this.operation = parsed;
                this.operationCorrupt = false;
            } else {
                this.operationCorrupt = true;
            }
        } else if (map.get("operation") instanceof BooleanSdfData.Operation value) {
            this.operation = value;
            this.operationCorrupt = false;
        }
        if (map.get("smoothK") instanceof Number value) {
            smoothK = value.doubleValue();
        }
    }

    private static @Nullable BooleanSdfData.Operation parseOperation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BooleanSdfData.Operation.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

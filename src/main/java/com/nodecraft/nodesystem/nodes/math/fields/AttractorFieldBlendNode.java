package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "math.fields.attractor_blend",
    displayName = "Attractor Field Blend",
    description = "Blends up to four attractor/repulsor fields using per-field weights.",
    category = "math.fields",
    order = 13
)
public class AttractorFieldBlendNode extends BaseNode {

    @NodeProperty(displayName = "Normalize", category = "Blend", order = 1)
    private boolean normalize = false;

    @NodeProperty(displayName = "Max Magnitude", category = "Blend", order = 2)
    private double maxMagnitude = 0.0d;

    private static final String INPUT_FIELD_A_ID = "input_field_a";
    private static final String INPUT_FIELD_B_ID = "input_field_b";
    private static final String INPUT_FIELD_C_ID = "input_field_c";
    private static final String INPUT_FIELD_D_ID = "input_field_d";
    private static final String INPUT_WEIGHT_A_ID = "input_weight_a";
    private static final String INPUT_WEIGHT_B_ID = "input_weight_b";
    private static final String INPUT_WEIGHT_C_ID = "input_weight_c";
    private static final String INPUT_WEIGHT_D_ID = "input_weight_d";
    private static final String OUTPUT_FIELD_ID = "output_field";

    private final Vector3d tmp = new Vector3d();

    public AttractorFieldBlendNode() {
        super(UUID.randomUUID(), "math.fields.attractor_blend");

        addInputPort(new BasePort(INPUT_FIELD_A_ID, "Field A", "First vector field", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_FIELD_B_ID, "Field B", "Second vector field", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_FIELD_C_ID, "Field C", "Third vector field", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_FIELD_D_ID, "Field D", "Fourth vector field", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_WEIGHT_A_ID, "Weight A", "Weight for Field A", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_WEIGHT_B_ID, "Weight B", "Weight for Field B", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_WEIGHT_C_ID, "Weight C", "Weight for Field C", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_WEIGHT_D_ID, "Weight D", "Weight for Field D", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Blended vector field output", NodeDataType.VECTOR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        VectorFieldData fieldA = asField(inputValues.get(INPUT_FIELD_A_ID));
        VectorFieldData fieldB = asField(inputValues.get(INPUT_FIELD_B_ID));
        VectorFieldData fieldC = asField(inputValues.get(INPUT_FIELD_C_ID));
        VectorFieldData fieldD = asField(inputValues.get(INPUT_FIELD_D_ID));
        if (fieldA == null && fieldB == null && fieldC == null && fieldD == null) {
            outputValues.put(OUTPUT_FIELD_ID, null);
            return;
        }

        double weightA = getInputDouble(INPUT_WEIGHT_A_ID, 1.0d);
        double weightB = getInputDouble(INPUT_WEIGHT_B_ID, 1.0d);
        double weightC = getInputDouble(INPUT_WEIGHT_C_ID, 1.0d);
        double weightD = getInputDouble(INPUT_WEIGHT_D_ID, 1.0d);
        boolean normalizeOutput = normalize;
        double limit = Math.max(0.0d, maxMagnitude);

        VectorFieldData field = (point, dest) -> {
            dest.zero();
            if (fieldA != null && Math.abs(weightA) > AttractorFieldUtils.EPS) {
                fieldA.sampleVector(point, tmp);
                dest.fma(weightA, tmp);
            }
            if (fieldB != null && Math.abs(weightB) > AttractorFieldUtils.EPS) {
                fieldB.sampleVector(point, tmp);
                dest.fma(weightB, tmp);
            }
            if (fieldC != null && Math.abs(weightC) > AttractorFieldUtils.EPS) {
                fieldC.sampleVector(point, tmp);
                dest.fma(weightC, tmp);
            }
            if (fieldD != null && Math.abs(weightD) > AttractorFieldUtils.EPS) {
                fieldD.sampleVector(point, tmp);
                dest.fma(weightD, tmp);
            }

            double lenSq = dest.lengthSquared();
            if (lenSq <= AttractorFieldUtils.EPS) {
                dest.zero();
                return;
            }
            if (normalizeOutput) {
                dest.normalize();
            }
            if (limit > AttractorFieldUtils.EPS) {
                double len = dest.length();
                if (len > limit) {
                    dest.mul(limit / len);
                }
            }
        };

        outputValues.put(OUTPUT_FIELD_ID, field);
    }

    private static VectorFieldData asField(Object value) {
        return value instanceof VectorFieldData field ? field : null;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}

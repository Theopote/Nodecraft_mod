package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.FieldMath;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.fields.attractor_blend",
    displayName = "Blend Vector Fields",
    description = "Blends up to four vector fields using per-field weights.",
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
    public String getDescription() {
        return "Blends up to four vector fields using per-field weights.";
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

        double weightA = FieldMath.resolveFinite(inputValues.get(INPUT_WEIGHT_A_ID), 1.0d);
        double weightB = FieldMath.resolveFinite(inputValues.get(INPUT_WEIGHT_B_ID), 1.0d);
        double weightC = FieldMath.resolveFinite(inputValues.get(INPUT_WEIGHT_C_ID), 1.0d);
        double weightD = FieldMath.resolveFinite(inputValues.get(INPUT_WEIGHT_D_ID), 1.0d);
        boolean normalizeOutput = normalize;
        double limit = Double.isFinite(maxMagnitude) && maxMagnitude >= 0.0d ? maxMagnitude : 0.0d;

        final double weightAFinal = weightA;
        final double weightBFinal = weightB;
        final double weightCFinal = weightC;
        final double weightDFinal = weightD;
        final double limitFinal = limit;

        VectorFieldData field = (point, dest) -> {
            dest.zero();
            Vector3d tmp = new Vector3d();
            if (fieldA != null && weightAFinal != 0.0d) {
                fieldA.sampleVector(point, tmp);
                dest.fma(weightAFinal, tmp);
            }
            if (fieldB != null && weightBFinal != 0.0d) {
                fieldB.sampleVector(point, tmp);
                dest.fma(weightBFinal, tmp);
            }
            if (fieldC != null && weightCFinal != 0.0d) {
                fieldC.sampleVector(point, tmp);
                dest.fma(weightCFinal, tmp);
            }
            if (fieldD != null && weightDFinal != 0.0d) {
                fieldD.sampleVector(point, tmp);
                dest.fma(weightDFinal, tmp);
            }

            if (normalizeOutput) {
                double lenSq = dest.lengthSquared();
                if (lenSq <= AttractorFieldUtils.EPS) {
                    dest.zero();
                    return;
                }
                dest.normalize();
            }
            if (limitFinal > AttractorFieldUtils.EPS) {
                double len = dest.length();
                if (len > limitFinal) {
                    dest.mul(limitFinal / len);
                }
            }
        };

        outputValues.put(OUTPUT_FIELD_ID, field);
    }

    private static VectorFieldData asField(Object value) {
        return value instanceof VectorFieldData field ? field : null;
    }
}

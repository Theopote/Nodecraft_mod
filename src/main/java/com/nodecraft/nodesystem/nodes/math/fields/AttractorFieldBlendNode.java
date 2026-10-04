package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.VectorUtils;
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
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

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
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the field was constructed",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Blends up to four vector fields using per-field weights.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        FieldSampleUtils.OptionalVectorField driveA = FieldSampleUtils.resolveOptionalVectorField(this, INPUT_FIELD_A_ID);
        FieldSampleUtils.OptionalVectorField driveB = FieldSampleUtils.resolveOptionalVectorField(this, INPUT_FIELD_B_ID);
        FieldSampleUtils.OptionalVectorField driveC = FieldSampleUtils.resolveOptionalVectorField(this, INPUT_FIELD_C_ID);
        FieldSampleUtils.OptionalVectorField driveD = FieldSampleUtils.resolveOptionalVectorField(this, INPUT_FIELD_D_ID);
        if (driveA.failed() || driveB.failed() || driveC.failed() || driveD.failed()) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_FIELD);
            return;
        }

        VectorFieldData fieldA = driveA.field();
        VectorFieldData fieldB = driveB.field();
        VectorFieldData fieldC = driveC.field();
        VectorFieldData fieldD = driveD.field();
        if (fieldA == null && fieldB == null && fieldC == null && fieldD == null) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_FIELD);
            return;
        }

        Double weightA = FieldSampleUtils.resolveOptionalFiniteDouble(this, INPUT_WEIGHT_A_ID, 1.0d);
        Double weightB = FieldSampleUtils.resolveOptionalFiniteDouble(this, INPUT_WEIGHT_B_ID, 1.0d);
        Double weightC = FieldSampleUtils.resolveOptionalFiniteDouble(this, INPUT_WEIGHT_C_ID, 1.0d);
        Double weightD = FieldSampleUtils.resolveOptionalFiniteDouble(this, INPUT_WEIGHT_D_ID, 1.0d);
        if (weightA == null || weightB == null || weightC == null || weightD == null) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_INPUT);
            return;
        }

        boolean normalizeOutput = normalize;
        double limit = Double.isFinite(maxMagnitude) && maxMagnitude >= 0.0d ? maxMagnitude : 0.0d;

        final double weightAFinal = weightA;
        final double weightBFinal = weightB;
        final double weightCFinal = weightC;
        final double weightDFinal = weightD;
        final double limitFinal = limit;

        VectorFieldData field = (point, dest) -> {
            double sx = 0.0d;
            double sy = 0.0d;
            double sz = 0.0d;
            if (fieldA != null && weightAFinal != 0.0d) {
                fieldA.sampleVector(point, dest);
                sx += weightAFinal * dest.x;
                sy += weightAFinal * dest.y;
                sz += weightAFinal * dest.z;
            }
            if (fieldB != null && weightBFinal != 0.0d) {
                fieldB.sampleVector(point, dest);
                sx += weightBFinal * dest.x;
                sy += weightBFinal * dest.y;
                sz += weightBFinal * dest.z;
            }
            if (fieldC != null && weightCFinal != 0.0d) {
                fieldC.sampleVector(point, dest);
                sx += weightCFinal * dest.x;
                sy += weightCFinal * dest.y;
                sz += weightCFinal * dest.z;
            }
            if (fieldD != null && weightDFinal != 0.0d) {
                fieldD.sampleVector(point, dest);
                sx += weightDFinal * dest.x;
                sy += weightDFinal * dest.y;
                sz += weightDFinal * dest.z;
            }
            dest.set(sx, sy, sz);

            if (normalizeOutput) {
                if (!VectorUtils.isFinite(dest)) {
                    dest.set(Double.NaN, Double.NaN, Double.NaN);
                    return;
                }
                Vector3d normalized = VectorUtils.safeNormalize(dest);
                if (normalized != null) {
                    dest.set(normalized);
                } else {
                    dest.zero();
                    return;
                }
            }
            if (limitFinal > 0.0d) {
                double len = VectorUtils.safeLength(dest);
                if (Double.isFinite(len) && len > limitFinal) {
                    dest.mul(limitFinal / len);
                }
            }
        };

        outputValues.put(OUTPUT_FIELD_ID, field);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_FIELD_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

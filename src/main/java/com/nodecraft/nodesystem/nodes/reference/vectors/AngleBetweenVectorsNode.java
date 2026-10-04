package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.vectors.angle_between",
    displayName = "Angle Between Vectors",
    description = "Angle between two vectors in degrees. Optional reference is used only to determine signed-angle sign; it need not be perpendicular to A and B.",
    category = "reference.vectors",
    order = 12
)
public class AngleBetweenVectorsNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String INPUT_REFERENCE_ID = "input_reference";

    private static final String OUTPUT_ANGLE_ID = "output_angle";
    private static final String OUTPUT_SIGNED_ANGLE_ID = "output_signed_angle";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public AngleBetweenVectorsNode() {
        super(UUID.randomUUID(), "reference.vectors.angle_between");

        addInputPort(new BasePort(INPUT_A_ID, "A", "First direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Second direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_REFERENCE_ID, "Reference",
            "Optional axis for signed angle (typically the plane normal). Unsigned output ignores this.",
            NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_ANGLE_ID, "Angle",
            "Unsigned angle in degrees between A and B",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIGNED_ANGLE_ID, "Signed Angle",
            "Signed angle in degrees using right-hand rule around Reference; NaN when Reference is not connected",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when A and B are valid non-zero vectors",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Angle Between Vectors";
    }

    @Override
    public String getDescription() {
        return "Angle between two vectors in degrees. Optional reference is used only to determine signed-angle sign; it need not be perpendicular to A and B.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d a = VectorUtils.toStrictVectorPortValue(inputValues.get(INPUT_A_ID));
        Vector3d b = VectorUtils.toStrictVectorPortValue(inputValues.get(INPUT_B_ID));
        if (!VectorUtils.isFinite(a)) {
            writeInvalid("Vector A must be a finite VECTOR");
            return;
        }
        if (!VectorUtils.isFinite(b)) {
            writeInvalid("Vector B must be a finite VECTOR");
            return;
        }

        double lenA = VectorUtils.safeLength(a);
        double lenB = VectorUtils.safeLength(b);
        if (!VectorUtils.isFinite(lenA) || lenA <= VectorUtils.EPS) {
            writeInvalid("Vector A must be non-zero");
            return;
        }
        if (!VectorUtils.isFinite(lenB) || lenB <= VectorUtils.EPS) {
            writeInvalid("Vector B must be non-zero");
            return;
        }

        Vector3d an = VectorUtils.normalizeByLength(a, lenA);
        Vector3d bn = VectorUtils.normalizeByLength(b, lenB);
        if (an == null || bn == null) {
            writeInvalid("Normalized direction vectors are not finite");
            return;
        }

        double cos = VectorUtils.safeDot(an, bn);
        if (!VectorUtils.isFinite(cos)) {
            writeInvalid("Angle cosine is not finite");
            return;
        }
        cos = Math.max(-1.0d, Math.min(1.0d, cos));
        double angleRad = Math.acos(cos);
        if (!VectorUtils.isFinite(angleRad)) {
            writeInvalid("Angle is not finite");
            return;
        }
        double deg = Math.toDegrees(angleRad);
        if (!VectorUtils.isFinite(deg)) {
            writeInvalid("Angle in degrees is not finite");
            return;
        }

        boolean referenceConnected = OptionalPortDrive.isConnected(this, INPUT_REFERENCE_ID);
        if (referenceConnected) {
            Vector3d ref = VectorUtils.toStrictVectorPortValue(inputValues.get(INPUT_REFERENCE_ID));
            if (!VectorUtils.isFinite(ref)) {
                writeInvalid("Reference must be a finite VECTOR");
                return;
            }
            double lenRef = VectorUtils.safeLength(ref);
            if (!VectorUtils.isFinite(lenRef) || lenRef <= VectorUtils.EPS) {
                writeInvalid("Reference must be non-zero");
                return;
            }
            Vector3d rn = VectorUtils.normalizeByLength(ref, lenRef);
            if (rn == null) {
                writeInvalid("Reference normal is not finite");
                return;
            }
            Vector3d cross = VectorUtils.safeCross(an, bn);
            if (cross == null) {
                writeInvalid("Cross product for signed angle is not finite");
                return;
            }
            double sinSigned = VectorUtils.safeDot(rn, cross);
            if (!VectorUtils.isFinite(sinSigned)) {
                writeInvalid("Signed angle sine is not finite");
                return;
            }
            double signedRad = Math.atan2(sinSigned, cos);
            if (!VectorUtils.isFinite(signedRad)) {
                writeInvalid("Signed angle is not finite");
                return;
            }
            double signedDeg = Math.toDegrees(signedRad);
            if (!VectorUtils.isFinite(signedDeg)) {
                writeInvalid("Signed angle in degrees is not finite");
                return;
            }
            outputValues.put(OUTPUT_SIGNED_ANGLE_ID, signedDeg);
        } else {
            outputValues.put(OUTPUT_SIGNED_ANGLE_ID, Double.NaN);
        }

        outputValues.put(OUTPUT_ANGLE_ID, deg);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_ANGLE_ID, 0.0d);
        outputValues.put(OUTPUT_SIGNED_ANGLE_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}

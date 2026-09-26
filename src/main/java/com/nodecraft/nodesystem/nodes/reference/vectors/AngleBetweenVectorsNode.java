package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Measures the angle between two vectors, optionally signed using a reference axis (plane normal).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.vectors.angle_between",
    displayName = "Angle Between Vectors",
    description = "Angle between two vectors in degrees; optional reference vector yields a signed angle",
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
    }

    @Override
    public String getDisplayName() {
        return "Angle Between Vectors";
    }

    @Override
    public String getDescription() {
        return "Angle between two vectors in degrees; optional reference vector yields a signed angle";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d a = VectorUtils.toVector(inputValues.get(INPUT_A_ID));
        Vector3d b = VectorUtils.toVector(inputValues.get(INPUT_B_ID));
        if (!VectorUtils.isFinite(a) || !VectorUtils.isFinite(b)
            || a.lengthSquared() < VectorUtils.EPS_SQ || b.lengthSquared() < VectorUtils.EPS_SQ) {
            writeInvalid();
            return;
        }

        Vector3d an = new Vector3d(a).normalize();
        Vector3d bn = new Vector3d(b).normalize();
        double cos = Math.max(-1.0d, Math.min(1.0d, an.dot(bn)));
        double angleRad = Math.acos(cos);
        double deg = Math.toDegrees(angleRad);

        boolean referenceConnected = isInputConnected(INPUT_REFERENCE_ID);
        Vector3d ref = VectorUtils.toVector(inputValues.get(INPUT_REFERENCE_ID));

        if (referenceConnected) {
            if (!VectorUtils.isFinite(ref) || ref.lengthSquared() < VectorUtils.EPS_SQ) {
                writeInvalid();
                return;
            }
            Vector3d rn = new Vector3d(ref).normalize();
            Vector3d cross = new Vector3d(an).cross(bn);
            double sinSigned = rn.dot(cross);
            double signedRad = Math.atan2(sinSigned, cos);
            outputValues.put(OUTPUT_SIGNED_ANGLE_ID, Math.toDegrees(signedRad));
        } else {
            outputValues.put(OUTPUT_SIGNED_ANGLE_ID, Double.NaN);
        }

        outputValues.put(OUTPUT_ANGLE_ID, deg);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private boolean isInputConnected(String inputPortId) {
        return inputPorts.stream()
            .anyMatch(port -> inputPortId.equals(port.getId()) && port.isConnected());
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_ANGLE_ID, Double.NaN);
        outputValues.put(OUTPUT_SIGNED_ANGLE_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

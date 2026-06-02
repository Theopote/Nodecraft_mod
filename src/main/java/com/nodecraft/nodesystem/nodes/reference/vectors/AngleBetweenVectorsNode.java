package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Measures the angle between two vectors, optionally signed using a reference axis (plane normal).
 */
@NodeInfo(
    id = "reference.vectors.angle_between",
    displayName = "Angle Between Vectors",
    description = "Angle between two vectors in radians and degrees; optional reference vector yields a signed angle",
    category = "reference.vectors",
    order = 11
)
public class AngleBetweenVectorsNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String INPUT_REFERENCE_ID = "input_reference";

    private static final String OUTPUT_RADIANS_ID = "output_radians";
    private static final String OUTPUT_DEGREES_ID = "output_degrees";
    private static final String OUTPUT_SIGNED_RADIANS_ID = "output_signed_radians";
    private static final String OUTPUT_SIGNED_DEGREES_ID = "output_signed_degrees";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public AngleBetweenVectorsNode() {
        super(UUID.randomUUID(), "reference.vectors.angle_between");

        addInputPort(new BasePort(INPUT_A_ID, "A", "First direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Second direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_REFERENCE_ID, "Reference",
            "Optional axis for signed angle (typically the plane normal). Unsigned outputs ignore this.",
            NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_RADIANS_ID, "Radians",
            "Unsigned angle in radians between A and B",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_DEGREES_ID, "Degrees",
            "Unsigned angle in degrees between A and B",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIGNED_RADIANS_ID, "Signed Radians",
            "Signed angle using right-hand rule around Reference; NaN when Reference is not connected",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIGNED_DEGREES_ID, "Signed Degrees",
            "Signed angle in degrees; NaN when Reference is not connected",
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
        return "Angle between two vectors in radians and degrees; optional reference vector yields a signed angle";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d a = VectorUtils.toVector(inputValues.get(INPUT_A_ID));
        Vector3d b = VectorUtils.toVector(inputValues.get(INPUT_B_ID));
        Vector3d ref = VectorUtils.toVector(inputValues.get(INPUT_REFERENCE_ID));
        if (!VectorUtils.isFinite(a) || !VectorUtils.isFinite(b)
            || a.lengthSquared() < VectorUtils.EPS || b.lengthSquared() < VectorUtils.EPS) {
            writeInvalid();
            return;
        }
        Vector3d an = new Vector3d(a).normalize();
        Vector3d bn = new Vector3d(b).normalize();
        double cos = Math.max(-1.0d, Math.min(1.0d, an.dot(bn)));
        double angle = Math.acos(cos);
        double deg = Math.toDegrees(angle);

        outputValues.put(OUTPUT_RADIANS_ID, angle);
        outputValues.put(OUTPUT_DEGREES_ID, deg);

        if (VectorUtils.isFinite(ref) && ref.lengthSquared() >= VectorUtils.EPS) {
            Vector3d rn = new Vector3d(ref).normalize();
            Vector3d cross = new Vector3d(an).cross(bn);
            double sinSigned = rn.dot(cross);
            double signed = Math.atan2(sinSigned, cos);
            outputValues.put(OUTPUT_SIGNED_RADIANS_ID, signed);
            outputValues.put(OUTPUT_SIGNED_DEGREES_ID, Math.toDegrees(signed));
        } else {
            outputValues.put(OUTPUT_SIGNED_RADIANS_ID, Double.NaN);
            outputValues.put(OUTPUT_SIGNED_DEGREES_ID, Double.NaN);
        }
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_RADIANS_ID, Double.NaN);
        outputValues.put(OUTPUT_DEGREES_ID, Double.NaN);
        outputValues.put(OUTPUT_SIGNED_RADIANS_ID, Double.NaN);
        outputValues.put(OUTPUT_SIGNED_DEGREES_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

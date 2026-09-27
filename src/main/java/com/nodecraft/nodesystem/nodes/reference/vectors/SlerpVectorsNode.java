package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.StrictDoubleUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.vectors.slerp",
    displayName = "Slerp Vectors",
    description = "Performs spherical linear interpolation between two direction vectors.",
    category = "reference.vectors",
    order = 14
)
public class SlerpVectorsNode extends BaseNode {

    @NodeProperty(displayName = "Preserve Magnitude", category = "Slerp", order = 1)
    private boolean preserveMagnitude = true;

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String INPUT_T_ID = "input_t";

    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_ANGLE_ID = "output_angle";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public SlerpVectorsNode() {
        super(UUID.randomUUID(), "reference.vectors.slerp");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Start vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "End vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_T_ID, "T", "Interpolation parameter", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Slerp result vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_ANGLE_ID, "Angle",
            "Unsigned angle in degrees between normalized A and B", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether slerp input is valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Slerp Vectors";
    }

    @Override
    public String getDescription() {
        return "Performs spherical linear interpolation between two direction vectors.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d aRaw = VectorUtils.toVector(inputValues.get(INPUT_A_ID));
        Vector3d bRaw = VectorUtils.toVector(inputValues.get(INPUT_B_ID));
        Double t = StrictDoubleUtils.requireExactFiniteDouble(inputValues.get(INPUT_T_ID));

        if (!VectorUtils.isFinite(aRaw)) {
            writeInvalid("Vector A must be a finite VECTOR");
            return;
        }
        if (!VectorUtils.isFinite(bRaw)) {
            writeInvalid("Vector B must be a finite VECTOR");
            return;
        }
        if (t == null) {
            writeInvalid("T must be exact finite DOUBLE");
            return;
        }

        double lenA = VectorUtils.safeLength(aRaw);
        double lenB = VectorUtils.safeLength(bRaw);
        if (!VectorUtils.isFinite(lenA) || lenA <= VectorUtils.EPS) {
            writeInvalid("Vector A must be non-zero");
            return;
        }
        if (!VectorUtils.isFinite(lenB) || lenB <= VectorUtils.EPS) {
            writeInvalid("Vector B must be non-zero");
            return;
        }

        Vector3d a = VectorUtils.normalizeByLength(aRaw, lenA);
        Vector3d b = VectorUtils.normalizeByLength(bRaw, lenB);
        if (a == null || b == null) {
            writeInvalid("Normalized direction vectors are not finite");
            return;
        }

        double dot = VectorUtils.safeDot(a, b);
        if (!VectorUtils.isFinite(dot)) {
            writeInvalid("Slerp dot product is not finite");
            return;
        }
        dot = Math.max(-1.0d, Math.min(1.0d, dot));
        double angle = Math.acos(dot);
        if (!VectorUtils.isFinite(angle)) {
            writeInvalid("Slerp angle is not finite");
            return;
        }

        Vector3d perp = VectorUtils.safeSubtract(b, VectorUtils.safeScale(a, dot));
        Vector3d direction;
        if (perp != null && VectorUtils.safeLength(perp) > VectorUtils.EPS) {
            Vector3d perpUnit = VectorUtils.safeNormalize(perp);
            if (perpUnit == null) {
                writeInvalid("Slerp perpendicular axis is not finite");
                return;
            }
            direction = sphericalCombination(a, perpUnit, angle, t);
        } else if (dot > 0.0d) {
            direction = VectorUtils.safeLerp(a, b, t);
            if (direction == null) {
                writeInvalid("Parallel slerp lerp is not finite");
                return;
            }
            direction = VectorUtils.safeNormalize(direction);
        } else {
            Vector3d axis = orthogonalAxis(a);
            if (axis == null) {
                writeInvalid("Antiparallel slerp axis is not finite");
                return;
            }
            direction = sphericalCombination(a, axis, Math.PI, t);
            angle = Math.PI;
        }

        if (direction == null) {
            writeInvalid("Slerp direction is not finite");
            return;
        }

        if (preserveMagnitude) {
            double length = VectorUtils.safeScalarLerp(lenA, lenB, t);
            if (!VectorUtils.isFinite(length)) {
                writeInvalid("Slerp magnitude is not finite");
                return;
            }
            direction = VectorUtils.safeScale(direction, length);
            if (direction == null) {
                writeInvalid("Slerp result magnitude scaling is not finite");
                return;
            }
        }

        VectorData output = VectorUtils.toVectorPort(direction);
        if (output == null) {
            writeInvalid("Slerp result is not finite");
            return;
        }

        double angleDeg = Math.toDegrees(angle);
        if (!VectorUtils.isFinite(angleDeg)) {
            writeInvalid("Slerp angle in degrees is not finite");
            return;
        }

        outputValues.put(OUTPUT_RESULT_ID, output);
        outputValues.put(OUTPUT_ANGLE_ID, angleDeg);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    /** Unit result: {@code a * cos(theta * t) + perp * sin(theta * t)}. */
    private static @Nullable Vector3d sphericalCombination(Vector3d a, Vector3d perp, double theta, double t) {
        double angleT = theta * t;
        if (!VectorUtils.isFinite(angleT)) {
            return null;
        }
        double cos = Math.cos(angleT);
        double sin = Math.sin(angleT);
        if (!VectorUtils.isFinite(cos) || !VectorUtils.isFinite(sin)) {
            return null;
        }
        Vector3d direction = VectorUtils.safeAdd(
            VectorUtils.safeScale(a, cos),
            VectorUtils.safeScale(perp, sin)
        );
        return VectorUtils.safeNormalize(direction);
    }

    private static @Nullable Vector3d orthogonalAxis(Vector3d a) {
        Vector3d axis = VectorUtils.safeCross(a, new Vector3d(0.0d, 1.0d, 0.0d));
        if (axis == null || VectorUtils.safeLength(axis) <= VectorUtils.EPS) {
            axis = VectorUtils.safeCross(a, new Vector3d(0.0d, 0.0d, 1.0d));
        }
        return VectorUtils.safeNormalize(axis);
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_RESULT_ID, null);
        outputValues.put(OUTPUT_ANGLE_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("preserveMagnitude", preserveMagnitude);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object preserveMagnitudeValue = map.get("preserveMagnitude");
        if (preserveMagnitudeValue instanceof Boolean value) {
            preserveMagnitude = value;
        }
    }
}

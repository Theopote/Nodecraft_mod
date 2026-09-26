package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
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

    public SlerpVectorsNode() {
        super(UUID.randomUUID(), "reference.vectors.slerp");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Start vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "End vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_T_ID, "T", "Interpolation parameter", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Slerp result vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_ANGLE_ID, "Angle",
            "Unsigned angle in degrees between normalized A and B", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether slerp input is valid", NodeDataType.BOOLEAN, this));
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
        Object tObj = inputValues.get(INPUT_T_ID);
        if (!VectorUtils.isFinite(aRaw) || !VectorUtils.isFinite(bRaw) || !(tObj instanceof Number tNumber)) {
            writeInvalid();
            return;
        }
        if (aRaw.lengthSquared() < VectorUtils.EPS_SQ || bRaw.lengthSquared() < VectorUtils.EPS_SQ) {
            writeInvalid();
            return;
        }

        double t = tNumber.doubleValue();
        if (!VectorUtils.isFinite(t)) {
            writeInvalid();
            return;
        }

        Vector3d a = new Vector3d(aRaw).normalize();
        Vector3d b = new Vector3d(bRaw).normalize();

        double dot = Math.max(-1.0d, Math.min(1.0d, a.dot(b)));
        double angle = Math.acos(dot);

        // Geodesic on the unit sphere: A*cos(θT) + perp*sin(θT), where perp is the
        // component of B orthogonal to A. Linear-lerp normalize is NOT used for the
        // antiparallel case — it collapses to A for T<0.5 and B for T>0.5.
        Vector3d perp = new Vector3d(b).fma(-dot, a);
        Vector3d direction;
        if (perp.lengthSquared() >= VectorUtils.EPS_SQ) {
            perp.normalize();
            direction = sphericalCombination(a, perp, angle, t);
        } else if (dot > 0.0d) {
            // Nearly parallel — θ≈0; lerp is numerically stable here.
            direction = new Vector3d(a).lerp(b, t).normalize();
        } else {
            // Exact / near antiparallel — deterministic semicircle through orthogonal(A).
            direction = sphericalCombination(a, orthogonalAxis(a), Math.PI, t);
            angle = Math.PI;
        }

        if (preserveMagnitude) {
            double length = aRaw.length() + (bRaw.length() - aRaw.length()) * t;
            direction.mul(length);
        }

        outputValues.put(OUTPUT_RESULT_ID, VectorUtils.toVectorPort(direction));
        outputValues.put(OUTPUT_ANGLE_ID, Math.toDegrees(angle));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    /** Unit result: {@code a * cos(theta * t) + perp * sin(theta * t)}. */
    private static Vector3d sphericalCombination(Vector3d a, Vector3d perp, double theta, double t) {
        double angleT = theta * t;
        Vector3d direction = new Vector3d(a).mul(Math.cos(angleT));
        direction.add(new Vector3d(perp).mul(Math.sin(angleT)));
        return direction.normalize();
    }

    private static Vector3d orthogonalAxis(Vector3d a) {
        Vector3d axis = new Vector3d(a).cross(0.0d, 1.0d, 0.0d);
        if (axis.lengthSquared() < VectorUtils.EPS_SQ) {
            axis = new Vector3d(a).cross(0.0d, 0.0d, 1.0d);
        }
        return axis.normalize();
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_RESULT_ID, null);
        outputValues.put(OUTPUT_ANGLE_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
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

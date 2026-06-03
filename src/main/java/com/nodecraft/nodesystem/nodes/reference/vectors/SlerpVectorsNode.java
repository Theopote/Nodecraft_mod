package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "reference.vectors.slerp",
    displayName = "Slerp Vectors",
    description = "Performs spherical linear interpolation between two direction vectors.",
    category = "reference.vectors",
    order = 16
)
public class SlerpVectorsNode extends BaseNode {

    @NodeProperty(displayName = "Shortest Path", category = "Slerp", order = 1)
    private boolean shortestPath = true;

    @NodeProperty(displayName = "Preserve Magnitude", category = "Slerp", order = 2)
    private boolean preserveMagnitude = true;

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String INPUT_T_ID = "input_t";

    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_ANGLE_ID = "output_angle_radians";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SlerpVectorsNode() {
        super(UUID.randomUUID(), "reference.vectors.slerp");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Start vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "End vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_T_ID, "T", "Interpolation parameter", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Slerp result vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_ANGLE_ID, "Angle (rad)", "Unsigned angle between normalized A and B", NodeDataType.DOUBLE, this));
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
        if (aRaw.lengthSquared() < VectorUtils.EPS || bRaw.lengthSquared() < VectorUtils.EPS) {
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
        if (shortestPath && dot < 0.0d) {
            b.negate();
            dot = -dot;
        }

        double angle = Math.acos(Math.max(-1.0d, Math.min(1.0d, dot)));
        Vector3d direction;
        if (1.0d - dot < 1.0e-6d) {
            direction = new Vector3d(a).lerp(b, t).normalize();
        } else {
            double sinTheta = Math.sin(angle);
            double wA = Math.sin((1.0d - t) * angle) / sinTheta;
            double wB = Math.sin(t * angle) / sinTheta;
            direction = new Vector3d(a).mul(wA).add(new Vector3d(b).mul(wB)).normalize();
        }

        if (preserveMagnitude) {
            double length = aRaw.length() + (bRaw.length() - aRaw.length()) * t;
            direction.mul(length);
        }

        outputValues.put(OUTPUT_RESULT_ID, direction);
        outputValues.put(OUTPUT_ANGLE_ID, angle);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_RESULT_ID, new Vector3d());
        outputValues.put(OUTPUT_ANGLE_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("shortestPath", shortestPath);
        state.put("preserveMagnitude", preserveMagnitude);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object shortestPathValue = map.get("shortestPath");
        if (shortestPathValue instanceof Boolean value) {
            shortestPath = value;
        }
        Object preserveMagnitudeValue = map.get("preserveMagnitude");
        if (preserveMagnitudeValue instanceof Boolean value) {
            preserveMagnitude = value;
        }
    }
}

package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.CurvePathSamplingUtil;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Evaluates point and frame vectors on a path at a normalized parameter.
 */
@NodeInfo(
    id = "geometry.curves.evaluate_curve",
    displayName = "Curve Evaluate",
    description = "Evaluates a curve/path at normalized parameter t and outputs point, tangent, normal, and binormal",
    category = "geometry.curves",
    order = 14
)
public class CurveEvaluateNode extends BaseNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Default t", category = "Evaluate", order = 1)
    private double defaultT = 0.0d;

    @NodeProperty(displayName = "Clamp t", category = "Evaluate", order = 2)
    private boolean clampT = true;

    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_T_ID = "input_t";
    private static final String INPUT_UP_VECTOR_ID = "input_up_vector";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_TANGENT_ID = "output_tangent";
    private static final String OUTPUT_NORMAL_ID = "output_normal";
    private static final String OUTPUT_BINORMAL_ID = "output_binormal";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public CurveEvaluateNode() {
        super(UUID.randomUUID(), "geometry.curves.evaluate_curve");

        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve",
            "Curve to evaluate", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline",
            "Fallback polyline to evaluate when no curve is connected", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line",
            "Fallback line to evaluate when no curve/polyline is connected", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_T_ID, "t",
            "Normalized parameter along path (0..1)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_UP_VECTOR_ID, "Up Vector",
            "Reference up vector used to derive the normal direction", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Point",
            "Evaluated point on path", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_TANGENT_ID, "Tangent",
            "Unit tangent direction at t", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal",
            "Unit normal direction derived from up-vector framing", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_BINORMAL_ID, "Binormal",
            "Unit binormal vector completing the local frame", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length",
            "Total path length used for parameterization", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when evaluation succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolveVertices();
        if (verts == null || verts.size() < 2) {
            writeInvalid();
            return;
        }

        boolean closed = CurvePathSamplingUtil.isClosedPolyline(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        if (unique.size() < 2) {
            writeInvalid();
            return;
        }

        double[] cumulative = CurvePathSamplingUtil.buildCumulative(unique, closed);
        if (cumulative == null) {
            writeInvalid();
            return;
        }
        double total = cumulative[cumulative.length - 1];
        if (total <= EPS) {
            writeInvalid();
            return;
        }

        double t = getInputDouble(INPUT_T_ID, defaultT);
        double normalized = clampT ? clamp(t, 0.0d, 1.0d) : wrap01(t);
        double distance = normalized * total;
        Vector3d point = CurvePathSamplingUtil.sampleAtDistance(unique, closed, cumulative, distance);

        double delta = Math.max(total * 1.0e-4d, 1.0e-4d);
        double backDistance = clampT ? Math.max(0.0d, distance - delta) : wrapDistance(distance - delta, total);
        double forwardDistance = clampT ? Math.min(total, distance + delta) : wrapDistance(distance + delta, total);

        Vector3d prev = CurvePathSamplingUtil.sampleAtDistance(unique, closed, cumulative, backDistance);
        Vector3d next = CurvePathSamplingUtil.sampleAtDistance(unique, closed, cumulative, forwardDistance);

        Vector3d tangent = new Vector3d(next).sub(prev);
        if (tangent.lengthSquared() <= EPS) {
            writeInvalid();
            return;
        }
        tangent.normalize();

        Vector3d up = resolveUpVector(inputValues.get(INPUT_UP_VECTOR_ID));
        Vector3d binormal = new Vector3d(tangent).cross(up);
        if (binormal.lengthSquared() <= EPS) {
            Vector3d fallbackUp = Math.abs(tangent.y) < 0.9d
                ? new Vector3d(0.0d, 1.0d, 0.0d)
                : new Vector3d(1.0d, 0.0d, 0.0d);
            binormal = new Vector3d(tangent).cross(fallbackUp);
            if (binormal.lengthSquared() <= EPS) {
                fallbackUp = new Vector3d(0.0d, 0.0d, 1.0d);
                binormal = new Vector3d(tangent).cross(fallbackUp);
            }
        }
        if (binormal.lengthSquared() <= EPS) {
            writeInvalid();
            return;
        }
        binormal.normalize();

        Vector3d normal = new Vector3d(binormal).cross(tangent);
        if (normal.lengthSquared() <= EPS) {
            writeInvalid();
            return;
        }
        normal.normalize();

        outputValues.put(OUTPUT_POINT_ID, new PointData(point.x, point.y, point.z));
        outputValues.put(OUTPUT_TANGENT_ID, tangent);
        outputValues.put(OUTPUT_NORMAL_ID, normal);
        outputValues.put(OUTPUT_BINORMAL_ID, binormal);
        outputValues.put(OUTPUT_LENGTH_ID, total);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public double getDefaultT() {
        return defaultT;
    }

    public void setDefaultT(double defaultT) {
        if (Double.compare(this.defaultT, defaultT) != 0) {
            this.defaultT = defaultT;
            markDirty();
        }
    }

    public boolean isClampT() {
        return clampT;
    }

    public void setClampT(boolean clampT) {
        if (this.clampT != clampT) {
            this.clampT = clampT;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("defaultT", defaultT);
            put("clampT", clampT);
        }};
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultT") instanceof Number value) {
            setDefaultT(value.doubleValue());
        }
        if (map.get("clampT") instanceof Boolean value) {
            setClampT(value);
        }
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINT_ID, null);
        outputValues.put(OUTPUT_TANGENT_ID, null);
        outputValues.put(OUTPUT_NORMAL_ID, null);
        outputValues.put(OUTPUT_BINORMAL_ID, null);
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Vector3d> resolveVertices() {
        return CurvePathSamplingUtil.resolveVertices(
            inputValues.get(INPUT_CURVE_ID),
            inputValues.get(INPUT_POLYLINE_ID),
            inputValues.get(INPUT_LINE_ID)
        );
    }

    private Vector3d resolveUpVector(Object value) {
        if (value instanceof Vector3d vector) {
            if (vector.lengthSquared() > EPS) {
                return new Vector3d(vector).normalize();
            }
        }
        return new Vector3d(0.0d, 1.0d, 0.0d);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double wrap01(double value) {
        double wrapped = value % 1.0d;
        return wrapped < 0.0d ? wrapped + 1.0d : wrapped;
    }

    private double wrapDistance(double value, double length) {
        if (length <= EPS) {
            return 0.0d;
        }
        double wrapped = value % length;
        return wrapped < 0.0d ? wrapped + length : wrapped;
    }
}

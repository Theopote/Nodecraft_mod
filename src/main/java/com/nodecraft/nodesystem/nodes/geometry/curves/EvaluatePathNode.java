package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.evaluate_path",
    displayName = "Evaluate Path",
    description = "Evaluates a path at a normalized parameter t in [0..1].",
    category = "geometry.curves",
    order = 2
)
public class EvaluatePathNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_PARAMETER_ID = "input_parameter";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_TANGENT_ID = "output_tangent";
    private static final String OUTPUT_PARAMETER_ID = "output_parameter";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public EvaluatePathNode() {
        super(UUID.randomUUID(), "geometry.curves.evaluate_path");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to evaluate (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PARAMETER_ID, "Parameter",
            "Normalized parameter in [0..1] (0=start, 1=end)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Point", "Point at parameter", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_TANGENT_ID, "Tangent", "Unit tangent at parameter", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_PARAMETER_ID, "Parameter", "Clamped parameter used", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when evaluation succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        if (verts == null || verts.size() < 2) {
            writeInvalid();
            return;
        }

        double t = inputValues.get(INPUT_PARAMETER_ID) instanceof Number n ? n.doubleValue() : 0.0d;
        if (!Double.isFinite(t)) {
            writeInvalid();
            return;
        }
        t = Math.max(0.0d, Math.min(1.0d, t));

        boolean closed = PathUtils.isClosed(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        if (unique.size() < 2) {
            writeInvalid();
            return;
        }

        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null) {
            writeInvalid();
            return;
        }
        double total = cumulative[cumulative.length - 1];
        if (total <= EPS) {
            writeInvalid();
            return;
        }

        double distance = t * total;
        Vector3d point = PathUtils.sampleAtDistance(unique, closed, cumulative, distance);

        double delta = Math.max(total * 1.0e-4d, 1.0e-4d);
        double back = closed ? wrapDistance(distance - delta, total) : Math.max(0.0d, distance - delta);
        double forward = closed ? wrapDistance(distance + delta, total) : Math.min(total, distance + delta);
        Vector3d prev = PathUtils.sampleAtDistance(unique, closed, cumulative, back);
        Vector3d next = PathUtils.sampleAtDistance(unique, closed, cumulative, forward);
        Vector3d tangent = new Vector3d(next).sub(prev);
        if (tangent.lengthSquared() <= EPS) {
            writeInvalid();
            return;
        }
        tangent.normalize();

        outputValues.put(OUTPUT_POINT_ID, new PointData(point));
        outputValues.put(OUTPUT_TANGENT_ID, tangent);
        outputValues.put(OUTPUT_PARAMETER_ID, t);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private static double wrapDistance(double value, double length) {
        if (length <= EPS) {
            return 0.0d;
        }
        double wrapped = value % length;
        return wrapped < 0.0d ? wrapped + length : wrapped;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINT_ID, null);
        outputValues.put(OUTPUT_TANGENT_ID, null);
        outputValues.put(OUTPUT_PARAMETER_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

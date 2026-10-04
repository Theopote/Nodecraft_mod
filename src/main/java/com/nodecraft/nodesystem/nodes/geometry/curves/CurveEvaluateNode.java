package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.CurveInputUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.evaluate_curve",
    displayName = "Evaluate Path",
    description = "Evaluates a path at normalized arc-length parameter t and outputs point and tangent.",
    category = "geometry.curves",
    order = 22
)
public class CurveEvaluateNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Default t", category = "Evaluate", order = 1)
    private double defaultT = 0.0d;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_T_ID = "input_t";
    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_TANGENT_ID = "output_tangent";
    private static final String OUTPUT_LENGTH_ID = "output_length";

    public CurveEvaluateNode() {
        super(UUID.randomUUID(), "geometry.curves.evaluate_curve");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to evaluate (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_T_ID, "t",
            "Normalized arc-length parameter along path (0..1)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Point",
            "Evaluated point on path", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_TANGENT_ID, "Tangent",
            "Unit tangent direction at t", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length",
            "Total path length used for parameterization", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when evaluation succeeded", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        if (verts == null || verts.size() < 2) {
            invalidate("Path is missing or invalid");
            return;
        }

        boolean closed = PathUtils.isClosed(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        if (unique.size() < 2) {
            invalidate("Path is missing or invalid");
            return;
        }

        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null) {
            invalidate("Path length could not be computed");
            return;
        }
        double total = cumulative[cumulative.length - 1];
        if (total <= EPS) {
            invalidate("Path has zero length");
            return;
        }

        Double normalized = closed
            ? CurveInputUtils.resolveNormalizedTClosed(this, INPUT_T_ID, defaultT)
            : CurveInputUtils.resolveNormalizedTOpen(this, INPUT_T_ID, defaultT);
        if (normalized == null) {
            invalidate(closed ? "t is connected but invalid" : "t must be finite and in [0, 1]");
            return;
        }

        double distance = normalized * total;
        Vector3d point = PathUtils.sampleAtDistance(unique, closed, cumulative, distance);
        if (!VectorUtils.isFinite(point)) {
            invalidate("Evaluated point is not finite");
            return;
        }

        Vector3d tangent = PathUtils.sampleTangentAtDistance(unique, closed, cumulative, distance);
        if (tangent == null || !VectorUtils.isFinite(tangent)) {
            invalidate("Tangent is degenerate at t");
            return;
        }

        outputValues.put(OUTPUT_POINT_ID, new PointData(point.x, point.y, point.z));
        outputValues.put(OUTPUT_TANGENT_ID, VectorUtils.toVectorPort(tangent));
        outputValues.put(OUTPUT_LENGTH_ID, total);
        markSuccess();
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_POINT_ID, OUTPUT_TANGENT_ID, OUTPUT_LENGTH_ID);
        markInvalid(error);
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

    @Override
    public Object getNodeState() {
        return java.util.Map.of("defaultT", defaultT);
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map<?, ?> map && map.get("defaultT") instanceof Number value) {
            setDefaultT(value.doubleValue());
        }
    }
}

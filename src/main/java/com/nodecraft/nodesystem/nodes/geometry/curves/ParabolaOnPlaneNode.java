package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.parabola_curve",
    displayName = "Parabola On Plane",
    description = "Builds a sampled parabola on a plane from vertex, curvature, x-range, and segment count",
    category = "geometry.curves",
    order = 16
)
public class ParabolaOnPlaneNode extends AbstractCurveNode {
    private static final String INPUT_VERTEX_ID = "input_vertex";
    private static final String INPUT_CURVATURE_ID = "input_curvature";
    private static final String INPUT_X_MIN_ID = "input_x_min";
    private static final String INPUT_X_MAX_ID = "input_x_max";
    private static final String INPUT_SEGMENTS_ID = "input_segments";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_CURVE_ID = "output_curve";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ParabolaOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.curves.parabola_curve");
        addInputPort(new BasePort(INPUT_VERTEX_ID, "Vertex", "Parabola vertex point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_CURVATURE_ID, "Curvature", "Parabola curvature factor (y = a*x^2)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_X_MIN_ID, "X Min", "Minimum local x", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_X_MAX_ID, "X Max", "Maximum local x", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Segments", "Segment count along the parabola", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "X Axis", "Optional in-plane parabola x axis", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_CURVE_ID, "Curve", "Sampled parabola as curve", NodeDataType.CURVE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Sampled parabola boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled parabola points", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when parabola could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d vertex = resolveInputPoint(inputValues.get(INPUT_VERTEX_ID));
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        Object preferredAxisObj = inputValues.get(INPUT_AXIS_ID);

        if (vertex == null) {
            writeInvalidOutputs();
            return;
        }
        double curvature = readDoubleInput(INPUT_CURVATURE_ID, Double.NaN);
        double xMin = readDoubleInput(INPUT_X_MIN_ID, Double.NaN);
        double xMax = readDoubleInput(INPUT_X_MAX_ID, Double.NaN);
        int segments = GenerationLimits.clampSegments(2, readIntInput(INPUT_SEGMENTS_ID, 0));
        if (!Double.isFinite(curvature) || !Double.isFinite(xMin) || !Double.isFinite(xMax) || Math.abs(xMax - xMin) < 1.0e-9d) {
            writeInvalidOutputs();
            return;
        }

        var basis = resolvePlaneBasis(planeObj, preferredAxisObj, PlaneData.XY_PLANE);
        if (basis == null) {
            writeInvalidOutputs();
            return;
        }

        List<Vec3d> pts = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double x = xMin + (xMax - xMin) * t;
            double y = curvature * x * x;
            Vector3d world = new Vector3d(vertex)
                .add(new Vector3d(basis.xAxis()).mul(x))
                .add(new Vector3d(basis.yAxis()).mul(y));
            pts.add(new Vec3d(world.x, world.y, world.z));
        }

        Curve curve = buildLinearCurve(pts);
        outputValues.put(OUTPUT_CURVE_ID, curve);
        outputValues.put(OUTPUT_POLYLINE_ID, new PolylineData(pts));
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(pts));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

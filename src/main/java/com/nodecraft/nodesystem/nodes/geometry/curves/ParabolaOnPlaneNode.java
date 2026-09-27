package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.parabola_curve",
    displayName = "Parabola On Plane",
    description = "Builds a sampled parabola on a plane from vertex, curvature, x-range, and segment count",
    category = "geometry.curves",
    order = 9
)
public class ParabolaOnPlaneNode extends AbstractCurveNode {

    private static final int DEFAULT_SEGMENTS = 32;

    @NodeProperty(displayName = "Default Segments", category = "Parabola", order = 1)
    private int defaultSegments = DEFAULT_SEGMENTS;

    private static final String INPUT_VERTEX_ID = "input_vertex";
    private static final String INPUT_CURVATURE_ID = "input_curvature";
    private static final String INPUT_X_MIN_ID = "input_x_min";
    private static final String INPUT_X_MAX_ID = "input_x_max";
    private static final String INPUT_SEGMENTS_ID = "input_segments";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_POINTS_ID = "output_points";

    public ParabolaOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.curves.parabola_curve");
        addInputPort(new BasePort(INPUT_VERTEX_ID, "Vertex", "Parabola vertex point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_CURVATURE_ID, "Curvature", "Parabola curvature factor (y = a*x^2)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_X_MIN_ID, "X Min", "Minimum local x", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_X_MAX_ID, "X Max", "Maximum local x", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Segments", "Segment count along the parabola", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "X Axis", "Optional in-plane parabola x axis", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Primary parabola path output", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled parabola points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when parabola could be constructed", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d vertex = resolveInputPoint(inputValues.get(INPUT_VERTEX_ID));
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        Object preferredAxisObj = inputValues.get(INPUT_AXIS_ID);

        if (vertex == null) {
            invalidate("Vertex is missing or invalid");
            return;
        }

        Double curvature = resolveFiniteDouble(INPUT_CURVATURE_ID, Double.NaN);
        Double xMin = resolveFiniteDouble(INPUT_X_MIN_ID, Double.NaN);
        Double xMax = resolveFiniteDouble(INPUT_X_MAX_ID, Double.NaN);
        Integer segments = resolveBoundedInteger(INPUT_SEGMENTS_ID, defaultSegments, 2, GenerationLimits.MAX_CURVE_SAMPLES);

        if (curvature == null || xMin == null || xMax == null) {
            invalidate("Curvature, X Min, and X Max must be finite");
            return;
        }
        if (segments == null) {
            invalidate("Segments must be an integer from 2 to " + GenerationLimits.MAX_CURVE_SAMPLES);
            return;
        }
        if (Math.abs(xMax - xMin) < 1.0e-9d) {
            invalidate("X Min and X Max must define a non-zero span");
            return;
        }

        var basis = resolvePlaneBasis(planeObj, preferredAxisObj, PlaneData.XY_PLANE);
        if (basis == null) {
            invalidate("Plane basis could not be constructed");
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

        PolylineData polyline = new PolylineData(pts);
        List<Vector3d> pointVectors = new ArrayList<>(pts.size());
        for (Vec3d point : pts) {
            pointVectors.add(new Vector3d(point.x, point.y, point.z));
        }

        outputValues.put(OUTPUT_PATH_ID, PathData.fromPolyline(polyline));
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(pointVectors));
        markSuccess();
    }

    public int getDefaultSegments() {
        return defaultSegments;
    }

    public void setDefaultSegments(int defaultSegments) {
        if (defaultSegments >= 2
                && defaultSegments <= GenerationLimits.MAX_CURVE_SAMPLES
                && this.defaultSegments != defaultSegments) {
            this.defaultSegments = defaultSegments;
            markDirty();
        }
    }

    private void invalidate(String message) {
        putNullOutputs(OUTPUT_PATH_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        markInvalid(message);
    }
}

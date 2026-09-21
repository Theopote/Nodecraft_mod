package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.orientation.project_curve_to_plane",
    displayName = "Project Curve To Plane",
    description = "Projects a curve, polyline, or line onto a target plane",
    category = "transform.orientation",
    order = 4
)
public class ProjectCurveToPlaneNode extends BaseNode {

    @NodeProperty(displayName = "Sample Curve", category = "Projection", order = 1)
    private boolean sampleCurve = true;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_CURVE_ID = "output_curve";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ProjectCurveToPlaneNode() {
        super(UUID.randomUUID(), "transform.orientation.project_curve_to_plane");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to project (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target plane for projection", NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_CURVE_ID, "Curve", "Projected linear curve", NodeDataType.CURVE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Projected polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Projected points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances", "Absolute distances from source vertices to the plane", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of projected points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when projection succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Projects a curve, polyline, or line onto a target plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(planeObj instanceof PlaneData plane) || !OrientationUtils.isUsablePlane(plane)) {
            writeInvalid();
            return;
        }

        List<Vec3d> sourcePoints = resolveSourcePoints();
        if (sourcePoints.size() < 2) {
            writeInvalid();
            return;
        }

        List<Vector3d> projectedVectors = new ArrayList<>(sourcePoints.size());
        List<Vec3d> projectedVec3d = new ArrayList<>(sourcePoints.size());
        List<Double> distances = new ArrayList<>(sourcePoints.size());
        for (Vec3d point : sourcePoints) {
            Vector3d source = new Vector3d(point.x, point.y, point.z);
            Vector3d projected = plane.projectPoint(source);
            projectedVectors.add(projected);
            projectedVec3d.add(new Vec3d(projected.x, projected.y, projected.z));
            distances.add(Math.abs(plane.signedDistanceTo(source)));
        }

        PolylineData polyline;
        try {
            polyline = new PolylineData(projectedVec3d);
        } catch (IllegalArgumentException ex) {
            writeInvalid();
            return;
        }

        Curve curve = new Curve(Curve.CurveType.LINEAR, 2);
        for (Vec3d point : projectedVec3d) {
            curve.addControlPoint(point);
        }

        outputValues.put(OUTPUT_CURVE_ID, curve);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(projectedVectors));
        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
        outputValues.put(OUTPUT_COUNT_ID, projectedVectors.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public boolean isSampleCurve() {
        return sampleCurve;
    }

    public void setSampleCurve(boolean sampleCurve) {
        if (this.sampleCurve != sampleCurve) {
            this.sampleCurve = sampleCurve;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of("sampleCurve", sampleCurve);
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map<?, ?> map && map.get("sampleCurve") instanceof Boolean value) {
            setSampleCurve(value);
        }
    }

    private List<Vec3d> resolveSourcePoints() {
        Object pathObj = inputValues.get(INPUT_PATH_ID);
        PathData path = PathData.wrap(pathObj);
        if (path != null && path.getKind() == PathData.Kind.CURVE && !sampleCurve) {
            List<Vec3d> controlPoints = path.getCurve().getControlPoints();
            return controlPoints == null ? List.of() : controlPoints;
        }
        List<Vector3d> vertices = PathUtils.resolvePath(pathObj);
        if (vertices == null || vertices.isEmpty()) {
            return List.of();
        }
        List<Vec3d> points = new ArrayList<>(vertices.size());
        for (Vector3d vertex : vertices) {
            points.add(new Vec3d(vertex.x, vertex.y, vertex.z));
        }
        return points;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_CURVE_ID, null);
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_DISTANCES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

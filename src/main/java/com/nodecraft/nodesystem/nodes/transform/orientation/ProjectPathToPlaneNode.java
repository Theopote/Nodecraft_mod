package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.orientation.project_path_to_plane",
    displayName = "Project Path To Plane",
    description = "Projects a path onto a target plane",
    category = "transform.orientation",
    order = 4
)
public class ProjectPathToPlaneNode extends AbstractOrientationNode {

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_DISTANCES_ID = "output_distances";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public ProjectPathToPlaneNode() {
        super("transform.orientation.project_path_to_plane");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to project (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target plane for projection", NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Projected path on the target plane", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Projected points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCES_ID, "Distances", "Absolute distances from source vertices to the plane", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of projected points", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Projects a path onto a target plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        PlaneData plane = planeObj instanceof PlaneData p ? p : null;
        PlaneData normalized = OrientationUtils.resolveNormalizedPlane(plane);
        if (normalized == null) {
            writeInvalid("Plane is missing or invalid");
            return;
        }

        List<Vector3d> sourceVertices = PathUtils.resolvePath(inputValues.get(INPUT_PATH_ID));
        if (sourceVertices == null || sourceVertices.size() < 2) {
            writeInvalid("Path is missing, invalid, or has fewer than 2 vertices");
            return;
        }
        if (sourceVertices.size() > GenerationLimits.MAX_CURVE_SAMPLES) {
            writeInvalid("Path sample count exceeds MAX_CURVE_SAMPLES");
            return;
        }

        List<Vector3d> projectedVectors = new ArrayList<>(sourceVertices.size());
        List<Vec3d> projectedVec3d = new ArrayList<>(sourceVertices.size());
        List<Double> distances = new ArrayList<>(sourceVertices.size());
        for (Vector3d source : sourceVertices) {
            OrientationUtils.PointProjection projection = OrientationUtils.projectPoint(normalized, source);
            if (projection == null) {
                writeInvalid("Path contains a non-finite vertex");
                return;
            }
            projectedVectors.add(projection.projected());
            projectedVec3d.add(new Vec3d(projection.projected().x, projection.projected().y, projection.projected().z));
            distances.add(projection.distance());
        }

        PolylineData polyline;
        try {
            polyline = new PolylineData(projectedVec3d);
        } catch (IllegalArgumentException ex) {
            writeInvalid("Projected path is invalid");
            return;
        }

        outputValues.put(OUTPUT_PATH_ID, PathData.fromPolyline(polyline));
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(projectedVectors));
        outputValues.put(OUTPUT_DISTANCES_ID, List.copyOf(distances));
        outputValues.put(OUTPUT_COUNT_ID, projectedVectors.size());
        markSuccess();
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of();
    }

    @Override
    public void setNodeState(Object state) {
        // stateless
    }

    private void writeInvalid(String error) {
        putNullOutputs(OUTPUT_PATH_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID, OUTPUT_DISTANCES_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }
}

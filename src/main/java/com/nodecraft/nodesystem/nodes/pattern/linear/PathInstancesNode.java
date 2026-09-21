package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "pattern.linear.path_instances",
    displayName = "Path Instances",
    description = "Generates path instance frames (origin + axes) for oriented placement along a path.",
    category = "pattern.linear",
    order = 4
)
public class PathInstancesNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(displayName = "Deduplicate Anchors", category = "Instances", order = 1)
    private boolean deduplicateAnchors = true;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_PATH_POINTS_ID = "input_path_points";
    private static final String INPUT_UP_VECTOR_ID = "input_up_vector";

    private static final String OUTPUT_ORIGINS_ID = "output_origins";
    private static final String OUTPUT_X_AXES_ID = "output_x_axes";
    private static final String OUTPUT_Y_AXES_ID = "output_y_axes";
    private static final String OUTPUT_Z_AXES_ID = "output_z_axes";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PathInstancesNode() {
        super(UUID.randomUUID(), "pattern.linear.path_instances");
        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to sample (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PATH_POINTS_ID, "Path Points",
            "Fallback ordered point list when Path is unconnected", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_UP_VECTOR_ID, "Up Vector", "Reference up vector for frame construction", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_ORIGINS_ID, "Origins", "Frame origins along path", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_X_AXES_ID, "X Axes", "Frame X axes (tangent)", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_Y_AXES_ID, "Y Axes", "Frame Y axes (normal)", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_Z_AXES_ID, "Z Axes", "Frame Z axes (binormal)", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of generated instance frames", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame generation succeeds", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates path instance frames (origin + axes) for oriented placement along a path.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> points = resolvePathPoints();
        if (points.size() < 2) {
            writeInvalid();
            return;
        }

        Vector3d up = resolveUp(inputValues.get(INPUT_UP_VECTOR_ID));
        int pointLimit = GenerationLimits.clampPositiveCount(points.size());
        List<Vector3d> origins = new ArrayList<>(pointLimit);
        List<Vector3d> xAxes = new ArrayList<>(pointLimit);
        List<Vector3d> yAxes = new ArrayList<>(pointLimit);
        List<Vector3d> zAxes = new ArrayList<>(pointLimit);

        for (int i = 0; i < pointLimit; i++) {
            Vector3d origin = points.get(i);
            Vector3d tangent = computeTangent(points, i);
            if (tangent.lengthSquared() <= EPSILON) {
                continue;
            }
            tangent.normalize();

            Vector3d binormal = new Vector3d(tangent).cross(up);
            if (binormal.lengthSquared() <= EPSILON) {
                Vector3d fallback = Math.abs(tangent.y) < 0.9d
                    ? new Vector3d(0.0d, 1.0d, 0.0d)
                    : new Vector3d(1.0d, 0.0d, 0.0d);
                binormal = new Vector3d(tangent).cross(fallback);
            }
            if (binormal.lengthSquared() <= EPSILON) {
                continue;
            }
            binormal.normalize();

            Vector3d normal = new Vector3d(binormal).cross(tangent);
            if (normal.lengthSquared() <= EPSILON) {
                continue;
            }
            normal.normalize();

            origins.add(new Vector3d(origin));
            xAxes.add(new Vector3d(tangent));
            yAxes.add(new Vector3d(normal));
            zAxes.add(new Vector3d(binormal));
        }

        if (origins.isEmpty()) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_ORIGINS_ID, SpatialValueResolver.toPointDataList(origins));
        outputValues.put(OUTPUT_X_AXES_ID, List.copyOf(xAxes));
        outputValues.put(OUTPUT_Y_AXES_ID, List.copyOf(yAxes));
        outputValues.put(OUTPUT_Z_AXES_ID, List.copyOf(zAxes));
        outputValues.put(OUTPUT_COUNT_ID, origins.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_ORIGINS_ID, List.of());
        outputValues.put(OUTPUT_X_AXES_ID, List.of());
        outputValues.put(OUTPUT_Y_AXES_ID, List.of());
        outputValues.put(OUTPUT_Z_AXES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Vector3d> resolvePathPoints() {
        List<Vector3d> resolved = PathUtils.resolvePathOrPointList(
            inputValues.get(INPUT_PATH_ID),
            inputValues.get(INPUT_PATH_POINTS_ID)
        );

        if (!deduplicateAnchors) {
            return resolved;
        }
        Set<BlockPos> unique = new LinkedHashSet<>();
        List<Vector3d> deduplicated = new ArrayList<>();
        for (Vector3d point : resolved) {
            BlockPos blockPos = BlockPos.ofFloored(point.x, point.y, point.z);
            if (unique.add(blockPos)) {
                deduplicated.add(new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
            }
        }
        return deduplicated;
    }

    private Vector3d resolveUp(Object value) {
        Vector3d resolved = SpatialValueResolver.resolveVector(value);
        if (resolved != null && resolved.lengthSquared() > EPSILON) {
            return new Vector3d(resolved).normalize();
        }
        return new Vector3d(0.0d, 1.0d, 0.0d);
    }

    private Vector3d computeTangent(List<Vector3d> points, int index) {
        if (index == 0) {
            return new Vector3d(points.get(1)).sub(points.get(0));
        }
        if (index == points.size() - 1) {
            return new Vector3d(points.get(index)).sub(points.get(index - 1));
        }
        return new Vector3d(points.get(index + 1)).sub(points.get(index - 1));
    }
}

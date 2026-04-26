package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "transform.basic_transforms.transform_by_frames",
    displayName = "Transform Points by Frames",
    description = "Transforms local points by frame origin and basis axes into world-space positions.",
    category = "transform.basic_transforms",
    order = 12
)
public class TransformPointsByFramesNode extends BaseNode {

    @NodeProperty(displayName = "Normalize Axes", category = "Frames", order = 1)
    private boolean normalizeAxes = true;

    @NodeProperty(displayName = "Use Shortest Frame List", category = "Frames", order = 2)
    private boolean useShortestFrameList = true;

    private static final String INPUT_LOCAL_POINTS_ID = "input_local_points";
    private static final String INPUT_ORIGINS_ID = "input_origins";
    private static final String INPUT_X_AXES_ID = "input_x_axes";
    private static final String INPUT_Y_AXES_ID = "input_y_axes";
    private static final String INPUT_Z_AXES_ID = "input_z_axes";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_FRAME_COUNT_ID = "output_frame_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TransformPointsByFramesNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.transform_by_frames");

        addInputPort(new BasePort(INPUT_LOCAL_POINTS_ID, "Local Points", "Point list in local frame coordinates", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_ORIGINS_ID, "Origins", "Frame origins list", NodeDataType.VECTOR_LIST, this));
        addInputPort(new BasePort(INPUT_X_AXES_ID, "X Axes", "Frame X axes list", NodeDataType.VECTOR_LIST, this));
        addInputPort(new BasePort(INPUT_Y_AXES_ID, "Y Axes", "Frame Y axes list", NodeDataType.VECTOR_LIST, this));
        addInputPort(new BasePort(INPUT_Z_AXES_ID, "Z Axes", "Frame Z axes list", NodeDataType.VECTOR_LIST, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "World-space transformed points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FRAME_COUNT_ID, "Frame Count", "Number of frames actually used", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame transform succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Transform Points by Frames";
    }

    @Override
    public String getDescription() {
        return "Transforms local points by frame origin and basis axes into world-space positions.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object localObj = inputValues.get(INPUT_LOCAL_POINTS_ID);
        Object originsObj = inputValues.get(INPUT_ORIGINS_ID);
        Object xAxesObj = inputValues.get(INPUT_X_AXES_ID);
        Object yAxesObj = inputValues.get(INPUT_Y_AXES_ID);
        Object zAxesObj = inputValues.get(INPUT_Z_AXES_ID);

        if (!(localObj instanceof List<?> localList)
            || !(originsObj instanceof List<?> originsList)
            || !(xAxesObj instanceof List<?> xList)
            || !(yAxesObj instanceof List<?> yList)
            || !(zAxesObj instanceof List<?> zList)) {
            writeInvalid();
            return;
        }

        List<Vector3d> localPoints = resolvePoints(localList);
        List<Vector3d> origins = resolvePoints(originsList);
        List<Vector3d> xAxes = resolvePoints(xList);
        List<Vector3d> yAxes = resolvePoints(yList);
        List<Vector3d> zAxes = resolvePoints(zList);
        if (localPoints.isEmpty() || origins.isEmpty() || xAxes.isEmpty() || yAxes.isEmpty() || zAxes.isEmpty()) {
            writeInvalid();
            return;
        }

        int frameCount = useShortestFrameList
            ? Math.min(Math.min(origins.size(), xAxes.size()), Math.min(yAxes.size(), zAxes.size()))
            : Math.max(Math.max(origins.size(), xAxes.size()), Math.max(yAxes.size(), zAxes.size()));
        if (frameCount <= 0) {
            writeInvalid();
            return;
        }

        List<Vector3d> out = new ArrayList<>(frameCount * localPoints.size());
        for (int i = 0; i < frameCount; i++) {
            Vector3d origin = getByMode(origins, i);
            Vector3d xAxis = getByMode(xAxes, i);
            Vector3d yAxis = getByMode(yAxes, i);
            Vector3d zAxis = getByMode(zAxes, i);
            if (origin == null || xAxis == null || yAxis == null || zAxis == null) {
                if (useShortestFrameList) {
                    break;
                }
                continue;
            }

            Vector3d x = new Vector3d(xAxis);
            Vector3d y = new Vector3d(yAxis);
            Vector3d z = new Vector3d(zAxis);
            if (normalizeAxes) {
                normalizeSafe(x);
                normalizeSafe(y);
                normalizeSafe(z);
            }

            for (Vector3d local : localPoints) {
                Vector3d world = new Vector3d(origin)
                    .add(new Vector3d(x).mul(local.x))
                    .add(new Vector3d(y).mul(local.y))
                    .add(new Vector3d(z).mul(local.z));
                out.add(world);
            }
        }

        if (out.isEmpty()) {
            writeInvalid();
            return;
        }
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(out));
        outputValues.put(OUTPUT_COUNT_ID, out.size());
        outputValues.put(OUTPUT_FRAME_COUNT_ID, frameCount);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_FRAME_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d getByMode(List<Vector3d> list, int index) {
        if (list.isEmpty()) return null;
        if (index < list.size()) return list.get(index);
        return useShortestFrameList ? null : list.get(list.size() - 1);
    }

    private void normalizeSafe(Vector3d v) {
        if (v.lengthSquared() > 1.0e-12d) {
            v.normalize();
        }
    }

    private List<Vector3d> resolvePoints(List<?> raw) {
        List<Vector3d> out = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            Vector3d point = resolvePoint(entry);
            if (point != null) {
                out.add(point);
            }
        }
        return out;
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof Vector3d v) return new Vector3d(v);
        if (value instanceof Vec3d v) return new Vector3d(v.x, v.y, v.z);
        if (value instanceof PointData p) return new Vector3d(p.getPosition());
        if (value instanceof BlockPos b) return new Vector3d(b.getX(), b.getY(), b.getZ());
        return null;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("normalizeAxes", normalizeAxes);
        state.put("useShortestFrameList", useShortestFrameList);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object normalizeValue = map.get("normalizeAxes");
        if (normalizeValue instanceof Boolean value) {
            normalizeAxes = value;
        }
        Object shortestValue = map.get("useShortestFrameList");
        if (shortestValue instanceof Boolean value) {
            useShortestFrameList = value;
        }
    }
}

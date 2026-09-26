package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.orientation.align_to_surface",
    displayName = "Align Points To Surface Normals",
    description = "Builds oriented frames per point by aligning local up axis to surface normals.",
    category = "transform.orientation",
    order = 2
)
public class AlignPointsToSurfaceNormalsNode extends BaseNode {

    public enum UpAxis {
        X,
        Y,
        Z
    }

    @NodeProperty(
        displayName = "Local Up Axis",
        category = "Orientation",
        order = 1,
        description = "Local frame axis that should align to the target surface normal"
    )
    private UpAxis localUpAxis = UpAxis.Y;

    @NodeProperty(displayName = "Use Shortest List", category = "Orientation", order = 2)
    private boolean useShortestList = true;

    private static final double EPS = 1.0e-12d;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_NORMALS_ID = "input_normals";
    private static final String INPUT_FORWARD_HINT_ID = "input_forward_hint";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_X_AXES_ID = "output_x_axes";
    private static final String OUTPUT_Y_AXES_ID = "output_y_axes";
    private static final String OUTPUT_Z_AXES_ID = "output_z_axes";
    private static final String OUTPUT_PLANES_ID = "output_planes";
    private static final String OUTPUT_FRAMES_ID = "output_frames";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public AlignPointsToSurfaceNormalsNode() {
        super(UUID.randomUUID(), "transform.orientation.align_to_surface");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Point list to align", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_NORMALS_ID, "Normals", "Target surface normal list", NodeDataType.VECTOR_LIST, this));
        addInputPort(new BasePort(INPUT_FORWARD_HINT_ID, "Forward Hint", "Optional forward hint vector for stable tangent orientation", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Aligned point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_X_AXES_ID, "X Axes", "Frame X axes", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_Y_AXES_ID, "Y Axes", "Frame Y axes", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_Z_AXES_ID, "Z Axes", "Frame Z axes", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLANES_ID, "Planes", "Plane list from point + normal", NodeDataType.PLANE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FRAMES_ID, "Frames", "Oriented frames (origin + X/Y/Z)", NodeDataType.FRAME_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of aligned frames", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when alignment succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Align Points To Surface Normals";
    }

    @Override
    public String getDescription() {
        return "Builds oriented frames per point by aligning local up axis to surface normals.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> points = SpatialValueResolver.resolvePointList(inputValues.get(INPUT_POINTS_ID));
        List<Vector3d> normals = SpatialValueResolver.resolveVectorList(inputValues.get(INPUT_NORMALS_ID));
        if (points.isEmpty() || normals.isEmpty()) {
            writeInvalid();
            return;
        }

        int count = useShortestList ? Math.min(points.size(), normals.size()) : Math.max(points.size(), normals.size());

        Vector3d forwardHint = SpatialValueResolver.resolveVector(inputValues.get(INPUT_FORWARD_HINT_ID));
        if (!OrientationUtils.isUsableDirection(forwardHint)) {
            forwardHint = new Vector3d(1.0d, 0.0d, 0.0d);
        } else {
            if (forwardHint != null) {
                forwardHint.normalize();
            }
        }

        List<Vector3d> outPoints = new ArrayList<>(count);
        List<Vector3d> xAxes = new ArrayList<>(count);
        List<Vector3d> yAxes = new ArrayList<>(count);
        List<Vector3d> zAxes = new ArrayList<>(count);
        List<PlaneData> planes = new ArrayList<>(count);
        List<FrameData> frames = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            Vector3d p = getByMode(points, i);
            Vector3d n = getByMode(normals, i);
            if (!OrientationUtils.isFinite(p) || !OrientationUtils.isUsableDirection(n)) {
                if (useShortestList) {
                    break;
                }
                continue;
            }
            Vector3d up = null;
            if (n != null) {
                up = new Vector3d(n).normalize();
            }

            Vector3d tangent = null;
            if (forwardHint != null) {
                tangent = new Vector3d(forwardHint);
            }
            if (tangent != null && Math.abs(tangent.dot(up)) > 0.999d) {
                if (up != null) {
                    tangent = Math.abs(up.y) < 0.9d ? new Vector3d(0.0d, 1.0d, 0.0d) : new Vector3d(1.0d, 0.0d, 0.0d);
                }
            }
            if (tangent != null) {
                if (up != null) {
                    tangent = tangent.sub(new Vector3d(up).mul(tangent.dot(up)));
                }
            }
            if (tangent != null && tangent.lengthSquared() <= EPS) {
                tangent = new Vector3d(1.0d, 0.0d, 0.0d);
                if (up != null) {
                    tangent.sub(new Vector3d(up).mul(tangent.dot(up)));
                }
                if (tangent.lengthSquared() <= EPS) {
                    tangent = new Vector3d(0.0d, 0.0d, 1.0d);
                    if (up != null) {
                        tangent.sub(new Vector3d(up).mul(tangent.dot(up)));
                    }
                }
            }
            if (!OrientationUtils.isUsableDirection(tangent)) {
                if (useShortestList) {
                    break;
                }
                continue;
            }
            if (tangent != null) {
                tangent.normalize();
            }
            Vector3d bitangent = null;
            if (up != null) {
                bitangent = new Vector3d(up).cross(tangent);
            }
            if (!OrientationUtils.isUsableDirection(bitangent)) {
                if (useShortestList) {
                    break;
                }
                continue;
            }
            if (bitangent != null) {
                bitangent.normalize();
            }

            Vector3d x = new Vector3d(1.0d, 0.0d, 0.0d);
            Vector3d y = new Vector3d(0.0d, 1.0d, 0.0d);
            Vector3d z = new Vector3d(0.0d, 0.0d, 1.0d);

            UpAxis axis = localUpAxis == null ? UpAxis.Y : localUpAxis;
            switch (axis) {
                case X -> {
                    x.set(up);
                    y.set(tangent);
                    z.set(bitangent);
                }
                case Y -> {
                    x.set(tangent);
                    y.set(up);
                    z.set(bitangent);
                }
                case Z -> {
                    x.set(tangent);
                    y.set(bitangent);
                    z.set(up);
                }
            }

            outPoints.add(new Vector3d(p));
            xAxes.add(x);
            yAxes.add(y);
            zAxes.add(z);
            if (up != null) {
                planes.add(new PlaneData(new Vector3d(p), new Vector3d(up)));
            }
            frames.add(new FrameData(p, x, y, z));
        }

        if (outPoints.isEmpty()) {
            writeInvalid();
            return;
        }
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(outPoints));
        outputValues.put(OUTPUT_X_AXES_ID, List.copyOf(xAxes));
        outputValues.put(OUTPUT_Y_AXES_ID, List.copyOf(yAxes));
        outputValues.put(OUTPUT_Z_AXES_ID, List.copyOf(zAxes));
        outputValues.put(OUTPUT_PLANES_ID, List.copyOf(planes));
        outputValues.put(OUTPUT_FRAMES_ID, List.copyOf(frames));
        outputValues.put(OUTPUT_COUNT_ID, outPoints.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_X_AXES_ID, List.of());
        outputValues.put(OUTPUT_Y_AXES_ID, List.of());
        outputValues.put(OUTPUT_Z_AXES_ID, List.of());
        outputValues.put(OUTPUT_PLANES_ID, List.of());
        outputValues.put(OUTPUT_FRAMES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d getByMode(List<Vector3d> list, int index) {
        if (list.isEmpty()) return null;
        if (index < list.size()) return list.get(index);
        return useShortestList ? null : list.getLast();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("localUpAxis", localUpAxis != null ? localUpAxis.name() : UpAxis.Y.name());
        state.put("useShortestList", useShortestList);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object axisValue = map.get("localUpAxis");
        if (axisValue instanceof String text) {
            try {
                localUpAxis = UpAxis.valueOf(text);
            } catch (IllegalArgumentException ignored) {
                localUpAxis = UpAxis.Y;
            }
        }
        Object shortestValue = map.get("useShortestList");
        if (shortestValue instanceof Boolean value) {
            useShortestList = value;
        }
    }
}

package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.orientation.align_to_surface",
    displayName = "Align Points To Surface Normals",
    description = "Builds oriented frames per point by aligning local up axis to surface normals.",
    category = "transform.orientation",
    order = 2
)
public class AlignPointsToSurfaceNormalsNode extends AbstractOrientationNode {

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

    public AlignPointsToSurfaceNormalsNode() {
        super("transform.orientation.align_to_surface");

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
        addValidAndErrorOutputs();
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
        List<Vector3d> points = PointUtils.resolveStrictPointListBounded(
            inputValues.get(INPUT_POINTS_ID),
            GenerationLimits.MAX_LAYOUT_INSTANCES
        );
        List<Vector3d> normals = VectorUtils.resolveStrictVectorListBounded(
            inputValues.get(INPUT_NORMALS_ID),
            GenerationLimits.MAX_LAYOUT_INSTANCES
        );
        if (points == null || normals == null) {
            writeInvalid("Points or Normals list is missing, empty, invalid, or exceeds MAX_LAYOUT_INSTANCES");
            return;
        }
        if (points.size() != normals.size()) {
            writeInvalid("Points and Normals must have equal length");
            return;
        }

        boolean hintConnected = OptionalPortDrive.isConnected(this, INPUT_FORWARD_HINT_ID);
        Vector3d connectedHint = hintConnected
            ? OptionalPortDrive.resolveOptionalVector(this, INPUT_FORWARD_HINT_ID, null)
            : null;
        if (hintConnected && connectedHint == null) {
            writeInvalid("Forward Hint connected but invalid");
            return;
        }

        List<Vector3d> outPoints = new ArrayList<>(points.size());
        List<Vector3d> xAxes = new ArrayList<>(points.size());
        List<Vector3d> yAxes = new ArrayList<>(points.size());
        List<Vector3d> zAxes = new ArrayList<>(points.size());
        List<PlaneData> planes = new ArrayList<>(points.size());
        List<FrameData> frames = new ArrayList<>(points.size());

        UpAxis axis = localUpAxis == null ? UpAxis.Y : localUpAxis;
        for (int i = 0; i < points.size(); i++) {
            Vector3d p = points.get(i);
            Vector3d n = normals.get(i);
            if (!VectorUtils.isNonZero(n)) {
                writeInvalid("Normals list contains a zero-length vector");
                return;
            }

            Vector3d up = new Vector3d(n).normalize();
            Vector3d tangent = resolveTangent(up, connectedHint, hintConnected);
            if (tangent == null) {
                writeInvalid(hintConnected
                    ? "Forward Hint has zero length when projected onto the tangent plane"
                    : "Could not resolve tangent direction");
                return;
            }

            FrameUtils.LocalUpAxis frameAxis = switch (axis) {
                case X -> FrameUtils.LocalUpAxis.X;
                case Y -> FrameUtils.LocalUpAxis.Y;
                case Z -> FrameUtils.LocalUpAxis.Z;
            };
            FrameData frame = FrameUtils.fromNormalUpAxis(p, up, tangent, frameAxis);
            if (frame == null) {
                writeInvalid("Could not build orthonormal frame");
                return;
            }

            outPoints.add(new Vector3d(p));
            xAxes.add(new Vector3d(frame.getXAxis()));
            yAxes.add(new Vector3d(frame.getYAxis()));
            zAxes.add(new Vector3d(frame.getZAxis()));
            planes.add(new PlaneData(new Vector3d(p), new Vector3d(up)));
            frames.add(frame);
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(outPoints));
        outputValues.put(OUTPUT_X_AXES_ID, List.copyOf(xAxes));
        outputValues.put(OUTPUT_Y_AXES_ID, List.copyOf(yAxes));
        outputValues.put(OUTPUT_Z_AXES_ID, List.copyOf(zAxes));
        outputValues.put(OUTPUT_PLANES_ID, List.copyOf(planes));
        outputValues.put(OUTPUT_FRAMES_ID, List.copyOf(frames));
        outputValues.put(OUTPUT_COUNT_ID, outPoints.size());
        markSuccess();
    }

    private @Nullable Vector3d resolveTangent(Vector3d up, @Nullable Vector3d connectedHint, boolean hintConnected) {
        if (hintConnected) {
            return FrameUtils.projectOntoTangentPlane(connectedHint, up);
        }
        FrameData auto = FrameUtils.fromNormal(new Vector3d(), up, null);
        if (auto == null) {
            return null;
        }
        return new Vector3d(auto.getXAxis());
    }

    private void writeInvalid(String error) {
        putEmptyListOutputs(
            OUTPUT_POINTS_ID,
            OUTPUT_X_AXES_ID,
            OUTPUT_Y_AXES_ID,
            OUTPUT_Z_AXES_ID,
            OUTPUT_PLANES_ID,
            OUTPUT_FRAMES_ID
        );
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("localUpAxis", localUpAxis != null ? localUpAxis.name() : UpAxis.Y.name());
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
    }
}

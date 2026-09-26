package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.basic_transforms.transform_by_frames",
    displayName = "Transform Points by Frames",
    description = "Transforms local POINT_LIST by FRAME_LIST into world-space positions (cartesian: Frame0 x all points, Frame1 x all points, ...).",
    category = "transform.basic_transforms",
    order = 6
)
public class TransformPointsByFramesNode extends BaseNode {

    private static final String INPUT_LOCAL_POINTS_ID = "input_local_points";
    private static final String INPUT_FRAMES_ID = "input_frames";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TransformPointsByFramesNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.transform_by_frames");

        addInputPort(new BasePort(INPUT_LOCAL_POINTS_ID, "Local Points", "Point list in local frame coordinates", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_FRAMES_ID, "Frames", "Frame list defining placement orientation", NodeDataType.FRAME_LIST, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "World-space transformed points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame transform succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> localPoints = PointUtils.resolveStrictPointList(inputValues.get(INPUT_LOCAL_POINTS_ID));
        List<FrameData> frames = resolveFrameList(inputValues.get(INPUT_FRAMES_ID));

        if (localPoints == null || frames.isEmpty()) {
            writeInvalid();
            return;
        }

        List<Vector3d> out = new ArrayList<>(frames.size() * localPoints.size());
        for (FrameData frame : frames) {
            if (frame == null) {
                writeInvalid();
                return;
            }
            FrameData basis = frame.orthonormalized();
            if (basis == null) {
                writeInvalid();
                return;
            }
            Vector3d origin = basis.getOrigin();
            Vector3d x = basis.getXAxis();
            Vector3d y = basis.getYAxis();
            Vector3d z = basis.getZAxis();

            for (Vector3d local : localPoints) {
                Vector3d world = new Vector3d(origin)
                    .add(new Vector3d(x).mul(local.x))
                    .add(new Vector3d(y).mul(local.y))
                    .add(new Vector3d(z).mul(local.z));
                out.add(world);
            }
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(out));
        outputValues.put(OUTPUT_COUNT_ID, out.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private static List<FrameData> resolveFrameList(@Nullable Object value) {
        if (!(value instanceof List<?> raw) || raw.isEmpty()) {
            return List.of();
        }
        List<FrameData> frames = new ArrayList<>(raw.size());
        for (Object element : raw) {
            if (!(element instanceof FrameData frame)) {
                return List.of();
            }
            frames.add(frame);
        }
        return frames;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

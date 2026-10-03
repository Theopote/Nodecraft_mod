package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
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
public class TransformPointsByFramesNode extends AbstractBasicTransformNode {

    private static final String INPUT_LOCAL_POINTS_ID = "input_local_points";
    private static final String INPUT_FRAMES_ID = "input_frames";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public TransformPointsByFramesNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.transform_by_frames");

        addInputPort(new BasePort(INPUT_LOCAL_POINTS_ID, "Local Points", "Point list in local frame coordinates", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_FRAMES_ID, "Frames", "Frame list defining placement orientation", NodeDataType.FRAME_LIST, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "World-space transformed points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output points", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object pointsObj = inputValues.get(INPUT_LOCAL_POINTS_ID);
        if (pointsObj instanceof Collection<?> collection && collection.size() > GenerationLimits.MAX_LIST_ELEMENTS) {
            writeInvalid("Point list exceeds limit (" + GenerationLimits.MAX_LIST_ELEMENTS + ")");
            return;
        }

        List<Vector3d> localPoints = PointUtils.resolveStrictPointListBounded(
            pointsObj,
            GenerationLimits.MAX_LIST_ELEMENTS
        );
        if (localPoints == null) {
            if (pointsObj instanceof Collection<?> collection && !collection.isEmpty()) {
                writeInvalid("Point list contains an invalid point");
            } else {
                writeInvalid("Point list is required");
            }
            return;
        }

        Object framesObj = inputValues.get(INPUT_FRAMES_ID);
        List<FrameData> frames = FrameUtils.resolveStrictFrameListBounded(
            framesObj,
            GenerationLimits.MAX_LIST_ELEMENTS
        );
        if (frames == null) {
            writeInvalid("Frame list is required, over budget, or contains a non-canonical FRAME");
            return;
        }

        long outputCount = (long) frames.size() * (long) localPoints.size();
        if (outputCount > GenerationLimits.MAX_LIST_ELEMENTS) {
            writeInvalid("Transform workload exceeds limit (" + GenerationLimits.MAX_LIST_ELEMENTS + ")");
            return;
        }

        List<Vector3d> out = new ArrayList<>((int) outputCount);
        for (FrameData frame : frames) {
            Vector3d origin = frame.getOrigin();
            Vector3d x = frame.getXAxis();
            Vector3d y = frame.getYAxis();
            Vector3d z = frame.getZAxis();

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
        markSuccess();
    }

    private void writeInvalid(String error) {
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }
}

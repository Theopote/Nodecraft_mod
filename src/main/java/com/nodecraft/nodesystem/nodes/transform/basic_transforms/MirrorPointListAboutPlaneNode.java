package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryMirror;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.basic_transforms.mirror_point_list_plane",
    displayName = "Mirror Point List About Plane",
    description = "Mirrors each point in a POINT_LIST about a plane",
    category = "transform.basic_transforms",
    order = 5
)
public class MirrorPointListAboutPlaneNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MirrorPointListAboutPlaneNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.mirror_point_list_plane");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "Point list to mirror",
            NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Mirror plane",
            NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points",
            "Mirrored point list",
            NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of mirrored points",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when mirroring succeeded",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Mirror Point List About Plane";
    }

    @Override
    public String getDescription() {
        return "Mirrors each point in a POINT_LIST about a plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(planeObj instanceof PlaneData planeRaw)) {
            writeInvalid();
            return;
        }
        PlaneData plane = planeRaw.normalized();
        if (plane == null) {
            writeInvalid();
            return;
        }

        List<Vector3d> sources = PointUtils.resolveStrictPointList(inputValues.get(INPUT_POINTS_ID));
        if (sources == null) {
            writeInvalid();
            return;
        }

        List<Vector3d> mirrored = new ArrayList<>(sources.size());
        for (Vector3d point : sources) {
            mirrored.add(GeometryMirror.mirrorPoint(point, plane));
        }

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(mirrored));
        outputValues.put(OUTPUT_COUNT_ID, mirrored.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.util.Coordinate;
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
    id = "output.preview.preview_points",
    displayName = "Preview Points",
    description = "Previews one or more reference points before voxelization",
    category = "output.preview",
    order = 2
)
public class PreviewPointsNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_POINTS_ID = "input_points";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";
    private static final String OUTPUT_POINT_COUNT_ID = "output_point_count";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Point Size", category = "Preview", order = 2)
    private float pointSize = 0.35f;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 3)
    private int duration = 30;

    public PreviewPointsNode() {
        super(UUID.randomUUID(), "output.preview.preview_points");
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", "Single reference point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "List of reference points", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Active preview identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_POINT_COUNT_ID, "Point Count", "Number of points shown", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Previews one or more reference points before voxelization";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Coordinate> points = new ArrayList<>();
        collectPoint(inputValues.get(INPUT_POINT_ID), points);
        collectPoints(inputValues.get(INPUT_POINTS_ID), points);

        boolean success = false;
        String previewId = null;
        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
        } else if (!points.isEmpty()) {
            PreviewOptions options = PreviewOptions.createPoints().setDuration(duration);
            options.pointSize = pointSize;
            previewId = PreviewManager.showPoints(getId().toString(), points, options);
            success = previewId != null;
        } else {
            PreviewManager.hideNodePreviews(getId().toString());
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewId);
        outputValues.put(OUTPUT_POINT_COUNT_ID, points.size());
    }

    private void collectPoints(Object value, List<Coordinate> out) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                collectPoint(item, out);
            }
        }
    }

    private void collectPoint(Object value, List<Coordinate> out) {
        if (value instanceof Coordinate coordinate) {
            out.add(coordinate);
        } else if (value instanceof PointData pointData) {
            Vector3d p = pointData.getPosition();
            out.add(new Coordinate((int) Math.round(p.x), (int) Math.round(p.y), (int) Math.round(p.z)));
        } else if (value instanceof Vector3d vector) {
            out.add(new Coordinate((int) Math.round(vector.x), (int) Math.round(vector.y), (int) Math.round(vector.z)));
        } else if (value instanceof Vec3d vec) {
            out.add(new Coordinate((int) Math.round(vec.x), (int) Math.round(vec.y), (int) Math.round(vec.z)));
        } else if (value instanceof BlockPos pos) {
            out.add(new Coordinate(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("previewEnabled", previewEnabled);
        state.put("pointSize", pointSize);
        state.put("duration", duration);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("previewEnabled") instanceof Boolean bool) {
                previewEnabled = bool;
            }
            if (map.get("pointSize") instanceof Number number) {
                pointSize = Math.max(0.05f, number.floatValue());
            }
            if (map.get("duration") instanceof Number number) {
                duration = Math.max(1, number.intValue());
            }
        }
    }
}
